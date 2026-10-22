package app.web.oneonone.data.games

import app.web.oneonone.data.games.PongPhysics.Ball
import app.web.oneonone.data.games.PongPhysics.Bat
import app.web.oneonone.data.games.PongPhysics.BatMotion
import app.web.oneonone.data.games.PongPhysics.Kind
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class PongPhysicsTest {
    private fun resting() = Ball(.5, .5)
    private fun bat(x: Double, y: Double, vx: Double, vy: Double, dt: Double = PongPhysics.Step, f: Double = -1.0) =
        Bat(x, y, x + vx * dt, y + vy * dt, vx, vy, f)
    private fun near(a: Double, b: Double, tolerance: Double = 1e-8) = assertEquals(a, b, tolerance)

    @Test fun sweptSoftHardMaximumAndGiantJumpStrikeOnceAtToiOnBothSides() {
        assertTrue(PongPhysics.VMax >= PongPhysics.BatSpeedMax)
        for (f in listOf(-1.0, 1.0)) for (speed in listOf(.15, 1.0, 8.0, PongPhysics.BatSpeedMax))
            for (incoming in listOf(0.0, .1)) for (dt in listOf(PongPhysics.Step, .2)) {
                val p = bat(.5, .5 - f * (PongPhysics.Radius + (speed + incoming) * dt / 2), 0.0, f * speed, dt, f)
                val result = PongPhysics.stepBall(resting().copy(vy = -f * incoming), listOf(p), dt)
                assertEquals(1, result.impacts.count { it.kind == Kind.Strike })
                val b = result.ball
                assertTrue(hypot(b.x - p.nextX, b.y - p.nextY) >= PongPhysics.Radius - 1e-8)
                near(b.vx, 0.0); near(b.spin, 0.0); assertTrue(b.vy * f > speed)
            }
    }
    @Test fun diagonalCenterStrokesAreStraightAndGlancingSlicesHaveFixedSpinAndBoundedFadingCurve() {
        for (side in listOf(-1.0, 1.0)) for (f in listOf(-1.0, 1.0)) {
            val dx = .6 * side; val dy = .8 * f; val distance = PongPhysics.Radius + 5 * PongPhysics.Step / 2
            val p = bat(.5 - dx * distance, .5 - dy * distance, dx * 5, dy * 5, f = f)
            val hit = PongPhysics.stepBall(resting(), listOf(p)).ball
            near(hit.spin, 0.0); near(hit.vx / hit.vy, dx / dy)
            var b = hit
            repeat(240) { b = PongPhysics.freeFlight(b, PongPhysics.Step) }
            near((b.x - hit.x) / (b.y - hit.y), dx / dy)
        }
        for (side in listOf(-1.0, 1.0)) {
            val d = PongPhysics.Radius + .002
            val hit = PongPhysics.stepBall(resting(), listOf(bat(.5 - side * .6 * d, .5 + .8 * d, 0.0, -8.0))).ball
            assertTrue(hit.spin * -side > 0)
            var curve = hit; var straight = hit.copy(spin = 0.0)
            repeat(ceil(1.8 / hypot(hit.vx, hit.vy) / PongPhysics.Step).toInt()) {
                curve = PongPhysics.freeFlight(curve, PongPhysics.Step); straight = PongPhysics.freeFlight(straight, PongPhysics.Step)
            }
            assertTrue(abs(curve.x - straight.x) in 1e-5.. .12)
            near(hypot(curve.vx, curve.vy), hypot(hit.vx, hit.vy))
            assertTrue(abs(PongPhysics.freeFlight(curve, 3.0).spin) < abs(hit.spin) * .01)
        }
        val hit = PongPhysics.stepBall(resting(), listOf(bat(.5, .5 + PongPhysics.Radius + .002, 4.0, -8.0))).ball
        assertTrue(hit.spin > 0); assertTrue(PongPhysics.freeFlight(hit, .1).vx < hit.vx)
    }
    @Test fun sidewaysBackswingAndSlowPushNeverLaunchOrOverlapAndRestAfterMotionStops() {
        for ((vx, vy, nx, ny) in listOf(listOf(8.0, 0.0, 1.0, 0.0), listOf(0.0, 8.0, 0.0, 1.0), listOf(0.0, -.1, 0.0, -1.0))) {
            var b = resting(); var p = bat(.5 - nx * (PongPhysics.Radius + .001), .5 - ny * (PongPhysics.Radius + .001), vx, vy)
            repeat(24) {
                val result = PongPhysics.stepBall(b, listOf(p)); b = result.ball
                assertTrue(result.impacts.all { it.kind == Kind.Push }); near(b.vx, 0.0); near(b.vy, 0.0)
                assertTrue(hypot(b.x - p.nextX, b.y - p.nextY) >= PongPhysics.Radius - 1e-8)
                p = bat(p.nextX, p.nextY, vx, vy)
            }
            val end = PongPhysics.stepBall(b, listOf(bat(p.x, p.y, 0.0, 0.0))).ball
            near(end.x, b.x); near(end.y, b.y)
        }
        val corrupt = PongPhysics.stepBall(resting(), listOf(bat(.5, .5, 0.0, 0.0))).ball
        near(hypot(corrupt.x - .5, corrupt.y - .5), PongPhysics.Radius)
        assertTrue(corrupt.x.isFinite() && corrupt.y.isFinite())
    }
    @Test fun stillBatBlocksWithoutBoostAndLockedBatCannotSteerAfterAStrike() {
        val result = PongPhysics.stepBall(resting().copy(y = .5 - PongPhysics.Radius - .002, vy = 2.0), listOf(bat(.5, .5, 0.0, 0.0)))
        assertEquals(Kind.Block, result.impacts.single().kind); near(result.ball.vy, -2 * PongPhysics.EBlock)
        var moving = PongPhysics.stepBall(resting(), listOf(bat(.5, .5 + PongPhysics.Radius + .002, 0.0, -8.0))).ball
        var control = moving
        repeat(100) { i ->
            moving = PongPhysics.stepBall(moving, listOf(bat(moving.x, moving.y, sin(i.toDouble()) * 12, cos(i.toDouble()) * 12))).ball
            control = PongPhysics.freeFlight(control, PongPhysics.Step)
            near(moving.x, control.x); near(moving.y, control.y); near(moving.vx, control.vx); near(moving.vy, control.vy)
        }
    }
    @Test fun liftAndCancelClearThePathAndVelocityImmediately() {
        val p = BatMotion(.5, 2.0); p.sample(.1, .5, 1.3); p.advance(.05, .05); p.stop(.05)
        val x = p.x; val y = p.y
        repeat(100) { p.advance(.05 + (it + 1) * PongPhysics.Step) }
        near(p.x, x); near(p.y, y); near(p.vx, 0.0); near(p.vy, 0.0)
    }
    @Test fun shallowShotCurvatureIsBoundedAtTheFarEndAndAGiantPushCannotTunnel() {
        for (fraction in listOf(.15, .4, .8, 1.0)) for (spin in listOf(-1.0, 1.0)) for (speed in listOf(.2, 2.0, PongPhysics.VMax)) {
            var b = resting().copy(vx = speed * sqrt(1 - fraction * fraction), vy = speed * fraction, spin = spin)
            val start = b; var steps = 0
            while (b.y - start.y < 1.8 && steps++ < 30000) b = PongPhysics.freeFlight(b, PongPhysics.Step)
            val straightX = start.x + start.vx / start.vy * (b.y - start.y)
            assertTrue(abs(b.x - straightX) < .12)
        }
        val p = bat(.5 - PongPhysics.Radius - .001, .5, 8.0, 0.0, .2)
        val result = PongPhysics.stepBall(resting(), listOf(p), .2)
        assertTrue(result.impacts.all { it.kind == Kind.Push }); near(result.ball.vx, 0.0)
        assertTrue(result.ball.x in .033.. .967)
        assertTrue(hypot(result.ball.x - p.nextX, result.ball.y - p.nextY) >= PongPhysics.Radius - 1e-8)
    }
    @Test fun timestampPathsAt30And60And120HzHaveIdenticalStrikesAndNoBatStutter() {
        val outcomes = listOf(30, 60, 120).map { hz ->
            val p = BatMotion(.5, 2.0)
            for (i in 0..(.6 * hz).toInt()) p.sample(i.toDouble() / hz, .5, 2 - i.toDouble() / hz)
            var b = resting().copy(y = 1.5); var hits = 0
            repeat(144) { i ->
                p.advance((i + 1) * PongPhysics.Step); near((p.y - p.previousY) / PongPhysics.Step, -1.0)
                val result = PongPhysics.stepBall(b, listOf(p.bat(-1.0))); b = result.ball
                hits += result.impacts.count { it.kind == Kind.Strike }
            }
            assertEquals(1, hits); b
        }
        outcomes.drop(1).forEach { near(it.x, outcomes[0].x); near(it.y, outcomes[0].y); near(it.vy, outcomes[0].vy) }
    }
    @Test fun pushesSlideAlongEveryEdgeWithoutWedgingOrLaunching() {
        for ((x, y, vx, vy) in listOf(listOf(.94, .9, 8.0, 0.0), listOf(.06, .9, -8.0, 0.0), listOf(.5, 1.74, 0.0, 8.0), listOf(.5, .06, 0.0, -8.0))) {
            val length = hypot(vx, vy); val nx = vx / length; val ny = vy / length
            var b = resting().copy(x = x, y = y)
            var p = bat(x - nx * (PongPhysics.Radius + .001), y - ny * (PongPhysics.Radius + .001), vx, vy).copy(active = false)
            repeat(24) {
                b = PongPhysics.stepBall(b, listOf(p)).ball
                assertTrue(b.x >= .033 - 1e-9 && b.x <= .967 + 1e-9 && b.y >= .033 - 1e-9 && b.y <= 1.767 + 1e-9)
                assertTrue(hypot(b.x - p.nextX, b.y - p.nextY) >= PongPhysics.Radius - 1e-8)
                near(b.vx, 0.0); near(b.vy, 0.0)
                p = bat(p.nextX, p.nextY, vx, vy).copy(active = false)
            }
        }
    }
    @Test fun hardStartsClearTheBatOnEveryStepDespiteTheVelocityAveragingWindow() {
        for (speed in listOf(.2, 1.0, 8.0, PongPhysics.BatSpeedMax)) for (offset in listOf(-.08, 0.0, .08)) {
            val p = BatMotion(.5 + offset, 1.88); val duration = .58 / speed
            p.sample(0.0, .5 + offset, 1.88); p.sample(.1, .5 + offset, 1.88); p.sample(.1 + duration, .5 + offset, 1.3)
            var b = resting().copy(y = 1.56); var hits = 0; var i = 1
            while (i * PongPhysics.Step <= .2 + duration) {
                p.advance(i++ * PongPhysics.Step)
                val result = PongPhysics.stepBall(b, listOf(p.bat(-1.0))); b = result.ball
                hits += result.impacts.count { it.kind == Kind.Strike }
                assertTrue("speed=$speed, offset=$offset", hypot(b.x - p.x, b.y - p.y) >= PongPhysics.Radius - 1e-8)
            }
            assertEquals(1, hits)
        }
    }
}
