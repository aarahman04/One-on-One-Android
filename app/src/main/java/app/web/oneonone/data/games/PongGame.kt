package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import kotlin.math.*

data class PongTrail(val x: Float, val y: Float, val height: Float, val atNanos: Long)

/** Displays the server's Ping Pong flight. Touch owns only the local racket, never the ball or score. */
class PongClient(val seat: Int) {
    init { require(seat in 1..2) }
    var ballX = .5f; private set
    var ballY = CourtH / 2; private set
    var height = 0f; private set
    var myX = .5f; private set
    var myY = CourtH + .08f; private set
    var theirX = .5f; private set
    var theirY = -.08f; private set
    var myScore = 0; private set
    var theirScore = 0; private set
    var countdownMs = 0; private set
    var waitingServe = false; private set
    var myServe = false; private set
    var hits = 0; private set
    var live = false; private set
    var round = 0; private set
    var spin = 0f; private set
    var bounceX = 0f; private set
    var bounceY = 0f; private set
    var bounceAt = 0L; private set
    private val trailPoints = ArrayDeque<PongTrail>()
    val trail: List<PongTrail> get() = trailPoints
    private var frame = FloatArray(4)
    private var arc: FloatArray? = null
    private var frameHeight = 0f
    private var frameDrift: Float? = null
    private var frameTheirX = .5f
    private var frameTheirY = -.08f
    private var frameCountdown = 0
    private var frameAt = 0L
    private var serverOffset: Long? = null
    private var lastTimestamp = 0L
    private var lastAdvance = 0L
    private var lastTrail = 0L
    private var bounces = 0
    private var returnReady = false
    private var localContact: Pair<Float, Float>? = null
    private var localContactAt = 0L
    private var localContactUsed = false

    private fun viewX(x: Float) = if (seat == 1) x else 1 - x
    private fun viewY(y: Float) = if (seat == 1) y else CourtH - y

    fun receive(data: JsonObject, matchId: String, nowNanos: Long): Boolean {
        return runCatching {
            require((data["id"] as JsonPrimitive).content == matchId)
            fun numbers(key: String, size: Int): FloatArray {
                val values = (data[key] as JsonArray).map { (it as JsonPrimitive).float }.toFloatArray()
                require(values.size == size && values.all { it.isFinite() })
                return values
            }
            val b = numbers("b", 4)
            val p = numbers("p", 2)
            val s = (data["s"] as JsonArray).map { (it as JsonPrimitive).int }
            require(s.size == 2 && s.all { it in 0..99 })
            val r = if (data["r"] != null) numbers("r", 2) else floatArrayOf(CourtH, 0f)
            val z = if (data["z"] != null) numbers("z", 2) else floatArrayOf(0f, 0f)
            val curve = if (data["a"] != null && data["a"] !is JsonNull) numbers("a", 14) else null
            if (curve != null) require(curve[12] >= 0 && curve[13] in .1f..2f)
            val incomingSpin = data["spin"]?.let { (it as JsonPrimitive).float } ?: 0f
            require(incomingSpin.isFinite())
            val incomingDrift = data["drift"]?.let { (it as JsonPrimitive).float }
            require(incomingDrift == null || incomingDrift.isFinite())
            val timestamp = data["t"]?.let { (it as JsonPrimitive).long }
            require(timestamp == null || timestamp in lastTimestamp..Long.MAX_VALUE / 1_000_000)
            val countdown = (data["c"] as JsonPrimitive).int
            val hitCount = (data["h"] as JsonPrimitive).int
            val serving = data["serve"]?.let { (it as JsonPrimitive).int } ?: 0
            val bounceCount = data["j"]?.let { (it as JsonPrimitive).int } ?: 0
            val landed = data["l"]?.let { (it as JsonPrimitive).boolean } ?: false
            require(countdown >= 0 && hitCount >= 0 && serving in 0..2 && bounceCount >= 0)

            val newHit = hitCount != hits
            val reset = !live || s[seat - 1] != myScore || s[2 - seat] != theirScore ||
                serving != 0 && (!waitingServe || countdown > frameCountdown + 100)
            frame = floatArrayOf(viewX(b[0]), viewY(b[1]), if (seat == 1) b[2] else -b[2], if (seat == 1) b[3] else -b[3])
            arc = curve?.also { for (i in 0..3) { it[i * 3] = viewX(it[i * 3]); it[i * 3 + 1] = viewY(it[i * 3 + 1]) } }
            frameHeight = z[0]
            val nextTheirX = viewX(p[2 - seat]); val nextTheirY = viewY(r[2 - seat])
            frameTheirX = nextTheirX; frameTheirY = nextTheirY
            frameCountdown = countdown
            if (reset) {
                myX = viewX(p[seat - 1]); myY = viewY(r[seat - 1]); round++
                trailPoints.clear(); lastTrail = 0L
            }
            if (reset || newHit) { localContact = null; localContactUsed = false }
            myScore = s[seat - 1]; theirScore = s[2 - seat]
            spin = if (seat == 1) incomingSpin else -incomingSpin
            frameDrift = incomingDrift?.let { if (seat == 1) it else -it }
            hits = hitCount
            waitingServe = serving != 0; myServe = serving == seat
            returnReady = landed
            if (bounceCount > bounces) { bounceX = frame[0]; bounceY = frame[1]; bounceAt = nowNanos }
            bounces = bounceCount
            frameAt = if (timestamp == null) nowNanos else {
                // Anchor to the fastest packet so a delayed snapshot cannot rewind the same flight.
                val sentAt = timestamp * 1_000_000
                val offset = minOf(serverOffset ?: Long.MAX_VALUE, nowNanos - sentAt)
                serverOffset = offset; lastTimestamp = timestamp
                sentAt + offset
            }
            if (!live || newHit || curve == null && b[2] == 0f && b[3] == 0f) {
                ballX = frame[0]; ballY = frame[1]; height = frameHeight
                if (!live) { theirX = frameTheirX; theirY = frameTheirY }
                if (!live || curve == null) trailPoints.clear()
            }
            live = true
            true
        }.getOrDefault(false)
    }

    fun advance(nowNanos: Long) {
        if (!live) return
        val dt = if (lastAdvance == 0L) 0f else ((nowNanos - lastAdvance) / 1e9f).coerceIn(0f, .1f)
        lastAdvance = nowNanos
        if (nowNanos - localContactAt > 150_000_000L) localContact = null
        val age = ((nowNanos - frameAt) / 1e9f).coerceIn(0f, .15f)
        countdownMs = (frameCountdown - (age * 1000).toInt()).coerceAtLeast(0)
        val curve = arc
        val target = if (curve == null) floatArrayOf(frame[0] + frame[2] * age, frame[1] + frame[3] * age, frameHeight)
            else flightPosition(curve, age, spin, frameDrift)
        if (frameDrift != null && frame[3] > 0 && returnReady && target[2] >= -BallR) {
            // Keep a pending local contact on the painted rim until the server confirms the return.
            val contact = frontContact(ballX, ballY, target[0], target[1], myX, myY, contactRadius(max(height, target[2])))
            if (contact != null) {
                target[0] = ballX + (target[0] - ballX) * contact
                target[1] = ballY + (target[1] - ballY) * contact
                target[2] = height + (target[2] - height) * contact
            }
        }
        val k = if (frameDrift != null || abs(target[0] - ballX) + abs(target[1] - ballY) > .35f) 1f else 1 - exp(-45f * dt)
        ballX += (target[0] - ballX) * k
        ballY += (target[1] - ballY) * k
        height += (target[2] - height) * k
        val follow = 1 - exp(-35f * dt)
        theirX += (frameTheirX - theirX) * follow; theirY += (frameTheirY - theirY) * follow
        while (trailPoints.isNotEmpty() && nowNanos - trailPoints.first().atNanos >= 300_000_000L) trailPoints.removeFirst()
        val previous = trailPoints.lastOrNull()
        if (curve != null && age < .15f && nowNanos - lastTrail >= 12_000_000L &&
            (previous == null || hypot(ballX - previous.x, ballY - previous.y) > .001f)) {
            trailPoints.addLast(PongTrail(ballX, ballY, height, nowNanos))
            lastTrail = nowNanos
        }
    }

    /** Returns the two server coordinates after rotating seat two's view. */
    fun paddle(x: Float, y: Float): Pair<Float, Float> {
        if (!x.isFinite() || !y.isFinite()) return viewX(myX) to viewY(myY)
        val nextX = x.coerceIn(RacketMin, RacketMax)
        val nextY = y.coerceIn(RacketNear, RacketFar)
        val ready = frame[3] > 0 && returnReady || waitingServe && myServe && countdownMs == 0
        if (ready && !localContactUsed && height >= -BallR) {
            val radius = contactRadius(height).toDouble()
            val contact = PongPhysics.sweepContact(ballX.toDouble(), ballY.toDouble(), ballX.toDouble(), ballY.toDouble(),
                myX.toDouble(), myY.toDouble(), nextX.toDouble(), nextY.toDouble(), radius, -1.0)
            if (contact != null && contact.ny <= 1e-6 &&
                (nextX - myX) * contact.nx + (nextY - myY) * contact.ny > 1e-6) {
                val rim = PongPhysics.rimPosition(contact, nextX.toDouble(), nextY.toDouble(), radius)
                localContact = rim.first.toFloat() to rim.second.toFloat()
                localContactAt = lastAdvance
                localContactUsed = true
            }
        }
        myX = nextX; myY = nextY
        return viewX(myX) to viewY(myY)
    }

    internal fun visibleBallPosition(): Pair<Float, Float> {
        // A pending impact has one fixed position; after confirmation only the server flight positions the ball.
        return localContact ?: (ballX to ballY)
    }

    companion object {
        const val CourtH = 1.8f
        const val HeadR = .137f
        const val BallR = .033f
        const val HeadOutline = .025f
        const val RacketMin = -.26f
        const val RacketMax = 1.26f
        const val RacketNear = 1.3f
        const val RacketFar = 2.5f
        const val ArcH = 6f / 12.16f
        private const val LegacySpinDrift = .8f
        private const val LegacySpinTau = 1.8f

        internal fun ballRadius(height: Float) = BallR * (1 + .7f * (height / ArcH).coerceIn(0f, 1f))
        internal fun contactRadius(height: Float) = HeadR + HeadOutline / 2 + ballRadius(height) * 1.35f

        /** First front-face contact of a segment, including a segment that crosses the entire head. */
        internal fun frontContact(x0: Float, y0: Float, x1: Float, y1: Float, px: Float, py: Float, radius: Float): Float? {
            if (y0 > py) return null
            val dx = x1 - x0; val dy = y1 - y0
            val ox = x0 - px; val oy = y0 - py
            val c = ox * ox + oy * oy - radius * radius
            if (c <= 1e-7f) return if (ox * dx + oy * dy < 0) 0f else null
            val a = dx * dx + dy * dy
            if (a < 1e-12f) return null
            val b = ox * dx + oy * dy
            val discriminant = b * b - a * c
            if (discriminant < 0) return null
            val t = (-b - sqrt(discriminant)) / a
            return t.takeIf { it in 0f..1f && y0 + dy * it <= py }
        }

        /** Halfway to a held touch per 60 Hz frame, including on 90/120 Hz displays. */
        internal fun racketFollow(seconds: Float) = 1 - exp(-41.58883f * seconds.coerceIn(0f, .1f))

        internal fun flightPosition(arc: FloatArray, age: Float, spin: Float, drift: Float? = null): FloatArray {
            val t = ((arc[12] + age) / arc[13]).coerceIn(0f, 1f)
            val u = (t * t * t - 2 * t * t + t) * 1.363f + (-2 * t * t * t + 3 * t * t) + (t * t * t - t * t) * .3368f
            val a = 1 - u
            return FloatArray(3) { i ->
                val end = arc[9 + i] + if (i == 0) drift?.let { it * age }
                    ?: (spin * LegacySpinDrift * LegacySpinTau * (1 - exp(-age / LegacySpinTau))) else 0f
                a * a * a * arc[i] + 3 * a * a * u * arc[3 + i] + 3 * a * u * u * arc[6 + i] + u * u * u * end
            }
        }
    }
}
