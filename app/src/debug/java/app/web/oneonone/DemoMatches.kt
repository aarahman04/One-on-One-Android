package app.web.oneonone

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.*

/**
 * Debug-only stand-in for the server's matches (contract §12) so the real game screens can be played on an
 * emulator without accounts: the partner auto-accepts and answers with a random legal move; table tennis runs
 * a copy of the server simulation with a tracking bot. Not multiplayer, never in release builds.
 * shortcut: rules mirror backend/src/games at 2026-10-21; re-copy if the server rules change.
 */
internal class DemoMatches(private val scope: CoroutineScope, private val me: String, private val partner: String) {
    val states = MutableSharedFlow<JsonObject>(extraBufferCapacity = 16)
    val frames = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)

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
    private val series = mutableMapOf(me to 0, partner to 0)
    private var rules: Rules? = null
    private var pong: Pong? = null
    private var loop: Job? = null

    private fun snapshot(): JsonObject = buildJsonObject {
        put("id", id); put("game", game); put("status", status); put("inviter", inviter)
        putJsonArray("players") { players.forEach { add(it) } }
        put("turn", if (status == "playing" && rules != null) players[turnSeat - 1] else null)
        put("seq", seq); put("version", version)
        put("board", rules?.view() ?: pong?.let { p -> buildJsonObject { putJsonArray("score") { add(p.score[0]); add(p.score[1]) } } } ?: JsonNull)
        put("outcome", outcome?.let { (w, r) -> buildJsonObject { put("winner", w); put("reason", r) } } ?: JsonNull)
        put("drawOffer", null as String?)
        putJsonArray("rematch") { rematch.forEach { add(it) } }
        putJsonObject("series") { series.forEach { (k, v) -> put(k, v) } }
        putJsonArray("away") {}
        put("paused", false)
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
        pong = if (kind == "pong") Pong() else null
        pong?.let { p -> loop = scope.launch { runPong(p) } }
        publish()
        botTurn()
    }

    private fun finish(winnerSeat: Int, reason: String) {
        val winner = if (winnerSeat == 0) null else players[winnerSeat - 1]
        winner?.let { series[it] = (series[it] ?: 0) + 1 }
        outcome = winner to reason; status = "over"; loop?.cancel()
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
        val error = r.apply(turnSeat, move) ?: run {
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
            "match:invite" -> {
                val kind = p["game"]!!.jsonPrimitive.content
                if (kind != game) series.keys.forEach { series[it] = 0 } // the server keeps a series per game
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
            else -> ack() // draw offers, away: accepted and ignored here
        }
    }

    fun paddle(matchId: String, x: Float) { if (matchId == id) pong?.paddle(players.indexOf(me) + 1, x.toDouble()) }

    private suspend fun runPong(p: Pong) {
        var n = 0
        val botSeat = players.indexOf(partner) + 1
        while (currentCoroutineContext().isActive && p.outcome == null) {
            // The bot follows the ball at a limited speed, so fast angled shots can beat it.
            val target = if (p.vy != 0.0 && (p.vy < 0) == (botSeat == 2)) p.x else .5
            val cur = p.paddles[botSeat - 1]
            p.paddle(botSeat, cur + (target - cur).coerceIn(-.022, .022))
            p.step(1000.0 / 60)
            if (++n % 2 == 0 || p.outcome != null) frames.tryEmit(p.frame(id))
            if (p.scored) { p.scored = false; publish() }
            delay(16)
        }
        p.outcome?.let { finish(it, "first_to_7"); publish() }
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

    /** Copy of backend/src/games/pong.ts (top-down, sidespin, first to 7). */
    private class Pong {
        val courtH = 1.8; val headR = .13; val ballR = .03; val slack = .025
        var x = .5; var y = courtH / 2; var vx = 0.0; var vy = 0.0; var spin = 0.0
        val paddles = doubleArrayOf(.5, .5); val score = intArrayOf(0, 0)
        var countdown = 3000.0; var hits = 0; var outcome: Int? = null; var scored = false
        private var clock = 0.0; private val swing = doubleArrayOf(0.0, 0.0); private val moved = doubleArrayOf(-1.0, -1.0)
        private var serveTo = 2
        private val maxAngle = 62 * PI / 180
        fun paddle(seat: Int, px: Double) {
            val i = seat - 1; val next = px.coerceIn(headR * .6, 1 - headR * .6)
            val dt = if (moved[i] < 0) 0.0 else (clock - moved[i]) / 1000
            if (dt > 0) swing[i] = .5 * swing[i] + .5 * ((next - paddles[i]) / max(dt, 1.0 / 60)).coerceIn(-6.0, 6.0)
            paddles[i] = next; moved[i] = clock
        }
        fun step(ms: Double) {
            if (outcome != null) return
            clock += ms
            for (i in 0..1) if (clock - moved[i] > 120) swing[i] = 0.0
            if (countdown > 0) { countdown -= ms; if (countdown <= 0) { countdown = 0.0; serve() }; return }
            val dt = ms / 1000
            val n = min(32, max(1, ceil(hypot(vx, vy) * dt / (ballR * .8)).toInt()))
            val decay = exp(-dt / n / .6)
            for (k in 0 until n) {
                if (outcome != null || countdown != 0.0) break
                if (spin != 0.0) { val cap = abs(vy) * tan(maxAngle); vx = (vx + spin * dt / n).coerceIn(-cap, cap); spin *= decay }
                x += vx * dt / n; y += vy * dt / n
                if (x < ballR) { x = 2 * ballR - x; vx = abs(vx); spin = 0.0 } else if (x > 1 - ballR) { x = 2 * (1 - ballR) - x; vx = -abs(vx); spin = 0.0 }
                when {
                    vy > 0 && y <= courtH && touches(1) -> hit(1)
                    vy < 0 && y >= 0 && touches(2) -> hit(2)
                    y - ballR > courtH -> point(2)
                    y + ballR < 0 -> point(1)
                }
            }
        }
        private fun head(seat: Int) = paddles[seat - 1] to if (seat == 1) courtH else 0.0
        private fun touches(seat: Int) = head(seat).let { (hx, hy) -> hypot(x - hx, y - hy) <= headR + ballR + slack }
        private fun serve() { val a = (Math.random() * 2 - 1) * PI / 8; vx = .95 * sin(a); vy = .95 * cos(a) * if (serveTo == 1) 1 else -1 }
        private fun hit(seat: Int) {
            val (hx, hy) = head(seat); val away = if (seat == 1) -1 else 1
            var nx = x - hx; var ny = (y - hy) * away; val len = hypot(nx, ny).takeIf { it > 0 } ?: 1.0
            nx /= len; ny = max(ny / len, .05)
            var angle = atan2(nx, ny)
            val speed = min(2.6, max(.95, hypot(vx, vy) * 1.06))
            angle += atan2(.3 * swing[seat - 1], speed) + .25 * spin
            angle = angle.coerceIn(-maxAngle, maxAngle)
            spin = (.3 * swing[seat - 1]).coerceIn(-.75, .75)
            vx = speed * sin(angle); vy = speed * cos(angle) * away
            x = (hx + sin(angle) * (headR + ballR)).coerceIn(ballR, 1 - ballR); y = hy + cos(angle) * (headR + ballR) * away
            hits++
        }
        private fun point(scorer: Int) {
            score[scorer - 1]++; x = .5; y = courtH / 2; vx = 0.0; vy = 0.0; spin = 0.0; scored = true
            if (score[scorer - 1] >= 7) { outcome = scorer; return }
            serveTo = 3 - scorer; countdown = 1200.0
        }
        private fun r4(v: Double) = (v * 10000).roundToInt() / 10000.0
        fun frame(id: String) = buildJsonObject {
            put("id", id); put("t", System.currentTimeMillis())
            putJsonArray("b") { add(r4(x)); add(r4(y)); add(r4(vx)); add(r4(vy)) }
            putJsonArray("p") { add(r4(paddles[0])); add(r4(paddles[1])) }
            putJsonArray("s") { add(score[0]); add(score[1]) }
            put("c", ceil(countdown).toInt()); put("h", hits); put("w", r4(spin))
        }
    }
}
