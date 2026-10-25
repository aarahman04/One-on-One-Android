package app.web.oneonone.data.games

import kotlin.math.*

/** Shared by rendering and the debug simulator; mirrored by backend pongPhysics.ts. */
internal object PongPhysics {
    // The existing black ball edge is 35% of its white radius (not extra hit forgiveness).
    const val BallEdge = .35
    const val ContactForgiveness = 0.0
    fun ballRadius(height: Double) = .033 * (1 + .7 * (height / (6 / 12.16)).coerceIn(0.0, 1.0))
    fun contactRadius(height: Double) = .137 + .025 / 2 + ballRadius(height) * (1 + BallEdge) + ContactForgiveness

    /** Same 50 ms raw-touch window and shot formula as backend pongPhysics.ts. */
    class StrokeWindow {
        private val times = LongArray(32)
        private val xs = DoubleArray(32)
        private val ys = DoubleArray(32)
        private var start = 0
        private var size = 0
        var side = 0.0; private set
        var forward = 0.0; private set
        var direction = 0.0; private set
        var power = 0.0; private set
        /** Raw finger velocity (widths/s; vy toward the opponent), unclamped. */
        var vx = 0.0; private set
        var vy = 0.0; private set
        fun clear() { start = 0; size = 0; side = 0.0; forward = 0.0; direction = 0.0; power = 0.0; vx = 0.0; vy = 0.0 }
        fun sample(ct: Long, x: Double, y: Double, forwardSign: Double = -1.0, legacy: Boolean = false) {
            val last = (start + size - 1) and 31
            val still = size > 0 && hypot(x - xs[last], y - ys[last]) < 1e-5
            if (size == 32) { start = (start + 1) and 31; size-- }
            val index = (start + size) and 31
            times[index] = ct; xs[index] = x; ys[index] = y; size++
            while (size > 2 && times[(start + 1) and 31] <= ct - 50) { start = (start + 1) and 31; size-- }
            while (size > 2 && times[start] < ct - 100) { start = (start + 1) and 31; size-- }
            val dx = x - xs[start]; val dy = y - ys[start]
            val dt = (ct - times[start]) / 1000.0
            if (still || hypot(dx, dy) < 1e-5) { side = 0.0; forward = 0.0; direction = 0.0; power = 0.0; vx = 0.0; vy = 0.0 }
            else if (dt > 0) {
                val sx = dx / max(dt, 1.0 / 60); val sy = dy / max(dt, 1.0 / 60)
                side = (sx * 12.16 / 60).coerceIn(-1.0, 1.0)
                forward = (sy * forwardSign * 12.16 / 60).coerceIn(-.5, 1.0)
                direction = if (legacy) side else dx / hypot(dx, dy)
                power = (sy * forwardSign / 10).coerceIn(0.0, 1.0)
                vx = sx; vy = sy * forwardSign
            }
        }
    }

    // Mirrors backend flightDuration/bounce: hard ~1 s, soft 1.35 s.
    fun flightDuration(power: Double) = 1.35 - .35 * power
    const val BouncePace = .9
    /** Pace the bounce can keep at most (widths/s), so a very fast shot still leaves a return window. */
    const val BouncePaceMax = 2.2
    /** Reach multiplier and duration keeping .9 of the shot's average ground pace; the 2 s cap shortens reach.
     *  Spin scales reach by m and pace by q (topspin skids long and quick, backspin checks up short and slow). */
    fun bounce(vx: Double, vy: Double, vz: Double, pace: Double, m: Double = 1.0, q: Double = 1.0): Pair<Double, Double> {
        val ground = hypot(vx, vy); val scale = m * 30 / 12.16 / (hypot(ground, vz).takeIf { it != 0.0 } ?: 1.0)
        val duration = ground * scale / max(min(pace, BouncePaceMax) * BouncePace * q, 1e-6)
        return if (duration > 2) scale * 2 / duration to 2.0 else scale to max(.1, duration)
    }

    // Mirrors backend lateral(): aim plus a deterministic bend, softly saturated inside the table so every stroke
    // lands in-table; a passive block returns part of the incoming sidespin inS.
    const val LateralMax = .42
    const val CurveGain = .15
    private fun smooth(v: Double, a: Double, b: Double): Double { val t = ((v - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
    private fun passive(vx: Double, vy: Double) = 1 - (hypot(vx, vy) / 6).coerceIn(0.0, 1.0)
    /** Spin captured at contact: S sidespin (+ curves toward +x) and T topspin (+) / backspin (-), both in [-1, 1]. */
    fun strokeSpin(vx: Double, vy: Double): Pair<Double, Double> =
        (vx / 8).coerceIn(-1.0, 1.0) to if (vy > 0) smooth(vy, 4.0, 10.0) else -smooth(-vy, .5, 4.0)
    fun lateral(direction: Double, forward: Double, vx: Double, vy: Double, inS: Double = 0.0): Double {
        val raw = abs(direction) * (8 / 12.16) * (.7 + .3 * forward) + CurveGain * abs(vx) * 12.16 / 60
        return LateralMax * tanh((sign(direction) * raw + .15 * inS * passive(vx, vy)) / LateralMax)
    }
    private fun ease(t: Double) = (t * t * t - 2 * t * t + t) * 1.363 + (-2 * t * t * t + 3 * t * t) + (t * t * t - t * t) * .3368
    /** Bernstein weights at the table crossing and its normalized time for a flight launched at height z,
     *  both handles at height k*6/12.16. */
    fun landingWeights(z: Double, k: Double = 1.0): DoubleArray {
        val h = 6 / 12.16
        fun height(u: Double) = (1 - u) * (1 - u) * (1 - u) * z + 3 * (1 - u) * u * k * h - u * u * u * h
        var lo = .5; var hi = 1.0
        repeat(50) { val m = (lo + hi) / 2; if (height(m) > .033) lo = m else hi = m }
        val u = (lo + hi) / 2; val a = 1 - u
        lo = 0.0; hi = 1.0
        repeat(50) { val m = (lo + hi) / 2; if (ease(m) < u) lo = m else hi = m }
        return doubleArrayOf(a * a * a, 3 * a * a * u, 3 * a * u * u, u * u * u, (lo + hi) / 2)
    }

    /** Arc points, elapsed, duration, the endpoint drift (widths/s) that lands the bend exactly, then the stroke's
     *  spin S, T. inS/inT are the incoming ball's spin at contact (after its bounce decay). */
    fun launchArc(x: Double, y: Double, z: Double, direction: Double, forward: Double, power: Double,
                  vx: Double, vy: Double, forwardSign: Double = -1.0, inS: Double = 0.0, inT: Double = 0.0): DoubleArray {
        val (s, t) = strokeSpin(vx, vy); val top = max(t, 0.0); val back = max(-t, 0.0)
        val side = lateral(direction, forward, vx, vy, inS); val tx = .5 + (.85 - .35 * abs(s)) * side
        // Backspin floats longer; topspin's speed-up comes from its lower arc only (plan -.1*T+ tuned to 0 by the fuzz).
        val duration = flightDuration(power) * (1 + .15 * back)
        // Passive blocks: incoming topspin runs long, incoming backspin drops short. Floor -.35 (plan -.5, tuned by
        // the fuzz) so a full pull-back from deep behind the end line still lands past the net.
        val f = (forward + passive(vx, vy) * (.2 * max(inT, 0.0) - .35 * max(-inT, 0.0))).coerceIn(-.35, 1.0)
        val ty = .9 + forwardSign * (5 + 7 * f) / 12.16
        val h = 6 / 12.16; val k = 1 - .3 * top + .1 * back
        val w = landingWeights(z, k); val x1 = x + (tx - x) * .2
        val drift = (.5 + side - (w[0] * x + w[1] * x1 + (w[2] + w[3]) * tx)) / (w[3] * w[4] * duration)
        return doubleArrayOf(x, y, z, x1, y + (ty - y) * .2, k * h, tx, ty, k * h, tx, ty, -h, 0.0, duration, drift, s, t)
    }
    fun launch(x: Double, y: Double, z: Double, stroke: StrokeWindow, forwardSign: Double = -1.0,
               inS: Double = 0.0, inT: Double = 0.0): FloatArray =
        launchArc(x, y, z, stroke.direction, stroke.forward, stroke.power, stroke.vx, stroke.vy, forwardSign, inS, inT)
            .map { it.toFloat() }.toFloatArray()
    data class Contact(val time: Double, val nx: Double, val ny: Double)
    fun rimPosition(contact: Contact, px: Double, py: Double, radius: Double) =
        px + contact.nx * radius to py + contact.ny * radius

    fun sweepContact(bx0: Double, by0: Double, bx1: Double, by1: Double,
                     px0: Double, py0: Double, px1: Double, py1: Double,
                     radius: Double, forward: Double): Contact? {
        val ox = bx0 - px0; val oy = by0 - py0
        val dx = bx1 - px1 - ox; val dy = by1 - py1 - oy
        val c = ox * ox + oy * oy - radius * radius
        val a = dx * dx + dy * dy; val b = ox * dx + oy * dy
        var time = 0.0
        if (c > 1e-9) {
            val discriminant = b * b - a * c
            if (a < 1e-12 || b >= 0 || discriminant < 0) return null
            time = (-b - sqrt(discriminant)) / a
            if (time !in 0.0..1.0) return null
        } else if (c >= -1e-9 && (b > 1e-12 || a > 1e-12 && b >= 0)) return null
        val nx = ox + dx * time; val ny = oy + dy * time; val length = hypot(nx, ny)
        return Contact(time, if (length > 1e-9) nx / length else 0.0, if (length > 1e-9) ny / length else forward)
    }
}
