package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PongClientTest {
    private val ms = 1_000_000L
    private fun frame(
        b: List<Float> = listOf(.2f, .3f, 0f, 0f),
        extra: JsonObjectBuilder.() -> Unit = {},
    ) = buildJsonObject {
        put("id", "m"); put("c", 0); put("h", 1)
        putJsonArray("b") { b.forEach { add(it) } }
        putJsonArray("p") { add(.3f); add(.7f) }
        putJsonArray("r") { add(1.9f); add(-.2f) }
        putJsonArray("s") { add(4); add(2) }
        putJsonArray("z") { add(.16f); add(0f) }
        extra()
    }
    private fun JsonObjectBuilder.arc(elapsed: Float = .2f) {
        putJsonArray("a") { listOf(.5f, 1.7f, .16f, .5f, 1.4f, .49f, .7f, .5f, .49f, .7f, .5f, -.49f, elapsed, .9f).forEach { add(it) } }
    }

    @Test fun seatTwoRotatesBothCoordinatesAndOwnScoreComesFirst() {
        val one = PongClient(1); val two = PongClient(2)
        assertTrue(one.receive(frame(), "m", ms)); assertTrue(two.receive(frame(), "m", ms))
        assertEquals(.2f, one.ballX, 1e-5f); assertEquals(.3f, one.ballY, 1e-5f)
        assertEquals(.8f, two.ballX, 1e-5f); assertEquals(1.5f, two.ballY, 1e-5f)
        assertEquals(-.2f, one.theirY, 1e-5f); assertEquals(-.1f, two.theirY, 1e-5f)
        assertEquals(4 to 2, one.myScore to one.theirScore); assertEquals(2 to 4, two.myScore to two.theirScore)
        val moved = two.paddle(.25f, 2.1f)
        assertEquals(.75f, moved.first, 1e-5f); assertEquals(-.3f, moved.second, 1e-5f)
        assertEquals(PongClient.RacketMax to PongClient.RacketNear, one.paddle(5f, -4f))
        one.paddle(.5f, 2f); one.paddle(.6f, 1.9f)
        one.advance(ms); one.advance(30 * ms)
        assertTrue(one.myAngle > 0)
        one.paddle(.6f, 1.9f, true)
        for (i in 1..30) one.advance((30 + i * 20) * ms)
        assertEquals(0f, one.myAngle, .01f)
    }

    @Test fun displaysActualCubicHeightAndRotatesTheWholeFlight() {
        val f = frame { arc() }
        val one = PongClient(1); val two = PongClient(2)
        one.receive(f, "m", ms); two.receive(f, "m", ms)
        for (i in 1..15) { one.advance(i * 10 * ms); two.advance(i * 10 * ms) }
        assertTrue(one.height > .16f)
        assertEquals(1f, one.ballX + two.ballX, 1e-4f)
        assertEquals(PongClient.CourtH, one.ballY + two.ballY, 1e-4f)
        assertEquals(one.height, two.height, 1e-4f)
        assertTrue(one.trail.isNotEmpty() && one.trail.size <= 8)
        val arc = FloatArray(14).also { it[0] = .1f; it[9] = .8f; it[13] = 1f }
        assertEquals(.1f, PongClient.flightPosition(arc, 0f, 0f)[0], 1e-5f)
        assertEquals(.8f, PongClient.flightPosition(arc, 1f, 0f)[0], 1e-5f)
    }

    @Test fun openEdgesDoNotReflectOrHoldTheBallAtTheRacket() {
        val p = PongClient(1)
        p.receive(frame(listOf(.99f, 2.1f, 2f, 2f)), "m", ms)
        for (i in 1..25) p.advance(i * 10 * ms)
        assertTrue(p.ballX > 1.2f && p.ballY > 2.3f)
        val stopped = p.ballX
        for (i in 26..50) p.advance(i * 10 * ms)
        assertTrue(p.ballX - stopped < .02f) // prediction ends at 150 ms
    }

    @Test fun spinBendsTheServerCurveAndMirrorsForSeatTwo() {
        val f = frame { arc(.5f); put("spin", .8f) }
        val plain = PongClient(1); val one = PongClient(1); val two = PongClient(2)
        plain.receive(frame { arc(.5f) }, "m", ms)
        one.receive(f, "m", ms); two.receive(f, "m", ms)
        for (i in 1..16) { plain.advance(i * 10 * ms); one.advance(i * 10 * ms); two.advance(i * 10 * ms) }
        assertTrue(one.ballX > plain.ballX + .005f)
        assertEquals(1f, one.ballX + two.ballX, 1e-4f)
        assertTrue(one.spinAngle > 0 && two.spinAngle < 0)
    }

    @Test fun bounceIsReportedOnceAndAWaitingServeClearsTheTrail() {
        val p = PongClient(1)
        p.receive(frame { arc(); put("j", 1) }, "m", ms)
        assertEquals(ms, p.bounceAt)
        p.receive(frame { arc(); put("j", 1) }, "m", 2 * ms)
        assertEquals(ms, p.bounceAt)
        for (i in 1..10) p.advance(i * 10 * ms)
        assertTrue(p.trail.isNotEmpty())
        p.receive(frame { put("serve", 1); put("c", 1000) }, "m", 200 * ms)
        p.advance(200 * ms)
        assertTrue(p.waitingServe && p.myServe && p.trail.isEmpty())
        assertEquals(.2f, p.ballX, 0f); assertEquals(.16f, p.height, 0f)
        p.advance(700 * ms); assertEquals(850, p.countdownMs)
    }

    @Test fun confirmedContactImmediatelyClearsTheBatInsteadOfEasingThroughIt() {
        val p = PongClient(1)
        p.receive(frame(listOf(.5f, 1.5f, 0f, 1f)), "m", ms)
        p.paddle(.5f, 1.5f)
        p.receive(frame(listOf(.5f, 1.325f, 0f, -1f)) { put("h", 2); arc() }, "m", 20 * ms)
        assertEquals(1.325f, p.ballY, 1e-5f)
        assertTrue(kotlin.math.hypot(p.ballX - p.myX, p.ballY - p.myY) >= PongClient.HeadR + PongClient.BallR)
    }

    @Test fun aForwardStrokeAtTheEdgeMeetsTheFrontFaceAndThenFollowsThrough() {
        val p = PongClient(1)
        p.receive(frame(listOf(.98f, 1.45f, 0f, 1f)) { put("l", true) }, "m", ms)
        p.paddle(.98f, 1.8f)
        p.paddle(1.03f, 1.3f)
        assertTrue(p.myY > p.ballY + .15f)
        assertEquals(.19f, kotlin.math.hypot(p.myX - p.ballX, p.myY - p.ballY), 1e-4f)
        p.receive(frame(listOf(1.03f, 1.4f, 0f, -1f)) { put("h", 2); put("l", false) }, "m", 20 * ms)
        p.paddle(1.03f, 1.3f)
        assertEquals(1.3f, p.myY, 1e-5f)
    }

    @Test fun malformedFramesAreRejectedAtomicallyAndLegacyFieldsStillWork() {
        val p = PongClient(1)
        assertFalse(p.receive(frame(), "other", ms)); assertFalse(p.live)
        assertTrue(p.receive(frame(), "m", ms))
        val invalid = listOf(
            frame(listOf(1f)), frame(listOf(.5f, Float.NaN, 0f, 0f)),
            frame { put("spin", "broken") }, frame { put("serve", 3) },
            frame { putJsonArray("s") { add(100); add(0) } }, frame { putJsonArray("a") { add(0) } },
        )
        invalid.forEach { assertFalse(p.receive(it, "m", 2 * ms)) }
        assertEquals(.2f, p.ballX, 0f); assertEquals(4, p.myScore)
        val legacy = JsonObject(frame().filterKeys { it !in setOf("r", "z") })
        assertTrue(p.receive(legacy, "m", 3 * ms)); assertEquals(0f, p.height, 0f)
    }
}
