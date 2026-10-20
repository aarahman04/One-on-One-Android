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
        assertEquals(.8f, two.ballX, 1e-5f); assertEquals(1.5f, two.ballY, 1e-5f)
        assertEquals(.7f, one.theirX, 1e-5f); assertEquals(.7f, two.theirX, 1e-5f) // seat 1's paddle 0.3 seen rotated
        assertEquals(4 to 2, one.myScore to one.theirScore)
        assertEquals(2 to 4, two.myScore to two.theirScore)
        // Own paddle goes back to the server in server coordinates.
        assertEquals(.75f, two.paddle(.25f), 1e-5f)
        assertEquals(.25f, one.paddle(.25f), 1e-5f)
        assertEquals(1f - PongClient.RacketMin, one.paddle(2f), 1e-5f) // clamped to the table
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
        // Toward my racket: held on the head's surface until the server rules hit or miss.
        p.paddle(.5f)
        p.receive(frame(listOf(.5f, 1.5f, 0f, 1.5f)), "m", 200 * ms)
        repeat(30) { p.advance((200 + it * 5) * ms) }
        val gap = kotlin.math.hypot(p.ballX - .5f, p.ballY - PongClient.CourtH)
        assertTrue("ball stops on the head, gap $gap", gap >= PongClient.HeadR + PongClient.BallR - 1e-3f)
        // With the racket out of the way it is drawn travelling past the end line.
        p.paddle(.9f)
        p.receive(frame(listOf(.3f, 1.6f, 0f, 1.5f)), "m", 400 * ms)
        repeat(30) { p.advance((400 + it * 5) * ms) }
        assertTrue(p.ballY > 1.75f)
        // A served reset (zero velocity) snaps.
        p.receive(frame(listOf(.5f, .8f, 0f, 0f), c = 1500), "m", 600 * ms)
        p.advance(600 * ms)
        assertEquals(.8f, p.ballY, 1e-5f)
        assertEquals(1500, p.countdownMs)
        p.advance(1100 * ms)
        assertEquals(1500 - 150, p.countdownMs) // extrapolation is capped at 150 ms
    }

    @Test fun ballArcsOverTheTableAndMarksOneBounce() {
        val p = PongClient(1)
        // Leaving my racket upwards: low at the racket, high mid-way, down on the far half, up again.
        val heights = (0..17).map { i ->
            val y = 1.7f - i * .1f
            p.receive(frame(listOf(.5f, y, 0f, -1f)), "m", i * 100 * ms); p.advance(i * 100 * ms); p.height
        }
        assertTrue(heights.first() < .3f)
        assertTrue(heights.max() > .9f)
        val landing = heights.indexOfFirst { it < .1f && heights.indexOf(it) > 5 }
        assertTrue("touches down on the far half", landing in 10..14)
        assertTrue(p.bounceAt > 0L && p.bounceY < PongClient.CourtH / 2)
        // Waiting for a serve: on the table.
        p.receive(frame(listOf(.5f, .9f, 0f, 0f), c = 1000), "m", 5000 * ms); p.advance(5000 * ms)
        assertEquals(0f, p.height, 0f)
    }

    @Test fun sidespinCurvesTheDrawnBallAndRotatesWithTheTable() {
        for (seat in 1..2) {
            val p = PongClient(seat)
            val f = buildJsonObject { frame(listOf(.5f, .9f, 0f, -1f)).forEach { (k, v) -> put(k, v) }; put("w", 2) }
            p.receive(f, "m", 0)
            repeat(16) { p.advance(it * 10 * ms) }
            if (seat == 1) assertTrue("curves right, x ${p.ballX}", p.ballX > .51f) else assertTrue("mirrored, x ${p.ballX}", p.ballX < .49f)
            assertTrue(p.spinAngle != 0f)
        }
        val plain = PongClient(1)
        plain.receive(frame(listOf(.5f, .9f, 0f, -1f)), "m", 0)
        assertEquals(0f, plain.spin, 0f) // older servers send no `w`
    }

    @Test fun rejectsMalformedAndForeignFrames() {
        val p = PongClient(1)
        assertFalse(p.receive(frame(listOf(.5f, .5f, 0f, 0f), id = "other"), "m", 0))
        assertFalse(p.receive(buildJsonObject { put("id", "m"); putJsonArray("b") { add(1) } }, "m", 0))
        assertFalse(p.receive(frame(listOf(.5f, Float.NaN, 0f, 0f)), "m", 0))
        assertFalse(p.live)
    }
}
