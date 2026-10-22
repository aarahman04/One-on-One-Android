package app.web.oneonone.data.games

import kotlin.math.*

/** Shared by rendering and the debug simulator; mirrored by backend pongPhysics.ts. */
internal object PongPhysics {
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
        } else if (c >= -1e-9 && b >= 0) return null
        val nx = ox + dx * time; val ny = oy + dy * time; val length = hypot(nx, ny)
        return Contact(time, if (length > 1e-9) nx / length else 0.0, if (length > 1e-9) ny / length else forward)
    }
}
