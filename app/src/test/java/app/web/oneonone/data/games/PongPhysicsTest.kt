package app.web.oneonone.data.games

import kotlin.math.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PongPhysicsTest {
    @Test fun sharedGoldenContactsAndClientTimeStrokes() {
        val golden = Json.parseToJsonElement(javaClass.getResource("/pong-contact-golden.json")!!.readText()).jsonObject
        for (item in golden["contacts"]!!.jsonArray) {
            val c = item.jsonObject
            fun v(key: String, index: Int) = c[key]!!.jsonArray[index].jsonPrimitive.double
            val contact = PongPhysics.sweepContact(v("ball0", 0), v("ball0", 1), v("ball1", 0), v("ball1", 1),
                v("bat0", 0), v("bat0", 1), v("bat1", 0), v("bat1", 1), c["radius"]!!.jsonPrimitive.double, c["forward"]!!.jsonPrimitive.double)
            if (c["expected"] is JsonNull) assertNull(c["name"].toString(), contact)
            else {
                assertNotNull(c["name"].toString(), contact)
                listOf(contact!!.time, contact.nx, contact.ny).forEachIndexed { i, value -> assertEquals(v("expected", i), value, 1e-8) }
            }
        }
        for (item in golden["strokes"]!!.jsonArray) {
            val c = item.jsonObject; val window = PongPhysics.StrokeWindow()
            for (sample in c["samples"]!!.jsonArray) {
                val a = sample.jsonArray
                window.sample(a[0].jsonPrimitive.long, a[1].jsonPrimitive.double, a[2].jsonPrimitive.double)
            }
            listOf(window.side, window.forward, window.direction, window.power).forEachIndexed { i, value ->
                assertEquals(c["expected"]!!.jsonArray[i].jsonPrimitive.double, value, 1e-8)
            }
            val flight = PongPhysics.launchArc(.5, 1.6, .033, window.direction, window.forward, window.power, window.vx, window.vy)
            for (i in 0..2) assertEquals(c["target"]!!.jsonArray[i].jsonPrimitive.double, flight[9 + i], 1e-8)
            assertEquals(c["duration"]!!.jsonPrimitive.double, flight[13], 1e-8)
            assertEquals(c["drift"]!!.jsonPrimitive.double, flight[14], 1e-8)
            window.clear(); assertEquals(0.0, window.side, 0.0)
        }
    }
    @Test fun launchAndBounceMatchTheBackendSpinGoldenTo1e9() {
        val golden = Json.parseToJsonElement(javaClass.getResource("/pong-spin-golden.json")!!.readText()).jsonObject
        fun JsonObject.d(key: String) = this[key]!!.jsonPrimitive.double
        val launches = golden["launch"]!!.jsonArray; val bounces = golden["bounce"]!!.jsonArray
        assertTrue(launches.size >= 60 && bounces.size >= 24)
        for ((i, item) in launches.withIndex()) {
            val c = item.jsonObject; val s = c["stroke"]!!.jsonObject
            val a = PongPhysics.launchArc(c.d("x"), c.d("y"), c.d("z"), s.d("direction"), s.d("forward"), s.d("power"),
                s.d("vx"), s.d("vy"), c.d("forwardSign"), c.d("inS"), c.d("inT"))
            val expected = c["expected"]!!.jsonArray
            assertEquals(expected.size, a.size)
            expected.forEachIndexed { k, e -> assertEquals("launch $i [$k]", e.jsonPrimitive.double, a[k], 1e-9) }
        }
        for ((i, item) in bounces.withIndex()) {
            val c = item.jsonObject
            val (scale, duration) = PongPhysics.bounce(c.d("vx"), c.d("vy"), c.d("vz"), c.d("pace"), c.d("m"), c.d("q"))
            assertEquals("bounce $i scale", c["expected"]!!.jsonArray[0].jsonPrimitive.double, scale, 1e-9)
            assertEquals("bounce $i duration", c["expected"]!!.jsonArray[1].jsonPrimitive.double, duration, 1e-9)
        }
    }

    /** Table-crossing x of a launch, from its own landing weights and endpoint drift. */
    private fun landingX(a: DoubleArray, z: Double): Double {
        val w = PongPhysics.landingWeights(z, a[5] / (6 / 12.16))
        return w[0] * a[0] + w[1] * a[3] + w[2] * a[6] + w[3] * (a[9] + a[14] * w[4] * a[13])
    }

    @Test fun theHardestSidewaysSwipeLandsInsideTheTable() {
        for (sign in listOf(-1.0, 1.0)) for (vx in listOf(8.0, 20.0, 60.0)) for (inS in listOf(-1.0, 1.0)) {
            val side = PongPhysics.lateral(sign, 0.0, sign * vx, 0.0, inS)
            assertTrue(abs(side) < PongPhysics.LateralMax)
            val a = PongPhysics.launchArc(.5, 1.6, .2, sign, 0.0, 0.0, sign * vx, 0.0, -1.0, inS, 0.0)
            assertEquals(.5 + side, landingX(a, .2), 1e-6)
            assertTrue(landingX(a, .2) in .08..0.92)
            assertEquals(sign, a[15], 0.0) // full sidespin
        }
    }

    @Test fun strokeSpinComesFromTheFingerAndShapesFlightHeightAndTime() {
        assertEquals(0.0 to 0.0, PongPhysics.strokeSpin(0.0, 0.0).let { it.first to abs(it.second) })
        assertEquals(1.0, PongPhysics.strokeSpin(0.0, 12.0).second, 0.0) // fast forward flick: topspin
        assertEquals(-1.0, PongPhysics.strokeSpin(0.0, -5.0).second, 0.0) // pulling back: backspin
        assertEquals(-.5, PongPhysics.strokeSpin(-4.0, 6.0).first, 1e-12)
        val flat = PongPhysics.launchArc(.5, 1.6, .2, 0.0, 1.0, 1.0, 0.0, 3.9)
        val top = PongPhysics.launchArc(.5, 1.6, .2, 0.0, 1.0, 1.0, 0.0, 12.0)
        val back = PongPhysics.launchArc(.5, 1.6, .2, 0.0, 0.0, 0.0, 0.0, -5.0)
        assertEquals(flat[5] * .7, top[5], 1e-12) // topspin dips: handles at .7 height
        assertEquals(flat[13], top[13], 1e-12)
        assertEquals(PongPhysics.flightDuration(0.0) * 1.15, back[13], 1e-12) // backspin floats
        assertEquals(6 / 12.16 * 1.1, back[5], 1e-12)
    }

    @Test fun topspinBouncesLongAndQuickBackspinShortAndSlow() {
        val (neutral, neutralTime) = PongPhysics.bounce(.2, 1.5, -1.0, 2.0)
        val (top, topTime) = PongPhysics.bounce(.2, 1.5, -1.0, 2.0, 1.35, 1.1)
        val (back, backTime) = PongPhysics.bounce(.2, 1.5, -1.0, 2.0, .55, .65)
        assertEquals(neutral * 1.35, top, 1e-12); assertEquals(neutral * .55, back, 1e-12)
        assertEquals(neutralTime * 1.35 / 1.1, topTime, 1e-12); assertEquals(neutralTime * .55 / .65, backTime, 1e-12)
        val (capped, cappedTime) = PongPhysics.bounce(0.0, 3.0, -1.0, .1, 1.35, 1.1)
        assertEquals(2.0, cappedTime, 0.0); assertTrue(capped < 30 / 12.16 / hypot(3.0, 1.0) * 1.35)
    }

    @Test fun aPassiveBlockReturnsIncomingSpinButASwingIgnoresIt() {
        // Still bat: incoming sidespin deflects it, topspin runs long, backspin drops short.
        assertEquals(.42 * tanh(.15 / .42), PongPhysics.lateral(0.0, 0.0, 0.0, 0.0, 1.0), 1e-12)
        assertEquals(-PongPhysics.lateral(0.0, 0.0, 0.0, 0.0, 1.0), PongPhysics.lateral(0.0, 0.0, 0.0, 0.0, -1.0), 1e-12)
        fun depth(inT: Double, vy: Double = 0.0) = PongPhysics.launchArc(.5, 1.6, .2, 0.0, 0.0, 0.0, 0.0, vy, -1.0, 0.0, inT)[10]
        assertTrue(depth(1.0) < depth(0.0) && depth(-1.0) > depth(0.0)) // seat one hits toward y = 0
        // A full swing (6 widths/s) is not passive.
        assertEquals(PongPhysics.lateral(1.0, .5, 3.0, 6.0 * sqrt(.75), 0.0), PongPhysics.lateral(1.0, .5, 3.0, 6.0 * sqrt(.75), 1.0), 1e-12)
        assertEquals(depth(0.0, 6.0), depth(-1.0, 6.0), 1e-12)
    }

    @Test fun sweptMatrixCoversMovingBallsSpeedAnglesAndRefreshRates() {
        for (speed in listOf(.15, 2.0, 80.0)) for (ballSpeed in listOf(0.0, .7))
            for (angle in listOf(-60, -30, 0, 30, 60)) for (hz in listOf(30, 60, 120)) for (forward in listOf(-1.0, 1.0)) {
                val radians = angle * PI / 180; val dx = sin(radians); val dy = cos(radians) * forward
                val dt = 1.0 / hz; val radius = PongClient.contactRadius(.16f).toDouble()
                val distance = radius + .4 * (speed + ballSpeed) * dt
                val px0 = -dx * distance; val py0 = -dy * distance
                val px1 = px0 + dx * speed * dt; val py1 = py0 + dy * speed * dt
                val contact = PongPhysics.sweepContact(0.0, 0.0, -dx * ballSpeed * dt, -dy * ballSpeed * dt,
                    px0, py0, px1, py1, radius, forward)
                assertNotNull("$speed, $ballSpeed, $angle, $hz, $forward", contact)
                val end = PongPhysics.rimPosition(contact!!, px1, py1, radius)
                assertTrue(hypot(end.first - px1, end.second - py1) >= radius - 1e-9)

            }
    }

    @Test fun penetrationAndZeroSpeedHaveFiniteOutwardFallbacks() {
        for (forward in listOf(-1.0, 1.0)) {
            val contact = PongPhysics.sweepContact(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, .2, forward)!!
            assertEquals(0.0 to forward * .2, PongPhysics.rimPosition(contact, 0.0, 0.0, .2))
            assertNull(PongPhysics.sweepContact(1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, .2, forward))
            assertNull(PongPhysics.sweepContact(0.0, .2, 0.0, .3, 0.0, 0.0, 0.0, 0.0, .2, forward))
        }
    }
}
