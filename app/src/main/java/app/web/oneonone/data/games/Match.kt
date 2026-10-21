package app.web.oneonone.data.games

import kotlinx.serialization.json.*

enum class GameKind(val wire: String, val title: String, val description: String) {
    Chess("chess", "Chess", "A little strategy, a worthy opponent"),
    Pong("pong", "Ping Pong", "Swipe, spin and rally"),
    Connect4("connect4", "Connect 4", "Drop a disc, connect four"),
    TicTacToe("tictactoe", "Tic-Tac-Toe", "Three in a row, one good move"),
    Reversi("reversi", "Reversi", "Outflank and flip · most discs wins"),
    /** Retired from the hub (2026-10-21); still decoded so older apps' invites and logs work. */
    Dots("dots", "Dots & Boxes", "Close a box, take another turn"),
}

enum class MatchStatus(val wire: String) { Invited("invited"), Playing("playing"), Over("over") }
data class MatchOutcome(val winner: String?, val reason: String)

/** A turn game's time control: each seat starts with [base] ms, gains [inc] ms per move; [left] is per seat at the snapshot. */
data class MatchClock(val base: Long, val inc: Long, val left: List<Long>)

/** What the inviter chooses: a turn game's clock (null = no time limit) or Ping Pong's target score. */
data class MatchOptions(val clock: Pair<Long, Long>? = null, val points: Int? = null)

/** "10 min", "3 min + 2 s", "30 s", "1 h" for a time control. */
fun clockLabel(base: Long, inc: Long): String {
    val s = base / 1000
    val main = when {
        s % 3600 == 0L -> "${s / 3600} h"
        s % 60 == 0L -> "${s / 60} min"
        s > 60 -> "${s / 60} min ${s % 60} s"
        else -> "$s s"
    }
    return if (inc > 0) "$main + ${inc / 1000} s" else main
}

/** Clock face: "1:05:00", "4:59", "0:09.4" under ten seconds. */
fun clockFace(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val s = t / 1000
    return when {
        s >= 3600 -> "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
        t < 10_000 -> "0:%02d.%d".format(s, t / 100 % 10)
        else -> "%d:%02d".format(s / 60, s % 60)
    }
}

/** The server's authoritative match (contract §12). Clients render it; they never decide it. */
data class Match(
    val id: String,
    val game: GameKind,
    val status: MatchStatus,
    val inviter: String,
    val players: List<String>,
    val turn: String?,
    val seq: Int,
    val version: Int,
    val board: JsonObject?,
    val outcome: MatchOutcome?,
    val drawOffer: String?,
    val rematch: List<String>,
    val series: Map<String, Int>,
    val away: List<String>,
    val paused: Boolean,
    val clock: MatchClock? = null,
    /** Ping Pong's target score (7 by default). */
    val points: Int? = null,
) {
    /** 1 or 2 for a player (chess: 1 = white), 0 otherwise. */
    fun seat(user: String) = players.indexOf(user) + 1
}

class MatchException(val code: String) : Exception(code)

/** A short emoji or comment a player sent during a game; shown briefly, never stored. */
data class GameReaction(val id: String, val from: String, val text: String)
const val REACTION_MAX_CHARS = 60

/** Strict decoding at the trust boundary: anything malformed is dropped, never half-applied. */
object MatchCodec {
    val events = setOf("match:invite", "match:accept", "match:decline", "match:leave", "match:resign",
        "match:move", "match:draw", "match:rematch", "match:sync", "match:away", "match:react")
    private val endings = setOf("declined", "cancelled", "expired")
    fun quietEnding(reason: String?) = reason in endings

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    private fun JsonObject.texts(key: String) = (get(key) as? JsonArray)?.map { (it as JsonPrimitive).also { p -> require(p.isString) }.content }

    /** `match:reaction` (contract §12): kept only for [matchId] and a player in it; text bounded like the server's. */
    fun reaction(value: JsonElement?, matchId: String, players: List<String>): GameReaction? = runCatching {
        val o = value as JsonObject
        val text = o.text("text")!!.trim()
        require(o.text("matchId") == matchId && text.isNotEmpty() && text.codePointCount(0, text.length) <= REACTION_MAX_CHARS)
        GameReaction(o.text("id")!!.also { require(it.isNotBlank() && it.length <= 64) }, o.text("from")!!.also { require(it in players) }, text)
    }.getOrNull()

    fun decode(value: JsonElement?): Match? = runCatching {
        val o = value as JsonObject
        val game = GameKind.entries.first { it.wire == o.text("game") }
        val status = MatchStatus.entries.first { it.wire == o.text("status") }
        val players = o.texts("players")!!
        require(players.size == 2 && players.all { it.isNotBlank() } && players[0] != players[1])
        val member = { id: String? -> id == null || id in players }
        val turn = o.text("turn")
        val outcome = (o["outcome"] as? JsonObject)?.let { MatchOutcome(it.text("winner"), it.text("reason")!!) }
        val drawOffer = o.text("drawOffer")
        val inviter = o.text("inviter")!!
        require(member(turn) && member(outcome?.winner) && member(drawOffer) && inviter in players)
        require((status == MatchStatus.Over) == (outcome != null))
        val series = (o["series"] as? JsonObject)?.mapValues { (it.value as JsonPrimitive).int } ?: emptyMap()
        val clock = (o["clock"] as? JsonObject)?.let { c ->
            val left = (c["left"] as JsonArray).map { (it as JsonPrimitive).long }
            MatchClock(c.long("base")!!, c.long("inc")!!, left).also {
                require(it.base > 0 && it.inc >= 0 && left.size == 2 && left.all { ms -> ms >= 0 })
            }
        }
        Match(
            id = o.text("id")!!.also { require(it.isNotBlank() && it.length <= 64) },
            game = game, status = status, inviter = inviter, players = players, turn = turn,
            seq = o.int("seq")!!.also { require(it >= 0) }, version = o.int("version")!!,
            board = o["board"] as? JsonObject, outcome = outcome, drawOffer = drawOffer,
            rematch = o.texts("rematch").orEmpty().filter { it in players },
            series = series.filterKeys { it in players },
            away = o.texts("away").orEmpty().filter { it in players },
            paused = (o["paused"] as? JsonPrimitive)?.booleanOrNull == true,
            clock = clock,
            points = o.int("points")?.takeIf { it in 1..99 },
        )
    }.getOrNull()
}
