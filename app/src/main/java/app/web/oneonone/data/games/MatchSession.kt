package app.web.oneonone.data.games

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** What a [MatchSession] needs from the network; kept small so the session is testable without Android. */
interface MatchChannel {
    val states: Flow<JsonObject>
    val frames: Flow<JsonObject>
    /** Emits a `match:*` event and returns its ack. */
    suspend fun call(event: String, payload: JsonObject): JsonObject
    fun paddle(matchId: String, x: Float)
}

enum class GameStage { Idle, Hub, Outgoing, Incoming, Playing, Result }

/** What the game screen shows for a server match; `dismissed` is a finished match the user closed. */
fun stageOf(match: Match?, me: String, hub: Boolean, dismissed: String?): GameStage {
    if (match != null && match.id != dismissed) when (match.status) {
        MatchStatus.Invited -> return if (match.inviter == me) GameStage.Outgoing else GameStage.Incoming
        MatchStatus.Playing -> return GameStage.Playing
        MatchStatus.Over -> if (!MatchCodec.quietEnding(match.outcome?.reason)) return GameStage.Result
    }
    return if (hub) GameStage.Hub else GameStage.Idle
}

/**
 * Client side of one connection's server match (contract §12): keeps the newest snapshot, sends actions,
 * retries a move once after a lost ack (the server de-duplicates by `seq`) and throttles paddle updates.
 * Errors are emitted as server codes, plus `network` and `match_gone`.
 */
class MatchSession(private val channel: MatchChannel, private val scope: CoroutineScope, private val clock: () -> Long = System::nanoTime) {
    private val mutableMatch = MutableStateFlow<Match?>(null)
    val match = mutableMatch.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = mutableErrors.asSharedFlow()
    var me = ""; private set
    var pong: PongClient? = null; private set
    private var pendingPaddle: Float? = null
    private var paddleJob: Job? = null

    init {
        scope.launch { channel.states.collect { offer(MatchCodec.decode(it)) } }
        scope.launch { channel.frames.collect { frame -> match.value?.let { pong?.receive(frame, it.id, clock()) } } }
    }

    fun reset(user: String) {
        me = user; mutableMatch.value = null; pong = null; mutableBusy.value = false
        paddleJob?.cancel(); pendingPaddle = null
    }

    private fun offer(next: Match?) {
        if (next == null || me.isEmpty() || next.seat(me) == 0) return
        val current = match.value
        if (current != null && current.id == next.id && next.version < current.version) return
        if (next.game == GameKind.Pong && (current?.id != next.id || pong == null)) pong = PongClient(next.seat(me))
        if (next.game != GameKind.Pong) pong = null
        mutableMatch.value = next
    }

    private suspend fun ack(event: String, payload: JsonObject): JsonObject? = try {
        channel.call(event, payload)
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }

    private fun handle(event: String, ack: JsonObject?): Boolean {
        if (ack == null) { mutableErrors.tryEmit("network"); return false }
        val ok = (ack["ok"] as? JsonPrimitive)?.booleanOrNull == true
        when (val m = ack["match"]) {
            is JsonObject -> offer(MatchCodec.decode(m))
            // The server no longer has the match (restart or cleanup): drop a live one, keep a result on screen.
            is JsonNull -> if (ok && event == "match:sync" && match.value?.status.let { it != null && it != MatchStatus.Over }) {
                mutableMatch.value = null; pong = null; mutableErrors.tryEmit("match_gone")
            }
            else -> Unit
        }
        if (!ok) mutableErrors.tryEmit((ack["error"] as? JsonPrimitive)?.contentOrNull ?: "invalid_payload")
        return ok
    }

    private fun send(event: String, build: JsonObjectBuilder.() -> Unit = {}) = scope.launch {
        handle(event, ack(event, buildJsonObject(build)))
    }
    private fun onMatch(event: String, build: JsonObjectBuilder.(Match) -> Unit = {}) {
        val m = match.value ?: return
        send(event) { put("matchId", m.id); build(m) }
    }

    fun sync() = send("match:sync")
    fun invite(kind: GameKind) = send("match:invite") { put("game", kind.wire) }
    fun accept() = onMatch("match:accept")
    fun decline() = onMatch("match:decline")
    fun leave() = onMatch("match:leave")
    fun resign() = onMatch("match:resign")
    fun rematch() = onMatch("match:rematch")
    fun draw(action: String) = onMatch("match:draw") { put("action", action) }
    fun away(away: Boolean) {
        val m = match.value ?: return
        if (m.status == MatchStatus.Playing) send("match:away") { put("matchId", m.id); put("away", away) }
    }

    /** One move at a time; the board stays locked until the server answers. */
    fun move(data: JsonObject) {
        val m = match.value ?: return
        if (m.status != MatchStatus.Playing || m.turn != me || mutableBusy.value) return
        mutableBusy.value = true
        val payload = buildJsonObject { put("matchId", m.id); put("seq", m.seq); put("move", data) }
        scope.launch {
            try { handle("match:move", ack("match:move", payload) ?: ack("match:move", payload)) }
            finally { mutableBusy.value = false }
        }
    }

    /** Own paddle (view x); sent at most every [PaddleIntervalMs], always the latest position. */
    fun paddle(viewX: Float) {
        val m = match.value ?: return
        val client = pong ?: return
        if (m.status != MatchStatus.Playing || m.paused) return
        pendingPaddle = client.paddle(viewX)
        if (paddleJob?.isActive == true) return
        paddleJob = scope.launch {
            while (true) {
                val x = pendingPaddle ?: break
                pendingPaddle = null
                channel.paddle(m.id, x)
                delay(PaddleIntervalMs)
            }
        }
    }

    companion object { const val PaddleIntervalMs = 33L }
}
