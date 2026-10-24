package app.web.oneonone

import app.web.oneonone.data.games.PongPhysics
import kotlinx.serialization.json.*
import kotlin.math.*

/** Local demo mirror of backend PongSim. It never supplies production game state. */
internal class DemoPong(private val target: Int = 7) {
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
    private val strokes = Array(2) { DoubleArray(4) }
    private val contactStrokes = arrayOfNulls<DoubleArray>(2)
    private var server = 2
    private data class Flight(val points: Array<DoubleArray>, var elapsed: Double, val duration: Double)
    private var flight: Flight? = null
    private var lastHit = 0
    private var landed = false
    private val spin get() = if (lastHit == 0) 0.0 else strokes[lastHit - 1][0]
    private val samples = Array(2) { PongPhysics.StrokeWindow() }

    @Synchronized fun serving(seat: Int) = flight == null && server == seat
    private fun serveY() = if (server == 1) courtH - .24 else .24

    @Synchronized fun paddle(seat: Int, px: Double, py: Double? = null, released: Boolean = false, touch: Pair<Double, Double>? = null) {
        if (outcome != null || !px.isFinite() || py != null && !py.isFinite()) return
        val i = seat - 1
        val oldX = paddles[i]; val oldY = paddleY[i]
        val nextX = px.coerceIn(-.26, 1.26)
        val nextY = if (py == null) paddleY[i] else if (seat == 1) py.coerceIn(1.3, 2.5) else courtH - (courtH - py).coerceIn(1.3, 2.5)
        val inputX = nextX; val inputY = nextY
        val forward = if (seat == 1) -1 else 1
        val radius = contactRadius()
        val ready = if (flight != null) landed && lastHit == 3 - seat else server == seat && countdown == 0.0
        var contacted = false
        if (ready && contactStrokes[i] == null && z >= -.033) {
            val contact = PongPhysics.sweepContact(x, y, x, y, oldX, oldY, nextX, nextY, radius, forward.toDouble())
            if (contact != null) {
                val rim = PongPhysics.rimPosition(contact, oldX + (nextX - oldX) * contact.time,
                    oldY + (nextY - oldY) * contact.time, radius)
                x = rim.first; y = rim.second
                contacted = ready
            }
        }
        if (py != null) modern[i] = true
        val sampleX = touch?.first?.coerceIn(-.26, 1.26) ?: inputX
        val sampleY = touch?.second?.let { if (seat == 1) it.coerceIn(1.3, 2.5) else courtH - (courtH - it).coerceIn(1.3, 2.5) } ?: inputY
        val window = samples[i]
        window.sample(clock.toLong(), sampleX, sampleY, forward.toDouble(), py == null)
        strokes[i][0] = window.side; strokes[i][1] = window.forward
        strokes[i][2] = window.direction; strokes[i][3] = window.power
        // Release stops live spin without erasing impact strength.
        if (contacted && contactStrokes[i] == null) contactStrokes[i] = strokes[i].copyOf()
        if (released) { window.clear(); strokes[i].fill(0.0) }
        paddles[i] = nextX; paddleY[i] = nextY; moved[i] = if (released) -1.0 else clock
    }

    @Synchronized fun step(ms: Double) {
        if (outcome != null || !ms.isFinite() || ms <= 0) return
        clock += ms
        for (i in 0..1) if (clock - moved[i] > 120) strokes[i].fill(0.0)
        if (countdown > 0) {
            countdown = max(0.0, countdown - ms)
            if (countdown == 0.0 && !modern[server - 1]) strike(server)
            return
        }
        if (flight == null) { if (contactStrokes[server - 1] != null || touches(server)) strike(server); return }
        if (landed && lastHit != 0 && (contactStrokes[2 - lastHit] != null || touches(3 - lastHit))) strike(3 - lastHit)
        var left = ms / 1000
        while (left > 1e-8 && flight != null && outcome == null) { advance(min(left, .008)); left -= .008 }
    }

    private fun touches(seat: Int) = z >= -.033 &&
        hypot(x - paddles[seat - 1], y - paddleY[seat - 1]) <= contactRadius() + 1e-6
    private fun contactRadius() = PongPhysics.contactRadius(z)
    private fun curve(target: DoubleArray, duration: Double) {
        flight = Flight(arrayOf(doubleArrayOf(x, y, z), doubleArrayOf(x + (target[0] - x) * .2, y + (target[1] - y) * .2, ArcH),
            doubleArrayOf(target[0], target[1], ArcH), target), 0.0, duration)
    }
    private fun strike(seat: Int) {
        val stroke = contactStrokes[seat - 1] ?: strokes[seat - 1]
        contactStrokes[seat - 1] = null
        val target = doubleArrayOf(.5 + stroke[2] * (8 / 12.16) * (.7 + .3 * stroke[1]),
            courtH / 2 + (if (seat == 1) -1 else 1) * (5 + 7 * stroke[1]) / 12.16, -ArcH)
        curve(target, 1.15 - .45 * stroke[3])
        lastHit = seat; landed = false
        hits++
    }
    private fun advance(dt: Double) {
        val current = flight ?: return
        val oldX = x; val oldY = y; val oldZ = z
        current.points[3][0] += spin * SpinDrift * dt
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
                val length = sqrt(vx * vx + vy * vy + vz * vz).takeIf { it != 0.0 } ?: 1.0
                val reach = 30 / 12.16
                curve(doubleArrayOf(x + vx / length * reach, y + vy / length * reach, .033 - ArcH), current.duration)
            }
        }
        val seat = 3 - lastHit
        val contact = if (landed && lastHit != 0 && z >= -.033)
            PongPhysics.sweepContact(oldX, oldY, x, y, paddles[seat - 1], paddleY[seat - 1],
                paddles[seat - 1], paddleY[seat - 1], contactRadius(), (if (seat == 1) -1 else 1).toDouble()) else null
        if (contact != null) {
            val rim = PongPhysics.rimPosition(contact, paddles[seat - 1], paddleY[seat - 1], contactRadius())
            x = rim.first; y = rim.second
            strike(seat)
        }
        else if (z <= -ArcH || flight === current && current.elapsed >= current.duration) point(if (landed) lastHit else 3 - lastHit)
    }
    private fun point(seat: Int) {
        score[seat - 1]++; scored = true
        if (score.sum() % 2 == 0) server = 3 - server
        flight = null; lastHit = 0; landed = false
        x = .5; y = serveY(); z = 2 / 12.16; vx = 0.0; vy = 0.0; vz = 0.0
        strokes.forEach { it.fill(0.0) }; contactStrokes.fill(null); moved.fill(-1.0); samples.forEach { it.clear() }
        if (score[seat - 1] >= target) outcome = seat else countdown = 1000.0
    }
    @Synchronized fun frame(id: String) = buildJsonObject {
        fun array(name: String, values: Iterable<Number>) { putJsonArray(name) { values.forEach { add(round(it.toDouble() * 10000) / 10000) } } }
        put("id", id); put("t", System.currentTimeMillis())
        array("b", listOf(x, y, vx, vy)); array("p", paddles.asIterable())
        putJsonArray("s") { score.forEach { add(it) } }
        array("r", paddleY.asIterable()); array("z", listOf(z, vz))
        val current = flight
        if (current == null) put("a", JsonNull) else array("a", current.points.flatMap { it.asIterable() } + listOf(current.elapsed, current.duration))
        put("c", ceil(countdown).toInt()); put("h", hits); put("w", 0)
        put("spin", round(spin * 10000) / 10000)
        put("drift", round(spin * SpinDrift * 10000) / 10000)
        put("serve", if (current != null || outcome != null) 0 else server); put("j", bounces); put("l", landed)
    }
    companion object {
        private const val ArcH = 6 / 12.16
        private const val SpinDrift = .3 / 12.16 / .02
    }
}
