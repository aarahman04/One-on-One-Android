package app.web.oneonone

import app.web.oneonone.data.games.PongClient
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DemoPongTest {
    @Test fun aFastForwardStrokeStopsAtThePaintedRimAndReturnsTheBall() {
        val p = DemoPong()
        p.paddle(2, .5, -.46); p.step(3000.0)
        p.paddle(2, .5, .44)
        val radius = PongClient.contactRadius(p.z.toFloat()).toDouble()
        assertEquals(radius, kotlin.math.hypot(p.x - p.paddles[1], p.y - p.paddleY[1]), 1e-6)
        p.step(8.0)
        assertEquals(1, p.hits)
    }

    @Test fun pullingTheRedBatBackThenForwardCannotSkipARallyContact() {
        val p = DemoPong()
        p.paddle(1, .5, 2.4, true); p.step(3000.0)
        var steps = 0
        while ((p.bounces == 0 || p.y < 1.5) && steps++ < 500) p.step(8.0)
        assertEquals(1, p.hits); assertEquals(1, p.bounces)
        p.paddle(1, p.x, 1.3)
        assertEquals(PongClient.contactRadius(p.z.toFloat()).toDouble(), p.paddleY[0] - p.y, 1e-6)
        p.step(8.0)
        assertEquals(2, p.hits)
        assertTrue(p.frame("m")["a"]!!.jsonArray[10].jsonPrimitive.double < .9)
        assertTrue(p.paddleY[0] - p.y >= PongClient.contactRadius(p.z.toFloat()) - .001)
    }

    @Test fun neutralBlocksAtBothBaselinesProduceARally() {
        val p = DemoPong()
        p.paddle(1, .5, 1.88, true)
        p.step(3000.0)
        repeat(1500) { p.step(8.0) }
        assertEquals(listOf(0, 0), p.score.toList())
        assertTrue(p.hits >= 7)
    }

    @Test fun demoUsesTheBackendStrokeFixtureAndProducesAReadableFrame() {
        val p = DemoPong()
        p.paddle(2, .2, .21); p.step(3000.0)
        p.paddle(2, .2, .21); p.step(16.0)
        p.paddle(2, .5, .24); p.step(8.0); p.step(64.0)
        // Same input sequence in backend PongSim, rounded by its wire encoder.
        assertEquals(.5431, p.x, .0001); assertEquals(.344, p.y, .0001)
        assertEquals(.2564, p.z, .0001); assertEquals(1, p.hits)
        val client = PongClient(1)
        assertTrue(client.receive(p.frame("m"), "m", 1_000_000))
        assertEquals(1f, client.spin, .0001f)
        assertEquals(1.2336f, p.frame("m")["drift"]!!.jsonPrimitive.float, .0001f)
        p.paddle(2, .5, .24, true)
        assertEquals(0f, p.frame("m")["spin"]!!.jsonPrimitive.float, 0f)
    }

    @Test fun demoWaitsForServeAndScoresAnOpenSideMiss() {
        val p = DemoPong()
        p.paddle(2, .2, .21); p.step(3000.0); p.step(2000.0)
        assertEquals(0, p.hits)
        p.paddle(2, .2, .21); p.step(900.0); p.paddle(2, .5, .24); p.step(8.0) // slow wide placement: no hook, runs out
        var outside = false
        repeat(250) {
            p.step(8.0)
            if (p.x > 1.02) { outside = true; assertTrue(p.vx > 0) }
        }
        assertTrue(outside); assertTrue(p.bounces > 0)
        assertEquals(listOf(0, 1), p.score.toList())
    }

    @Test fun easingTheBatDoesNotProlongTheFingersStroke() {
        val p = DemoPong()
        p.paddle(2, .2, .21); p.step(3000.0)
        p.paddle(2, .2, .21, touch = .2 to .21); p.step(16.0)
        p.paddle(2, .35, .225, touch = .5 to .24); p.step(8.0)
        assertEquals(1, p.hits)
        assertEquals(1f, p.frame("m")["spin"]!!.jsonPrimitive.float, 0f)
        p.paddle(2, .425, .2325, touch = .5 to .24)
        assertEquals(0f, p.frame("m")["spin"]!!.jsonPrimitive.float, 0f)
    }
}
