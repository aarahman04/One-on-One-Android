package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PongClientTest {
    private val ms = 1_000_000L
    private fun frame(
        b: List<Float> = listOf(.2f, .3f, 0f, 0f),
        extra: JsonObjectBuilder.() -> Unit = {},
    ) = buildJsonObject {
        put("id", "m"); put("c", 0); put("h", 1)
        putJsonArray("b") { b.forEach { add(it) } }
        putJsonArray("p") { add(.3f); add(.7f) }
        putJsonArray("r") { add(1.9f); add(-.2f) }
        putJsonArray("s") { add(4); add(2) }
        putJsonArray("z") { add(.16f); add(0f) }
        extra()
    }
    private fun JsonObjectBuilder.arc(elapsed: Float = .2f, endX: Float = .7f) {
        putJsonArray("a") { listOf(.5f, 1.7f, .16f, .5f, 1.4f, .49f, .7f, .5f, .49f, endX, .5f, -.49f, elapsed, .9f).forEach { add(it) } }
    }

    @Test fun seatTwoRotatesBothCoordinatesAndOwnScoreComesFirst() {
        val one = PongClient(1); val two = PongClient(2)
        assertTrue(one.receive(frame(), "m", ms)); assertTrue(two.receive(frame(), "m", ms))
        assertEquals(.2f, one.ballX, 1e-5f); assertEquals(.3f, one.ballY, 1e-5f)
        assertEquals(.8f, two.ballX, 1e-5f); assertEquals(1.5f, two.ballY, 1e-5f)
        assertEquals(-.2f, one.theirY, 1e-5f); assertEquals(-.1f, two.theirY, 1e-5f)
        assertEquals(4 to 2, one.myScore to one.theirScore); assertEquals(2 to 4, two.myScore to two.theirScore)
        val moved = two.paddle(.25f, 2.1f)
        assertEquals(.75f, moved.first, 1e-5f); assertEquals(-.3f, moved.second, 1e-5f)
        assertEquals(PongClient.RacketMax to PongClient.RacketNear, one.paddle(5f, -4f))
    }

    @Test fun displaysActualCubicHeightAndRotatesTheWholeFlight() {
        val f = frame { arc() }
        val one = PongClient(1); val two = PongClient(2)
        one.receive(f, "m", ms); two.receive(f, "m", ms)
        for (i in 1..15) { one.advance(i * 10 * ms); two.advance(i * 10 * ms) }
        assertTrue(one.height > .16f)
        assertEquals(1f, one.ballX + two.ballX, 1e-4f)
        assertEquals(PongClient.CourtH, one.ballY + two.ballY, 1e-4f)
        assertEquals(one.height, two.height, 1e-4f)
        assertTrue(one.trail.isNotEmpty() && one.trail.size <= 26)
        val arc = FloatArray(14).also { it[0] = .1f; it[9] = .8f; it[13] = 1f }
        assertEquals(.1f, PongClient.flightPosition(arc, 0f, 0f)[0], 1e-5f)
        assertEquals(.8f, PongClient.flightPosition(arc, 1f, 0f)[0], 1e-5f)
    }

    @Test fun openEdgesDoNotReflectOrHoldTheBallAtTheRacket() {
        val p = PongClient(1)
        p.receive(frame(listOf(.99f, 2.1f, 2f, 2f)), "m", ms)
        for (i in 1..25) p.advance(i * 10 * ms)
        assertTrue(p.ballX > 1.2f && p.ballY > 2.3f)
        val stopped = p.ballX
        for (i in 26..50) p.advance(i * 10 * ms)
        assertTrue(p.ballX - stopped < .02f) // prediction ends at 150 ms
    }

    @Test fun spinBendsTheServerCurveAndMirrorsForSeatTwo() {
        val f = frame { arc(.5f); put("spin", .8f) }
        val plain = PongClient(1); val one = PongClient(1); val two = PongClient(2)
        plain.receive(frame { arc(.5f) }, "m", ms)
        one.receive(f, "m", ms); two.receive(f, "m", ms)
        for (i in 1..16) { plain.advance(i * 10 * ms); one.advance(i * 10 * ms); two.advance(i * 10 * ms) }
        assertTrue(one.ballX > plain.ballX + .005f)
        assertEquals(1f, one.ballX + two.ballX, 1e-4f)
    }

    @Test fun bounceIsReportedOnceAndAWaitingServeClearsTheTrail() {
        val p = PongClient(1)
        p.receive(frame { arc(); put("j", 1) }, "m", ms)
        assertEquals(ms, p.bounceAt)
        p.receive(frame { arc(); put("j", 1) }, "m", 2 * ms)
        assertEquals(ms, p.bounceAt)
        for (i in 1..10) p.advance(i * 10 * ms)
        assertTrue(p.trail.isNotEmpty())
        p.receive(frame { put("serve", 1); put("c", 1000) }, "m", 200 * ms)
        p.advance(200 * ms)
        assertTrue(p.waitingServe && p.myServe && p.trail.isEmpty())
        assertEquals(.2f, p.ballX, 0f); assertEquals(.16f, p.height, 0f)
        p.advance(700 * ms); assertEquals(850, p.countdownMs)
    }

    @Test fun anUnpredictedAuthoritativeContactDoesNotTeleportOnReceipt() {
        val p = PongClient(1)
        p.receive(frame(listOf(.5f, 1.5f, 0f, 1f)), "m", ms)
        val before = p.visibleBallPosition()
        p.receive(frame(listOf(.5f, 1.325f, 0f, -1f)) { put("h", 2); put("drift", 0f) }, "m", 20 * ms)
        assertEquals(before, p.visibleBallPosition())
        p.advance(80 * ms)
        assertTrue(p.ballY < before.second)
        assertEquals(2, p.hits)
    }

    @Test fun aForwardStrokeLaunchesFromTheImpactTimeRim() {
        val p = PongClient(1)
        p.receive(frame(listOf(.98f, 1.45f, 0f, 1f)) { put("l", true) }, "m", ms)
        p.advance(ms); p.paddle(.98f, 1.8f); p.paddle(1.03f, 1.3f)
        assertEquals(1.3f, p.myY, 1e-5f)
        val impact = p.visibleBallPosition()
        assertEquals(.98f, impact.first, 1e-4f)
        assertEquals(1.45f, impact.second, 1e-4f)
        p.advance(20 * ms)
        assertTrue(p.ballY < impact.second)
        assertEquals(1, p.hits)
    }

    @Test fun incomingBallLaunchesWhileTheReturnIsPending() {
        for (seat in 1..2) {
            val p = PongClient(seat)
            val b = if (seat == 1) listOf(.5f, 1.45f, 0f, 3f) else listOf(.5f, .35f, 0f, -3f)
            p.receive(frame(b) { put("l", true); put("drift", 0f) }, "m", ms)
            p.advance(ms); p.paddle(.5f, 1.8f); p.paddle(.5f, 1.3f)
            val impact = p.visibleBallPosition()
            p.advance(30 * ms)
            assertTrue(p.ballY < impact.second)
            assertEquals(1, p.hits)
            assertEquals(if (seat == 1) 4 else 2, p.myScore)
        }
    }

    @Test fun sharedSweepAcceptsFrontSideAndBackButRejectsAnActualGap() {
        val r = PongClient.contactRadius(.16f).toDouble()
        assertNotNull(PongPhysics.sweepContact(.5, .2, .5, 1.4, .5, .8, .5, .8, r, -1.0))
        assertNotNull(PongPhysics.sweepContact(.5, 1.4, .5, .2, .5, .8, .5, .8, r, -1.0))
        assertNotNull(PongPhysics.sweepContact(.1, .8, .9, .8, .5, .8, .5, .8, r, -1.0))
        assertNull(PongPhysics.sweepContact(.9, .2, .9, 1.4, .5, .8, .5, .8, r, -1.0))
        for (height in listOf(0f, .16f, PongClient.ArcH))
            assertEquals(PongClient.HeadR + PongClient.HeadOutline / 2 + (1 + PongPhysics.BallEdge.toFloat()) * PongClient.ballRadius(height),
                PongClient.contactRadius(height), 1e-6f)
    }

    @Test fun malformedFramesAreRejectedAtomicallyAndLegacyFieldsStillWork() {
        val p = PongClient(1)
        assertFalse(p.receive(frame(), "other", ms)); assertFalse(p.live)
        assertTrue(p.receive(frame(), "m", ms))
        val invalid = listOf(
            frame(listOf(1f)), frame(listOf(.5f, Float.NaN, 0f, 0f)),
            frame { put("spin", "broken") }, frame { put("serve", 3) },
            frame { put("drift", "NaN") }, frame { put("t", -1) },
            frame { putJsonArray("s") { add(100); add(0) } }, frame { putJsonArray("a") { add(0) } },
        )
        invalid.forEach { assertFalse(p.receive(it, "m", 2 * ms)) }
        assertEquals(.2f, p.ballX, 0f); assertEquals(4, p.myScore)
        val legacy = JsonObject(frame().filterKeys { it !in setOf("r", "z") })
        assertTrue(p.receive(legacy, "m", 3 * ms)); assertEquals(0f, p.height, 0f)
    }

    @Test fun nativeEndpointDriftIsLinearAndRotatesWithTheWholeFlight() {
        val f = frame { arc(.5f); put("spin", 1f); put("drift", 1.2336f) }
        val one = PongClient(1); val two = PongClient(2)
        one.receive(f, "m", ms); two.receive(f, "m", ms)
        one.advance(101 * ms); two.advance(101 * ms)
        val curve = f["a"]!!.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
        val expected = PongClient.flightPosition(curve, .1f, 1f, 1.2336f)
        assertEquals(expected[0], one.ballX, 1e-5f)
        assertEquals(expected[1], one.ballY, 1e-5f)
        assertEquals(expected[2], one.height, 1e-5f)
        assertEquals(1f, one.ballX + two.ballX, 1e-5f)
        assertEquals(PongClient.CourtH, one.ballY + two.ballY, 1e-5f)
    }

    @Test fun lateSnapshotsDoNotRewindAFlightAndOlderPacketsAreRejected() {
        val p = PongClient(1)
        p.receive(frame { arc(); put("drift", .2f); put("t", 1000) }, "m", ms)
        p.advance(91 * ms)
        val before = listOf(p.ballX, p.ballY, p.height)
        // Server progressed 50 ms; the second packet spent 40 ms longer in transit.
        assertTrue(p.receive(frame { arc(.25f, .71f); put("drift", .2f); put("t", 1050) }, "m", 91 * ms))
        p.advance(91 * ms)
        listOf(p.ballX, p.ballY, p.height).zip(before).forEach { (actual, expected) -> assertEquals(expected, actual, 1e-5f) }
        assertFalse(p.receive(frame { arc(); put("t", 1049); put("h", 99) }, "m", 92 * ms))
        assertEquals(1, p.hits)
    }

    @Test fun racketFollowingHasTheSameResponseAt60And120Hz() {
        val sixty = PongClient.racketFollow(1f / 60)
        val oneTwenty = PongClient.racketFollow(1f / 120)
        assertEquals(.5f, sixty, 1e-5f)
        assertEquals(sixty, 1 - (1 - oneTwenty) * (1 - oneTwenty), 1e-5f)
        val ninety = PongClient.racketFollow(1f / 90)
        assertEquals(.75f, 1 - (1 - ninety) * (1 - ninety) * (1 - ninety), 1e-5f)
        assertEquals(0f, PongClient.racketFollow(0f), 0f)
    }

    @Test fun trailCoversOnlyTheLastThreeTenthsOfASecondAndContinuesThroughContact() {
        val p = PongClient(1)
        p.receive(frame { arc(); put("drift", 0f) }, "m", ms)
        for (i in 1..14) p.advance(i * 10 * ms)
        assertTrue(p.trail.isNotEmpty())
        assertTrue(p.trail.all { 140 * ms - it.atNanos <= 300 * ms })
        val size = p.trail.size
        p.receive(frame { arc(); put("h", 2); put("drift", 0f) }, "m", 141 * ms)
        assertEquals(size, p.trail.size)
        p.advance(700 * ms)
        assertTrue(p.trail.isEmpty())
    }
    @Test fun sweepingTheBatThroughAStationaryBallKeepsTheHistoricalImpact() {
        val p = PongClient(1)
        p.receive(frame(listOf(.5f, 1.6f, 0f, 1f)) { put("l", true); put("drift", 0f) }, "m", ms)
        p.advance(ms); p.paddle(.2f, 2f)
        val before = p.ballX to p.ballY
        p.paddle(.8f, 1.3f)
        assertEquals(before.first, p.ballX, 1e-5f); assertEquals(before.second, p.ballY, 1e-5f)
        assertEquals(1, p.hits)
    }

    @Test fun followThroughKeepsTheDisplayedOutgoingFlightAtAnyShotSpeed() {
        for (speed in listOf(.15f, 1f, 4f)) for (seat in 1..2) {
            val p = PongClient(seat)
            val b = if (seat == 1) listOf(.5f, 1.5f, 0f, -speed) else listOf(.5f, .3f, 0f, speed)
            p.receive(frame(b) { put("l", false); put("drift", 0f) }, "m", ms)
            val before = p.ballX to p.ballY
            p.paddle(.5f, 1.3f)
            assertEquals(before, p.ballX to p.ballY)
            assertEquals(before, p.visibleBallPosition())
            repeat(8) { i ->
                p.advance((2 + i * 10) * ms)
                p.paddle(.5f, 1.3f)
                assertEquals(p.ballX to p.ballY, p.visibleBallPosition())
                assertEquals(before.first, p.ballX, 1e-6f)
                assertEquals(before.second - speed * (1 + i * 10) / 1000, p.ballY, 1e-6f)
            }
        }
    }

    @Test fun anAngledOutgoingFrameIsDrawnOnItsFlightRegardlessOfTheBat() {
        val p = PongClient(1)
        p.receive(frame(listOf(.5f, 1.5f, -1.5f, -1f)) { put("drift", 0f) }, "m", ms)
        p.paddle(.2f, 1.8f)
        p.paddle(.8f, 1.3f)
        for (i in 1..8) {
            p.advance((1 + i * 10) * ms)
            assertEquals(.5f - 1.5f * i * .01f, p.ballX, 1e-5f)
            assertEquals(1.5f - i * .01f, p.ballY, 1e-5f)
            assertEquals(p.ballX to p.ballY, p.visibleBallPosition())
        }
    }

    @Test fun sideAndBackFollowThroughKeepTheSameFlightInRedAndBlueViews() {
        for (seat in 1..2) for (speed in listOf(.15f, 4f))
            for (swipe in listOf(.005f, .15f)) for (side in listOf(-1f, 1f)) {
                val red = PongClient(seat); val blue = PongClient(3 - seat)
                val b = if (seat == 1) listOf(.5f, 1.7f, .2f, -speed) else listOf(.5f, .1f, -.2f, speed)
                val snapshot = frame(b) { put("drift", 0f) }
                for (p in listOf(red, blue)) assertTrue(p.receive(snapshot, "m", ms))
                repeat(8) { i ->
                    val now = (1 + i * 16) * ms
                    red.advance(now); blue.advance(now)
                    red.paddle(red.ballX + side * (.12f - swipe * i), red.ballY - .08f)
                    val shown = red.visibleBallPosition(); val other = blue.visibleBallPosition()
                    assertEquals(red.ballX to red.ballY, shown)
                    assertEquals(1 - other.first, shown.first, 1e-5f)
                    assertEquals(PongClient.CourtH - other.second, shown.second, 1e-5f)
                    assertEquals(1, red.hits)
                    assertEquals(if (seat == 1) 4 else 2, red.myScore)
                }
            }
    }

    @Test fun ballSnapshotsCannotPushTheLocalBatBackAgainstAForwardSwipe() {
        val p = PongClient(1)
        p.receive(frame(listOf(.5f, 1.5f, 0f, -1f)) { put("drift", 0f) }, "m", ms)
        p.paddle(.5f, 1.3f)
        var previousY = p.myY
        for ((i, y) in listOf(1.52f, 1.49f, 1.5f, 1.47f).withIndex()) {
            p.receive(frame(listOf(.5f, y, 0f, -1f)) { put("h", 2); put("drift", 0f) }, "m", (2 + i * 20) * ms)
            p.advance((3 + i * 20) * ms)
            p.paddle(.5f, 1.3f)
            assertTrue("frame=$i moved backwards", p.myY <= previousY + 1e-5f)
            assertEquals(p.ballX to p.ballY, p.visibleBallPosition())
            previousY = p.myY
        }
    }

    @Test fun aHardSwipeAndPredictedBallBothContinueWithoutAHitPacket() {
        for (seat in 1..2) {
            val p = PongClient(seat)
            val b = if (seat == 1) listOf(.5f, 1.6f, 0f, 1f) else listOf(.5f, .2f, 0f, -1f)
            p.receive(frame(b) { put("l", true); put("drift", 0f) }, "m", ms)
            p.advance(ms); p.paddle(.5f, 2.4f)
            var impactY = 0f
            for ((i, y) in listOf(2f, 1.7f, 1.4f, 1.3f).withIndex()) {
                p.paddle(.5f, y); p.advance((2 + i * 16) * ms)
                assertEquals(y, p.myY, 1e-6f)
                if (i == 1) impactY = p.ballY
                if (i > 1) assertTrue(p.ballY < impactY)
            }
            assertEquals(1, p.hits)
        }
    }

    @Test fun aPendingCornerLaunchIsIndependentOfFollowThrough() {
        for (side in listOf(-1f, 1f)) {
            val p = PongClient(1)
            p.receive(frame(listOf(.5f, 1.6f, 0f, 1f)) { put("l", true); put("drift", 0f) }, "m", ms)
            p.advance(ms); p.paddle(.5f + side * .12f, 2f); p.paddle(.5f + side * .12f, 1.6f)
            val impact = p.visibleBallPosition()
            p.paddle(.5f - side * .15f, 1.3f)
            assertEquals(impact, p.visibleBallPosition())
            p.advance(20 * ms)
            assertTrue(p.ballY < impact.second)
            val beforeConfirmation = p.visibleBallPosition()
            p.receive(frame(listOf(.5f, 1.4f, 0f, -1f)) { put("h", 2); put("drift", 0f) }, "m", 20 * ms)
            assertEquals(beforeConfirmation, p.visibleBallPosition())
            p.advance(20 * ms); p.advance(80 * ms)
            val flight = p.visibleBallPosition()
            p.paddle(.35f, 1.3f); assertEquals(flight, p.visibleBallPosition())
        }
    }

    @Test fun movingTheRedBatAroundTheBallCannotOrbitItsDisplayedFlight() {
        for (seat in 1..2) for (height in listOf(0f, .16f, PongClient.ArcH)) for (angle in 0 until 360 step 30) {
            val p = PongClient(seat)
            val b = if (seat == 1) listOf(.5f, 1.7f, 0f, 0f) else listOf(.5f, .1f, 0f, 0f)
            p.receive(frame(b) { putJsonArray("z") { add(height); add(0f) } }, "m", ms)
            val radians = angle * kotlin.math.PI / 180
            val x = .5f + .05f * kotlin.math.cos(radians).toFloat()
            val y = 1.7f + .05f * kotlin.math.sin(radians).toFloat()
            val flight = p.ballX to p.ballY
            p.paddle(x, y)
            assertEquals("seat=$seat, height=$height, angle=$angle", flight, p.visibleBallPosition())
            assertEquals(x to y, p.myX to p.myY)
            assertEquals(flight, p.ballX to p.ballY)
        }
    }

    @Test fun predictionDoesNotFreezeOrExpireBackToAnIncomingFlight() {
        val p = PongClient(1)
        p.receive(frame(listOf(.5f, 1.6f, 0f, 1f)) { put("l", true); put("drift", 0f) }, "m", ms)
        p.advance(ms); p.paddle(.5f, 2.4f); p.paddle(.5f, 1.6f)
        val impact = p.visibleBallPosition()
        p.advance(100 * ms)
        val a = p.ballY
        p.advance(200 * ms)
        assertTrue(a < impact.second && p.ballY < a)
        assertEquals(1, p.hits)
        val before = p.visibleBallPosition()
        p.receive(frame(listOf(.5f, 1.3f, 0f, -1f)) { put("h", 2); put("drift", 0f) }, "m", 201 * ms)
        assertEquals(before, p.visibleBallPosition())
        assertEquals(2, p.hits)
    }

    @Test fun redHandleCannotDisplaceAnOutgoingBallBehindTheHead() {
        for (height in listOf(0f, .16f, PongClient.ArcH)) for (dx in listOf(0f, .05f, .08f))
            for (dy in listOf(.18f, .25f, .3f)) {
                val p = PongClient(1)
                p.receive(frame(listOf(.5f + dx, 1.4f + dy, 0f, -1f)) {
                    putJsonArray("z") { add(height); add(0f) }
                }, "m", ms)
                p.paddle(.5f, 1.4f)
                assertEquals(p.ballX to p.ballY, p.visibleBallPosition())
                assertEquals(.5f + dx to 1.4f + dy, p.ballX to p.ballY)
            }
    }

    @Test fun aDelayedSnapshotCannotStopOrOrbitAPredictedReturn() {
        val p = PongClient(1)
        val incoming = frame(listOf(.5f, 1.6f, 0f, 1f)) { put("l", true); put("drift", 0f) }
        p.receive(incoming, "m", ms); p.advance(ms)
        p.paddle(.5f, 2f); p.paddle(.5f, 1.6f)
        val impact = p.visibleBallPosition()
        p.paddle(impact.first, impact.second)
        assertTrue(p.receive(incoming, "m", 33 * ms))
        p.advance(33 * ms)
        val a = p.ballY
        p.advance(152 * ms)
        assertTrue(a < impact.second && p.ballY < a)
        assertEquals(1, p.hits)
    }

}
