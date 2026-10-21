package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import kotlin.math.*

data class PongTrail(val x: Float, val y: Float, val height: Float)

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
    var myAngle = 0f; private set
    var theirAngle = 0f; private set
    var myScore = 0; private set
    var theirScore = 0; private set
    var countdownMs = 0; private set
    var waitingServe = false; private set
    var myServe = false; private set
    var hits = 0; private set
    var live = false; private set
    var spin = 0f; private set
    var spinAngle = 0f; private set
    var bounceX = 0f; private set
    var bounceY = 0f; private set
    var bounceAt = 0L; private set
    private val trailPoints = ArrayDeque<PongTrail>()
    val trail: List<PongTrail> get() = trailPoints
    private var frame = FloatArray(4)
    private var arc: FloatArray? = null
    private var frameHeight = 0f
    private var frameTheirX = .5f
    private var frameTheirY = -.08f
    private var frameCountdown = 0
    private var frameAt = 0L
    private var lastAdvance = 0L
    private var lastTrail = 0L
    private var bounces = 0
    private var racketAngle = 0f
    private var remoteAngle = 0f
    private var returnReady = false

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
            val countdown = (data["c"] as JsonPrimitive).int
            val hitCount = (data["h"] as JsonPrimitive).int
            val serving = data["serve"]?.let { (it as JsonPrimitive).int } ?: 0
            val bounceCount = data["j"]?.let { (it as JsonPrimitive).int } ?: 0
            val landed = data["l"]?.let { (it as JsonPrimitive).boolean } ?: false
            require(countdown >= 0 && hitCount >= 0 && serving in 0..2 && bounceCount >= 0)

            val newHit = hitCount != hits
            frame = floatArrayOf(viewX(b[0]), viewY(b[1]), if (seat == 1) b[2] else -b[2], if (seat == 1) b[3] else -b[3])
            arc = curve?.also { for (i in 0..3) { it[i * 3] = viewX(it[i * 3]); it[i * 3 + 1] = viewY(it[i * 3 + 1]) } }
            frameHeight = z[0]
            val nextTheirX = viewX(p[2 - seat]); val nextTheirY = viewY(r[2 - seat])
            remoteAngle = strokeAngle(nextTheirX - frameTheirX, nextTheirY - frameTheirY)
            frameTheirX = nextTheirX; frameTheirY = nextTheirY
            frameCountdown = countdown
            myScore = s[seat - 1]; theirScore = s[2 - seat]
            spin = if (seat == 1) incomingSpin else -incomingSpin
            hits = hitCount
            waitingServe = serving != 0; myServe = serving == seat
            returnReady = landed
            if (bounceCount > bounces) { bounceX = frame[0]; bounceY = frame[1]; bounceAt = nowNanos }
            bounces = bounceCount
            frameAt = nowNanos
            if (!live || newHit || curve == null && b[2] == 0f && b[3] == 0f) {
                ballX = frame[0]; ballY = frame[1]; height = frameHeight
                theirX = frameTheirX; theirY = frameTheirY
                trailPoints.clear()
            }
            live = true
            true
        }.getOrDefault(false)
    }

    fun advance(nowNanos: Long) {
        if (!live) return
        val dt = if (lastAdvance == 0L) 0f else ((nowNanos - lastAdvance) / 1e9f).coerceIn(0f, .1f)
        lastAdvance = nowNanos
        val age = ((nowNanos - frameAt) / 1e9f).coerceIn(0f, .15f)
        countdownMs = (frameCountdown - (age * 1000).toInt()).coerceAtLeast(0)
        val curve = arc
        val target = if (curve == null) floatArrayOf(frame[0] + frame[2] * age, frame[1] + frame[3] * age, frameHeight)
            else flightPosition(curve, age, spin)
        val k = if (abs(target[0] - ballX) + abs(target[1] - ballY) > .35f) 1f else 1 - exp(-45f * dt)
        ballX += (target[0] - ballX) * k
        ballY += (target[1] - ballY) * k
        height += (target[2] - height) * k
        val follow = 1 - exp(-35f * dt)
        theirX += (frameTheirX - theirX) * follow; theirY += (frameTheirY - theirY) * follow
        val turn = 1 - exp(-18f * dt)
        myAngle += (racketAngle - myAngle) * turn
        theirAngle += (remoteAngle - theirAngle) * turn
        spinAngle = (spinAngle + spin * exp(-age / SpinTau) * dt * 900) % 360
        if (curve != null && nowNanos - lastTrail >= 12_000_000L) {
            trailPoints.addLast(PongTrail(ballX, ballY, height))
            while (trailPoints.size > 8) trailPoints.removeFirst()
            lastTrail = nowNanos
        }
    }

    /** Returns the two server coordinates after rotating seat two's view. */
    fun paddle(x: Float, y: Float, released: Boolean = false): Pair<Float, Float> {
        val nextX = x.coerceIn(RacketMin, RacketMax)
        var nextY = y.coerceIn(RacketNear, RacketFar)
        val incoming = frame[3] > 0 && returnReady || waitingServe && myServe && countdownMs == 0
        if (incoming && nextY < myY && myY >= ballY && abs(nextX - ballX) < ContactR) {
            // Meet the incoming ball with the front face before the finger can push the head through it.
            val contactY = ballY + sqrt(ContactR * ContactR - (nextX - ballX).pow(2))
            if (myY >= contactY && nextY < contactY) nextY = contactY.coerceIn(RacketNear, RacketFar)
        }
        val dx = nextX - myX; val dy = nextY - myY
        racketAngle = if (released) 0f else strokeAngle(dx, dy)
        myX = nextX; myY = nextY
        return viewX(myX) to viewY(myY)
    }

    companion object {
        const val CourtH = 1.8f
        const val HeadR = .137f
        const val BallR = .033f
        const val RacketMin = -.26f
        const val RacketMax = 1.26f
        const val RacketNear = 1.3f
        const val RacketFar = 2.5f
        const val ArcH = 6f / 12.16f
        private const val SpinDrift = .4f
        private const val ContactR = HeadR + BallR + .02f
        private const val SpinTau = 1.8f

        private fun strokeAngle(dx: Float, dy: Float) = if (hypot(dx, dy) < .001f) 0f
            else (atan2(dx, abs(dy) + .005f) * 180f / PI.toFloat()).coerceIn(-55f, 55f)

        internal fun flightPosition(arc: FloatArray, age: Float, spin: Float): FloatArray {
            val t = ((arc[12] + age) / arc[13]).coerceIn(0f, 1f)
            val u = (t * t * t - 2 * t * t + t) * 1.363f + (-2 * t * t * t + 3 * t * t) + (t * t * t - t * t) * .3368f
            val a = 1 - u
            return FloatArray(3) { i ->
                val end = arc[9 + i] + if (i == 0) spin * SpinDrift * SpinTau * (1 - exp(-age / SpinTau)) else 0f
                a * a * a * arc[i] + 3 * a * a * u * arc[3 + i] + 3 * a * u * u * arc[6 + i] + u * u * u * end
            }
        }
    }
}
