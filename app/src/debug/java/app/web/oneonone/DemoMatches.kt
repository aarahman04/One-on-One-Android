package app.web.oneonone

import android.util.Log
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.*

private const val PongLogTag = "DemoPong"

/**
 * Debug-only stand-in for the server's matches (contract §12) so the real game screens can be played on an
 * emulator without accounts: the partner auto-accepts and answers with a random legal move; table tennis runs
 * a copy of the server simulation with a tracking bot. Not multiplayer, never in release builds.
 * shortcut: rules mirror backend/src/games at 2026-10-21; re-copy if the server rules change.
 */
internal class DemoMatches(private val scope: CoroutineScope, private val me: String, private val partner: String) {
    val states = MutableSharedFlow<JsonObject>(extraBufferCapacity = 16)
    val frames = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val reactions = MutableSharedFlow<JsonObject>(extraBufferCapacity = 16)

    private var id = ""
    private var game = ""
    private var status = "invited"
    private var players = listOf(me, partner)
    private var inviter = me
    private var turnSeat = 1
    private var seq = 0
    private var version = 0
    private var outcome: Pair<String?, String>? = null
    private var rematch = mutableSetOf<String>()
    private var drawOffer: String? = null
    private var drawAsks = 0
    private val series = mutableMapOf(me to 0, partner to 0)
    private var rules: Rules? = null
    private var pong: DemoPong? = null
    private var loop: Job? = null
    // Invite options, mirrored from the server: a turn game's clock and Ping Pong's target score.
    private var clock: Pair<Long, Long>? = null
    private var points = 7
    private val left = longArrayOf(0, 0)
    private var turnAt = 0L
    private var ticker: Job? = null
    private fun now() = System.currentTimeMillis()
    private fun clockLeft(): List<Long> = List(2) { i ->
        if (status == "playing" && rules != null && i == turnSeat - 1) max(0L, left[i] - (now() - turnAt)) else left[i]
    }

    private fun snapshot(): JsonObject = buildJsonObject {
        put("id", id); put("game", game); put("status", status); put("inviter", inviter)
        putJsonArray("players") { players.forEach { add(it) } }
        put("turn", if (status == "playing" && rules != null) players[turnSeat - 1] else null)
        put("seq", seq); put("version", version)
        put("board", rules?.view() ?: pong?.let { p -> buildJsonObject { putJsonArray("score") { add(p.score[0]); add(p.score[1]) }; put("target", points) } } ?: JsonNull)
        put("outcome", outcome?.let { (w, r) -> buildJsonObject { put("winner", w); put("reason", r) } } ?: JsonNull)
        put("drawOffer", drawOffer)
        putJsonArray("rematch") { rematch.forEach { add(it) } }
        putJsonObject("series") { series.forEach { (k, v) -> put(k, v) } }
        putJsonArray("away") {}
        put("paused", false)
        clock?.let { (base, inc) -> putJsonObject("clock") { put("base", base); put("inc", inc); putJsonArray("left") { clockLeft().forEach { add(it) } } } }
        put("points", if (game == "pong") points else null)
    }
    private fun publish() { version++; states.tryEmit(snapshot()) }
    private fun ack(ok: Boolean = true, error: String? = null) = buildJsonObject {
        put("ok", ok); error?.let { put("error", it) }; put("match", if (id.isEmpty()) JsonNull else snapshot())
    }

    private fun start(kind: String, seats: List<String>) {
        loop?.cancel()
        id = UUID.randomUUID().toString(); game = kind; players = seats; status = "playing"; seq = 0
        outcome = null; rematch = mutableSetOf(); turnSeat = 1
        rules = when (kind) { "chess" -> Chess(); "tictactoe" -> Grid(3); "connect4" -> Grid(7); "reversi" -> Reversi(); else -> null }
        pong = if (kind == "pong") DemoPong(target = points) else null
        pong?.let { p -> loop = scope.launch { runPong(p) } }
        clock?.let { (base, _) -> left.fill(base); turnAt = now() }
        ticker?.cancel()
        if (clock != null && rules != null) ticker = scope.launch {
            while (status == "playing") {
                delay(200)
                if (status == "playing" && clockLeft()[turnSeat - 1] <= 0L) { left[turnSeat - 1] = 0; finish(3 - turnSeat, "timeout"); publish() }
            }
        }
        publish()
        botTurn()
    }

    private fun finish(winnerSeat: Int, reason: String) {
        val winner = if (winnerSeat == 0) null else players[winnerSeat - 1]
        winner?.let { series[it] = (series[it] ?: 0) + 1 }
        if (status == "playing" && clock != null && rules != null) left[turnSeat - 1] = clockLeft()[turnSeat - 1]
        outcome = winner to reason; status = "over"; loop?.cancel(); ticker?.cancel()
    }

    private fun botTurn() {
        val r = rules ?: return
        if (status != "playing" || players[turnSeat - 1] != partner) return
        scope.launch {
            delay(700)
            r.botMove()?.let { applyMove(it) }
        }
    }

    private fun applyMove(move: JsonObject): String? {
        val r = rules ?: return "bad_state"
        val mover = turnSeat
        val error = r.apply(turnSeat, move) ?: run {
            clock?.let { (_, inc) -> left[mover - 1] = max(0L, left[mover - 1] - (now() - turnAt)) + inc; turnAt = now() }
            seq++
            val o = r.outcome
            if (o != null) finish(o.first, o.second) else turnSeat = r.turn
            null
        }
        if (error == null) { publish(); botTurn() }
        return error
    }

    suspend fun call(event: String, payload: JsonObject): JsonObject {
        val p = payload
        return when (event) {
            "match:sync" -> ack()
            // Alex turns the first draw offer down and takes the next, so both answers can be seen.
            "match:draw" -> {
                if (status != "playing" || p["action"]?.jsonPrimitive?.content != "offer") return ack(false, "no_offer")
                drawOffer = me; publish()
                val accept = drawAsks++ % 2 == 1
                scope.launch {
                    delay(1_500)
                    if (drawOffer != me || status != "playing") return@launch
                    drawOffer = null
                    if (accept) finish(0, "agreement")
                    publish()
                }
                ack()
            }
            "match:accept" -> {
                if (status != "invited" || inviter != partner) return ack(false, "bad_state")
                start(game, players); ack()
            }
            "match:decline" -> {
                if (status == "invited") { status = "over"; outcome = null to if (inviter == partner) "declined" else "cancelled"; publish() }
                ack()
            }
            "match:invite" -> {
                val kind = p["game"]!!.jsonPrimitive.content
                if (kind != game) series.keys.forEach { series[it] = 0 } // the server keeps a series per game
                clock = (p["clock"] as? JsonObject)?.let { it["base"]!!.jsonPrimitive.long to it["inc"]!!.jsonPrimitive.long }?.takeIf { kind != "pong" }
                points = p["points"]?.jsonPrimitive?.intOrNull ?: 7
                id = UUID.randomUUID().toString(); game = kind; status = "invited"; inviter = me
                players = listOf(me, partner); outcome = null; rules = null; pong = null
                publish()
                scope.launch { delay(900); start(game, players) }
                ack()
            }
            "match:move" -> {
                if (status != "playing") return ack(false, "game_over")
                if (players[turnSeat - 1] != me) return ack(false, "not_your_turn")
                if (p["seq"]?.jsonPrimitive?.intOrNull != seq) return ack(false, "stale_move")
                applyMove(p["move"]!!.jsonObject)?.let { ack(false, it) } ?: ack()
            }
            "match:resign", "match:leave" -> {
                if (status == "playing") { finish(players.indexOf(partner) + 1, if (event == "match:resign") "resigned" else "left"); publish() }
                else if (status == "invited") { status = "over"; outcome = null to "cancelled"; publish() }
                ack()
            }
            "match:rematch" -> {
                rematch += me; publish()
                scope.launch { delay(600); start(game, players.reversed()) }
                ack()
            }
            "match:react" -> {
                val text = p["text"]?.jsonPrimitive?.content.orEmpty()
                if (p["matchId"]?.jsonPrimitive?.content != id || (status != "playing" && status != "over")) return ack(false, "not_found")
                react(me, text)
                // Alex answers some of the time, like a partner glancing at the screen.
                scope.launch { delay(1_400); react(partner, listOf("😂", "Ha! Watch this", "🔥", "Not fair 😤", "👏").random()) }
                ack()
            }
            else -> ack() // draw offers, away: accepted and ignored here
        }
    }

    /** Emulator check of the invitee side: Alex invites us; accept/decline arrive through [call]. */
    fun partnerInvite(kind: String) {
        if (status == "playing") return
        id = UUID.randomUUID().toString(); game = kind; status = "invited"; inviter = partner
        players = listOf(partner, me); outcome = null; rules = null; pong = null; clock = 300_000L to 0L
        publish()
    }

    private fun react(from: String, text: String) {
        reactions.tryEmit(buildJsonObject { put("id", UUID.randomUUID().toString()); put("matchId", id); put("from", from); put("text", text) })
    }

    fun paddle(matchId: String, x: Float, y: Float?, released: Boolean) { if (matchId == id) pong?.paddle(players.indexOf(me) + 1, x.toDouble(), y?.toDouble(), released) }

    private suspend fun runPong(p: DemoPong) {
        var last = System.nanoTime()
        var frameMs = 0.0
        val botSeat = players.indexOf(partner) + 1
        while (currentCoroutineContext().isActive && p.outcome == null) {
            val now = System.nanoTime()
            val ms = ((now - last) / 1e6).coerceIn(1.0, 50.0)
            last = now
            val reach = .0024 * ms
            // Hold the baseline until countdown ends; don't sit on top of the waiting ball.
            val target = if (p.vy != 0.0 && (p.vy < 0) == (botSeat == 2)) p.x else .5
            val cur = p.paddles[botSeat - 1]
            val targetY = if (p.serving(botSeat) && p.countdown == 0.0) p.y else if (botSeat == 1) p.courtH + .08 else -.08
            val py = p.paddleY[botSeat - 1]
            p.paddle(botSeat, cur + (target - cur).coerceIn(-reach, reach), py + (targetY - py).coerceIn(-reach, reach), released = true)
            p.step(ms)
            frameMs += ms
            if (frameMs >= 1000.0 / 30 || p.outcome != null) {
                frameMs %= 1000.0 / 30
                val frame = p.frame(id)
                frames.tryEmit(frame)
                if (Log.isLoggable(PongLogTag, Log.DEBUG)) Log.d(PongLogTag, frame.toString())
            }
            if (p.scored) { p.scored = false; publish() }
            delay(16)
        }
        p.outcome?.let { finish(it, if (points == 7) "first_to_7" else "first_to_points"); publish() }
    }

    private interface Rules {
        val turn: Int
        val outcome: Pair<Int, String>?
        fun apply(seat: Int, move: JsonObject): String?
        fun view(): JsonObject
        fun botMove(): JsonObject?
    }

    private class Chess : Rules {
        val board = Board()
        var last: Pair<String, String>? = null
        val history = mutableListOf<String>()
        override val turn get() = if (board.sideToMove == Side.WHITE) 1 else 2
        override var outcome: Pair<Int, String>? = null
        override fun apply(seat: Int, move: JsonObject): String? {
            val uci = move["uci"]?.jsonPrimitive?.contentOrNull ?: return "invalid_move"
            val m = board.legalMoves().firstOrNull { it.toString() == uci } ?: return "illegal_move"
            board.doMove(m); last = uci.substring(0, 2) to uci.substring(2, 4); history += uci
            outcome = when { board.isMated -> seat to "checkmate"; board.isStaleMate -> 0 to "stalemate"; board.isInsufficientMaterial -> 0 to "insufficient_material"; else -> null }
            return null
        }
        override fun view() = buildJsonObject {
            put("fen", board.fen)
            put("last", last?.let { (f, t) -> buildJsonObject { put("from", f); put("to", t) } } ?: JsonNull)
            put("check", board.isKingAttacked); put("claim", null as String?)
            putJsonArray("history") { history.forEach { add(it) } }
        }
        override fun botMove() = board.legalMoves().randomOrNull()?.let { m -> buildJsonObject { put("uci", m.toString()) } }
    }

    /** Tic-tac-toe (width 3) and Connect 4 (width 7, gravity), as in backend rules.ts. */
    private class Grid(val width: Int) : Rules {
        val four = width == 7
        val cells = IntArray(if (four) 42 else 9)
        var line = listOf<Int>(); var last = -1
        override var turn = 1
        override var outcome: Pair<Int, String>? = null
        private fun rows() = cells.size / width
        override fun apply(seat: Int, move: JsonObject): String? {
            val cell = if (four) {
                val col = move["col"]?.jsonPrimitive?.intOrNull?.takeIf { it in 0 until width } ?: return "invalid_move"
                (rows() - 1 downTo 0).map { it * width + col }.firstOrNull { cells[it] == 0 } ?: return "illegal_move"
            } else (move["cell"]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..8 } ?: return "invalid_move").also { if (cells[it] != 0) return "illegal_move" }
            cells[cell] = seat; last = cell
            val need = if (four) 4 else 3
            val r = cell / width; val c = cell % width
            for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                val run = mutableListOf(cell)
                for (sign in listOf(-1, 1)) {
                    var rr = r + dr * sign; var cc = c + dc * sign
                    while (rr in 0 until rows() && cc in 0 until width && cells[rr * width + cc] == seat) { run += rr * width + cc; rr += dr * sign; cc += dc * sign }
                }
                if (run.size >= need) { line = run.sorted(); outcome = seat to if (four) "four_connected" else "three_in_a_row"; break }
            }
            if (outcome == null && cells.all { it != 0 }) outcome = 0 to "board_full"
            turn = 3 - seat
            return null
        }
        override fun view() = buildJsonObject {
            putJsonArray("cells") { cells.forEach { add(it) } }; putJsonArray("line") { line.forEach { add(it) } }; put("last", last)
        }
        override fun botMove() = if (four) (0 until width).filter { cells[it] == 0 }.randomOrNull()?.let { c -> buildJsonObject { put("col", c) } }
            else cells.indices.filter { cells[it] == 0 }.randomOrNull()?.let { c -> buildJsonObject { put("cell", c) } }
    }

    private class Reversi : Rules {
        val cells = IntArray(64).apply { this[27] = 1; this[28] = 2; this[35] = 2; this[36] = 1 }
        var last = -1; var flipped = listOf<Int>(); var passed = false
        override var turn = 1
        override var outcome: Pair<Int, String>? = null
        fun flips(seat: Int, cell: Int): List<Int> {
            if (cells[cell] != 0) return emptyList()
            val out = mutableListOf<Int>()
            for (dr in -1..1) for (dc in -1..1) if (dr != 0 || dc != 0) {
                val run = mutableListOf<Int>(); var r = cell / 8 + dr; var c = cell % 8 + dc
                while (r in 0..7 && c in 0..7 && cells[r * 8 + c] == 3 - seat) { run += r * 8 + c; r += dr; c += dc }
                if (run.isNotEmpty() && r in 0..7 && c in 0..7 && cells[r * 8 + c] == seat) out += run
            }
            return out
        }
        fun legal(seat: Int) = (0..63).filter { flips(seat, it).isNotEmpty() }
        override fun apply(seat: Int, move: JsonObject): String? {
            val cell = move["cell"]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..63 } ?: return "invalid_move"
            val f = flips(seat, cell).takeIf { it.isNotEmpty() } ?: return "illegal_move"
            cells[cell] = seat; f.forEach { cells[it] = seat }; last = cell; flipped = f.sorted(); passed = false
            when {
                legal(3 - seat).isNotEmpty() -> turn = 3 - seat
                legal(seat).isNotEmpty() -> passed = true
                else -> { val a = cells.count { it == 1 }; val b = cells.count { it == 2 }; outcome = (if (a == b) 0 else if (a > b) 1 else 2) to if (a == b) "equal_discs" else "most_discs" }
            }
            return null
        }
        override fun view() = buildJsonObject {
            putJsonArray("cells") { cells.forEach { add(it) } }; put("last", last)
            putJsonArray("flipped") { flipped.forEach { add(it) } }; put("passed", passed)
            putJsonArray("legal") { if (outcome == null) legal(turn).forEach { add(it) } }
        }
        override fun botMove() = legal(turn).randomOrNull()?.let { c -> buildJsonObject { put("cell", c) } }
    }

}
