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
            val flight = PongPhysics.launch(.5, 1.6, .033, window.direction, window.forward, window.power)
            for (i in 0..2) assertEquals(c["target"]!!.jsonArray[i].jsonPrimitive.float, flight[9 + i], 1e-6f)
            assertEquals(c["duration"]!!.jsonPrimitive.float, flight[13], 1e-6f)
            window.clear(); assertEquals(0.0, window.side, 0.0)
        }
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
