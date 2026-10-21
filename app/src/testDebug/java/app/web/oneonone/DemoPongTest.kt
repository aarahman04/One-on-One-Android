package app.web.oneonone

import app.web.oneonone.data.games.PongClient
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DemoPongTest {
    @Test fun neutralBlocksAtBothBaselinesProduceARally() {
        val p = DemoPong()
        p.paddle(1, .5, 1.88, true)
        p.step(3000.0)
        repeat(1500) { p.step(8.0) }
        assertEquals(listOf(0, 0), p.score.toList())
        assertTrue(p.hits >= 7)
    }

    @Test fun demoUsesTheBackendStrokeFixtureAndProducesAReadableFrame() {
        val p = DemoPong { .5 } // the backend fixture's roll: no razor spin
        p.paddle(2, .2, .21); p.step(3000.0)
        p.paddle(2, .2, .21); p.step(16.0)
        p.paddle(2, .5, .24); p.step(8.0); p.step(64.0)
        // Same input sequence in backend PongSim, rounded by its wire encoder.
        assertEquals(.5951, p.x, .0001); assertEquals(.5077, p.y, .0001)
        assertEquals(.2472, p.z, .0001); assertEquals(1, p.hits)
        val client = PongClient(1)
        assertTrue(client.receive(p.frame("m"), "m", 1_000_000))
        assertTrue(client.spin > .36f && client.spin < .4f)
        p.paddle(2, .5, .24, true)
        assertTrue(p.frame("m")["spin"]!!.jsonPrimitive.float > .36f)
    }

    @Test fun demoWaitsForServeAndScoresAnOpenSideMiss() {
        val p = DemoPong()
        p.paddle(2, .2, .21); p.step(3000.0); p.step(2000.0)
        assertEquals(0, p.hits)
        p.paddle(2, .2, .21); p.step(16.0); p.paddle(2, .5, .24); p.step(8.0)
        var outside = false
        repeat(250) {
            p.step(8.0)
            if (p.x > 1.02) { outside = true; assertTrue(p.vx > 0) }
        }
        assertTrue(outside); assertTrue(p.bounces > 0)
        assertEquals(listOf(0, 1), p.score.toList())
    }
}
