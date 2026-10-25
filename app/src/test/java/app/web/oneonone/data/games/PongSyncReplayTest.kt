package app.web.oneonone.data.games

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.math.hypot

/**
 * Replays scripts/pong-sync-trace.ts: two players rallying against the real backend PongSim over 50-300 ms RTT links
 * with jitter and frame loss. Each seat's received frames and touches drive the real PongClient at 60 Hz, and what it
 * draws is measured. "legacy" strips the additive `pv` field, which is how the display behaved before.
 */
class PongSyncReplayTest {
    private data class Result(val gaps: List<Float>, val jumps: Int, val batJumps: Int, val earlyPoints: Int, val scores: List<Pair<Int, Int>>)

    private val traces by lazy {
        Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/pong-sync-traces.json.gz")!!).reader().readText()).jsonArray
    }

    private fun replay(seatTrace: JsonObject, legacy: Boolean, hz: Int = 60): Result {
        val seat = seatTrace["seat"]!!.jsonPrimitive.int
        val p = PongClient(seat)
        val frames = seatTrace["frames"]!!.jsonArray.map { it.jsonObject }
        val inputs = seatTrace["inputs"]!!.jsonArray.map { it.jsonObject }
        var fi = 0; var ii = 0
        val gaps = mutableListOf<Float>()
        var jumps = 0; var batJumps = 0; var earlyPoints = 0
        var lastX = Float.NaN; var lastY = Float.NaN; var lastTheirX = Float.NaN; var lastTheirY = Float.NaN
        var falling = false; var round = p.round; var score = 0 to 0
        val scores = mutableListOf<Pair<Int, Int>>()
        val step = 1000.0 / hz
        val start = frames.first()["at"]!!.jsonPrimitive.double
        var now = start
        val end = frames.last()["at"]!!.jsonPrimitive.double + 1000 // let the delayed timeline draw the last frames
        while (now <= end) {
            while (true) {
                val f = frames.getOrNull(fi)?.takeIf { it["at"]!!.jsonPrimitive.double <= now }
                val i = inputs.getOrNull(ii)?.takeIf { it["at"]!!.jsonPrimitive.double <= now }
                if (f == null && i == null) break
                if (f != null && (i == null || f["at"]!!.jsonPrimitive.double <= i["at"]!!.jsonPrimitive.double)) {
                    val data = f["f"]!!.jsonObject.let { if (legacy) JsonObject(it - "pv") else it }
                    assertTrue(p.receive(data, "m", (f["at"]!!.jsonPrimitive.double * 1e6).toLong())); fi++
                } else {
                    val x = i!!["x"]!!.jsonPrimitive.float; val y = i["y"]!!.jsonPrimitive.float
                    val vx = if (seat == 1) x else 1 - x; val vy = if (seat == 1) y else PongClient.CourtH - y
                    p.paddle(vx, vy, (i["at"]!!.jsonPrimitive.double * 1e6).toLong(), vx to vy); ii++
                }
            }
            p.advance((now * 1e6).toLong())
            val reset = p.round != round
            if (p.myScore to p.theirScore != score) {
                // The ball must visibly get past the player who lost the point before the score changes.
                val lost = if (p.myScore > score.first) lastY > .3f else lastY < PongClient.CourtH - .3f
                if (lost && !lastY.isNaN()) earlyPoints++
                score = p.myScore to p.theirScore; scores += score
            }
            // The first 1.5 s is the serve countdown, where the opponent's untimed start position is replaced.
            if (!lastX.isNaN() && !reset && now - start > 1500) {
                val moved = hypot(p.ballX - lastX, p.ballY - lastY)
                if (moved > .12f * 60 / hz) jumps++
                // The ball turns back toward me on the opponent's side: how far was it from their drawn racket?
                if (falling && p.ballY > lastY + 1e-4f && lastY < .9f)
                    gaps += hypot(lastX - lastTheirX, lastY - lastTheirY) - PongClient.contactRadius(p.height)
                if (hypot(p.theirX - lastTheirX, p.theirY - lastTheirY) > .2f * 60 / hz) batJumps++
            }
            if (!reset && !lastY.isNaN()) falling = p.ballY < lastY - 1e-4f || falling && p.ballY <= lastY + 1e-4f
            if (reset) falling = false
            lastX = p.ballX; lastY = p.ballY; lastTheirX = p.theirX; lastTheirY = p.theirY; round = p.round
            now += step
        }
        return Result(gaps, jumps, batJumps, earlyPoints, scores)
    }

    private fun List<Float>.pct(q: Double) = if (isEmpty()) 0f else sorted()[((size - 1) * q).toInt()]

    @Test fun bothPhonesSeeTheOpponentStrikeTheBallAndAgreeOnEveryPoint() {
        val failures = mutableListOf<String>()
        val report = StringBuilder("profile seat mode    turns gapMedian gapP95 gapMax jumps batJumps earlyPoints\n")
        for (trace in traces) {
            val t = trace.jsonObject
            val points = t["truth"]!!.jsonObject["points"]!!.jsonArray.map { it.jsonObject["s"]!!.jsonArray.map { s -> s.jsonPrimitive.int } }
            var legacyResult: Result? = null
            for (seatTrace in t["seats"]!!.jsonArray) for (legacy in listOf(true, false)) {
                val seat = seatTrace.jsonObject["seat"]!!.jsonPrimitive.int
                val r = replay(seatTrace.jsonObject, legacy)
                report.append("%-7s %4d %-7s %5d %9.3f %6.3f %6.3f %5d %8d %11d\n".format(t["profile"]!!.jsonPrimitive.content, seat,
                    if (legacy) "legacy" else "pv", r.gaps.size, r.gaps.pct(.5), r.gaps.pct(.95), r.gaps.maxOrNull() ?: 0f, r.jumps, r.batJumps, r.earlyPoints))
                // Server-authoritative: both phones show every one of the server's points, in order, from their own side.
                assertEquals(points.map { it[seat - 1] to it[2 - seat] }, r.scores)
                if (!legacy) {
                    val name = "${t["profile"]} seat $seat"
                    val extreme = t["rtt"]!!.jsonPrimitive.int >= 300 // 300 ms RTT, 5% frame loss: bounded, and better than before
                    if (r.gaps.pct(.95) > if (extreme) .07f else .03f) failures += "$name: opponent hits drawn away from their racket"
                    if (r.jumps > if (extreme) 1 else 0) failures += "$name: ball jumps"
                    if (r.batJumps > if (extreme) 2 else 0) failures += "$name: opponent racket teleports"
                    if (r.earlyPoints != 0) failures += "$name: score before the miss was visible"
                    val before = legacyResult!!
                    if (r.jumps > before.jumps || r.gaps.pct(.95) > maxOf(before.gaps.pct(.95), .03f)) failures += "$name: not better than before"
                } else legacyResult = r
            }
        }
        println(report)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun drawingAt120HzKeepsTheSameAgreement() {
        val t = traces.last().jsonObject
        for (seatTrace in t["seats"]!!.jsonArray) {
            val r = replay(seatTrace.jsonObject, legacy = false, hz = 120)
            assertTrue(r.gaps.pct(.95) <= .07f); assertTrue(r.jumps <= 1); assertEquals(0, r.earlyPoints)
        }
    }
}
