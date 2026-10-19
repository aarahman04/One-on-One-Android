package app.web.oneonone.ui.games

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.FakeSession
import app.web.oneonone.data.FakeTransport
import app.web.oneonone.data.MemoryMessageStore
import app.web.oneonone.data.connection
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.games.*
import app.web.oneonone.data.model.PresenceUpdate
import app.web.oneonone.data.realtime.ConnectionState
import app.web.oneonone.data.transport.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

private class RelayTransport(val chat: FakeTransport = FakeTransport()) : Transport by chat {
    override val gameEvents = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val sent = mutableListOf<Triple<String, String, JsonObject>>()
    var inviteAck: suspend () -> Unit = {}
    var acceptAck: suspend () -> Unit = {}
    var moveAck: suspend () -> Unit = {}
    override suspend fun gameInvite(gameId: String, game: String) { sent += Triple("game:invite", gameId, buildJsonObject { put("game", game) }); inviteAck() }
    override suspend fun gameAccept(gameId: String) { sent += Triple("game:accept", gameId, buildJsonObject {}); acceptAck() }
    override suspend fun gameDecline(gameId: String) { sent += Triple("game:decline", gameId, buildJsonObject {}) }
    override suspend fun gameEnd(gameId: String, reason: String) { sent += Triple("game:end", gameId, buildJsonObject { put("reason", reason) }) }
    override suspend fun gameMove(gameId: String, seq: Int, data: JsonElement) {
        sent += Triple("game:move", gameId, buildJsonObject { put("seq", seq); put("data", data) }); moveAck()
    }
    suspend fun event(name: String, id: String, payload: JsonObject = buildJsonObject {}, from: String = "other") {
        gameEvents.emit(GameEvent(name, id, from, payload))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val models = mutableListOf<GameViewModel>()
    @Before fun main() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private data class Fixture(val vm: GameViewModel, val relay: RelayTransport, val messages: MessageService, val saved: SavedStateHandle)
    private suspend fun TestScope.fixture(online: Boolean = true, saved: SavedStateHandle = SavedStateHandle()): Fixture {
        val relay = RelayTransport()
        val messages = MessageService(relay, MemoryMessageStore(), FakeSession(), Json { ignoreUnknownKeys = true }, backgroundScope)
        messages.activate(connection("active", true)); runCurrent()
        if (online) relay.chat.presence.emit(PresenceUpdate("other", true))
        runCurrent()
        val vm = GameViewModel(GameService(relay, messages), saved).also { models += it }
        vm.open(connection("active", true).copy(otherNickname = "Alex")); runCurrent()
        return Fixture(vm, relay, messages, saved)
    }
    private suspend fun TestScope.playing(kind: GameKind = GameKind.TicTacToe): Fixture {
        val f = fixture(); f.vm.invite(kind); runCurrent()
        f.relay.event("game:accept", f.vm.ui.value.id); runCurrent()
        assertEquals(GameStage.Playing, f.vm.ui.value.stage)
        return f
    }

    @Test fun commandRequiresPresenceAndConnectedSocket() = runTest {
        val (vm, relay) = fixture(online = false)
        vm.hub(); assertEquals("Games need Alex to be online.", vm.error.value)
        assertEquals(GameStage.Idle, vm.ui.value.stage)
        relay.chat.presence.emit(PresenceUpdate("other", true)); runCurrent()
        vm.hub(); assertEquals(GameStage.Hub, vm.ui.value.stage)
        vm.dismissHub(); relay.chat.state.value = ConnectionState.Offline; runCurrent()
        vm.hub(); assertEquals(GameStage.Idle, vm.ui.value.stage)
        assertTrue(relay.sent.isEmpty())
    }
    @Test fun inviteCancelAndLateAcceptCannotStartGame() = runTest {
        val (vm, relay) = fixture()
        vm.invite(GameKind.Chess); runCurrent()
        val id = vm.ui.value.id
        assertTrue(id.matches(Regex("^[A-Za-z0-9-]{1,64}$")))
        vm.cancelInvite(); runCurrent()
        assertEquals("cancelled", relay.sent.last().third["reason"]!!.jsonPrimitive.content)
        relay.event("game:accept", id); runCurrent()
        assertEquals(GameStage.Idle, vm.ui.value.stage)
    }
    @Test fun incomingAcceptDeclineAndThirtySecondAutoDecline() = runTest {
        val (vm, relay) = fixture()
        relay.event("game:invite", "first", buildJsonObject { put("game", "chess") }); runCurrent()
        assertEquals(GameStage.Incoming, vm.ui.value.stage)
        advanceTimeBy(29_999); runCurrent(); assertEquals(GameStage.Incoming, vm.ui.value.stage)
        advanceTimeBy(1); runCurrent()
        assertEquals(GameStage.Idle, vm.ui.value.stage)
        assertEquals("game:decline", relay.sent.last().first)
        relay.event("game:invite", "second", buildJsonObject { put("game", "connect4") }); runCurrent()
        vm.accept(); runCurrent()
        assertEquals(2, vm.ui.value.player); assertEquals(GameStage.Playing, vm.ui.value.stage)
        assertEquals(1, vm.board!!.turn)
        assertEquals("second", vm.ui.value.id)
    }

    @Test fun outgoingInvitationExpiresAndSendsEnd() = runTest {
        val (vm, relay) = fixture()
        vm.invite(GameKind.Dots); runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(GameStage.Result, vm.ui.value.stage)
        assertEquals("invite_timeout", relay.sent.last().third["reason"]!!.jsonPrimitive.content)
    }
    @Test fun partnerOfflineAckUsesChatNotice() = runTest {
        val (vm, relay) = fixture()
        relay.inviteAck = { throw GameRelayException("partner_offline") }
        vm.invite(GameKind.Chess); runCurrent()
        assertEquals("Games need Alex to be online.", vm.error.value)
        assertEquals(GameStage.Idle, vm.ui.value.stage)
    }
    @Test fun legalMovesWorkAndDuplicateRemoteMoveEndsWithNotice() = runTest {
        val (vm, relay) = playing()
        vm.move(buildJsonObject { put("cell", 0) }); runCurrent()
        relay.event("game:move", vm.ui.value.id, buildJsonObject { put("seq", 1); put("data", buildJsonObject { put("cell", 4) }) }); runCurrent()
        assertEquals(2, vm.board!!.nextSeq)
        relay.event("game:move", vm.ui.value.id, buildJsonObject { put("seq", 1); put("data", buildJsonObject { put("cell", 8) }) }); runCurrent()
        assertEquals(GameStage.Result, vm.ui.value.stage)
        assertTrue(vm.ui.value.result!!.detail.contains("out-of-sequence"))
        assertEquals("invalid_move", relay.sent.last().third["reason"]!!.jsonPrimitive.content)
    }
    @Test fun illegalRemoteMoveAndMalformedDataEndTheGame() = runTest {
        for (data in listOf<JsonElement>(buildJsonObject { put("cell", 0) }, JsonPrimitive("bad"))) {
            val f = playing()
            f.vm.move(buildJsonObject { put("cell", 0) }); runCurrent()
            f.relay.event("game:move", f.vm.ui.value.id, buildJsonObject { put("seq", 1); put("data", data) }); runCurrent()
            assertEquals(GameStage.Result, f.vm.ui.value.stage)
        }
    }
    @Test fun unknownSenderAndStaleIdAreIgnored() = runTest {
        val (vm, relay) = playing()
        relay.event("game:end", vm.ui.value.id, from = "stranger")
        relay.event("game:end", "other-id"); runCurrent()
        assertEquals(GameStage.Playing, vm.ui.value.stage)
    }
    @Test fun disconnectPausesThenEndsAfterFifteenSeconds() = runTest {
        val (vm, relay) = playing()
        relay.chat.presence.emit(PresenceUpdate("other", false)); runCurrent()
        assertTrue(vm.ui.value.paused)
        vm.move(buildJsonObject { put("cell", 0) }); assertEquals(0, vm.board!!.nextSeq)
        advanceTimeBy(14_999); runCurrent(); assertEquals(GameStage.Playing, vm.ui.value.stage)
        advanceTimeBy(1); runCurrent(); assertEquals(GameStage.Result, vm.ui.value.stage)
        assertEquals("disconnected", relay.sent.last().third["reason"]!!.jsonPrimitive.content)
    }
    @Test fun quickPresenceReconnectKeepsBoardAndCancelsTimeout() = runTest {
        val (vm, relay) = playing()
        val board = vm.board
        relay.chat.presence.emit(PresenceUpdate("other", false)); runCurrent()
        advanceTimeBy(5_000)
        relay.chat.presence.emit(PresenceUpdate("other", true)); runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(GameStage.Playing, vm.ui.value.stage); assertFalse(vm.ui.value.paused); assertSame(board, vm.board)
    }
    @Test fun lostRelayMovePausesThenEndsEvenIfPresenceSnapshotIsStale() = runTest {
        val (vm, relay) = playing()
        relay.moveAck = { throw GameRelayException("partner_offline") }
        vm.move(buildJsonObject { put("cell", 0) }); runCurrent()
        assertTrue(vm.ui.value.paused); assertEquals(GameStage.Playing, vm.ui.value.stage)
        advanceTimeBy(15_000); runCurrent(); assertEquals(GameStage.Result, vm.ui.value.stage)
    }
    @Test fun rotationReopeningKeepsGameAndSavedRole() = runTest {
        val f = playing(GameKind.Chess)
        val engine = f.vm.board
        f.vm.move(buildJsonObject { put("uci", "e2e4") }); runCurrent()
        f.vm.foreground(false); f.vm.open(connection("active", true)); f.vm.foreground(true); runCurrent()
        assertSame(engine, f.vm.board); assertEquals(1, engine!!.nextSeq)
        assertEquals(f.vm.ui.value.id, f.saved.get<String>("gameId")); assertEquals(1, f.saved.get<Int>("gameRole"))
    }
    @Test fun processRestorationEndsAndNeverRestoresAPartialBoard() = runTest {
        val f = fixture(saved = SavedStateHandle(mapOf("gameId" to "restored", "gameRole" to 2, "gameKind" to "dots", "gameOwner" to "me", "gameConnection" to "connection")))
        assertEquals(GameStage.Result, f.vm.ui.value.stage)
        assertEquals(GameKind.Dots, f.vm.ui.value.kind)
        assertNull(f.vm.board)
        assertEquals("process_restarted", f.relay.sent.last().third["reason"]!!.jsonPrimitive.content)
        assertNull(f.saved.get<String>("gameId"))
    }
    @Test fun savedGameFromAnotherConnectionIsDiscarded() = runTest {
        val f = fixture(saved = SavedStateHandle(mapOf("gameId" to "old", "gameOwner" to "another", "gameConnection" to "connection")))
        assertEquals(GameStage.Idle, f.vm.ui.value.stage)
        assertTrue(f.relay.sent.isEmpty())
    }
    @Test fun resignRematchNewIdAndLeaveAreRelayed() = runTest {
        val (vm, relay) = playing()
        val first = vm.ui.value.id
        vm.leave(true); runCurrent()
        assertEquals("resigned", relay.sent.last().third["reason"]!!.jsonPrimitive.content)
        vm.invite(GameKind.TicTacToe); runCurrent(); assertNotEquals(first, vm.ui.value.id)
        relay.event("game:accept", vm.ui.value.id); runCurrent()
        vm.leave(false); runCurrent()
        assertEquals("left", relay.sent.last().third["reason"]!!.jsonPrimitive.content)
    }
    @Test fun simultaneousInvitesConvergeOnLexicographicallySmallerId() = runTest {
        val (vm, relay) = fixture()
        vm.invite(GameKind.Chess); runCurrent()
        relay.event("game:invite", "0", buildJsonObject { put("game", "dots") }); runCurrent()
        assertEquals(GameStage.Incoming, vm.ui.value.stage); assertEquals("0", vm.ui.value.id)
        assertTrue(relay.sent.any { it.first == "game:end" })
    }
    @Test fun pongSenderNeverExceedsThirtyPerSecondAndWaitsForGuestAcceptAck() = runTest {
        val (vm, relay) = fixture()
        val accept = CompletableDeferred<Unit>()
        relay.acceptAck = { accept.await() }
        relay.event("game:invite", "pong-id", buildJsonObject { put("game", "pong") }); runCurrent()
        vm.accept(); runCurrent(); advanceTimeBy(500); runCurrent()
        assertTrue(relay.sent.none { it.first == "game:move" })
        accept.complete(Unit); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        val moves = relay.sent.filter { it.first == "game:move" }
        assertTrue(moves.size in 29..30)
        assertEquals((moves.indices).toList(), moves.map { it.third["seq"]!!.jsonPrimitive.int })
        assertTrue(moves.all { it.third["data"]!!.jsonObject.keys == setOf("p") })
        vm.leave(false); runCurrent()
    }
    @Test fun teardownDiscardsGameAndLateAcksCannotReopenIt() = runTest {
        val (vm, relay, messages) = fixture()
        val ack = CompletableDeferred<Unit>()
        relay.inviteAck = { ack.await() }
        vm.invite(GameKind.Chess); runCurrent()
        messages.deactivate(); runCurrent(); ack.complete(Unit); runCurrent()
        assertEquals(GameStage.Idle, vm.ui.value.stage)
    }
}
