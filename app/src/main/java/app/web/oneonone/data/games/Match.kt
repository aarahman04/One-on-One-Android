package app.web.oneonone.data.games

import kotlinx.serialization.json.*

enum class GameKind(val wire: String, val title: String, val description: String) {
    Chess("chess", "Chess", "A little strategy, a worthy opponent"),
    Pong("pong", "Table Tennis", "Keep the rally going · first to 7"),
    Connect4("connect4", "Connect 4", "Drop a disc, connect four"),
    TicTacToe("tictactoe", "Tic-Tac-Toe", "Three in a row, one good move"),
    Dots("dots", "Dots & Boxes", "Close a box, take another turn"),
}

enum class MatchStatus(val wire: String) { Invited("invited"), Playing("playing"), Over("over") }
data class MatchOutcome(val winner: String?, val reason: String)

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
) {
    /** 1 or 2 for a player (chess: 1 = white), 0 otherwise. */
    fun seat(user: String) = players.indexOf(user) + 1
}

class MatchException(val code: String) : Exception(code)

/** Strict decoding at the trust boundary: anything malformed is dropped, never half-applied. */
object MatchCodec {
    val events = setOf("match:invite", "match:accept", "match:decline", "match:leave", "match:resign",
        "match:move", "match:draw", "match:rematch", "match:sync", "match:away")
    private val endings = setOf("declined", "cancelled", "expired")
    fun quietEnding(reason: String?) = reason in endings

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    private fun JsonObject.texts(key: String) = (get(key) as? JsonArray)?.map { (it as JsonPrimitive).also { p -> require(p.isString) }.content }

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
        Match(
            id = o.text("id")!!.also { require(it.isNotBlank() && it.length <= 64) },
            game = game, status = status, inviter = inviter, players = players, turn = turn,
            seq = o.int("seq")!!.also { require(it >= 0) }, version = o.int("version")!!,
            board = o["board"] as? JsonObject, outcome = outcome, drawOffer = drawOffer,
            rematch = o.texts("rematch").orEmpty().filter { it in players },
            series = series.filterKeys { it in players },
            away = o.texts("away").orEmpty().filter { it in players },
            paused = (o["paused"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }.getOrNull()
}
