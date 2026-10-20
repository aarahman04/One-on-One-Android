package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

internal fun matchJson(
    id: String = "m1", game: String = "chess", status: String = "playing", turn: String? = "me", seq: Int = 0, version: Int = 1,
    board: JsonObject? = null, outcome: JsonObject? = null, players: List<String> = listOf("me", "other"), inviter: String = "me",
    rematch: List<String> = emptyList(), away: List<String> = emptyList(), paused: Boolean = false, drawOffer: String? = null,
) = buildJsonObject {
    put("id", id); put("game", game); put("status", status); put("inviter", inviter)
    putJsonArray("players") { players.forEach { add(it) } }
    put("turn", turn); put("seq", seq); put("version", version)
    if (board != null) put("board", board) else put("board", JsonNull)
    if (outcome != null) put("outcome", outcome) else put("outcome", JsonNull)
    put("drawOffer", drawOffer)
    putJsonArray("rematch") { rematch.forEach { add(it) } }
    putJsonObject("series") { players.forEach { put(it, 0) } }
    putJsonArray("away") { away.forEach { add(it) } }
    put("paused", paused); put("expiresAt", JsonNull)
}
internal fun outcome(winner: String?, reason: String) = buildJsonObject { put("winner", winner); put("reason", reason) }
internal fun chessBoard(fen: String, from: String? = null, to: String? = null, claim: String? = null) = buildJsonObject {
    put("fen", fen)
    if (from != null) putJsonObject("last") { put("from", from); put("to", to) } else put("last", JsonNull)
    put("check", false); put("claim", claim)
}

class MatchTest {
    @Test fun decodesSnapshotsAndRejectsMalformedOnes() {
        val m = MatchCodec.decode(matchJson(players = listOf("other", "me")))!!
        assertEquals(GameKind.Chess, m.game)
        assertEquals(2, m.seat("me"))
        assertEquals(1, m.seat("other"))
        assertEquals(0, m.seat("stranger"))
        assertNull(MatchCodec.decode(matchJson(game = "poker")))
        assertNull(MatchCodec.decode(matchJson(players = listOf("me", "me"))))
        assertNull(MatchCodec.decode(matchJson(turn = "stranger")))
        assertNull(MatchCodec.decode(matchJson(status = "over"))) // over needs an outcome
        assertNull(MatchCodec.decode(matchJson(outcome = outcome("me", "checkmate")))) // and playing must not have one
        assertNull(MatchCodec.decode(JsonPrimitive("x")))
        val over = MatchCodec.decode(matchJson(status = "over", turn = null, outcome = outcome(null, "agreement")))!!
        assertEquals(MatchOutcome(null, "agreement"), over.outcome)
    }

    @Test fun stageFollowsServerStatusAndDismissal() {
        fun m(status: String, inviter: String = "me", reason: String? = null) =
            MatchCodec.decode(matchJson(status = status, inviter = inviter, turn = null, outcome = reason?.let { outcome(null, it) }))
        assertEquals(GameStage.Outgoing, stageOf(m("invited"), "me", false, null))
        assertEquals(GameStage.Incoming, stageOf(m("invited", inviter = "other"), "me", false, null))
        assertEquals(GameStage.Playing, stageOf(m("playing"), "me", true, null))
        assertEquals(GameStage.Result, stageOf(m("over", reason = "resigned"), "me", false, null))
        assertEquals(GameStage.Idle, stageOf(m("over", reason = "resigned"), "me", false, "m1"))
        assertEquals(GameStage.Hub, stageOf(m("over", reason = "declined"), "me", true, null))
        assertEquals(GameStage.Idle, stageOf(m("over", reason = "expired"), "me", false, null))
        assertEquals(GameStage.Hub, stageOf(null, "me", true, null))
    }

    @Test fun chessViewReadsFenLegalMovesCheckAndCaptures() {
        val start = ChessView.from(chessBoard("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))!!
        assertTrue(start.whiteToMove)
        assertEquals(20, start.legal.size)
        assertEquals(setOf("e2e3", "e2e4"), start.movesFrom(squareIndex("e2")).toSet())
        assertEquals('K', start.pieces[squareIndex("e1")])
        assertEquals(-1, start.checkSquare)
        // Fool's mate: black queen took nothing yet, white king in check.
        val mate = ChessView.from(chessBoard("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3", "d8", "h4"))!!
        assertEquals(squareIndex("e1"), mate.checkSquare)
        assertTrue(mate.legal.isEmpty())
        assertEquals(squareIndex("d8") to squareIndex("h4"), mate.lastFrom to mate.lastTo)
        val traded = ChessView.from(chessBoard("rnbqkbnr/ppp1pppp/8/8/8/8/PPPP1PPP/RNBQKBNR w KQkq - 0 3"))!!
        assertEquals(listOf('p'), traded.captured(white = false))
        assertEquals(listOf('P'), traded.captured(white = true))
        val promote = ChessView.from(chessBoard("8/4P3/8/8/8/8/k7/4K3 w - - 0 1"))!!
        assertEquals(setOf("e7e8q", "e7e8r", "e7e8b", "e7e8n"), promote.movesFrom(squareIndex("e7")).toSet())
        assertNull(ChessView.from(buildJsonObject { put("fen", 3) }))
    }

    private fun step(before: String, after: String, from: String, to: String): Pair<List<Sprite>, List<Sprite>> {
        var id = 0
        val first = advanceSprites(null, ChessView.from(chessBoard(before))!!) { id++ }
        return first to advanceSprites(first, ChessView.from(chessBoard(after, from, to))!!) { id++ }
    }
    private fun List<Sprite>.at(square: String) = first { it.square == squareIndex(square) }

    @Test fun spritesSlideCaptureCastleEnPassantAndPromote() {
        val (a, b) = step("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", "e2", "e4")
        assertEquals(a.at("e2").id, b.at("e4").id)
        assertEquals(32, b.size)
        val (c, d) = step("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1", "4k3/8/8/3P4/8/8/8/4K3 b - - 0 1", "e4", "d5")
        assertEquals(c.at("e4").id, d.at("d5").id)
        assertEquals(3, d.size)
        val (e, f) = step("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", "4k3/8/8/8/8/8/8/R4RK1 b - - 1 1", "e1", "g1")
        assertEquals(e.at("e1").id, f.at("g1").id)
        assertEquals(e.at("h1").id, f.at("f1").id)
        val (g, h) = step("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1", "4k3/8/8/8/8/4p3/8/4K3 w - - 0 2", "d4", "e3")
        assertEquals(g.at("d4").id, h.at("e3").id)
        assertEquals(3, h.size)
        val (i, j) = step("8/4P3/8/8/8/8/k7/4K3 w - - 0 1", "4N3/8/8/8/8/8/k7/4K3 b - - 0 1", "e7", "e8")
        assertEquals(i.at("e7").id, j.at("e8").id)
        assertEquals('N', j.at("e8").piece)
        // Not one move apart (e.g. reconnect after several moves): fresh identities, still the right board.
        val (k, l) = step("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "e7", "e5")
        assertTrue(l.none { s -> k.any { it.id == s.id } })
        assertEquals('P', l.at("e4").piece)
    }

    @Test fun gridAndDotsViews() {
        val c4 = GridView.from(buildJsonObject {
            putJsonArray("cells") { repeat(42) { add(if (it == 38) 1 else 0) } }
            putJsonArray("line") {}; put("last", 38)
        }, 42)!!
        assertEquals(31, c4.landing(3))
        assertEquals(37, c4.landing(2))
        assertEquals(-1, c4.landing(7))
        assertNull(GridView.from(buildJsonObject { putJsonArray("cells") { repeat(9) { add(3) } }; putJsonArray("line") {} }, 9))
        assertNull(GridView.from(buildJsonObject { putJsonArray("cells") { repeat(8) { add(0) } }; putJsonArray("line") {} }, 9))
        val dots = DotsView.from(buildJsonObject {
            putJsonArray("h") { repeat(30) { add(0) } }; putJsonArray("v") { repeat(30) { add(0) } }
            putJsonArray("boxes") { repeat(25) { add(if (it < 3) 2 else 0) } }
            putJsonObject("last") { put("h", false); put("r", 1); put("c", 2) }
        })!!
        assertEquals(3, dots.score(2))
        assertEquals(false to 8, dots.lastHorizontal to dots.lastIndex)
    }
}
