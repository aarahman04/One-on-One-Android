package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BoardGamesTest {
    private fun TurnGame.play(data: String) = play(turn, nextSeq, Json.parseToJsonElement(data).jsonObject)
    private fun fails(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java, block) }

    @Test fun ticTacToeTurnSequenceLegalityAndWin() {
        val game = TicTacToeGame()
        val first = buildJsonObject { put("cell", 0) }
        fails { game.play(2, 0, first) }
        fails { game.play(1, 1, first) }
        fails { game.play("""{"cell":"0"}""") }
        assertEquals(0, game.nextSeq)
        game.play(1, 0, first)
        fails { game.play(1, 0, first) }
        fails { game.play(2, 1, first) }
        assertEquals(1, game.nextSeq)
        for (cell in intArrayOf(3, 1, 4, 2)) game.play("""{"cell":$cell}""")
        assertEquals(1, game.result!!.winner)
        assertArrayEquals(intArrayOf(0, 1, 2), game.winning)
        fails { game.play("""{"cell":8}""") }
    }

    @Test fun ticTacToeDraw() {
        val game = TicTacToeGame()
        for (cell in intArrayOf(0,1,2,4,3,5,7,6,8)) game.play("""{"cell":$cell}""")
        assertEquals(0, game.result!!.winner)
        assertEquals(9, game.nextSeq)
    }

    @Test fun connectFourVerticalHorizontalAndDiagonal() {
        for (moves in listOf(intArrayOf(0,1,0,1,0,1,0), intArrayOf(0,6,1,6,2,5,3), intArrayOf(0,1,1,2,4,2,2,3,4,3,5,3,3))) {
            val game = ConnectFourGame()
            for (col in moves) game.play("""{"col":$col}""")
            assertEquals(1, game.result!!.winner)
            assertEquals(4, game.winning.size)
        }
    }

    @Test fun connectFourFullColumnBadSequenceAndOutOfTurnAreRejected() {
        val game = ConnectFourGame()
        repeat(6) { game.play("""{"col":0}""") }
        val state = game.cells.copyOf()
        fails { game.play("""{"col":0}""") }
        fails { game.play("""{"col":7}""") }
        fails { game.play(2, 6, buildJsonObject { put("col", 1) }) }
        fails { game.play(1, 5, buildJsonObject { put("col", 1) }) }
        assertEquals(6, game.nextSeq)
        assertArrayEquals(state, game.cells)
    }

    @Test fun connectFourFullBoardDraw() {
        val game = ConnectFourGame()
        for (col in intArrayOf(3,5,3,2,4,6,5,4,0,1,2,4,1,4,6,2,4,5,3,5,2,0,4,0,0,1,2,6,5,3,0,0,2,1,1,5,1,3,6,6,6,3)) game.play("""{"col":$col}""")
        assertEquals(0, game.result!!.winner)
        assertEquals(42, game.nextSeq)
    }

    @Test fun dotsCompletingBoxesKeepsTurnAndCountsScore() {
        val game = DotsGame()
        for (data in listOf("""{"h":true,"r":0,"c":0}""", """{"h":false,"r":0,"c":0}""",
            """{"h":true,"r":1,"c":0}""", """{"h":false,"r":0,"c":1}""")) game.play(data)
        assertEquals(2, game.turn)
        assertEquals(1, game.score(2))
        game.play("""{"h":true,"r":0,"c":1}""")
        game.play("""{"h":true,"r":1,"c":1}""")
        game.play("""{"h":false,"r":0,"c":2}""")
        assertEquals(2, game.score(2)); assertEquals(2, game.turn)
        fails { game.play("""{"h":false,"r":0,"c":2}""") }
        fails { game.play("""{"h":false,"r":5,"c":0}""") }
        fails { game.play(1, game.nextSeq, buildJsonObject { put("h", true); put("r", 2); put("c", 3) }) }
        fails { game.play(2, game.nextSeq - 1, buildJsonObject { put("h", true); put("r", 2); put("c", 3) }) }
        assertEquals(7, game.nextSeq)
    }

    @Test fun dotsAllSixtyLinesFinishTwentyFiveBoxes() {
        val game = DotsGame()
        for (h in listOf(true, false)) repeat(30) { i ->
            game.play(buildJsonObject { put("h", h); put("r", i / if (h) 5 else 6); put("c", i % if (h) 5 else 6) }.toString())
        }
        assertEquals(60, game.nextSeq)
        assertEquals(25, game.score(1) + game.score(2))
        assertNotNull(game.result)
    }

    @Test fun dotsOneEdgeCanCompleteTwoBoxes() {
        val game = DotsGame()
        for (data in listOf("""{"h":true,"r":0,"c":0}""", """{"h":true,"r":1,"c":0}""",
            """{"h":true,"r":0,"c":1}""", """{"h":true,"r":1,"c":1}""",
            """{"h":false,"r":0,"c":0}""", """{"h":false,"r":0,"c":2}""")) game.play(data)
        game.play("""{"h":false,"r":0,"c":1}""")
        assertEquals(2, game.score(1)); assertEquals(1, game.turn)
    }

    @Test fun chessRejectsTeleportBlockedMoveDuplicateOutOfOrderAndWrongPlayer() {
        val game = ChessGame()
        assertEquals(20, game.legal.size)
        for (uci in listOf("a1a8", "c1h6", "e2e5", "e2e4q")) fails { game.play("""{"uci":"$uci"}""") }
        val move = buildJsonObject { put("uci", "e2e4") }
        fails { game.play(2, 0, move) }; fails { game.play(1, 2, move) }
        assertEquals(0, game.nextSeq)
        game.play(1, 0, move)
        fails { game.play(2, 0, buildJsonObject { put("uci", "e7e5") }) }
        assertEquals('P', game.pieces[28])
        assertEquals(2, game.turn)
    }

    @Test fun chessMateStalemateInsufficientFiftyMovesAndRepetition() {
        val mate = ChessGame()
        for (uci in listOf("f2f3", "e7e5", "g2g4", "d8h4")) mate.play("""{"uci":"$uci"}""")
        assertEquals(GameResult(2, "Checkmate"), mate.result)
        assertEquals(4, mate.checkSquare)
        assertEquals("Stalemate", ChessGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").result!!.detail)
        assertEquals("Insufficient material", ChessGame("7k/8/6K1/8/8/8/8/8 w - - 0 1").result!!.detail)
        val fifty = ChessGame("7k/8/6K1/8/8/8/8/R7 w - - 99 60")
        fifty.play("""{"uci":"a1b1"}""")
        assertEquals("Draw by the 50-move rule", fifty.result!!.detail)
        val repetition = ChessGame()
        repeat(2) { for (uci in listOf("g1f3", "g8f6", "f3g1", "f6g8")) repetition.play("""{"uci":"$uci"}""") }
        assertEquals("Draw by repetition", repetition.result!!.detail)
    }

    @Test fun chessCastlingEnPassantAndAllPromotionOptions() {
        val castle = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        castle.play("""{"uci":"e1g1"}""")
        assertEquals('K', castle.pieces[6]); assertEquals('R', castle.pieces[5])
        val ep = ChessGame()
        for (uci in listOf("e2e4", "a7a6", "e4e5", "d7d5", "e5d6")) ep.play("""{"uci":"$uci"}""")
        assertEquals('P', ep.pieces[43]); assertEquals('.', ep.pieces[35]); assertEquals(listOf('p'), ep.captured)
        for (piece in "qrbn") {
            val promotion = ChessGame("7k/P7/8/8/8/8/8/7K w - - 0 1")
            promotion.play("""{"uci":"a7a8$piece"}""")
            assertEquals(piece.uppercaseChar(), promotion.pieces[56])
        }
    }
}
