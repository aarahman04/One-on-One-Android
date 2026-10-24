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
}
