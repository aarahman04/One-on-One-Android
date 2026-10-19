package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/** Host simulation and guest presentation share normalized court coordinates; velocities are units/second. */
class PongGame {
    var x = .5f; private set
    var y = .5f; private set
    var vx = 0f; private set
    var vy = 0f; private set
    var hostY = .5f; private set
    var guestY = .5f; private set
    var hostScore = 0; private set
    var guestScore = 0; private set
    var countdown = 3f; private set
    var hits = 0; private set
    val winner get() = when { hostScore == 7 -> 1; guestScore == 7 -> 2; else -> 0 }
    private val expected = IntArray(2)
    private var serveDirection = 1f
    private var targetX = .5f
    private var targetY = .5f
    private var snapshotAge = 0f

    fun paddle(player: Int, value: Float) {
        require(player in 1..2 && value.isFinite())
        if (player == 1) hostY = value.coerceIn(PaddleHalf, 1f - PaddleHalf)
        else guestY = value.coerceIn(PaddleHalf, 1f - PaddleHalf)
    }

    fun receive(player: Int, seq: Int, data: JsonObject) {
        require(player in 1..2 && seq == expected[player - 1]) { "Moves arrived out of order." }
        require(data.keys == if (player == 1) setOf("b", "p", "s") else setOf("p")) { "Invalid table tennis move." }
        val paddle = number(data["p"])
        require(paddle in PaddleHalf..(1f - PaddleHalf)) { "Invalid paddle position." }
        if (player == 1) {
            val ball = data["b"] as? JsonArray
            val score = data["s"] as? JsonArray
            require(ball?.size == 4 && score?.size == 2) { "Invalid court state." }
            val bx = number(ball[0]); val by = number(ball[1]); val bvx = number(ball[2]); val bvy = number(ball[3])
            val a = (score[0] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            val b = (score[1] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            require(bx in 0f..1f && by in 0f..1f && abs(bvx) <= MaxSpeed && abs(bvy) <= MaxSpeed) { "Invalid ball position." }
            require(a != null && b != null && a in hostScore..7 && b in guestScore..7 &&
                a - hostScore + b - guestScore <= 1 && !(a == 7 && b == 7)) { "Invalid score." }
            val newServe = a != hostScore || b != guestScore
            if (!newServe && vx * bvx < 0f) hits++
            hostScore = a; guestScore = b; hostY = paddle
            targetX = bx; targetY = by; vx = bvx; vy = bvy; snapshotAge = 0f
            if (newServe) { countdown = 3f; x = bx; y = by }
            if (bvx != 0f || bvy != 0f) countdown = 0f
        } else guestY = paddle
        expected[player - 1]++
    }

    fun tick(seconds: Float, authority: Boolean) {
        if (winner != 0) return
        val dt = seconds.coerceIn(0f, .05f)
        if (!authority) {
            countdown = (countdown - dt).coerceAtLeast(0f)
            snapshotAge += dt
            val prediction = min(snapshotAge, .12f)
            val blend = 1f - exp(-22f * dt)
            x += ((targetX + vx * prediction).coerceIn(0f, 1f) - x) * blend
            y += ((targetY + vy * prediction).coerceIn(0f, 1f) - y) * blend
            return
        }
        if (countdown > 0f) {
            countdown = (countdown - dt).coerceAtLeast(0f)
            if (countdown == 0f) { vx = .48f * serveDirection; vy = .19f }
            return
        }
        // Substeps prevent a fast ball crossing a paddle between two display frames.
        repeat(4) {
            if (countdown > 0f || winner != 0) return@repeat
            val oldX = x
            x += vx * dt / 4; y += vy * dt / 4
            if (y < BallRadius) { y = BallRadius * 2f - y; vy = abs(vy) }
            if (y > 1f - BallRadius) { y = 2f * (1f - BallRadius) - y; vy = -abs(vy) }
            if (vx < 0f && oldX >= PaddleX + BallRadius && x < PaddleX + BallRadius && abs(y - hostY) <= PaddleHalf + BallRadius) {
                x = PaddleX + BallRadius; bounce(hostY, 1f)
            } else if (vx > 0f && oldX <= 1f - PaddleX - BallRadius && x > 1f - PaddleX - BallRadius && abs(y - guestY) <= PaddleHalf + BallRadius) {
                x = 1f - PaddleX - BallRadius; bounce(guestY, -1f)
            }
            if (x < 0f || x > 1f) {
                if (x < 0f) guestScore++ else hostScore++
                serveDirection = if (x < 0f) -1f else 1f
                x = .5f; y = .5f; vx = 0f; vy = 0f; countdown = 3f
            }
        }
    }

    private fun bounce(paddleY: Float, direction: Float) {
        val speed = min(MaxSpeed, sqrt(vx * vx + vy * vy) * 1.07f)
        vy = ((y - paddleY) / PaddleHalf).coerceIn(-1f, 1f) * speed * .72f
        vx = sqrt(speed * speed - vy * vy) * direction.sign
        hits++
    }

    fun packet(authority: Boolean): JsonObject = buildJsonObject {
        put("p", if (authority) hostY else guestY)
        if (authority) {
            put("b", buildJsonArray { add(x); add(y); add(vx); add(vy) })
            put("s", buildJsonArray { add(hostScore); add(guestScore) })
        }
    }

    companion object {
        const val PaddleHalf = .105f
        const val PaddleX = .055f
        const val BallRadius = .014f
        const val MaxSpeed = 1.8f
        private fun number(value: JsonElement?): Float {
            val number = (value as? JsonPrimitive)?.takeUnless { it.isString }?.floatOrNull
            require(number != null && number.isFinite()) { "Invalid court state." }
            return number
        }
    }
}
