package app.web.oneonone.ui.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.games.*
import app.web.oneonone.data.realtime.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject

enum class GameStage { Idle, Hub, Outgoing, Incoming, Playing, Result }
data class GameUi(
    val stage: GameStage = GameStage.Idle,
    val id: String = "",
    val kind: GameKind = GameKind.Chess,
    val player: Int = 1,
    val paused: Boolean = false,
    val busy: Boolean = false,
    val revision: Int = 0,
    val result: GameResult? = null,
    val bestOfThree: Boolean = false,
    val myWins: Int = 0,
    val theirWins: Int = 0,
)

@HiltViewModel
class GameViewModel @Inject constructor(private val service: GameService, private val saved: SavedStateHandle) : ViewModel() {
    private val mutableUi = MutableStateFlow(GameUi())
    val ui = mutableUi.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableFrame = MutableStateFlow(0L)
    val frame = mutableFrame.asStateFlow()
    var board: TurnGame? = null; private set
    var pong: PongGame? = null; private set
    private var connectionId = ""
    private var owner = ""
    private var partnerId = ""
    var nickname = "Your One on One"; private set
    private var timer: Job? = null
    private var pauseTimer: Job? = null
    private var pongSender: Job? = null
    private var resultTimer: Job? = null
    private var noticeTimer: Job? = null
    private var relayLost = false
    private var pongSeq = 0
    private var foreground = true
    private val retired = ArrayDeque<String>()
    private var restoredId: String? = saved["gameId"]
    private var restoredPlayer: Int = saved["gameRole"] ?: 1
    private val restoredKind = GameKind.entries.firstOrNull { it.wire == saved.get<String>("gameKind") } ?: GameKind.Chess
    private var lastFrame = 0L
    private val online get() = foreground && service.connection.value == ConnectionState.Connected && service.presence.value?.online == true
    private val scoped get() = service.active.value?.let { it.ownerId == owner && it.connectionId == connectionId } == true

    init {
        viewModelScope.launch { service.events.collect(::receive) }
        viewModelScope.launch {
            combine(service.connection, service.presence) { _, _ -> Unit }.collect {
                if (scoped && service.connection.value == ConnectionState.Connected) {
                    restoredId?.let { id ->
                        restoredId = null
                        rememberRetired(id)
                        mutableUi.value = GameUi(GameStage.Result, id = id, kind = restoredKind, player = restoredPlayer,
                            result = GameResult(0, "The app restarted. This game has ended."))
                        saved["gameId"] = null
                        sendEnd(id, "process_restarted")
                    }
                }
                availability()
            }
        }
        viewModelScope.launch { service.ended.collect { finish(GameResult(0, "The connection has ended.")) } }
        viewModelScope.launch { service.active.collect { if (connectionId.isNotEmpty() && !scoped) clear() } }
    }

    fun open(connection: CurrentConnection) {
        if (connectionId.isNotEmpty() && (connectionId != connection.id || owner != connection.myUserId)) clear()
        connectionId = connection.id; owner = connection.myUserId
        nickname = connection.otherNickname ?: "Your One on One"
        if (restoredId != null && (saved.get<String>("gameOwner") != owner || saved.get<String>("gameConnection") != connectionId)) {
            restoredId = null; saved["gameId"] = null
        }
        if (restoredId != null && service.connection.value == ConnectionState.Connected && scoped) {
            val id = restoredId!!; restoredId = null; rememberRetired(id)
            mutableUi.value = GameUi(GameStage.Result, id = id, kind = restoredKind, player = restoredPlayer,
                result = GameResult(0, "The app restarted. This game has ended."))
            saved["gameId"] = null; sendEnd(id, "process_restarted")
        }
    }

    fun foreground(value: Boolean) { foreground = value; availability() }
    fun hub() {
        mutableError.value = null
        if (!online || !scoped) { offlineNotice(); return }
        if (ui.value.stage in setOf(GameStage.Idle, GameStage.Result)) mutableUi.value = ui.value.copy(stage = GameStage.Hub)
    }
    fun dismissHub() { if (ui.value.stage == GameStage.Hub) mutableUi.value = ui.value.copy(stage = GameStage.Idle) }
    fun invite(kind: GameKind, bestOfThree: Boolean = ui.value.bestOfThree) {
        if (!online || !scoped) { offlineNotice(); return }
        if (ui.value.stage !in setOf(GameStage.Idle, GameStage.Hub, GameStage.Result)) return
        val old = ui.value
        val resetScore = kind != old.kind || old.myWins >= 2 || old.theirWins >= 2
        partnerId = service.presence.value!!.userId
        val id = UUID.randomUUID().toString()
        mutableError.value = null
        board = null; pong = null
        mutableUi.value = GameUi(GameStage.Outgoing, id, kind, bestOfThree = bestOfThree,
            myWins = if (resetScore) 0 else old.myWins, theirWins = if (resetScore) 0 else old.theirWins)
        persist()
        inviteTimeout(id, incoming = false)
        request(id) { service.invite(id, kind) }
    }

    fun accept() {
        val state = ui.value
        if (state.stage != GameStage.Incoming || state.busy) return
        if (!online || !scoped) { offlineNotice(); decline(); return }
        timer?.cancel()
        // Start before the ack: the host can send its first move immediately after receiving accept.
        start(state.copy(player = 2), sendPong = false)
        request(state.id) {
            service.accept(state.id)
            if (ui.value.id == state.id && ui.value.stage == GameStage.Playing && state.kind == GameKind.Pong) startPongSender(ui.value)
        }
    }
    fun decline() {
        val state = ui.value
        if (state.stage != GameStage.Incoming) return
        timer?.cancel(); rememberRetired(state.id)
        mutableUi.value = state.copy(stage = GameStage.Idle); saved["gameId"] = null
        viewModelScope.launch { ignoreFailure { service.decline(state.id) } }
    }
    fun cancelInvite() {
        val state = ui.value
        if (state.stage != GameStage.Outgoing) return
        timer?.cancel(); rememberRetired(state.id)
        mutableUi.value = state.copy(stage = GameStage.Idle); saved["gameId"] = null
        sendEnd(state.id, "cancelled")
    }
    fun leave(resign: Boolean) {
        if (ui.value.stage != GameStage.Playing) return
        val state = ui.value
        finish(GameResult(3 - state.player, if (resign) "You resigned." else "You left the game."))
        sendEnd(state.id, if (resign) "resigned" else "left")
    }
    fun done() { clear() }

    fun move(data: JsonObject) {
        val state = ui.value
        val engine = board ?: return
        if (state.stage != GameStage.Playing || state.paused || state.busy || engine.turn != state.player) return
        val seq = engine.nextSeq
        try { engine.play(state.player, seq, data) }
        catch (error: IllegalArgumentException) { notice(error.message ?: "That move is not legal."); return }
        mutableUi.value = state.copy(revision = state.revision + 1, busy = true)
        request(state.id) {
            service.move(state.id, seq, data)
            if (ui.value.id == state.id) {
                mutableUi.value = ui.value.copy(busy = false)
                concludeBoard(state.id)
            }
        }
    }

    fun paddle(y: Float) { if (ui.value.stage == GameStage.Playing && !ui.value.paused) pong?.paddle(ui.value.player, y) }
    fun frame(nanos: Long) {
        val state = ui.value
        if (state.stage != GameStage.Playing || state.paused) { lastFrame = 0; return }
        val engine = pong ?: return
        val dt = if (lastFrame == 0L) 0f else (nanos - lastFrame) / 1_000_000_000f
        lastFrame = nanos
        val score = engine.hostScore + engine.guestScore
        engine.tick(dt, state.player == 1)
        if (engine.hostScore + engine.guestScore != score) mutableUi.value = state.copy(revision = state.revision + 1)
        mutableFrame.value = nanos
        // The final score is sent by the 30 Hz sender before the host enters its result screen.
    }

    private fun receive(event: GameEvent) {
        if (!scoped || event.from == owner || event.gameId in retired) return
        var state = ui.value
        if (event.name == "game:invite") {
            if (state.stage == GameStage.Playing && board?.result != null) { finish(board!!.result!!); state = ui.value }
            if (service.presence.value?.userId != event.from || !online) {
                viewModelScope.launch { ignoreFailure { service.decline(event.gameId) } }; return
            }
            val wire = (event.payload["game"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val kind = GameKind.entries.firstOrNull { it.wire == wire } ?: return
            if (state.id == event.gameId) return
            if (state.stage == GameStage.Outgoing && event.gameId < state.id) {
                cancelInvite()
            } else if (state.stage !in setOf(GameStage.Idle, GameStage.Hub, GameStage.Result)) {
                viewModelScope.launch { ignoreFailure { service.decline(event.gameId) } }; return
            }
            partnerId = event.from
            val resetScore = kind != state.kind || state.myWins >= 2 || state.theirWins >= 2
            mutableUi.value = GameUi(GameStage.Incoming, event.gameId, kind, player = 2,
                bestOfThree = state.bestOfThree && kind == state.kind,
                myWins = if (resetScore) 0 else state.myWins, theirWins = if (resetScore) 0 else state.theirWins)
            persist(); inviteTimeout(event.gameId, incoming = true); return
        }
        if (event.gameId != state.id || event.from != partnerId) return
        when (event.name) {
            "game:accept" -> if (state.stage == GameStage.Outgoing) start(state)
            "game:decline" -> if (state.stage == GameStage.Outgoing) finish(GameResult(0, "$nickname declined the invitation."))
            "game:end" -> if (state.stage in setOf(GameStage.Incoming, GameStage.Outgoing, GameStage.Playing)) {
                val reason = (event.payload["reason"] as? JsonPrimitive)?.content
                finish(when (reason) {
                    "resigned" -> GameResult(state.player, "$nickname resigned.")
                    "left" -> GameResult(state.player, "$nickname left the game.")
                    "invalid_move" -> GameResult(0, "The game ended because a move could not be validated.")
                    "disconnected" -> GameResult(0, "$nickname disconnected. The game has ended.")
                    "cancelled" -> GameResult(0, "$nickname cancelled the invitation.")
                    "process_restarted" -> GameResult(0, "$nickname restarted the app. This game has ended.")
                    "invite_timeout" -> GameResult(0, "The invitation expired.")
                    else -> GameResult(0, "$nickname ended the game.")
                })
            }
            "game:move" -> if (state.stage == GameStage.Playing) {
                try {
                    val seq = event.payload.integer("seq")
                    val data = event.payload["data"] as? JsonObject ?: error("Invalid move data.")
                    if (state.kind == GameKind.Pong) {
                        val score = pong!!.hostScore + pong!!.guestScore
                        pong!!.receive(3 - state.player, seq, data)
                        if (pong!!.hostScore + pong!!.guestScore != score) mutableUi.value = state.copy(revision = state.revision + 1)
                        if (pong!!.winner != 0) finish(GameResult(pong!!.winner, "${pong!!.hostScore}–${pong!!.guestScore} · first to 7"))
                    } else {
                        board!!.play(3 - state.player, seq, data)
                        mutableUi.value = state.copy(revision = state.revision + 1)
                        concludeBoard(state.id)
                    }
                } catch (_: Exception) {
                    finish(GameResult(0, "The game ended: an illegal or out-of-sequence move arrived."))
                    sendEnd(state.id, "invalid_move")
                }
            }
        }
    }

    private fun start(state: GameUi, sendPong: Boolean = true) {
        timer?.cancel(); pauseTimer?.cancel(); pongSender?.cancel()
        if (state.kind == GameKind.Pong) { pong = PongGame(); board = null } else { board = state.kind.engine(); pong = null }
        pongSeq = 0; lastFrame = 0L; relayLost = false
        mutableUi.value = state.copy(stage = GameStage.Playing, paused = false, busy = false, result = null, revision = 0)
        persist(); availability()
        if (state.kind == GameKind.Pong && sendPong) startPongSender(state)
    }
    private fun startPongSender(state: GameUi) {
        pongSender = viewModelScope.launch {
            while (ui.value.id == state.id && ui.value.stage == GameStage.Playing) {
                if (!ui.value.paused) {
                    val data = pong!!.packet(state.player == 1)
                    val finalResult = pong!!.takeIf { it.winner != 0 }?.let { GameResult(it.winner, "${it.hostScore}–${it.guestScore} · first to 7") }
                    val seq = pongSeq++
                    request(state.id) {
                        service.move(state.id, seq, data)
                        if (ui.value.id == state.id && finalResult != null) finish(finalResult)
                    }
                }
                delay(34) // 29.4 packets/s, safely below the relay's 40/s/socket limit.
            }
        }
    }
    private fun concludeBoard(id: String) {
        val result = board?.result ?: return
        resultTimer?.cancel()
        resultTimer = viewModelScope.launch { delay(800); if (ui.value.id == id) finish(result) }
    }
    private fun availability() {
        val state = ui.value
        if (state.stage != GameStage.Playing) return
        if (online && scoped && !relayLost) {
            pauseTimer?.cancel(); pauseTimer = null
            if (state.paused) { lastFrame = 0; mutableUi.value = state.copy(paused = false) }
        } else if (pauseTimer == null) {
            mutableUi.value = state.copy(paused = true)
            pauseTimer = viewModelScope.launch {
                delay(15_000)
                if (ui.value.id == state.id && ui.value.stage == GameStage.Playing && ui.value.paused) {
                    finish(GameResult(0, "$nickname disconnected. The game has ended."))
                    sendEnd(state.id, "disconnected")
                }
            }
        }
    }
    private fun inviteTimeout(id: String, incoming: Boolean) {
        timer?.cancel()
        timer = viewModelScope.launch {
            delay(30_000)
            if (ui.value.id == id) {
                if (incoming) decline() else { finish(GameResult(0, "The invitation expired.")); sendEnd(id, "invite_timeout") }
            }
        }
    }
    private fun finish(result: GameResult) {
        val state = ui.value
        if (state.stage !in setOf(GameStage.Playing, GameStage.Incoming, GameStage.Outgoing)) return
        timer?.cancel(); pauseTimer?.cancel(); pauseTimer = null; pongSender?.cancel(); resultTimer?.cancel()
        val counted = state.stage == GameStage.Playing && state.kind == GameKind.TicTacToe && result.detail == "Three in a row"
        mutableUi.value = state.copy(stage = GameStage.Result, paused = false, busy = false, result = result,
            myWins = state.myWins + if (counted && result.winner == state.player) 1 else 0,
            theirWins = state.theirWins + if (counted && result.winner != state.player) 1 else 0)
        rememberRetired(state.id); saved["gameId"] = null
    }
    private fun persist() {
        saved["gameId"] = ui.value.id; saved["gameRole"] = ui.value.player; saved["gameKind"] = ui.value.kind.wire
        saved["gameOwner"] = owner; saved["gameConnection"] = connectionId
    }
    private fun rememberRetired(id: String) { if (id.isNotEmpty()) retired.addLast(id); while (retired.size > 32) retired.removeFirst() }
    private fun notice(text: String) {
        noticeTimer?.cancel(); mutableError.value = text
        noticeTimer = viewModelScope.launch { delay(4_000); mutableError.value = null }
    }
    private fun offlineNotice() { notice("Games need $nickname to be online.") }
    private fun request(id: String, block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (ui.value.id != id || ui.value.stage !in setOf(GameStage.Incoming, GameStage.Outgoing, GameStage.Playing)) return@launch
            if (error is GameRelayException && error.code == "partner_offline") {
                offlineNotice()
                if (ui.value.stage == GameStage.Playing) { relayLost = true; availability(); return@launch }
                timer?.cancel(); rememberRetired(id); saved["gameId"] = null
                mutableUi.value = ui.value.copy(stage = GameStage.Idle)
                return@launch
            } else if (ui.value.stage == GameStage.Playing && !online) {
                relayLost = true; availability(); return@launch
            } else notice(when ((error as? GameRelayException)?.code) {
                "rate_limited" -> "Games are moving too quickly. Try again in a moment."
                "not_member", "connection_inactive" -> "This connection is no longer available for games."
                else -> "The game could not stay connected. Please start a new game."
            })
            finish(GameResult(0, mutableError.value!!))
            sendEnd(id, "relay_failed")
        }
    }
    private fun sendEnd(id: String, reason: String) { viewModelScope.launch { ignoreFailure { service.end(id, reason) } } }
    private suspend fun ignoreFailure(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Ephemeral relay: peer also times out. */ }
    }
    private fun clear() {
        timer?.cancel(); pauseTimer?.cancel(); pauseTimer = null; pongSender?.cancel(); resultTimer?.cancel()
        rememberRetired(ui.value.id)
        mutableUi.value = GameUi(); board = null; pong = null; saved["gameId"] = null
        noticeTimer?.cancel(); mutableError.value = null
    }
}
