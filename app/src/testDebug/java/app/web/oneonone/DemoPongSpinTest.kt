package app.web.oneonone

import app.web.oneonone.data.games.PongClient
import app.web.oneonone.data.games.PongPhysics
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DemoPongSpinTest {
    private fun stroke(vx: Double, vy: Double): DoubleArray {
        val w = PongPhysics.StrokeWindow()
        w.sample(1000, 0.0, 0.0, 1.0); w.sample(1050, vx * .05, vy * .05, 1.0)
        return doubleArrayOf(w.side, w.forward, w.direction, w.power, w.vx, w.vy)
    }

    // Backend pongFairness reachability: the receiver's bat leaves the neutral baseline after .2 s at 6 widths/s
    // and must reach a legal centre within the contact radius of the ball.
    private fun reachable(p: DemoPong, bx: Double, by: Double, r: Double, budget: Double): Boolean {
        if (budget < 0) return false
        for (k in 0..48) {
            val angle = k * PI / 8; val d = if (k == 0) 0.0 else r * (1 + ((k - 1) shr 4)) / 3
            val qx = bx + cos(angle) * d; val qy = by + sin(angle) * d
            if (qx < PongClient.RacketMin || qx > PongClient.RacketMax || qy < PongClient.RacketNear || qy > PongClient.RacketFar) continue
            if (hypot(qx - .5, qy - (p.courtH + .08)) <= budget) return true
        }
        return false
    }

    private class Shot(val landed: Boolean, val landX: Double, val landY: Double, val window: Double, val duration: Double)

    private fun shot(vx: Double, vy: Double, hx: Double, hy: Double, hz: Double, inS: Double, inT: Double): Shot {
        val p = DemoPong()
        p.paddles[0] = 100.0 // the receiver never contacts; reachability is measured analytically
        p.strikeFrom(2, hx, hy, hz, stroke(vx, vy), inS, inT)
        val duration = p.frame("m")["a"]!!.jsonArray[13].jsonPrimitive.double
        var t = 0.0; var window = 0.0; var landX = 0.0; var landY = 0.0
        while (p.score.sum() == 0 && t < 5000 && window < 250) {
            val before = p.bounces
            p.step(8.0); t += 8
            for (v in listOf(p.x, p.y, p.z, p.vx, p.vy, p.vz)) assertTrue("finite", v.isFinite())
            if (p.score.sum() != 0) break
            if (before == 0 && p.bounces == 1) { landX = p.x; landY = p.y }
            if (p.bounces > 0 && p.z >= -.033 && reachable(p, p.x, p.y, PongPhysics.contactRadius(p.z), 6 * (t / 1000 - .2))) window += 8
        }
        return Shot(p.bounces > 0, landX, landY, window, duration)
    }

    @Test fun everySwipeAndIncomingSpinLandsOnTheOpponentsHalfAndCanBeReturned() {
        var shots = 0
        val failures = mutableListOf<String>()
        for (angle in 0 until 360 step 15) for (speed in listOf(0.0, .5, 2.0, 4.0, 6.0, 8.0, 10.0, 14.0, 20.0))
            for (hx in listOf(-.2, .1, .5, .9, 1.2)) for (hy in listOf(-.7, -.5, -.3, -.05, .2, .5)) for (hz in listOf(-.03, .05, .2, .45))
                for (inS in listOf(-1.0, 0.0, 1.0)) for (inT in listOf(-1.0, 0.0, 1.0)) {
                    // Same grid as backend spinFuzz: the plan's two depths in full, the rest of the legal depth
                    // (-.7 deepest .. .5 frontmost) as a corner subsample for runtime.
                    if (hy !in listOf(-.3, -.05) && !(speed in listOf(0.0, 4.0, 10.0, 20.0) && (hz == -.03 || hz == .45) &&
                            (inS == 0.0) == (inT == 0.0))) continue
                    val rad = angle * PI / 180
                    val r = shot(speed * sin(rad), speed * cos(rad), hx, hy, hz, inS, inT)
                    val name = "angle=$angle speed=$speed x=$hx y=$hy z=$hz in=($inS,$inT) land=(${r.landX},${r.landY}) window=${r.window}"
                    if (r.duration !in .1..2.0) failures += "duration: $name"
                    else if (!(r.landed && r.landX in 0.0..1.0 && r.landY in 1.8 / 2..1.8)) failures += "lands: $name"
                    else if (r.window < 250) failures += "returnable: $name"
                    shots++
                }
        assertEquals(24 * 9 * 5 * 2 * 4 * 9 + 24 * 4 * 5 * 4 * 2 * 5, shots)
        assertTrue("${failures.size} failures: " + failures.take(5).joinToString(" | "), failures.isEmpty())
    }

    @Test fun theDemoOpponentVariesItsReturnsWithRealStrokes() {
        val p = DemoPong(target = 99); val bot = DemoPongBot(2)
        val spins = mutableListOf<List<Double>>()
        var hits = 0
        repeat(60_000) {
            if (p.outcome != null) return@repeat
            // The human blocks with a still bat on the baseline under the ball.
            p.paddle(1, if (p.vy > 0) p.x else .5, p.courtH + .08, released = true)
            bot.tick(p, 16.0); p.step(16.0)
            if (p.hits != hits) {
                hits = p.hits
                if (p.vy > 0) spins += p.frame("m")["sp"]!!.jsonArray.map { it.jsonPrimitive.double }
            }
        }
        assertTrue(bot.returns >= 14)
        assertTrue("topspin drive", spins.any { it[1] > .3 }); assertTrue("backspin push", spins.any { it[1] < -.3 })
        assertTrue("sidespin left", spins.any { it[0] < -.3 }); assertTrue("sidespin right", spins.any { it[0] > .3 })
    }
}
