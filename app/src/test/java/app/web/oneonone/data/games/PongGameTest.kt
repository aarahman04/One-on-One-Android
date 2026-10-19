package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class PongGameTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
    @Test fun countdownThenServeAndPaddlesClamp() {
        val game = PongGame()
        game.paddle(1, -1f); game.paddle(2, 2f)
        assertEquals(PongGame.PaddleHalf, game.hostY, 0f)
        assertEquals(1f - PongGame.PaddleHalf, game.guestY, 0f)
        repeat(59) { game.tick(.05f, true) }
        assertEquals(0f, game.vx, 0f)
        repeat(3) { game.tick(.05f, true) }
        assertTrue(game.vx > 0f)
    }
    @Test fun guestValidatesSequenceShapeFiniteCoordinatesAndScore() {
        val game = PongGame()
        val first = json("""{"b":[0.8,0.5,0.3,-0.2],"p":0.5,"s":[0,0]}""")
        game.receive(1, 0, first)
        assertThrows(IllegalArgumentException::class.java) { game.receive(1, 0, first) }
        assertThrows(IllegalArgumentException::class.java) { game.receive(1, 2, first) }
        for (invalid in listOf(
            """{"b":[2,0.5,0.3,0],"p":0.5,"s":[0,0]}""",
            """{"b":[0.5,0.5,3,0],"p":0.5,"s":[0,0]}""",
            """{"b":[0.5,0.5,0,0],"p":0.5,"s":[7,0]}""",
            """{"b":[0.5,0.5,0,0],"p":0.5,"s":[-1,0]}""",
            """{"b":[0.5,0.5,0,0],"p":"0.5","s":[0,0]}""",
            """{"b":[0.5,0.5],"p":0.5,"s":[0,0]}""")) {
            assertThrows(IllegalArgumentException::class.java) { game.receive(1, 1, json(invalid)) }
        }
        game.receive(1, 1, first)
        repeat(3) { game.tick(.04f, false) }
        assertTrue(game.x > .5f && game.x < .9f)
        game.receive(2, 0, json("""{"p":0.4}"""))
        assertEquals(.4f, game.guestY, 0f)
        assertThrows(IllegalArgumentException::class.java) { game.receive(2, 1, first) }
        assertThrows(IllegalArgumentException::class.java) { game.paddle(1, Float.NaN) }
    }
    @Test fun ballSpeedsUpOnHitsAndStaysBounded() {
        val game = PongGame()
        var speed = 0f
        var hits = 0
        repeat(6000) {
            game.paddle(1, game.y); game.paddle(2, game.y)
            game.tick(1f / 60f, true)
            if (game.hits > hits) {
                val next = sqrt(game.vx * game.vx + game.vy * game.vy)
                assertTrue(next >= speed - .0001f && next <= PongGame.MaxSpeed + .0001f)
                speed = next; hits = game.hits
            }
            assertTrue(game.x in 0f..1f && game.y in 0f..1f)
        }
        assertTrue(hits > 10)
    }
    @Test fun firstToSevenStopsSimulationAndPacketsStaySmall() {
        val game = PongGame()
        game.paddle(1, PongGame.PaddleHalf); game.paddle(2, PongGame.PaddleHalf)
        repeat(120_000) { game.tick(1f / 60f, true) }
        assertTrue(game.winner in 1..2)
        assertEquals(7, maxOf(game.hostScore, game.guestScore))
        val final = game.packet(true)
        repeat(100) { game.tick(.05f, true) }
        assertEquals(final, game.packet(true))
        assertTrue(GameProtocol.payload("id") { put("seq", 0); put("data", final) }.toString().toByteArray().size < 4096)
    }
}
