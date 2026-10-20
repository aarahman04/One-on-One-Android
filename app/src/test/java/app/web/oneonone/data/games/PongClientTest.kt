package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PongClientTest {
    private fun frame(b: List<Float>, p: List<Float> = listOf(.5f, .5f), s: List<Int> = listOf(0, 0), c: Int = 0, id: String = "m") = buildJsonObject {
        put("id", id); put("t", 1)
        putJsonArray("b") { b.forEach { add(it) } }; putJsonArray("p") { p.forEach { add(it) } }
        putJsonArray("s") { s.forEach { add(it) } }; put("c", c); put("h", 0)
    }
    private val ms = 1_000_000L

    @Test fun seatTwoSeesTheCourtRotatedAndOwnScoreFirst() {
        val one = PongClient(1); val two = PongClient(2)
        val f = frame(listOf(.2f, .3f, 0f, 0f), p = listOf(.3f, .7f), s = listOf(4, 2))
        assertTrue(one.receive(f, "m", 0)); assertTrue(two.receive(f, "m", 0))
        assertEquals(.2f, one.ballX, 1e-5f); assertEquals(.3f, one.ballY, 1e-5f)
        assertEquals(.8f, two.ballX, 1e-5f); assertEquals(1.3f, two.ballY, 1e-5f)
        assertEquals(.7f, one.theirX, 1e-5f); assertEquals(.7f, two.theirX, 1e-5f) // seat 1's paddle 0.3 seen rotated
        assertEquals(4 to 2, one.myScore to one.theirScore)
        assertEquals(2 to 4, two.myScore to two.theirScore)
        // Own paddle goes back to the server in server coordinates.
        assertEquals(.75f, two.paddle(.25f), 1e-5f)
        assertEquals(.25f, one.paddle(.25f), 1e-5f)
        assertEquals(.88f, one.paddle(2f), 1e-5f) // clamped
    }

    @Test fun extrapolatesSmoothlyReflectsOffWallsAndHoldsAtThePaddle() {
        val p = PongClient(1)
        p.receive(frame(listOf(.5f, .8f, .5f, 0f)), "m", 0)
        repeat(10) { p.advance(it * 10 * ms) }
        assertTrue(p.ballX > .5f && p.ballX < .56f)
        // Toward the right wall: reflected, never outside the court.
        p.receive(frame(listOf(.95f, .8f, 1f, 0f)), "m", 100 * ms)
        repeat(20) { p.advance((100 + it * 5) * ms) }
        assertTrue(p.ballX <= 1f - PongClient.BallR + 1e-4f)
        // Toward my paddle: held at its plane until the server rules hit or miss.
        p.receive(frame(listOf(.5f, 1.48f, 0f, 1.5f)), "m", 200 * ms)
        repeat(30) { p.advance((200 + it * 5) * ms) }
        assertTrue(p.ballY <= PongClient.CourtH - PongClient.PaddleInset - PongClient.BallR + 1e-4f)
        // A served reset (zero velocity) snaps.
        p.receive(frame(listOf(.5f, .8f, 0f, 0f), c = 1500), "m", 400 * ms)
        p.advance(400 * ms)
        assertEquals(.8f, p.ballY, 1e-5f)
        assertEquals(1500, p.countdownMs)
        p.advance(900 * ms)
        assertEquals(1500 - 150, p.countdownMs) // extrapolation is capped at 150 ms
    }

    @Test fun rejectsMalformedAndForeignFrames() {
        val p = PongClient(1)
        assertFalse(p.receive(frame(listOf(.5f, .5f, 0f, 0f), id = "other"), "m", 0))
        assertFalse(p.receive(buildJsonObject { put("id", "m"); putJsonArray("b") { add(1) } }, "m", 0))
        assertFalse(p.receive(frame(listOf(.5f, Float.NaN, 0f, 0f)), "m", 0))
        assertFalse(p.live)
    }
}
