package app.web.oneonone.data.games

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class PongPhysicsTest {
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
