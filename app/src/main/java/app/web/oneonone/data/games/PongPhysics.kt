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
        fun clear() { start = 0; size = 0; side = 0.0; forward = 0.0; direction = 0.0; power = 0.0 }
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
            if (still || hypot(dx, dy) < 1e-5) { side = 0.0; forward = 0.0; direction = 0.0; power = 0.0 }
            else if (dt > 0) {
                val sx = dx / max(dt, 1.0 / 60); val sy = dy / max(dt, 1.0 / 60)
                side = (sx * 12.16 / 60).coerceIn(-1.0, 1.0)
                forward = (sy * forwardSign * 12.16 / 60).coerceIn(-.5, 1.0)
                direction = if (legacy) side else dx / hypot(dx, dy)
                power = (sy * forwardSign / 10).coerceIn(0.0, 1.0)
            }
        }
    }

    fun launch(x: Double, y: Double, z: Double, direction: Double, forward: Double, power: Double,
               forwardSign: Double = -1.0): FloatArray {
        val tx = .5 + direction * (8 / 12.16) * (.7 + .3 * forward)
        val ty = .9 + forwardSign * (5 + 7 * forward) / 12.16
        val h = 6 / 12.16
        return floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat(), (x + (tx - x) * .2).toFloat(),
            (y + (ty - y) * .2).toFloat(), h.toFloat(), tx.toFloat(), ty.toFloat(), h.toFloat(),
            tx.toFloat(), ty.toFloat(), -h.toFloat(), 0f, (1.15 - .45 * power).toFloat())
    }
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
