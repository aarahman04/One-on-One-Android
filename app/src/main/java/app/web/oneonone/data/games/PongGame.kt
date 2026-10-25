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
    /** When a racket last visibly struck the ball (draw clock) and whose racket it was. */
    var impactAt = 0L; private set
    var impactMine = false; private set
    private var frameSpinSide = 0f
    private var frameSpinTop = 0f
    /** The ball's current spin for rendering: the predicted return's, else the drawn frame's. */
    val spinSide get() = prediction?.get(15) ?: frameSpinSide
    val spinTop get() = prediction?.get(16) ?: frameSpinTop
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
                                    val theirX: Float, val theirY: Float, val spinSide: Float, val spinTop: Float,
                                    val myScore: Int = 0, val theirScore: Int = 0, val countdown: Int = 0, val bounces: Int = 0)
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
    /** Server time of the drawn ball. Equals [displayTimeMs] unless frames carry `pv` (coherent timeline). */
    var ballTimeMs = 0.0; private set
    private var coherent = false
    private var ballDelay = InterpolationMs
    private var farDelay = InterpolationMs
    private val lagT = DoubleArray(64)
    private val lagMs = DoubleArray(64)
    private var lagCount = 0
    private var lagIndex = 0
    private val batV = DoubleArray(64)
    private val batX = FloatArray(64)
    private val batY = FloatArray(64)
    private var batCount = 0
    private var batIndex = 0
    private var shownHits = -1
    private var shownBounces = -1
    private var shownScore = -1 to -1
    private var shownServing = 0
    private var landingFrame: DisplayFrame? = null
    private var landingAt = 0.0
    private var impactHits = 0
    private var stepX = 0f
    private var stepY = 0f
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
            // Optional additive field: the ball's [sidespin, top/back spin], clamped to the documented range.
            val ballSpin = if (data["sp"] != null) numbers("sp", 2).also { for (i in 0..1) it[i] = it[i].coerceIn(-1f, 1f) }
                else floatArrayOf(0f, 0f)
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
            // Optional additive field: the server time each seat's racket (p/r) was sent at, on its sender's display axis.
            val racketTimes = data["pv"]?.let { value ->
                value.jsonArray.map { it.jsonPrimitive.double }.also { require(it.size == 2 && it.all(Double::isFinite)) }
            }
            // With `pv` the whole display (score, serve, bounces, resets) follows the drawn ball, not packet arrival.
            val timeline = ack != null && timestamp != null && racketTimes != null

            val newHit = hitCount != hits
            if (live && newHit && hitCount != impactHits && !timeline) {
                impactHits = hitCount; impactAt = nowNanos; impactMine = curve?.let { viewY(it[10]) < viewY(it[1]) } ?: false
            }
            val viewSpin = if (seat == 1) ballSpin[0] else -ballSpin[0]
            val reset = !live || s[seat - 1] != myScore || s[2 - seat] != theirScore ||
                serving != 0 && (!waitingServe || countdown > frameCountdown + 100)
            frame = floatArrayOf(viewX(b[0]), viewY(b[1]), if (seat == 1) b[2] else -b[2], if (seat == 1) b[3] else -b[3])
            arc = curve?.also { for (i in 0..3) { it[i * 3] = viewX(it[i * 3]); it[i * 3 + 1] = viewY(it[i * 3 + 1]) } }
            frameHeight = z[0]
            val nextTheirX = viewX(p[2 - seat]); val nextTheirY = viewY(r[2 - seat])
            frameTheirX = nextTheirX; frameTheirY = nextTheirY
            frameCountdown = countdown
            if (reset && (!timeline || !live)) {
                myX = viewX(p[seat - 1]); myY = viewY(r[seat - 1]); round++
                trailPoints.clear(); lastTrail = 0L
            }
            if (reset || newHit || prediction != null && ack != null && ack >= predictionN && hitCount <= predictionHits) {
                if (prediction != null && (!reset || timeline)) {
                    reconcile = true
                    reconcileCurve = prediction; reconcileViewAt = predictionViewAt; reconcileAt = predictionAt
                    reconcileDriftAt = predictionDriftAt; reconcileDrift = predictionDrift
                    confirmationLog = true; confirmationAccepted = newHit
                    confirmationDelay = (nowNanos - predictionAt) / 1_000_000.0
                }
                prediction = null; localContactUsed = false
                if (reset && !timeline) {
                    frames.clear(); drawnFrame = null; stroke.clear()
                    reconcile = false; reconcileCurve = null; blendAt = 0L; confirmationLog = false
                }
            }
            if (!timeline || !live) { myScore = s[seat - 1]; theirScore = s[2 - seat]; waitingServe = serving != 0; myServe = serving == seat }
            spin = if (seat == 1) incomingSpin else -incomingSpin
            frameDrift = incomingDrift?.let { if (seat == 1) it else -it }
            frameSpinSide = viewSpin; frameSpinTop = ballSpin[1]
            hits = hitCount
            returnReady = landed
            acknowledged = ack ?: acknowledged
            sampleN = max(sampleN, acknowledged)
            compensated = ack != null && timestamp != null
            if (bounceCount > bounces && !timeline) { bounceX = frame[0]; bounceY = frame[1]; bounceAt = nowNanos }
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
                frames.addLast(DisplayFrame(timestamp!!, frame, arc, frameHeight, spin, frameDrift, landed, serving, hitCount, nextTheirX, nextTheirY,
                    viewSpin, ballSpin[1], s[seat - 1], s[2 - seat], countdown, bounceCount))
                if (reset && !timeline) drawnFrame = frames.last()
                while (frames.size > 32 || frames.size > 1 && timestamp - frames.first().t > 800) frames.removeFirst()
            }
            if (racketTimes != null && timestamp != null) {
                val v = racketTimes[2 - seat]
                // Time only goes back when the opponent's first timed sample replaces the untimed server clock.
                if (batCount > 0 && v < batV[(batIndex + 63) % 64]) batCount = 0
                val last = if (batCount > 0) batV[(batIndex + 63) % 64] else Double.NEGATIVE_INFINITY
                if (v > last) {
                    batV[batIndex] = v; batX[batIndex] = nextTheirX; batY[batIndex] = nextTheirY
                    batIndex = (batIndex + 1) % 64; batCount = minOf(batCount + 1, 64)
                    // How far a fresh racket sample trails the frame: the opponent's view lag plus the frame interval.
                    lagT[lagIndex] = timestamp.toDouble(); lagMs[lagIndex] = timestamp - v
                    lagIndex = (lagIndex + 1) % 64; lagCount = minOf(lagCount + 1, 64)
                }
            }
            coherent = timeline
            if (!live || !timeline && (reset || curve == null && b[2] == 0f && b[3] == 0f && prediction == null)) {
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
        val priorBallTime = ballTimeMs
        var age = ((nowNanos - frameAt) / 1e9f).coerceIn(0f, .15f)
        var shown = frames.lastOrNull()
        if (compensated && shown != null) {
            var selected: DisplayFrame = shown
            val desired = (nowNanos - (serverOffset ?: 0)) / 1_000_000.0 - InterpolationMs
            displayTimeMs = if (prediction != null && !coherent) max(displayTimeMs, desired).coerceAtLeast(frames.first().t.toDouble())
                // A local launch keeps moving through a frame stall at real time instead of jumping to the newest time.
                else if (prediction != null) max(displayTimeMs, min(desired, max(shown.t + 50.0, displayTimeMs + dt * 1000.0)))
                    .coerceAtLeast(frames.first().t.toDouble())
                else max(displayTimeMs, desired).coerceIn(frames.first().t.toDouble(), shown.t + 50.0)
            ballTimeMs = if (coherent) coherentBallTime(desired + InterpolationMs, dt * 1000.0) else displayTimeMs
            for (i in 0 until frames.size) {
                val candidate = frames[i]
                if (candidate.t <= ballTimeMs) selected = candidate else break
            }
            // A post-impact frame describes the return even before its emission time.
            val shownStart = selected.arc?.let { selected.t - round(it[12] * 10000.0) / 10 } ?: Double.NEGATIVE_INFINITY
            if (coherent) {
                // The newest later flight (a hit or a bounce) that has already started at the drawn time.
                var best = selected; var bestStart = shownStart
                for (i in 0 until frames.size) {
                    val candidate = frames[i]
                    val a = candidate.arc ?: continue
                    val start = candidate.t - a[12] * 1000.0
                    if (candidate.t > selected.t && (candidate.hits != selected.hits || candidate.bounces != selected.bounces) &&
                        start <= ballTimeMs && start >= bestStart) { best = candidate; bestStart = start }
                }
                selected = best
            } else for (i in 0 until frames.size) {
                val candidate = frames[i]
                val a = candidate.arc ?: continue
                val start = candidate.t - round(a[12] * 10000.0) / 10
                if (candidate.t > selected.t && start > shownStart + 1 && start <= ballTimeMs) { selected = candidate; break }
            }
            shown = selected; drawnFrame = selected
            age = ((ballTimeMs - selected.t) / 1000).toFloat()
            frame = selected.b; arc = selected.arc; frameHeight = selected.height
            frameDrift = selected.drift; returnReady = selected.landed
            frameSpinSide = selected.spinSide; frameSpinTop = selected.spinTop
        } else if (lastTimestamp > 0) displayTimeMs = lastTimestamp + age * 1000.0
        if (!coherent) ballTimeMs = displayTimeMs
        val drawnHitsBefore = shownHits
        if (coherent && shown != null) showDrawn(shown, nowNanos)
        countdownMs = ((if (coherent && shown != null) shown.countdown else frameCountdown) - (age * 1000).toInt()).coerceAtLeast(0)
        val curve = arc
        val target = targetPosition
        if (curve == null) { target[0] = frame[0] + frame[2] * age; target[1] = frame[1] + frame[3] * age; target[2] = frameHeight }
        else flightPositionInto(curve, age, shown?.spin ?: spin, frameDrift, target)
        previousBallX = ballX; previousBallY = ballY; previousHeight = height
        previousBallTime = priorBallTime
        if (prediction == null && contactEligible && (lastReleased || nowNanos - lastInputAt > 120_000_000))
            predict(ballX, ballY, height, target[0], target[1], target[2], myX, myY, myX, myY, nowNanos, true)
        val predicted = prediction
        if (predicted != null) {
            advancePredictionDrift(nowNanos)
            val elapsed = if (predictionViewAt > 0) ((ballTimeMs - predictionViewAt) / 1000).toFloat()
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
                val elapsed = if (reconcileViewAt > 0) ((ballTimeMs - reconcileViewAt) / 1000).toFloat() else ((nowNanos - reconcileAt) / 1e9).toFloat()
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
        // A hit the buffer could not hide (very late frame) eases onto the new flight instead of jumping.
        if (coherent && prediction == null && !reconcile && drawnHitsBefore >= 0 && shownHits > drawnHitsBefore && blendAt == 0L &&
            hypot(target[0] - ballX - stepX, target[1] - ballY - stepY) > .02f) {
            blendAt = nowNanos; blendX = ballX - target[0]; blendY = ballY - target[1]; blendZ = height - target[2]
        }
        if (blendAt > 0) {
            val remaining = (1 - (nowNanos - blendAt) / 60_000_000f).coerceIn(0f, 1f)
            target[0] += blendX * remaining; target[1] += blendY * remaining; target[2] += blendZ * remaining
            if (remaining == 0f) blendAt = 0L
        }
        val k = if (frameDrift != null || abs(target[0] - ballX) + abs(target[1] - ballY) > .35f) 1f else 1 - exp(-45f * dt)
        stepX = (target[0] - ballX) * k; stepY = (target[1] - ballY) * k
        ballX += stepX
        ballY += stepY
        height += (target[2] - height) * k
        val follow = 1 - exp(-35f * dt)
        if (coherent && batCount > 0) {
            // The opponent's racket at the time its own ball view had reached, on the same axis as the ball it hits.
            val at = (nowNanos - (serverOffset ?: 0)) / 1_000_000.0 - farDelay
            var newer = batCount
            for (i in 0 until batCount) if (batV[(batIndex - batCount + i + 64) % 64] > at) { newer = i; break }
            val a = (batIndex - batCount + maxOf(newer - 1, 0) + 64) % 64; val b = (batIndex - batCount + minOf(newer, batCount - 1) + 64) % 64
            val f = if (batV[b] > batV[a]) ((at - batV[a]) / (batV[b] - batV[a])).coerceIn(0.0, 1.0).toFloat() else 0f
            theirX = batX[a] + (batX[b] - batX[a]) * f; theirY = batY[a] + (batY[b] - batY[a]) * f
            // Past the newest sample (a lost frame): continue its last motion for at most BatExtrapolationMs.
            val p = (batIndex + 62) % 64
            if (newer == batCount && batCount > 1 && batV[b] > batV[p]) {
                val k = ((at - batV[b]).coerceAtMost(BatExtrapolationMs) / (batV[b] - batV[p])).toFloat()
                val dx = (batX[b] - batX[p]) * k; val dy = (batY[b] - batY[p]) * k
                val limit = min(1f, BatExtrapolationMax / max(hypot(dx, dy), 1e-6f))
                theirX = (batX[b] + dx * limit).coerceIn(RacketMin, RacketMax)
                theirY = (batY[b] + dy * limit).coerceIn(CourtH - RacketFar, CourtH - RacketNear)
            }
        } else if (compensated && frames.isNotEmpty()) {
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

    private fun towardMe(f: DisplayFrame?): Boolean = when {
        f == null -> false
        f.arc != null -> f.arc[10] > f.arc[1]
        f.serving != 0 -> f.serving == seat
        else -> f.b[3] > 0
    }

    /**
     * The ball is drawn [ballDelay] behind the newest server time: [InterpolationMs] while it comes to this player,
     * [farDelay] while it goes to the opponent, so their delayed racket and their lag-compensated hit line up with
     * it. The change is spread over the flight so that it finishes by the table bounce when the rate limits allow.
     */
    private fun coherentBallTime(newest: Double, dtMs: Double): Double {
        val latest = if (lagCount > 0) lagT[(lagIndex + 63) % 64] else 0.0
        var lag = -1.0
        for (i in 0 until lagCount) if (lagT[i] >= latest - 3000) lag = max(lag, lagMs[i])
        if (lag >= 0) farDelay += ((lag + FarMarginMs).coerceIn(InterpolationMs, MaxFarDelayMs) - farDelay).coerceIn(-.2 * dtMs, .2 * dtMs)
        val drawn = drawnFrame
        // A predicted local hit already sends the ball away, so the delay starts growing at contact.
        val toward = prediction == null && towardMe(drawn)
        val want = if (toward) InterpolationMs else farDelay
        // Changing the delay at rate r draws the ball at 1 - r of real time; finish by the landing when possible.
        val change = want - ballDelay
        val remaining = landingTime(drawn) - ballTimeMs + change
        val rate = if (remaining > 1) (change / remaining).coerceIn(-MaxDelayRate, MaxSlowRate)
            else if (change > 0) MaxSlowRate else -MaxDelayRate
        ballDelay = if (abs(change) <= abs(rate * dtMs)) want else ballDelay + rate * dtMs
        return max(ballTimeMs, newest - ballDelay).coerceIn(frames.first().t.toDouble(), max(displayTimeMs, frames.first().t.toDouble()))
    }

    /** Server time of the drawn flight's table landing, or NaN after it. */
    private fun landingTime(f: DisplayFrame?): Double {
        val a = f?.arc ?: return Double.NaN
        if (f.landed) return Double.NaN
        if (landingFrame !== f) {
            landingFrame = f
            val w = PongPhysics.landingWeights(a[2].toDouble(), (a[5] / ArcH).toDouble())
            landingAt = f.t - a[12] * 1000.0 + w[4] * a[13] * 1000.0
        }
        return landingAt
    }

    /** Score, serve, bounce and impact events happen when the drawn ball reaches them. */
    private fun showDrawn(f: DisplayFrame, now: Long) {
        val score = f.myScore to f.theirScore
        if (shownHits >= 0) {
            if (score != shownScore || f.serving != 0 && shownServing == 0) {
                round++; trailPoints.clear(); lastTrail = 0L; stroke.clear()
                ballDelay = if (f.serving == seat) InterpolationMs else farDelay
            }
            if (f.hits > shownHits && f.arc != null && f.hits != impactHits) {
                impactHits = f.hits; impactAt = now; impactMine = !towardMe(f)
            }
            if (f.bounces > shownBounces) {
                bounceX = if (f.landed) f.arc?.get(0) ?: f.b[0] else f.b[0]; bounceY = if (f.landed) f.arc?.get(1) ?: f.b[1] else f.b[1]
                bounceAt = now
            }
        }
        shownHits = f.hits; shownBounces = f.bounces; shownScore = score; shownServing = f.serving
        myScore = f.myScore; theirScore = f.theirScore
        waitingServe = f.serving != 0; myServe = f.serving == seat
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
        previousBallTime = ballTimeMs
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
        // The incoming ball's spin (after its bounce) shapes the return exactly as on the server.
        val drawn = if (compensated) drawnFrame else null
        prediction = PongPhysics.launch(rim.first, rim.second, h, stroke, -1.0,
            (drawn?.spinSide ?: frameSpinSide).toDouble(), (drawn?.spinTop ?: frameSpinTop).toDouble())
            .also { predictionDrift = it[14] }
        predictionViewAt = if (previousBallTime > 0) previousBallTime + (ballTimeMs - previousBallTime) * contact.time else ballTimeMs
        predictionAt = now - ((ballTimeMs - predictionViewAt).coerceAtLeast(0.0) * 1_000_000).toLong()
        predictionHits = hits; predictionN = max(sampleN + if (nextSample) 1 else 0, acknowledged + 1)
        predictionCt = sampleCt; predictionV = displayTimeMs
        predictionDriftAt = predictionAt
        localContactUsed = true
        impactAt = now; impactMine = true; impactHits = hits + 1
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
        /** Covers the opponent's send interval, jitter and one lost frame beyond their measured lag (which includes the frame interval). */
        const val FarMarginMs = 48.0
        const val MaxFarDelayMs = 500.0 // the server never rewinds further (MAX_COMP_MS)
        /** Drawn-time stretch limits while the delay moves between ends: the ball looks at most 75% faster coming
         *  in and 50% slower going out; at RTT up to ~200 ms the needed change stays near 20-40%. */
        const val MaxDelayRate = .75
        const val MaxSlowRate = .5
        const val BatExtrapolationMs = 50.0
        const val BatExtrapolationMax = .08f

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
