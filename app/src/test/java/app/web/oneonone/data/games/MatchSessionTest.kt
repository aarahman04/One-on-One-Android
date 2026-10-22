package app.web.oneonone.data.games

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

private class FakeChannel : MatchChannel {
    override val states = MutableSharedFlow<JsonObject>(extraBufferCapacity = 16)
    override val frames = MutableSharedFlow<JsonObject>(extraBufferCapacity = 16)
    override val reactions = MutableSharedFlow<JsonObject>(extraBufferCapacity = 16)
    val calls = mutableListOf<Pair<String, JsonObject>>()
    val paddles = mutableListOf<Float>()
    val releases = mutableListOf<Boolean>()
    val paddleY = mutableListOf<Float?>()
    val touches = mutableListOf<Pair<Float, Float>?>()
    var reply: suspend (String, JsonObject) -> JsonObject = { _, _ -> buildJsonObject { put("ok", true) } }
    override suspend fun call(event: String, payload: JsonObject): JsonObject { calls += event to payload; return reply(event, payload) }
    override fun paddle(matchId: String, x: Float, y: Float?, released: Boolean, touch: Pair<Float, Float>?) {
        paddles += x; paddleY += y; releases += released; touches += touch
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MatchSessionTest {
    private fun TestScope.session(): Pair<MatchSession, FakeChannel> {
        val channel = FakeChannel()
        return MatchSession(channel, backgroundScope) { testScheduler.currentTime * 1_000_000 }.also { it.reset("me"); runCurrent() } to channel
    }

    @Test fun reactionsAreSentCleanedAndOnlyShownForTheMatchOnScreen() = runTest {
        val (s, ch) = session()
        val seen = mutableListOf<GameReaction>()
        backgroundScope.launch { s.reactions.collect { seen += it } }
        s.react("hi"); runCurrent()
        assertTrue("no match, nothing sent", ch.calls.isEmpty())
        ch.states.emit(matchJson()); runCurrent()
        s.react("  Nice\n  move!  "); runCurrent()
        assertEquals("match:react", ch.calls.single().first)
        assertEquals("Nice move!", ch.calls.single().second["text"]!!.jsonPrimitive.content)
        s.react("   "); s.react("x".repeat(61)); runCurrent()
        assertEquals(1, ch.calls.size)
        fun reaction(id: String, matchId: String = "m1", from: String = "other", text: String = "🔥") =
            buildJsonObject { put("id", id); put("matchId", matchId); put("from", from); put("text", text) }
        ch.reactions.emit(reaction("r1")); ch.reactions.emit(reaction("r2", matchId = "old"))
        ch.reactions.emit(reaction("r3", from = "stranger")); ch.reactions.emit(reaction("r4", text = "y".repeat(61)))
        ch.reactions.emit(reaction("r5", from = "me", text = "GG")); runCurrent()
        assertEquals(listOf("r1", "r5"), seen.map { it.id })
        assertEquals(GameReaction("r1", "other", "🔥"), seen.first())
    }

    @Test fun commentFieldKeepsOneLineAndCountsEmojiOnce() {
        assertEquals("a b", app.web.oneonone.ui.games.limitReaction("a\nb"))
        assertEquals(REACTION_MAX_CHARS, app.web.oneonone.ui.games.limitReaction("x".repeat(80)).length)
        val emoji = app.web.oneonone.ui.games.limitReaction("👍".repeat(70))
        assertEquals(REACTION_MAX_CHARS, emoji.codePointCount(0, emoji.length))
    }

    @Test fun throttledReactionIsQuietButOtherFailuresShow() = runTest {
        val (s, ch) = session()
        val errors = mutableListOf<String>()
        backgroundScope.launch { s.errors.collect { errors += it } }
        ch.states.emit(matchJson()); runCurrent()
        ch.reply = { _, _ -> buildJsonObject { put("ok", false); put("error", "rate_limited") } }
        s.react("🔥"); runCurrent()
        assertTrue(errors.isEmpty())
        ch.reply = { _, _ -> throw IOException("down") }
        s.react("🔥"); runCurrent()
        assertEquals(listOf("network"), errors)
    }

    @Test fun keepsNewestSnapshotAndIgnoresStrangers() = runTest {
        val (s, ch) = session()
        ch.states.emit(matchJson(version = 3, seq = 2)); runCurrent()
        ch.states.emit(matchJson(version = 2, seq = 1)); runCurrent()
        assertEquals(2, s.match.value!!.seq)
        ch.states.emit(matchJson(id = "x", players = listOf("a", "b"), inviter = "a", turn = "a")); runCurrent()
        assertEquals("m1", s.match.value!!.id)
        ch.states.emit(matchJson(id = "m2", version = 1)); runCurrent() // a new match replaces the old one
        assertEquals("m2", s.match.value!!.id)
    }

    @Test fun clockUsesSnapshotReceiptIncludingSameVersionSyncAndScreenReentry() = runTest {
        val (s, ch) = session()
        fun timed(left: Long) = JsonObject(matchJson() + buildJsonObject {
            putJsonObject("clock") { put("base", 60_000); put("inc", 0); putJsonArray("left") { add(left); add(60_000) } }
        })
        ch.states.emit(timed(60_000)); runCurrent()
        advanceTimeBy(5_000)
        assertEquals(listOf(55_000L, 60_000L), s.match.value!!.clockLeft(testScheduler.currentTime * 1_000_000))
        ch.states.emit(timed(55_000)); runCurrent()
        assertEquals(listOf(55_000L, 60_000L), s.match.value!!.clockLeft(testScheduler.currentTime * 1_000_000))
        advanceTimeBy(2_000)
        val m = s.match.value!!
        assertEquals(listOf(53_000L, 60_000L), m.clockLeft(testScheduler.currentTime * 1_000_000))
        assertEquals(listOf(55_000L, 60_000L), m.clockLeft(m.receivedAtNanos - 1))
        assertEquals(listOf(55_000L, 60_000L), m.copy(status = MatchStatus.Over).clockLeft(Long.MAX_VALUE))
        assertEquals(listOf(0L, 60_000L), m.clockLeft(Long.MAX_VALUE))
    }

    @Test fun backgroundSyncFailuresStayQuietButActionsReportThem() = runTest {
        val (s, ch) = session()
        val errors = mutableListOf<String>()
        backgroundScope.launch { s.errors.collect { errors += it } }; runCurrent()
        ch.reply = { _, _ -> throw IOException("offline") }
        s.sync(); runCurrent()
        ch.reply = { _, _ -> buildJsonObject { put("ok", false); put("error", "rate_limited") } }
        s.sync(); runCurrent()
        assertEquals(emptyList<String>(), errors)
        s.invite(GameKind.Chess); runCurrent()
        assertEquals(listOf("rate_limited"), errors)
    }

    @Test fun moveSendsSeqLocksBoardAndRetriesOnceAfterLostAck() = runTest {
        val (s, ch) = session()
        ch.states.emit(matchJson(seq = 4)); runCurrent()
        var failures = 1
        ch.reply = { _, _ -> if (failures-- > 0) throw IOException("timeout") else buildJsonObject { put("ok", true); put("match", matchJson(seq = 5, version = 2, turn = "other")) } }
        s.move(buildJsonObject { put("uci", "e2e4") })
        assertTrue(s.busy.value)
        s.move(buildJsonObject { put("uci", "d2d4") }) // ignored while locked
        runCurrent()
        assertEquals(2, ch.calls.size)
        assertEquals(ch.calls[0].second, ch.calls[1].second) // same seq: the server de-duplicates
        assertEquals(4, ch.calls[0].second["seq"]!!.jsonPrimitive.int)
        assertFalse(s.busy.value)
        assertEquals("other", s.match.value!!.turn)
        s.move(buildJsonObject { put("uci", "d7d5") }) // not my turn
        runCurrent()
        assertEquals(2, ch.calls.size)
    }

    @Test fun rejectedMoveReportsCodeAndAppliesServerState() = runTest {
        val (s, ch) = session()
        ch.states.emit(matchJson()); runCurrent()
        val errors = mutableListOf<String>()
        backgroundScope.launch { s.errors.collect { errors += it } }
        ch.reply = { _, _ -> buildJsonObject { put("ok", false); put("error", "illegal_move"); put("match", matchJson(version = 2)) } }
        s.move(buildJsonObject { put("uci", "e2e5") }); runCurrent()
        assertEquals(listOf("illegal_move"), errors)
        assertEquals(2, s.match.value!!.version)
    }

    @Test fun syncWithoutServerMatchDropsALiveGameButKeepsAResult() = runTest {
        val (s, ch) = session()
        val errors = mutableListOf<String>()
        backgroundScope.launch { s.errors.collect { errors += it } }
        ch.reply = { _, _ -> buildJsonObject { put("ok", true); put("match", JsonNull) } }
        ch.states.emit(matchJson(status = "over", turn = null, outcome = outcome("me", "checkmate"))); runCurrent()
        s.sync(); runCurrent()
        assertNotNull(s.match.value)
        ch.states.emit(matchJson(id = "m2")); runCurrent()
        s.sync(); runCurrent()
        assertNull(s.match.value)
        assertEquals(listOf("match_gone"), errors)
    }

    @Test fun paddleIsThrottledAndAlwaysSendsTheLatest() = runTest {
        val (s, ch) = session()
        ch.states.emit(matchJson(game = "pong", turn = null)); runCurrent()
        assertNotNull(s.pong)
        s.paddle(.3f, 1.9f); s.paddle(.4f, 1.9f); s.paddle(.5f, 1.9f, touch = .8f to 1.7f); runCurrent()
        assertEquals(listOf(.5f), ch.paddles) // a burst collapses to the latest position
        assertEquals(listOf(.8f to 1.7f), ch.touches)
        s.paddle(.6f, 2f); s.paddle(.7f, 2.1f); runCurrent()
        assertEquals(listOf(.5f), ch.paddles) // within the interval: held
        advanceTimeBy(MatchSession.PaddleIntervalMs + 1); runCurrent()
        assertEquals(listOf(.5f, .7f), ch.paddles)
        assertEquals(listOf(1.9f, 2.1f), ch.paddleY)
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, ch.paddles.size) // nothing new, nothing sent
        ch.states.emit(matchJson(game = "pong", turn = null, version = 2, paused = true)); runCurrent()
        s.paddle(.6f, 1.9f); advanceTimeBy(100); runCurrent()
        assertEquals(2, ch.paddles.size) // paused: no input
    }

    @Test fun actionsCarryTheMatchId() = runTest {
        val (s, ch) = session()
        s.invite(GameKind.Dots); runCurrent()
        assertEquals("match:invite" to "dots", ch.calls.last().first to ch.calls.last().second["game"]!!.jsonPrimitive.content)
        ch.states.emit(matchJson(game = "dots")); runCurrent()
        s.resign(); s.draw("offer"); s.away(true); runCurrent()
        assertEquals(listOf("match:resign", "match:draw", "match:away"), ch.calls.drop(1).map { it.first })
        assertTrue(ch.calls.drop(1).all { it.second["matchId"]!!.jsonPrimitive.content == "m1" })
    }

    @Test fun inviteCarriesTheChosenClockOrTargetScore() = runTest {
        val (s, ch) = session()
        for (kind in listOf(GameKind.Chess, GameKind.TicTacToe, GameKind.Connect4, GameKind.Reversi)) {
            s.invite(kind, MatchOptions(clock = 180_000L to 2_000L)); runCurrent()
            assertEquals(kind.wire, ch.calls.last().second["game"]!!.jsonPrimitive.content)
            assertEquals(buildJsonObject { put("base", 180_000L); put("inc", 2_000L) }, ch.calls.last().second["clock"])
        }
        s.invite(GameKind.Pong, MatchOptions(points = 21)); runCurrent()
        assertEquals(21, ch.calls.last().second["points"]!!.jsonPrimitive.int)
        assertNull(ch.calls.last().second["clock"])
        s.invite(GameKind.TicTacToe); runCurrent()
        assertNull(ch.calls.last().second["clock"]) // no limit: no clock sent
    }

    @Test fun releaseFlushesTheHeldSegmentAndFinalMovementBeforeANewGesture() = runTest {
        val (s, ch) = session()
        ch.states.emit(matchJson(game = "pong", turn = null)); runCurrent()
        s.paddle(.3f, 1.9f); runCurrent()
        s.paddle(.6f, 1.9f)
        s.paddle(.8f, 1.5f, released = true); runCurrent()
        assertEquals(listOf(.3f, .6f, .8f), ch.paddles)
        assertEquals(listOf(false, false, true), ch.releases)
        advanceTimeBy(MatchSession.PaddleIntervalMs + 1); runCurrent()
        assertEquals(listOf(.3f, .6f, .8f), ch.paddles)
        // A new gesture cannot erase the release boundary.
        s.paddle(.6f, 1.9f, released = true)
        s.paddle(.7f, 1.9f)
        advanceTimeBy(MatchSession.PaddleIntervalMs + 1); runCurrent()
        assertEquals(.7f, ch.paddles.last(), 0f)
        assertFalse(ch.releases.last())
    }

    @Test fun queuedPaddleInputIsDiscardedOnPauseResultRematchAndReset() = runTest {
        for (next in listOf(
            matchJson(game = "pong", turn = null, version = 2, paused = true),
            matchJson(game = "pong", status = "over", turn = null, version = 2, outcome = outcome("me", "first_to_7")),
            matchJson(id = "rematch", game = "pong", turn = null),
        )) {
            val (s, ch) = session()
            ch.states.emit(matchJson(game = "pong", turn = null)); runCurrent()
            s.paddle(.3f, 1.9f); runCurrent()
            s.paddle(.6f, 1.9f)
            ch.states.emit(next); runCurrent()
            advanceTimeBy(100); runCurrent()
            assertEquals(listOf(.3f), ch.paddles)
        }
        val (s, ch) = session()
        ch.states.emit(matchJson(game = "pong", turn = null)); runCurrent()
        s.paddle(.3f, 1.9f); runCurrent()
        s.paddle(.6f, 1.9f); s.reset("me")
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf(.3f), ch.paddles)
    }
}
