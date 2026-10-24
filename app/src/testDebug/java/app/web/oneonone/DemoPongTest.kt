package app.web.oneonone

import app.web.oneonone.data.games.PongClient
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class DemoPongTest {
    @Test fun aFastForwardStrokeUsesTheImpactTimeRimAndReturnsTheBall() {
        val p = DemoPong()
        p.paddle(2, .5, -.46); p.step(3000.0)
        p.paddle(2, .5, .44)
        // The stationary ball is already on the rim when the moving head first reaches it.
        assertEquals(.5, p.x, 1e-6); assertEquals(.24, p.y, 1e-6)
        p.step(8.0)
        assertEquals(1, p.hits)
    }

    @Test fun pullingTheRedBatBackThenForwardCannotSkipARallyContact() {
        val p = DemoPong()
        p.paddle(1, .5, 2.4, true); p.step(3000.0)
        var steps = 0
        while ((p.bounces == 0 || p.y < 1.5) && steps++ < 500) p.step(8.0)
        assertEquals(1, p.hits); assertEquals(1, p.bounces)
        val before = p.x to p.y
        p.paddle(1, p.x, 1.3)
        assertEquals(before.first, p.x, 1e-6); assertEquals(before.second, p.y, 1e-6)
        p.step(8.0)
        assertEquals(2, p.hits)
        assertTrue(p.frame("m")["a"]!!.jsonArray[10].jsonPrimitive.double < .9)
        assertEquals(1.3, p.paddleY[0], 1e-6)
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
        assertEquals(.5256, p.x, .0001); assertEquals(.3019, p.y, .0001)
        assertEquals(.2265, p.z, .0001); assertEquals(1, p.hits)
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
        repeat(400) { // the slow bounce now lasts its full 2 s before the point
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
    @Test fun fastDiagonalContactFollowsThroughAndLaunchesFromTheHistoricalRim() {
        val p = DemoPong()
        p.paddle(2, .15, -.11); p.step(3000.0)
        p.paddle(2, .72, .46)
        assertEquals(.72, p.paddles[1], 1e-6); assertEquals(.46, p.paddleY[1], 1e-6)
        assertEquals(.5, p.x, 1e-6); assertEquals(.24, p.y, 1e-6)
        val start = listOf(p.x, p.y, p.z)
        p.step(8.0)
        assertEquals(1, p.hits)
        assertEquals(start, listOf(p.x, p.y, p.z))
        assertEquals(1.3434, p.frame("m")["a"]!!.jsonArray[13].jsonPrimitive.double, .0001)
    }

    @Test fun lightAndHardImpactUsesTheOutlineWithoutTrappingFollowThrough() {
        for (gap in listOf(16.0, 250.0)) {
            val p = DemoPong()
            p.paddle(2, .5, -.4); p.step(3000.0)
            p.paddle(2, .5, -.4); p.step(gap)
            p.paddle(2, .5, .44)
            assertEquals(.24, p.y, 1e-6)
            p.step(8.0)
            assertEquals(1, p.hits)
            repeat(15) {
                val before = listOf(p.x, p.y, p.z)
                p.paddle(2, .5, .5)
                assertEquals(.5, p.paddleY[1], 1e-6)
                assertEquals(before, listOf(p.x, p.y, p.z))
                p.step(8.0)
                assertTrue("gap=$gap, frame=$it", p.vy > 0)
                assertEquals(1, p.hits)
            }
        }
    }

    @Test fun lightSwipesAreSlowerThanHardSwipesEvenWhenReleasedAtContact() {
        fun duration(delta: Double, released: Boolean): Double {
            val p = DemoPong()
            p.paddle(2, .5, -.4); p.step(3000.0)
            val edge = p.y - PongClient.contactRadius(p.z.toFloat())
            p.paddle(2, .5, edge - .02 - delta); p.step(32.0)
            p.paddle(2, .5, edge - .02); p.step(32.0)
            p.paddle(2, .5, edge + delta, released)
            p.step(8.0)
            assertEquals(1, p.hits)
            return p.frame("m")["a"]!!.jsonArray[13].jsonPrimitive.double
        }
        for (released in listOf(false, true)) {
            val light = duration(.03, released)
            val hard = duration(.5, released)
            assertTrue("light=$light, hard=$hard, released=$released", light > hard + .25)
        }
        assertEquals(duration(.5, false), duration(.5, true), .0001)
    }

    @Test fun hardFollowThroughMovesTheBatContinuouslyAndKeepsOneShot() {
        val p = DemoPong()
        p.paddle(2, .5, -.4); p.step(3000.0)
        p.paddle(2, .5, .1); p.step(8.0)
        assertEquals(1, p.hits)
        val target = p.frame("m")["a"]!!.jsonArray.slice(9..11)
        for (y in listOf(.2, .3, .4, .5)) {
            val before = p.frame("m")["b"]
            p.paddle(2, .5, y)
            assertEquals(y, p.paddleY[1], 1e-6)
            assertEquals(before, p.frame("m")["b"])
            p.step(8.0)
            assertEquals(1, p.hits)
            assertEquals(target, p.frame("m")["a"]!!.jsonArray.slice(9..11))
        }
    }

    @Test fun slowAndHardCornerFollowThroughCannotRecaptureAnOutgoingBall() {
        for (side in listOf(-1.0, 1.0)) for (speed in listOf(.005, .15)) {
            val moving = DemoPong(); val control = DemoPong()
            for (p in listOf(moving, control)) {
                p.paddle(2, .5 + side * .12, -.4); p.step(3000.0)
                p.paddle(2, .5 + side * .12, .12, true); p.step(8.0)
                assertEquals(1, p.hits)
            }
            repeat(12) { i ->
                val touch = .5 + side * .12 to .12 + speed * i
                moving.paddle(2, control.x + side * .08, control.y - .06, touch = touch)
                control.paddle(2, if (side < 0) -.26 else 1.26, -.7, touch = touch)
                assertEquals(control.frame("m")["b"], moving.frame("m")["b"])
                for (p in listOf(moving, control)) p.step(8.0)
                assertEquals(control.frame("m")["b"], moving.frame("m")["b"])
                assertEquals(control.frame("m")["a"], moving.frame("m")["a"])
                assertEquals(1, moving.hits)
                assertTrue(moving.vy > 0)
            }
        }
    }

    @Test fun queuedBatUpdatesStillLaunchOnlyTheFirstContact() {
        val p = DemoPong()
        p.paddle(2, .62, -.4); p.step(3000.0)
        p.paddle(2, .62, .12)
        val contact = listOf(p.x, p.y, p.z)
        p.paddle(2, .54, .25); p.paddle(2, .45, .5, true)
        assertEquals(contact, listOf(p.x, p.y, p.z))
        p.step(8.0)
        assertEquals(1, p.hits)
        assertEquals(contact, listOf(p.x, p.y, p.z))
    }

    @Test fun steeringStopsAtTheBounceAndTheBounceIsNeverFaster() {
        val p = DemoPong()
        p.paddle(1, 5.0, 2.5, true) // receiver out of reach
        p.paddle(2, .5, -.4); p.step(3000.0)
        p.paddle(2, .5, -.4); p.step(16.0)
        p.paddle(2, .5, .3); p.step(8.0)
        assertEquals(1, p.hits)
        var x = .5; var incoming = 0.0
        while (p.bounces == 0) { incoming = hypot(p.vx, p.vy); x += .01; p.paddle(2, x, .3); p.step(16.0) }
        val end = p.frame("m")["a"]!!.jsonArray[9].jsonPrimitive.double
        repeat(10) { x += .01; p.paddle(2, x, .3); p.step(16.0); assertTrue(hypot(p.vx, p.vy) <= incoming * 1.05) }
        assertEquals(0.0, p.frame("m")["drift"]!!.jsonPrimitive.double, 0.0)
        assertEquals(end, p.frame("m")["a"]!!.jsonArray[9].jsonPrimitive.double, 1e-9)
    }
}
