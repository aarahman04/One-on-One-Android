package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Renders the server's table tennis (contract §12). The server owns the ball and the score; this only
 * smooths its ~30 frames/s for display and owns the local racket position.
 *
 * View coordinates: x 0..1, y 0..[CourtH], own racket head centred on the bottom end line. Seat 2 sees the
 * server table rotated by 180°, so both players face each other.
 */
class PongClient(val seat: Int) {
    var ballX = .5f; private set
    var ballY = CourtH / 2; private set
    var theirX = .5f; private set
    var myX = .5f; private set
    var myScore = 0; private set
    var theirScore = 0; private set
    var countdownMs = 0; private set
    var hits = 0; private set
    /** True once at least one frame arrived. */
    var live = false; private set
    /** Cosmetic ball height above the table, 0..1 (the simulation is flat): used for the shadow and size. */
    var height = 0f; private set
    /** Where and when (nanos) the ball last touched the table on its way, for a small bounce mark. */
    var bounceX = 0f; private set
    var bounceY = 0f; private set
    var bounceAt = 0L; private set
    /** Sidespin (view x acceleration, units/s²) and the drawn ball's roll angle in degrees, which turns with it. */
    var spin = 0f; private set
    var spinAngle = 0f; private set
    private var frame = FloatArray(4)
    private var frameTheirX = .5f
    private var frameCountdown = 0
    private var frameAt = 0L
    private var lastAdvance = 0L
    private var lastProgress = 0f

    private fun viewX(x: Float) = if (seat == 1) x else 1f - x
    private fun viewY(y: Float) = if (seat == 1) y else CourtH - y

    /** Applies one `match:pong` frame. Returns false (and changes nothing) for a malformed or foreign one. */
    fun receive(data: JsonObject, matchId: String, nowNanos: Long): Boolean {
        val parsed = runCatching {
            require((data["id"] as JsonPrimitive).content == matchId)
            val b = (data["b"] as JsonArray).map { (it as JsonPrimitive).float }
            val p = (data["p"] as JsonArray).map { (it as JsonPrimitive).float }
            val s = (data["s"] as JsonArray).map { (it as JsonPrimitive).int }
            require(b.size == 4 && p.size == 2 && s.size == 2 && (b + p).all { it.isFinite() })
            Triple(b, p, s)
        }.getOrNull() ?: return false
        val (b, p, s) = parsed
        // Velocities are rotated with the table.
        frame = floatArrayOf(viewX(b[0]), viewY(b[1]), if (seat == 1) b[2] else -b[2], if (seat == 1) b[3] else -b[3])
        frameTheirX = viewX(p[2 - seat])
        myScore = s[seat - 1]; theirScore = s[2 - seat]
        // Optional since 2026-10-21; rotated with the table like vx.
        spin = (data["w"] as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() }?.let { if (seat == 1) it else -it } ?: 0f
        frameCountdown = (data["c"] as? JsonPrimitive)?.intOrNull ?: 0
        hits = (data["h"] as? JsonPrimitive)?.intOrNull ?: hits
        frameAt = nowNanos
        if (!live || frame[2] == 0f && frame[3] == 0f) { ballX = frame[0]; ballY = frame[1]; theirX = frameTheirX }
        live = true
        return true
    }

    /** Moves the drawn ball and opponent racket toward the server's state, extrapolated to `nowNanos`. */
    fun advance(nowNanos: Long) {
        if (!live) return
        val dt = if (lastAdvance == 0L) 0f else ((nowNanos - lastAdvance) / 1e9f).coerceIn(0f, .1f)
        lastAdvance = nowNanos
        val age = ((nowNanos - frameAt) / 1e9f).coerceIn(0f, MaxExtrapolation)
        countdownMs = (frameCountdown - (age * 1000).toInt()).coerceAtLeast(0)
        var x = frame[0] + frame[2] * age + .5f * spin * age * age
        var y = frame[1] + frame[3] * age
        spinAngle = (spinAngle + spin * dt * SpinRoll) % 360f
        // Sidespin curve with side reflections between frames, like the server (decay over ≤150 ms is negligible).
        while (x < BallR || x > 1f - BallR) x = if (x < BallR) 2 * BallR - x else 2 * (1f - BallR) - x
        // Never draw the ball into a racket head the server hasn't ruled on yet: stop it on the head's
        // surface (own racket at the finger's position, the opponent's where it was last seen).
        if (frame[3] > 0) holdOutside(x, y, myX, CourtH)?.let { x = it.first; y = it.second }
        if (frame[3] < 0) holdOutside(x, y, theirX, 0f)?.let { x = it.first; y = it.second }
        if (abs(x - ballX) + abs(y - ballY) > SnapDistance) { ballX = x; ballY = y }
        else {
            val k = 1f - exp(-Smoothing * dt)
            ballX += (x - ballX) * k; ballY += (y - ballY) * k
        }
        theirX += (frameTheirX - theirX) * (1f - exp(-Smoothing * dt))
        updateHeight(nowNanos)
    }

    private fun holdOutside(x: Float, y: Float, headX: Float, headY: Float): Pair<Float, Float>? {
        val reach = HeadR + BallR
        val dx = x - headX; val dy = y - headY
        val d = hypot(dx, dy)
        if (d >= reach || d == 0f) return null
        return headX + dx / d * reach to headY + dy / d * reach
    }

    /** A rally shot looks like real table tennis: up off the racket, down onto the far half, up again. */
    private fun updateHeight(nowNanos: Long) {
        val vy = frame[3]
        if (vy == 0f) { height = 0f; lastProgress = 0f; return }
        // Progress from the racket that last hit it towards the other end.
        val progress = (if (vy < 0) (CourtH - ballY) / CourtH else ballY / CourtH).coerceIn(0f, 1f)
        height = if (progress < BouncePoint) sin(PI.toFloat() * progress / BouncePoint)
        else .45f * sin(PI.toFloat() * (progress - BouncePoint) / (2 * (1f - BouncePoint)))
        if (lastProgress < BouncePoint && progress >= BouncePoint) { bounceX = ballX; bounceY = ballY; bounceAt = nowNanos }
        lastProgress = progress
    }

    /** Sets the own racket (view x) and returns the server x to send. */
    fun paddle(viewXValue: Float): Float {
        myX = viewXValue.coerceIn(RacketMin, 1f - RacketMin)
        return viewX(myX)
    }

    companion object {
        const val CourtH = 1.8f
        const val HeadR = .13f
        const val BallR = .03f
        const val RacketMin = HeadR * .6f
        /** Where on the way (0 = hitting racket, 1 = far end) the ball lands on the table. */
        const val BouncePoint = .72f
        private const val MaxExtrapolation = .15f
        private const val SnapDistance = .3f
        private const val Smoothing = 28f
        /** Degrees of drawn roll per second per unit of spin: a strong curve visibly whirls. */
        private const val SpinRoll = 900f
    }
}
