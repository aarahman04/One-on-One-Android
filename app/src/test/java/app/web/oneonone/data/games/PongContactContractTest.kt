package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PongContactContractTest {
    private fun incoming() = buildJsonObject {
        put("id", "m"); put("t", 1000); put("h", 1); put("c", 0); put("l", true); put("drift", 0)
        putJsonArray("b") { add(.5); add(1.6); add(0); add(1) }
        putJsonArray("p") { add(.5); add(.5) }; putJsonArray("r") { add(2.4); add(-.08) }
        putJsonArray("s") { add(0); add(0) }; putJsonArray("z") { add(.033); add(0) }
    }

    @Test fun aPredictedReturnLaunchesInsteadOfFreezingOnTheRim() {
        val p = PongClient(1)
        p.receive(incoming(), "m", 1_000_000_000); p.advance(1_000_000_000)
        p.paddle(.5f, 2.4f); p.paddle(.5f, 1.6f)
        val impact = p.visibleBallPosition()
        p.advance(1_020_000_000)
        assertTrue("predicted flight must leave contact immediately", p.visibleBallPosition().second < impact.second)
        assertEquals("prediction cannot award a hit", 1, p.hits)
        assertEquals(0, p.myScore)
    }

    @Test fun aPassiveLocalReturnUsesTheIncomingBallSpinLikeTheServer() {
        fun returned(side: Double): PongClient {
            val p = PongClient(1)
            p.receive(JsonObject(incoming() + buildJsonObject { putJsonArray("sp") { add(side); add(0) } }), "m", 1_000_000_000)
            p.advance(1_000_000_000)
            p.paddle(.5f, 2f, 1_000_000_000); p.paddle(.5f, 1.75f, 1_100_000_000) // a slow, mostly passive push
            p.advance(1_400_000_000)
            return p
        }
        val plain = returned(0.0); val spun = returned(1.0)
        assertEquals(0f, spun.spinTop, 1e-6f) // the prediction's own stroke spin (2.5 widths/s: no topspin)
        assertTrue("incoming sidespin deflects a passive block toward +x", spun.ballX > plain.ballX + 1e-3f)
    }

    @Test fun backHalfContactAlsoPredictsAForwardLaunch() {
        val p = PongClient(1)
        val data = JsonObject(incoming() + buildJsonObject { putJsonArray("r") { add(1.3); add(-.08) } })
        p.receive(data, "m", 1_000_000_000); p.advance(1_000_000_000)
        p.paddle(.5f, 1.6f)
        val impact = p.visibleBallPosition()
        p.advance(1_020_000_000)
        assertTrue("back-half contact must launch forward", p.visibleBallPosition().second < impact.second)
    }

    @Test fun baselineHarnessDisplayMatchesKotlinGoldenTrace() {
        val trace = Json.parseToJsonElement(javaClass.getResource("/pong-display-baseline.json")!!.readText()).jsonArray
        val p = PongClient(1)
        for (item in trace) {
            val row = item.jsonObject
            val data = JsonObject(row["frame"]!!.jsonObject + ("id" to JsonPrimitive("m")))
            assertTrue(p.receive(data, "m", row["at"]!!.jsonPrimitive.long * 1_000_000))
            for (draw in row["draws"]!!.jsonArray) {
                p.advance(draw.jsonObject["at"]!!.jsonPrimitive.long * 1_000_000)
                val expected = draw.jsonObject["xyz"]!!.jsonArray
                listOf(p.ballX, p.ballY, p.height).forEachIndexed { i, x -> assertEquals(expected[i].jsonPrimitive.float, x, 1e-5f) }
            }
        }
    }

    @Test fun compensatedRenderingReportsTheTimeOfTheDrawnBall() {
        val p = PongClient(1)
        fun at(t: Int, y: Double) = JsonObject(incoming() + buildJsonObject {
            put("t", t); putJsonArray("b") { add(.5); add(y); add(0); add(1) }
            putJsonArray("ack") { add(0); add(0) }
        })
        p.receive(at(1000, 1.6), "m", 1_040_000_000)
        p.advance(1_070_000_000)
        assertEquals(1000.0, p.displayTimeMs, 1e-5) // Startup cannot interpolate before the first frame.
        assertEquals(1.6f, p.ballY, 1e-5f)
        p.receive(at(1033, 1.633), "m", 1_073_000_000)
        p.advance(1_113_000_000)
        assertEquals(1023.0, p.displayTimeMs, 1e-5)
        assertEquals(1.623f, p.ballY, 1e-5f)
    }

    @Test fun aNewFrameCannotChangeEligibilityBeforeItIsDrawn() {
        val p = PongClient(1)
        fun data(t: Int, landed: Boolean) = JsonObject(incoming() + buildJsonObject {
            put("t", t); put("l", landed); putJsonArray("ack") { add(0); add(0) }
        })
        p.receive(data(1000, false), "m", 1_000_000_000); p.advance(1_000_000_000)
        assertFalse(p.contactEligible)
        p.receive(data(1033, true), "m", 1_033_000_000)
        assertFalse("pointer events must use the eligibility that was drawn", p.contactEligible)
        p.advance(1_100_000_000)
        assertTrue(p.contactEligible)
    }

    @Test fun rejectingAPredictionBlendsAndAllowsAnotherAttempt() {
        val p = PongClient(1)
        fun ack(n: Long) = JsonObject(incoming() + buildJsonObject { putJsonArray("ack") { add(n); add(0) } })
        p.receive(ack(0), "m", 1_000_000_000); p.advance(1_000_000_000)
        p.paddle(.5f, 1.6f, 1_000_000_000)
        p.advance(1_020_000_000)
        val before = p.visibleBallPosition()
        p.receive(ack(p.sampleN), "m", 1_020_000_000)
        assertEquals(before, p.visibleBallPosition())
        p.advance(1_020_000_000); assertEquals(before, p.visibleBallPosition())
        p.advance(1_080_000_000)
        assertTrue(p.contactEligible)
        p.paddle(.5f, 2.4f, 1_080_000_000); p.paddle(.5f, 1.6f, 1_081_000_000)
        val impact = p.ballY
        p.advance(1_100_000_000)
        assertTrue(p.ballY < impact)
        assertEquals(1, p.hits)
    }

    @Test fun predictedCurveIsFixedAtContactWhateverTheBatDoesAfterwards() {
        fun client() = PongClient(1).also {
            it.receive(incoming(), "m", 1_000_000_000); it.advance(1_000_000_000)
            it.paddle(.2f, 2f, 1_000_000_000)
            it.paddle(.8f, 1.4f, 1_016_000_000)
            it.advance(1_080_000_000)
        }
        val released = client(); val swiping = client(); val held = client()
        released.paddle(released.myX, released.myY, 1_080_000_000, released = true)
        for (i in 1..20) {
            val at = 1_080_000_000L + i * 16_000_000L
            swiping.paddle(if (i % 2 == 0) .1f else 1.1f, 1.4f, at)
            held.paddle(held.myX, held.myY, at)
            listOf(released, swiping, held).forEach { it.advance(at) }
            assertEquals(released.ballX, swiping.ballX, 1e-6f); assertEquals(released.ballY, swiping.ballY, 1e-6f)
            assertEquals(released.ballX, held.ballX, 1e-6f)
        }
    }

    @Test fun pairedHarnessActionsMatchTheRealKotlinDisplay() {
        val traces = Json.parseToJsonElement(javaClass.getResource("/pong-display-traces.json")!!.readText()).jsonArray
        for (trace in traces) {
            val p = PongClient(1)
            for ((index, action) in trace.jsonObject["actions"]!!.jsonArray.withIndex()) {
                val a = action.jsonObject
                val at = a["at"]!!.jsonPrimitive.double
                val nanos = (at * 1_000_000).toLong()
                when (a["type"]!!.jsonPrimitive.content) {
                    "receive" -> assertTrue("frame $index", p.receive(JsonObject(a["frame"]!!.jsonObject + ("id" to JsonPrimitive("m"))), "m", nanos))
                    "advance" -> p.advance(nanos)
                    "paddle" -> p.paddle(a["x"]!!.jsonPrimitive.float, a["y"]!!.jsonPrimitive.float, nanos, released = a["released"]!!.jsonPrimitive.boolean)
                }
                val expected = a["expected"]!!.jsonArray
                listOf(p.ballX.toDouble(), p.ballY.toDouble(), p.height.toDouble(), p.displayTimeMs).forEachIndexed { i, value ->
                    assertEquals("latency=${trace.jsonObject["latency"]} style=${trace.jsonObject["style"]} action=$index ${a["type"]} component=$i",
                        expected[i].jsonPrimitive.double, value, if (i == 3) .001 else .0001)
                }
            }
        }
    }
}
