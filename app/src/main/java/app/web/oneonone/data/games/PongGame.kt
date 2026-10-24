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
    private var localContactUsed = false
    private var prediction: FloatArray? = null
    private var predictionAt = 0L
    private var predictionHits = 0
    private var predictionN = 0L
    private var predictionCt = 0L
    private var predictionV = 0.0
    private var predictionGap = 0.0
    private var predictionViewAt = 0.0
    private var predictionDriftAt = 0L
    private var predictionDrift = 0f // fixed at contact; later movement never steers
    private var confirmationLog = false
    private var confirmationAccepted = false
    private var confirmationDelay = 0.0
    private val stroke = PongPhysics.StrokeWindow()
    private var lastInputAt = 0L
    private var lastReleased = true
    var sampleCt = 0L; private set
    var sampleN = 0L; private set
    var displayTimeMs = 0.0; private set
    private var acknowledged = 0L
    private var compensated = false
    private val offsets = LongArray(32)
    private var offsetCount = 0
    private var offsetIndex = 0
    private data class DisplayFrame(val t: Long, val b: FloatArray, val arc: FloatArray?, val height: Float,
                                    val spin: Float, val drift: Float?, val landed: Boolean, val serving: Int, val hits: Int,
                                    val theirX: Float, val theirY: Float)
    private val frames = ArrayDeque<DisplayFrame>()
    private var drawnFrame: DisplayFrame? = null
    private var reconcile = false
    private var reconcileCurve: FloatArray? = null
    private var reconcileViewAt = 0.0
    private var reconcileAt = 0L
    private var reconcileDriftAt = 0L
    private var reconcileDrift = 0f
    private val blendSource = FloatArray(3)
    private var blendAt = 0L
    private var blendX = 0f
    private var blendY = 0f
    private var blendZ = 0f
    private val targetPosition = FloatArray(3)
    private var previousBallX = ballX
    private var previousBallY = ballY
    private var previousHeight = height
    private var previousBallTime = 0.0
    val contactEligible get(): Boolean {
        val drawn = if (compensated) drawnFrame else null
        val returning = (drawn?.landed ?: returnReady) && (drawn?.b?.get(3) ?: frame[3]) > 0
        val serving = drawn?.let { it.serving == seat } ?: (waitingServe && myServe)
        return height >= -BallR && (returning || serving && countdownMs == 0) && prediction == null && blendAt == 0L && !reconcile
    }
    val contactPossible get() = prediction != null || contactEligible && hypot(ballX - myX, ballY - myY) <= 2 * contactRadius(height)

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
            val ack = data["ack"]?.let {
                val values = it.jsonArray
                require(values.size == 2 && values.all { value -> value.jsonPrimitive.long >= 0 })
                values[seat - 1].jsonPrimitive.long
            }
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
            if (reset || newHit || prediction != null && ack != null && ack >= predictionN && hitCount <= predictionHits) {
                if (prediction != null && !reset) {
                    reconcile = true
                    reconcileCurve = prediction; reconcileViewAt = predictionViewAt; reconcileAt = predictionAt
                    reconcileDriftAt = predictionDriftAt; reconcileDrift = predictionDrift
                    confirmationLog = true; confirmationAccepted = newHit
                    confirmationDelay = (nowNanos - predictionAt) / 1_000_000.0
                }
                prediction = null; localContactUsed = false
                if (reset) {
                    frames.clear(); drawnFrame = null; stroke.clear()
                    reconcile = false; reconcileCurve = null; blendAt = 0L; confirmationLog = false
                }
            }
            myScore = s[seat - 1]; theirScore = s[2 - seat]
            spin = if (seat == 1) incomingSpin else -incomingSpin
            frameDrift = incomingDrift?.let { if (seat == 1) it else -it }
            hits = hitCount
            waitingServe = serving != 0; myServe = serving == seat
            returnReady = landed
            acknowledged = ack ?: acknowledged
            sampleN = max(sampleN, acknowledged)
            compensated = ack != null && timestamp != null
            if (bounceCount > bounces) { bounceX = frame[0]; bounceY = frame[1]; bounceAt = nowNanos }
            bounces = bounceCount
            frameAt = if (timestamp == null) nowNanos else {
                val sentAt = timestamp * 1_000_000
                offsets[offsetIndex] = nowNanos - sentAt
                offsetIndex = (offsetIndex + 1) % offsets.size; offsetCount = minOf(offsetCount + 1, offsets.size)
                val recent = offsets.copyOf(offsetCount).sortedArray()
                val estimate = recent[(offsetCount - 1) / 8]
                val offset = if (!compensated) minOf(serverOffset ?: Long.MAX_VALUE, estimate)
                    else serverOffset?.let { it + (estimate - it).coerceIn(-1_000_000L, 1_000_000L) } ?: estimate
                serverOffset = offset; lastTimestamp = timestamp
                sentAt + offset
            }
            if (compensated) {
                frames.addLast(DisplayFrame(timestamp!!, frame, arc, frameHeight, spin, frameDrift, landed, serving, hitCount, nextTheirX, nextTheirY))
                if (reset) drawnFrame = frames.last()
                while (frames.size > 32 || frames.size > 1 && timestamp - frames.first().t > 800) frames.removeFirst()
            }
            if (!live || reset || curve == null && b[2] == 0f && b[3] == 0f && prediction == null) {
                ballX = frame[0]; ballY = frame[1]; height = frameHeight
                previousBallX = ballX; previousBallY = ballY; previousHeight = height
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
        val priorViewTime = displayTimeMs
        var age = ((nowNanos - frameAt) / 1e9f).coerceIn(0f, .15f)
        var shown = frames.lastOrNull()
        if (compensated && shown != null) {
            var selected: DisplayFrame = shown
            val desired = (nowNanos - (serverOffset ?: 0)) / 1_000_000.0 - InterpolationMs
            displayTimeMs = if (prediction != null) max(displayTimeMs, desired).coerceAtLeast(frames.first().t.toDouble())
                else max(displayTimeMs, desired).coerceIn(frames.first().t.toDouble(), shown.t + 50.0)
            for (i in 0 until frames.size) {
                val candidate = frames[i]
                if (candidate.t <= displayTimeMs) selected = candidate else break
            }
            // A post-impact frame describes the return even before its emission time.
            val shownStart = selected.arc?.let { selected.t - round(it[12] * 10000.0) / 10 } ?: Double.NEGATIVE_INFINITY
            for (i in 0 until frames.size) {
                val candidate = frames[i]
                val a = candidate.arc ?: continue
                val start = candidate.t - round(a[12] * 10000.0) / 10
                if (candidate.t > selected.t && start > shownStart + 1 && start <= displayTimeMs) { selected = candidate; break }
            }
            shown = selected; drawnFrame = selected
            age = ((displayTimeMs - selected.t) / 1000).toFloat()
            frame = selected.b; arc = selected.arc; frameHeight = selected.height
            frameDrift = selected.drift; returnReady = selected.landed
        } else if (lastTimestamp > 0) displayTimeMs = lastTimestamp + age * 1000.0
        countdownMs = (frameCountdown - (age * 1000).toInt()).coerceAtLeast(0)
        val curve = arc
        val target = targetPosition
        if (curve == null) { target[0] = frame[0] + frame[2] * age; target[1] = frame[1] + frame[3] * age; target[2] = frameHeight }
        else flightPositionInto(curve, age, shown?.spin ?: spin, frameDrift, target)
        previousBallX = ballX; previousBallY = ballY; previousHeight = height
        previousBallTime = priorViewTime
        if (prediction == null && contactEligible && (lastReleased || nowNanos - lastInputAt > 120_000_000))
            predict(ballX, ballY, height, target[0], target[1], target[2], myX, myY, myX, myY, nowNanos, true)
        val predicted = prediction
        if (predicted != null) {
            advancePredictionDrift(nowNanos)
            val elapsed = if (predictionViewAt > 0) ((displayTimeMs - predictionViewAt) / 1000).toFloat()
                else ((nowNanos - predictionAt) / 1e9).toFloat()
            flightPositionInto(predicted, elapsed, 0f, 0f, target)
        }
        if (reconcile) {
            val source = reconcileCurve
            if (source == null) { blendSource[0] = ballX; blendSource[1] = ballY; blendSource[2] = height }
            else {
                val seconds = ((nowNanos - reconcileDriftAt).coerceAtLeast(0) / 1e9).toFloat()
                source[9] += reconcileDrift * seconds
                reconcileDriftAt = nowNanos
                val elapsed = if (reconcileViewAt > 0) ((displayTimeMs - reconcileViewAt) / 1000).toFloat() else ((nowNanos - reconcileAt) / 1e9).toFloat()
                flightPositionInto(source, elapsed, 0f, 0f, blendSource)
            }
            // Dropped pre-bounce return frames must not reconcile against the old incoming flight.
            if (confirmationAccepted && shown != null && shown.hits < hits) {
                target[0] = blendSource[0]; target[1] = blendSource[1]; target[2] = blendSource[2]
            } else {
                // Blend the correction at this draw time; using the previous frame's position adds a one-frame pause.
                blendAt = nowNanos; blendX = blendSource[0] - target[0]; blendY = blendSource[1] - target[1]; blendZ = blendSource[2] - target[2]
                reconcileCurve = null
                if (confirmationLog) {
                    PongContactLog.record(predictionV, predictionCt, predictionGap, false, confirmationAccepted,
                        confirmationDelay, hypot(blendX, blendY).toDouble())
                    confirmationLog = false
                }
                reconcile = false
            }
        }
        if (blendAt > 0) {
            val remaining = (1 - (nowNanos - blendAt) / 60_000_000f).coerceIn(0f, 1f)
            target[0] += blendX * remaining; target[1] += blendY * remaining; target[2] += blendZ * remaining
            if (remaining == 0f) blendAt = 0L
        }
        val k = if (frameDrift != null || abs(target[0] - ballX) + abs(target[1] - ballY) > .35f) 1f else 1 - exp(-45f * dt)
        ballX += (target[0] - ballX) * k
        ballY += (target[1] - ballY) * k
        height += (target[2] - height) * k
        val follow = 1 - exp(-35f * dt)
        if (compensated && frames.isNotEmpty()) {
            // Same buffered timeline as the ball, so the bat and ball stay in step and dropped frames are bridged.
            var next = frames.size
            for (i in 0 until frames.size) if (frames[i].t > displayTimeMs) { next = i; break }
            val a = frames[maxOf(next - 1, 0)]; val b = frames[minOf(next, frames.size - 1)]
            val f = if (b.t > a.t) ((displayTimeMs - a.t) / (b.t - a.t)).coerceIn(0.0, 1.0).toFloat() else 0f
            theirX = a.theirX + (b.theirX - a.theirX) * f; theirY = a.theirY + (b.theirY - a.theirY) * f
        } else { theirX += (frameTheirX - theirX) * follow; theirY += (frameTheirY - theirY) * follow }
        while (trailPoints.isNotEmpty() && nowNanos - trailPoints.first().atNanos >= 300_000_000L) trailPoints.removeFirst()
        val previous = trailPoints.lastOrNull()
        if (curve != null && age < .15f && nowNanos - lastTrail >= 12_000_000L &&
            (previous == null || hypot(ballX - previous.x, ballY - previous.y) > .001f)) {
            trailPoints.addLast(PongTrail(ballX, ballY, height, nowNanos))
            lastTrail = nowNanos
        }
    }

    /** Returns the two server coordinates after rotating seat two's view. */
    fun paddle(x: Float, y: Float, nowNanos: Long = lastAdvance, touch: Pair<Float, Float>? = null, released: Boolean = false, ctFloor: Long = 0): Pair<Float, Float> {
        if (!x.isFinite() || !y.isFinite()) return viewX(myX) to viewY(myY)
        val nextX = x.coerceIn(RacketMin, RacketMax)
        val nextY = y.coerceIn(RacketNear, RacketFar)
        advancePredictionDrift(nowNanos)
        sampleCt = maxOf(sampleCt + 1, nowNanos / 1_000_000, ctFloor + 1); sampleN++
        stroke.sample(sampleCt, (touch?.first ?: nextX).coerceIn(RacketMin, RacketMax).toDouble(),
            (touch?.second ?: nextY).coerceIn(RacketNear, RacketFar).toDouble())
        lastInputAt = nowNanos
        if (contactEligible) predict(previousBallX, previousBallY, previousHeight, ballX, ballY, height,
            myX, myY, nextX, nextY, nowNanos)
        if (released) stroke.clear()
        lastReleased = released
        myX = nextX; myY = nextY
        previousBallX = ballX; previousBallY = ballY; previousHeight = height
        previousBallTime = displayTimeMs
        return viewX(myX) to viewY(myY)
    }

    internal fun visibleBallPosition(): Pair<Float, Float> {
        return ballX to ballY
    }

    private fun predict(bx0: Float, by0: Float, h0: Float, bx1: Float, by1: Float, h1: Float,
                        px0: Float, py0: Float, px1: Float, py1: Float, now: Long, nextSample: Boolean = false) {
        if (localContactUsed || !contactEligible) return
        val radius = contactRadius(max(h0, h1)).toDouble()
        val contact = PongPhysics.sweepContact(bx0.toDouble(), by0.toDouble(), bx1.toDouble(), by1.toDouble(),
            px0.toDouble(), py0.toDouble(), px1.toDouble(), py1.toDouble(), radius, -1.0) ?: return
        val px = px0 + (px1 - px0) * contact.time; val py = py0 + (py1 - py0) * contact.time
        val rim = PongPhysics.rimPosition(contact, px, py, radius)
        val h = h0 + (h1 - h0) * contact.time
        prediction = PongPhysics.launch(rim.first, rim.second, h, stroke).also { predictionDrift = it[14] }
        predictionViewAt = if (previousBallTime > 0) previousBallTime + (displayTimeMs - previousBallTime) * contact.time else displayTimeMs
        predictionAt = now - ((displayTimeMs - predictionViewAt).coerceAtLeast(0.0) * 1_000_000).toLong()
        predictionHits = hits; predictionN = max(sampleN + if (nextSample) 1 else 0, acknowledged + 1)
        predictionCt = sampleCt; predictionV = displayTimeMs
        predictionDriftAt = predictionAt
        localContactUsed = true
        // Contact is on the segment's impact-time rim, never the final bat position.
        ballX = rim.first.toFloat(); ballY = rim.second.toFloat(); height = h.toFloat()
        predictionGap = hypot(bx0 + (bx1 - bx0) * contact.time - px, by0 + (by1 - by0) * contact.time - py) - radius
        PongContactLog.record(displayTimeMs, sampleCt, predictionGap, true, false, 0.0, 0.0)
    }

    private fun advancePredictionDrift(now: Long) {
        val curve = prediction ?: return
        val seconds = ((now - predictionDriftAt).coerceAtLeast(0) / 1e9).toFloat()
        curve[9] += predictionDrift * seconds
        predictionDriftAt = max(predictionDriftAt, now)
        if (now - lastInputAt > 120_000_000) stroke.clear()
    }

    companion object {
        const val CourtH = 1.8f
        const val HeadR = .137f
        const val BallR = .033f
        const val HeadOutline = .025f
        const val HandleWidth = .065f
        const val HandleLength = .12f
        const val HandleOutline = .023f
        const val RacketMin = -.26f
        const val RacketMax = 1.26f
        const val RacketNear = 1.3f
        const val RacketFar = 2.5f
        const val ArcH = 6f / 12.16f
        private const val LegacySpinDrift = .8f
        private const val LegacySpinTau = 1.8f
        const val InterpolationMs = 50.0

        internal fun ballRadius(height: Float) = BallR * (1 + .7f * (height / ArcH).coerceIn(0f, 1f))
        internal fun contactRadius(height: Float) = PongPhysics.contactRadius(height.toDouble()).toFloat()

        /** Halfway to a held touch per 60 Hz frame, including on 90/120 Hz displays. */
        internal fun racketFollow(seconds: Float) = 1 - exp(-41.58883f * seconds.coerceIn(0f, .1f))

        internal fun flightPosition(arc: FloatArray, age: Float, spin: Float, drift: Float? = null): FloatArray {
            return FloatArray(3).also { flightPositionInto(arc, age, spin, drift, it) }
        }
        private fun flightPositionInto(arc: FloatArray, age: Float, spin: Float, drift: Float?, result: FloatArray) {
            val t = ((arc[12] + age) / arc[13]).coerceIn(0f, 1f)
            val u = (t * t * t - 2 * t * t + t) * 1.363f + (-2 * t * t * t + 3 * t * t) + (t * t * t - t * t) * .3368f
            val a = 1 - u
            for (i in 0..2) {
                val end = arc[9 + i] + if (i == 0) drift?.let { it * age }
                    ?: (spin * LegacySpinDrift * LegacySpinTau * (1 - exp(-age / LegacySpinTau))) else 0f
                result[i] = a * a * a * arc[i] + 3 * a * a * u * arc[3 + i] + 3 * a * u * u * arc[6 + i] + u * u * u * end
            }
        }
    }
}
