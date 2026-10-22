package app.web.oneonone.data.games

import kotlin.math.*

/** Table widths and seconds. Pure mirror of backend pongPhysics.ts; see pingpong-rules.md. */
internal object PongPhysics {
    const val CourtH = 1.8
    const val BatR = .137
    const val BallR = .033
    const val Step = 1.0 / 240
    const val MaxSteps = 8
    const val BatSpeedMax = 1 / .06 // A table width in 60 ms.
    const val VMin = 2.0
    const val VMax = BatSpeedMax * 1.5
    const val VStrikeMin = .15
    const val EBlock = .6
    const val KSpeed = 1.25
    const val KSpin = 1.0
    const val EscapeGain = 1.05
    const val SpinTau = .6
    const val CurveRate = .1 // Total turn <= .06 radians: < 12% width per crossing.
    const val SepMargin = .01
    const val Radius = BatR + BallR
    data class Ball(val x: Double, val y: Double, val vx: Double = 0.0, val vy: Double = 0.0,
                    val spin: Double = 0.0, val locks: List<Int> = listOf(0, 0))
    data class Bat(val x: Double, val y: Double, val nextX: Double, val nextY: Double,
                   val vx: Double, val vy: Double, val forward: Double, val active: Boolean = true)
    data class Contact(val time: Double, val nx: Double, val ny: Double)
    enum class Kind { Strike, Block, Push }
    data class Impact(val bat: Int, val kind: Kind, val time: Double, val nx: Double, val ny: Double)
    data class Result(val ball: Ball, val impacts: List<Impact>)
    fun rimPosition(c: Contact, px: Double, py: Double, radius: Double) = px + c.nx * radius to py + c.ny * radius

    fun sweepContact(bx0: Double, by0: Double, bx1: Double, by1: Double,
                     px0: Double, py0: Double, px1: Double, py1: Double, radius: Double, forward: Double): Contact? {
        val ox = bx0 - px0; val oy = by0 - py0
        val dx = bx1 - px1 - ox; val dy = by1 - py1 - oy
        val c = ox * ox + oy * oy - radius * radius
        val a = dx * dx + dy * dy; val b = ox * dx + oy * dy
        var time = 0.0
        if (c > 1e-10) {
            val discriminant = b * b - a * c
            if (a < 1e-14 || b >= 0 || discriminant < 0) return null
            time = (-b - sqrt(discriminant)) / a
            if (time !in 0.0..1.0) return null
        } else if (c >= -1e-10 && b >= -1e-12) return null
        val nx = ox + dx * time; val ny = oy + dy * time; val length = hypot(nx, ny)
        return Contact(time, if (length > 1e-10) nx / length else 0.0, if (length > 1e-10) ny / length else forward)
    }

    fun freeFlight(ball: Ball, dt: Double): Ball {
        val decay = exp(-dt / SpinTau)
        // Shallow shots travel farther; scale turn to bound drift at the far end of the table.
        val scale = ball.vy * ball.vy / max(1e-12, ball.vx * ball.vx + ball.vy * ball.vy)
        val turn = -CurveRate * scale * ball.spin * SpinTau * (1 - decay)
        val c = cos(turn / 2); val s = sin(turn / 2)
        val vx = ball.vx * c - ball.vy * s; val vy = ball.vx * s + ball.vy * c
        return ball.copy(x = ball.x + vx * dt, y = ball.y + vy * dt,
            vx = vx * c - vy * s, vy = vx * s + vy * c, spin = ball.spin * decay)
    }

    /** Slide a pushed ball along the table edge, using the closest circle/edge intersection. */
    private fun boundedPush(ball: Ball, p: Bat): Ball {
        val x = ball.x.coerceIn(BallR, 1 - BallR); val y = ball.y.coerceIn(BallR, CourtH - BallR)
        if (hypot(x - p.nextX, y - p.nextY) >= Radius - 1e-10) return ball.copy(x = x, y = y)
        val candidates = mutableListOf<Pair<Double, Double>>()
        for (edge in listOf(BallR, 1 - BallR)) {
            val square = Radius * Radius - (edge - p.nextX).pow(2)
            if (square >= 0) for (sign in listOf(-1, 1)) candidates += edge to p.nextY + sign * sqrt(square)
        }
        for (edge in listOf(BallR, CourtH - BallR)) {
            val square = Radius * Radius - (edge - p.nextY).pow(2)
            if (square >= 0) for (sign in listOf(-1, 1)) candidates += p.nextX + sign * sqrt(square) to edge
        }
        // Rotate tie breaking with the player so both clients choose the same side.
        val point = candidates.filter { (cx, cy) -> cx >= BallR - 1e-10 && cx <= 1 - BallR + 1e-10 && cy >= BallR - 1e-10 && cy <= CourtH - BallR + 1e-10 }
            .minWithOrNull { a, b ->
                val d = (a.first - x).pow(2) + (a.second - y).pow(2) - (b.first - x).pow(2) - (b.second - y).pow(2)
                if (abs(d) > 1e-10) d.compareTo(0.0)
                else (p.forward * (if (a.first != b.first) a.first - b.first else a.second - b.second)).compareTo(0.0)
            }
        return if (point == null) ball.copy(x = x, y = y) else ball.copy(x = point.first, y = point.second)
    }

    /** Bats are read-only. Every impulse continues the remaining time after first contact. */
    fun stepBall(input: Ball, bats: List<Bat>, dt: Double = Step): Result {
        var ball = input
        val locks = input.locks.toMutableList(); val impacts = mutableListOf<Impact>(); val ignored = mutableSetOf<Int>()
        var elapsed = 0.0
        bats.forEachIndexed { i, p -> if (hypot(ball.x - p.x, ball.y - p.y) > Radius + SepMargin) locks[i] = 0 }
        var iteration = 0
        while (iteration++ < 8 && elapsed < dt - 1e-12) {
            val left = dt - elapsed; val end = freeFlight(ball, left)
            var first: Contact? = null; var index = -1; var startX = 0.0; var startY = 0.0
            bats.forEachIndexed { i, p ->
                if (locks[i] != 1 && i !in ignored) {
                    val k = elapsed / dt; val px = p.x + (p.nextX - p.x) * k; val py = p.y + (p.nextY - p.y) * k
                    val contact = sweepContact(ball.x, ball.y, end.x, end.y, px, py, p.nextX, p.nextY, Radius, p.forward)
                    if (contact != null && (first == null || contact.time < first.time)) {
                        first = contact; index = i; startX = px; startY = py
                    }
                }
            }
            val c = first
            if (c == null) { ball = end; elapsed = dt; break }
            val p = bats[index]; val penetrating = hypot(ball.x - startX, ball.y - startY) < Radius - 1e-8
            val advance = left * c.time
            ball = freeFlight(ball, advance); elapsed += advance
            val k = elapsed / dt; val px = p.x + (p.nextX - p.x) * k; val py = p.y + (p.nextY - p.y) * k
            val speed = hypot(p.vx, p.vy); val bn = p.vx * c.nx + p.vy * c.ny; val vn = ball.vx * c.nx + ball.vy * c.ny
            val closing = bn - vn; val front = c.ny * p.forward > -.2; val driven = abs(bn) >= abs(vn)
            var kind = Kind.Push
            if (!penetrating && locks[index] == 0 && p.active && closing > 1e-9) {
                if (front && p.vy * p.forward >= .5 * speed && driven && speed >= VStrikeMin) kind = Kind.Strike
                else if (front && !driven) kind = Kind.Block
            }
            ball = ball.copy(x = px + c.nx * Radius, y = py + c.ny * Radius)
            when (kind) {
                Kind.Strike -> {
                    var dx = .5 * c.nx + .5 * p.vx / speed; var dy = .5 * c.ny + .5 * p.vy / speed
                    val length = hypot(dx, dy); dx /= length; dy /= length
                    if (dy * p.forward < .15) { dy = .15 * p.forward; dx = sign(dx) * sqrt(1 - .15.pow(2)) }
                    // The 50 ms window understates hard starts: escape the actual sweep with this one impulse.
                    val sweepNormal = ((p.nextX - p.x) * c.nx + (p.nextY - p.y) * c.ny) / dt
                    val escape = EscapeGain * max(0.0, sweepNormal) / (dx * c.nx + dy * c.ny)
                    val launch = max(VMin + KSpeed * closing, escape).coerceIn(VMin, VMax)
                    ball = ball.copy(vx = dx * launch, vy = dy * launch,
                        spin = (KSpin * (c.nx * p.vy - c.ny * p.vx) / BatSpeedMax).coerceIn(-1.0, 1.0))
                    locks[index] = 1
                }
                Kind.Block -> {
                    val vx = ball.vx - (1 + EBlock) * (vn - bn) * c.nx; val vy = ball.vy - (1 + EBlock) * (vn - bn) * c.ny
                    val cap = min(1.0, VMax / max(1e-12, hypot(vx, vy)))
                    ball = ball.copy(vx = vx * cap, vy = vy * cap, spin = 0.0); locks[index] = 1
                }
                Kind.Push -> locks[index] = 2 // Stay a push until actual separation.
            }
            impacts += Impact(index, kind, elapsed, c.nx, c.ny); ignored += index
        }
        if (elapsed < dt) ball = freeFlight(ball, dt - elapsed)
        impacts.filter { it.kind == Kind.Push }.forEach { impact ->
            val p = bats[impact.bat]
            val displacement = max(0.0, Radius - (ball.x - p.nextX) * impact.nx - (ball.y - p.nextY) * impact.ny)
            ball = ball.copy(x = ball.x + impact.nx * displacement, y = ball.y + impact.ny * displacement)
        }
        // Open table: no ball walls or net barriers. Pushes may leave the table without wedging.
        bats.forEachIndexed { i, p ->
            val dx = ball.x - p.nextX; val dy = ball.y - p.nextY; val length = hypot(dx, dy)
            if (locks[i] != 1 && length < Radius) {
                ball = ball.copy(x = p.nextX + (if (length > 1e-10) dx / length else 0.0) * Radius,
                    y = p.nextY + (if (length > 1e-10) dy / length else p.forward) * Radius)
                locks[i] = 2
            }
        }
        if (hypot(input.vx, input.vy) < 1e-9 || input.x in BallR..1 - BallR && input.y in BallR..CourtH - BallR)
            impacts.filter { it.kind == Kind.Push }.forEach { ball = boundedPush(ball, bats[it.bat]) }
        return Result(ball.copy(locks = locks), impacts)
    }

    data class Sample(val time: Double, val x: Double, val y: Double)
    class BatMotion(var x: Double, var y: Double, time: Double = 0.0) {
        var previousX = x; private set
        var previousY = y; private set
        var vx = 0.0; private set
        var vy = 0.0; private set
        private val path = ArrayDeque<Sample>()
        private val history = ArrayDeque<Sample>()
        private var anchor = Sample(time, x, y)
        private var stopped = true
        fun sample(time: Double, x: Double, y: Double) {
            if (!time.isFinite() || !x.isFinite() || !y.isFinite()) return
            val last = path.lastOrNull() ?: anchor
            if (time < last.time) return
            if (time == last.time && path.isNotEmpty()) path.removeLast()
            path.addLast(Sample(time, x, y)); stopped = false
        }
        fun stop(time: Double) { path.clear(); history.clear(); anchor = Sample(time, x, y); vx = 0.0; vy = 0.0; stopped = true }
        fun advance(time: Double, dt: Double = Step) {
            previousX = x; previousY = y
            if (stopped) return
            if (history.isEmpty()) history.addLast(Sample(time - dt, x, y))
            var budget = BatSpeedMax * dt
            while (path.isNotEmpty()) {
                val next = path.first(); val k = ((time - anchor.time) / max(1e-12, next.time - anchor.time)).coerceIn(0.0, 1.0)
                val tx = anchor.x + (next.x - anchor.x) * k; val ty = anchor.y + (next.y - anchor.y) * k
                val dx = tx - x; val dy = ty - y; val distance = hypot(dx, dy); val move = min(distance, budget)
                if (distance > 1e-12) { x += dx / distance * move; y += dy / distance * move }
                budget -= move
                if (k < 1 || move < distance - 1e-10) break
                anchor = path.removeFirst()
                if (budget < 1e-12) break
            }
            history.addLast(Sample(time, x, y))
            val cutoff = time - .05
            while (history.size > 2 && history[1].time <= cutoff) history.removeFirst()
            val a = history[0]; val b = history.getOrNull(1) ?: a
            val k = ((cutoff - a.time) / max(1e-12, b.time - a.time)).coerceIn(0.0, 1.0); val duration = time - max(cutoff, a.time)
            vx = if (duration > 1e-10) (x - a.x - (b.x - a.x) * k) / duration else 0.0
            vy = if (duration > 1e-10) (y - a.y - (b.y - a.y) * k) / duration else 0.0
            if (hypot(x - previousX, y - previousY) < 1e-12) { vx = 0.0; vy = 0.0 }
        }
        fun bat(forward: Double, active: Boolean = true) = Bat(previousX, previousY, x, y, vx, vy, forward, active)
    }
}
