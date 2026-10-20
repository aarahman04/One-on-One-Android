package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.exp

/**
 * Renders the server's table tennis (contract §12). The server owns the ball and the score; this only
 * smooths its ~30 frames/s for display and owns the local paddle position.
 *
 * View coordinates: x 0..1, y 0..[CourtH], own paddle always at the bottom. Seat 2 sees the server court
 * rotated by 180°, so both players face each other.
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
    private var frame = FloatArray(4)
    private var frameTheirX = .5f
    private var frameCountdown = 0
    private var frameAt = 0L
    private var lastAdvance = 0L

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
        // Velocities are rotated with the court.
        frame = floatArrayOf(viewX(b[0]), viewY(b[1]), if (seat == 1) b[2] else -b[2], if (seat == 1) b[3] else -b[3])
        frameTheirX = viewX(p[2 - seat])
        myScore = s[seat - 1]; theirScore = s[2 - seat]
        frameCountdown = (data["c"] as? JsonPrimitive)?.intOrNull ?: 0
        hits = (data["h"] as? JsonPrimitive)?.intOrNull ?: hits
        frameAt = nowNanos
        if (!live || frame[2] == 0f && frame[3] == 0f) { ballX = frame[0]; ballY = frame[1]; theirX = frameTheirX }
        live = true
        return true
    }

    /** Moves the drawn ball and opponent paddle toward the server's state, extrapolated to `nowNanos`. */
    fun advance(nowNanos: Long) {
        if (!live) return
        val dt = if (lastAdvance == 0L) 0f else ((nowNanos - lastAdvance) / 1e9f).coerceIn(0f, .1f)
        lastAdvance = nowNanos
        val age = ((nowNanos - frameAt) / 1e9f).coerceIn(0f, MaxExtrapolation)
        countdownMs = (frameCountdown - (age * 1000).toInt()).coerceAtLeast(0)
        var x = frame[0] + frame[2] * age
        var y = frame[1] + frame[3] * age
        // Straight line with wall reflections between frames: exactly what the server simulates.
        while (x < BallR || x > 1f - BallR) x = if (x < BallR) 2 * BallR - x else 2 * (1f - BallR) - x
        // Never draw the ball through a paddle the server hasn't ruled on yet.
        val bottom = CourtH - PaddleInset - BallR
        val top = PaddleInset + BallR
        if (frame[3] > 0 && frame[1] <= bottom) y = y.coerceAtMost(bottom)
        if (frame[3] < 0 && frame[1] >= top) y = y.coerceAtLeast(top)
        if (abs(x - ballX) + abs(y - ballY) > SnapDistance) { ballX = x; ballY = y }
        else {
            val k = 1f - exp(-Smoothing * dt)
            ballX += (x - ballX) * k; ballY += (y - ballY) * k
        }
        theirX += (frameTheirX - theirX) * (1f - exp(-Smoothing * dt))
    }

    /** Sets the own paddle (view x) and returns the server x to send. */
    fun paddle(viewXValue: Float): Float {
        myX = viewXValue.coerceIn(PaddleHalf, 1f - PaddleHalf)
        return viewX(myX)
    }

    companion object {
        const val CourtH = 1.6f
        const val PaddleHalf = .12f
        const val PaddleInset = .08f
        const val BallR = .022f
        private const val MaxExtrapolation = .15f
        private const val SnapDistance = .25f
        private const val Smoothing = 28f
    }
}
