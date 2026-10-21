package app.web.oneonone

import kotlinx.serialization.json.*
import kotlin.math.*

/** Local demo mirror of backend PongSim. It never supplies production game state. */
internal class DemoPong(private val random: () -> Double = { kotlin.random.Random.nextDouble() }) {
    val courtH = 1.8
    val paddles = doubleArrayOf(.5, .5)
    val paddleY = doubleArrayOf(courtH + .08, -.08)
    val score = intArrayOf(0, 0)
    var x = .5; private set
    var y = .24; private set
    var z = 2 / 12.16; private set
    var vx = 0.0; private set
    var vy = 0.0; private set
    var vz = 0.0; private set
    var countdown = 3000.0; private set
    var hits = 0; private set
    var bounces = 0; private set
    var outcome: Int? = null; private set
    var scored = false
    private var clock = 0.0
    private val moved = doubleArrayOf(-1.0, -1.0)
    private val modern = booleanArrayOf(false, false)
    private val strokes = Array(2) { DoubleArray(3) }
    private var server = 2
    private data class Flight(val points: Array<DoubleArray>, var elapsed: Double, val duration: Double)
    private var flight: Flight? = null
    private var lastHit = 0
    private var landed = false
    private var spin = 0.0
    private var razor = false
    private val samples = Array(2) { ArrayDeque<DoubleArray>() }

    fun serving(seat: Int) = flight == null && server == seat
    private fun serveY() = if (server == 1) courtH - .24 else .24

    fun paddle(seat: Int, px: Double, py: Double? = null, released: Boolean = false) {
        val i = seat - 1
        val nextX = px.coerceIn(-.26, 1.26)
        val nextY = if (py == null) paddleY[i] else if (seat == 1) py.coerceIn(1.3, 2.5) else courtH - (courtH - py).coerceIn(1.3, 2.5)
        if (py != null) modern[i] = true
        // Mirrors the backend's ~50 ms stroke window.
        val window = samples[i]
        if (released) window.clear() else {
            window.addLast(doubleArrayOf(clock, nextX, nextY))
            while (window.size > 2 && window[1][0] <= clock - 50) window.removeFirst()
            while (window.size > 2 && window[0][0] < clock - 100) window.removeFirst()
        }
        val still = hypot(nextX - paddles[i], nextY - paddleY[i]) < 1e-5
        val ref = window.firstOrNull()
        val dx = if (ref == null) 0.0 else nextX - ref[1]; val dy = if (ref == null) 0.0 else nextY - ref[2]
        val dt = if (ref == null) 0.0 else (clock - ref[0]) / 1000
        if (released || still || hypot(dx, dy) < 1e-5) strokes[i].fill(0.0)
        else if (dt > 0) {
            val sx = dx / max(dt, 1.0 / 60); val sy = dy / max(dt, 1.0 / 60)
            strokes[i][0] = (sx * StrokeGain).coerceIn(-1.0, 1.0)
            strokes[i][1] = power(sy * (if (seat == 1) -1 else 1))
            strokes[i][2] = if (py == null) (sx * StrokeGain).coerceIn(-1.0, 1.0) else dx / hypot(dx, dy)
        }
        paddles[i] = nextX; paddleY[i] = nextY; moved[i] = if (released) -1.0 else clock
    }

    fun step(ms: Double) {
        if (outcome != null || ms <= 0) return
        clock += ms
        for (i in 0..1) if (clock - moved[i] > 120) strokes[i].fill(0.0)
        if (countdown > 0) {
            countdown = max(0.0, countdown - ms)
            if (countdown == 0.0 && !modern[server - 1]) strike(server)
            return
        }
        if (flight == null) { if (touches(server)) strike(server); return }
        var left = ms / 1000
        while (left > 1e-8 && flight != null && outcome == null) { advance(min(left, .008)); left -= .008 }
    }

    private fun touches(seat: Int) = z >= -.033 && (y - paddleY[seat - 1]) * (if (seat == 1) -1 else 1) >= -.001 &&
        hypot(x - paddles[seat - 1], y - paddleY[seat - 1]) <= .137 + .033 + .025
    private fun curve(target: DoubleArray, duration: Double) {
        flight = Flight(arrayOf(doubleArrayOf(x, y, z), doubleArrayOf(x + (target[0] - x) * .2, y + (target[1] - y) * .2, ArcH),
            doubleArrayOf(target[0], target[1], ArcH), target), 0.0, duration)
    }
    private fun strike(seat: Int) {
        val stroke = strokes[seat - 1]
        val px = paddles[seat - 1]; val py = paddleY[seat - 1]
        val block = if (lastHit == 3 - seat) spin * .3 else 0.0
        val target = doubleArrayOf(.5 + stroke[2] * .5 * (.85 + .15 * stroke[1]) + block,
            (if (seat == 1) .49 else courtH - .49) + (if (seat == 1) -1 else 1) * (-.03 + stroke[1] * .706 + max(0.0, stroke[1] - .85) * 4), -ArcH)
        val dx = target[0] - px; val dy = target[1] - py; val length = hypot(dx, dy)
        x = px + dx / length * .205; y = py + dy / length * .205
        curve(target, 1 - .22 * stroke[1])
        val brush = if (abs(stroke[2]) > .45) stroke[0] else 0.0
        lastHit = seat; landed = false
        spin = if (abs(brush) <= .08) 0.0 else sign(brush) * (.2 + .2 * min(1.0, (abs(brush) - .08) / .6))
        razor = abs(brush) >= .85 && random() < .15
        if (razor) spin = sign(brush) * .6
        hits++
    }
    private fun advance(dt: Double) {
        val current = flight ?: return
        val oldX = x; val oldY = y; val oldZ = z
        val decay = exp(-dt / 1.8)
        current.points[3][0] += spin * .4 * 1.8 * (1 - decay)
        spin *= decay
        current.elapsed += dt
        val t = (current.elapsed / current.duration).coerceIn(0.0, 1.0)
        val u = (t * t * t - 2 * t * t + t) * 1.363 + (-2 * t * t * t + 3 * t * t) + (t * t * t - t * t) * .3368
        val a = 1 - u
        val point = DoubleArray(3) { i -> a.pow(3) * current.points[0][i] + 3 * a * a * u * current.points[1][i] + 3 * a * u * u * current.points[2][i] + u.pow(3) * current.points[3][i] }
        x = point[0]; y = point[1]; z = point[2]
        vx = (x - oldX) / dt; vy = (y - oldY) / dt; vz = (z - oldZ) / dt
        if (oldZ > .033 && z <= .033 && vz < 0) {
            val k = (oldZ - .033) / (oldZ - z)
            val bx = oldX + (x - oldX) * k; val by = oldY + (y - oldY) * k
            if (bx in 0.0..1.0 && by in 0.0..courtH) {
                if (landed) { point(lastHit); return }
                x = bx; y = by; z = .033; landed = true; bounces++
                // Mirrors the backend: spin turns the heading by a capped angle (razor: sharper) and checks the pace.
                val turn = if (razor) sign(spin) * .6 else (spin * .8).coerceIn(-.35, .35)
                val heading = atan2(vx, vy) + turn
                val across = hypot(vx, vy)
                val kickX = across * sin(heading); val kickY = across * cos(heading)
                val length = sqrt(kickX * kickX + kickY * kickY + vz * vz).takeIf { it != 0.0 } ?: 1.0
                val reach = 30 / 12.16 * (1 - .4 * min(.6, abs(spin)))
                curve(doubleArrayOf(x + kickX / length * reach, y + kickY / length * reach, -ArcH), current.duration)
            }
        }
        if (landed && lastHit != 0 && touches(3 - lastHit)) strike(3 - lastHit)
        else if (z <= -ArcH || flight === current && current.elapsed >= current.duration) point(if (landed) lastHit else 3 - lastHit)
    }
    private fun point(seat: Int) {
        score[seat - 1]++; scored = true
        if (score.sum() % 2 == 0) server = 3 - server
        flight = null; lastHit = 0; landed = false; spin = 0.0; razor = false
        x = .5; y = serveY(); z = 2 / 12.16; vx = 0.0; vy = 0.0; vz = 0.0
        strokes.forEach { it.fill(0.0) }; moved.fill(-1.0); samples.forEach { it.clear() }
        if (score[seat - 1] >= 7) outcome = seat else countdown = 1000.0
    }
    fun frame(id: String) = buildJsonObject {
        fun array(name: String, values: Iterable<Number>) { putJsonArray(name) { values.forEach { add(round(it.toDouble() * 10000) / 10000) } } }
        put("id", id); put("t", System.currentTimeMillis())
        array("b", listOf(x, y, vx, vy)); array("p", paddles.asIterable())
        putJsonArray("s") { score.forEach { add(it) } }
        array("r", paddleY.asIterable()); array("z", listOf(z, vz))
        val current = flight
        if (current == null) put("a", JsonNull) else array("a", current.points.flatMap { it.asIterable() } + listOf(current.elapsed, current.duration))
        put("c", ceil(countdown).toInt()); put("h", hits); put("w", 0)
        put("spin", round(spin * 10000) / 10000)
        put("serve", if (current != null || outcome != null) 0 else server); put("j", bounces); put("l", landed)
    }
    companion object {
        private const val ArcH = 6 / 12.16
        private const val StrokeGain = 12.16 / 60
        /** Forward speed (table widths/s) -> power; saturates softly, pulling back is a soft touch. */
        private fun power(speed: Double) = if (speed >= 0) 1 - exp(-speed / 5) else (speed * StrokeGain).coerceIn(-.5, 0.0)
    }
}
