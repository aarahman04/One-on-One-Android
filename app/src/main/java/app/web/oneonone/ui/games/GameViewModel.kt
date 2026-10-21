package app.web.oneonone.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.games.*
import app.web.oneonone.data.realtime.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class GameUi(
    val stage: GameStage = GameStage.Idle,
    val match: Match? = null,
    val me: String = "",
    val busy: Boolean = false,
) {
    val seat get() = match?.seat(me) ?: 0
    val partner get() = match?.players?.firstOrNull { it != me }.orEmpty()
    val myTurn get() = match?.status == MatchStatus.Playing && match.turn == me && !busy
}

/** The server owns every game (contract §12); this only maps its snapshots to screens and sends actions. */
@HiltViewModel
class GameViewModel @Inject constructor(private val service: GameService) : ViewModel() {
    private val session = MatchSession(service, viewModelScope)
    private val hub = MutableStateFlow(false)
    private val dismissed = MutableStateFlow<String?>(null)
    val ui: StateFlow<GameUi> = combine(session.match, session.busy, hub, dismissed) { match, busy, hub, dismissed ->
        GameUi(stageOf(match, session.me, hub, dismissed), match, session.me, busy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GameUi())
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val pong get() = session.pong
    private val mutableReactions = MutableStateFlow<List<GameReaction>>(emptyList())
    /** Reactions currently popping up over the game, oldest first; each shows for [ReactionShowMs]. */
    val reactions = mutableReactions.asStateFlow()
    private var connectionId = ""
    var nickname = "Your One on One"; private set
    private var noticeTimer: Job? = null
    private var foreground = true
    private val online get() = service.connection.value == ConnectionState.Connected && service.presence.value?.online == true
    private val scoped get() = service.active.value?.let { it.ownerId == session.me && it.connectionId == connectionId } == true

    init {
        viewModelScope.launch { session.errors.collect { notice(message(it)) } }
        viewModelScope.launch {
            session.reactions.collect { reaction ->
                mutableReactions.update { (it + reaction).takeLast(MaxReactions) }
                launch { delay(ReactionShowMs); mutableReactions.update { list -> list.filterNot { it.id == reaction.id } } }
            }
        }
        // Every (re)connect recovers the match from the server: board, colours, score.
        viewModelScope.launch { service.connection.collect { if (it == ConnectionState.Connected && scoped) session.sync() } }
        viewModelScope.launch { service.ended.collect { session.reset(session.me); hub.value = false } }
        viewModelScope.launch { service.active.collect { if (connectionId.isNotEmpty() && !scoped) session.reset(session.me) } }
        viewModelScope.launch {
            var previous: Match? = null
            session.match.collect { match ->
                val ending = match?.outcome?.reason
                if (match != null && previous?.id == match.id && previous?.status == MatchStatus.Invited && MatchCodec.quietEnding(ending)) {
                    val mine = match.inviter == session.me
                    when {
                        ending == "declined" && mine -> notice("$nickname declined the invitation.")
                        ending == "cancelled" && !mine -> notice("$nickname cancelled the invitation.")
                        ending == "expired" -> notice("The invitation expired.")
                    }
                }
                // Our draw offer was turned down (or a move answered it): say so, as the button frees up again.
                if (match != null && previous?.id == match.id && previous?.drawOffer == session.me && match.drawOffer == null &&
                    match.status == MatchStatus.Playing) notice("$nickname declined your draw offer.")
                if (match?.status == MatchStatus.Playing) hub.value = false
                // A reconnect marks us present again; results we already closed stay closed for the partner.
                if (match?.status == MatchStatus.Over && match.id == dismissed.value && session.me !in match.away) session.leave()
                previous = match
            }
        }
    }

    fun open(connection: CurrentConnection) {
        nickname = connection.otherNickname ?: "Your One on One"
        if (connectionId == connection.id && session.me == connection.myUserId) return
        connectionId = connection.id
        session.reset(connection.myUserId)
        hub.value = false; dismissed.value = null
        if (service.connection.value == ConnectionState.Connected) session.sync()
    }

    /** Table tennis pauses for both players while this app is in the background. */
    fun foreground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        val match = ui.value.match
        if (match?.game == GameKind.Pong && match.status == MatchStatus.Playing) session.away(!value)
    }

    fun hub() {
        mutableError.value = null
        if (!online || !scoped) { offlineNotice(); return }
        if (ui.value.stage in setOf(GameStage.Idle, GameStage.Result)) {
            if (ui.value.stage == GameStage.Result) session.leave() // tells the partner we left the results
            dismissed.value = ui.value.match?.id; hub.value = true
        }
    }
    fun dismissHub() { hub.value = false }
    fun invite(kind: GameKind, options: MatchOptions = MatchOptions()) {
        if (!online || !scoped) { offlineNotice(); return }
        if (ui.value.stage !in setOf(GameStage.Idle, GameStage.Hub, GameStage.Result)) return
        dismissed.value = null; hub.value = false
        session.invite(kind, options)
    }
    fun accept() { if (ui.value.stage == GameStage.Incoming) session.accept() }
    fun decline() { if (ui.value.stage == GameStage.Incoming) session.decline() }
    fun cancelInvite() { if (ui.value.stage == GameStage.Outgoing) session.decline() }
    fun leave(resign: Boolean) {
        if (ui.value.stage != GameStage.Playing) return
        if (resign) session.resign() else session.leave()
    }
    fun rematch() { if (ui.value.stage == GameStage.Result) session.rematch() }
    fun draw(action: String) { if (ui.value.stage == GameStage.Playing) session.draw(action) }
    fun done() {
        val match = ui.value.match ?: return
        if (match.status == MatchStatus.Over) session.leave() // tells the partner we left, withdrawing any rematch request
        dismissed.value = match.id; hub.value = false
    }
    fun move(data: JsonObject) = session.move(data)
    fun react(text: String) { if (!online || !scoped) offlineNotice() else session.react(text) }
    fun paddle(viewX: Float, viewY: Float, released: Boolean = false) = session.paddle(viewX, viewY, released)

    private fun message(code: String) = when (code) {
        "partner_offline" -> "Games need $nickname to be online."
        "rate_limited" -> "Games are moving too quickly. Try again in a moment."
        "not_member", "connection_inactive" -> "This connection is no longer available for games."
        "busy" -> "A game is already in progress."
        "not_your_turn" -> "It's $nickname's turn."
        "illegal_move", "invalid_move" -> "That move isn't allowed."
        "stale", "out_of_order", "game_over", "bad_state", "not_found" -> "The game moved on. Showing the latest board."
        "no_claim" -> "There's no draw to claim right now."
        "match_gone" -> "This game is no longer available."
        "partner_left" -> "$nickname has left the game."
        "network" -> "Couldn't reach the game. Check your connection."
        else -> "Something went wrong with the game."
    }
    private fun notice(text: String) {
        noticeTimer?.cancel(); mutableError.value = text
        noticeTimer = viewModelScope.launch { delay(4_000); mutableError.value = null }
        // A stale or rejected action: fetch the authoritative state again.
        if (scoped && service.connection.value == ConnectionState.Connected && text.startsWith("The game moved on")) session.sync()
    }
    private fun offlineNotice() { notice("Games need $nickname to be online.") }

    companion object {
        const val ReactionShowMs = 3_500L
        const val MaxReactions = 4
    }
}
