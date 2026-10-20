package app.web.oneonone.ui.games

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

/** A fake server: records match:* calls, answers with `reply`, pushes `match:state` on demand. */
private class MatchTransport(val chat: FakeTransport = FakeTransport()) : Transport by chat {
    override val matchStates = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    override val pongFrames = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val calls = mutableListOf<Pair<String, JsonObject>>()
    var reply: (String, JsonObject) -> JsonObject = { _, _ -> buildJsonObject { put("ok", true) } }
    override suspend fun match(event: String, payload: JsonObject): JsonObject { calls += event to payload; return reply(event, payload) }
}

private fun snapshot(status: String, inviter: String, game: String = "chess", reason: String? = null, version: Int = 1) = buildJsonObject {
    put("id", "m1"); put("game", game); put("status", status); put("inviter", inviter)
    putJsonArray("players") { add("me"); add("other") }
    put("turn", if (status == "playing") "me" else null); put("seq", 0); put("version", version)
    put("board", JsonNull)
    if (reason != null) putJsonObject("outcome") { put("winner", JsonNull); put("reason", reason) } else put("outcome", JsonNull)
    put("drawOffer", JsonNull); putJsonArray("rematch") {}; putJsonObject("series") {}; putJsonArray("away") {}; put("paused", false)
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val models = mutableListOf<GameViewModel>()
    @Before fun main() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private suspend fun TestScope.fixture(online: Boolean = true): Pair<GameViewModel, MatchTransport> {
        val transport = MatchTransport()
        val messages = MessageService(transport, MemoryMessageStore(), FakeSession(), Json { ignoreUnknownKeys = true }, backgroundScope)
        messages.activate(connection("active", true)); runCurrent()
        if (online) transport.chat.presence.emit(PresenceUpdate("other", true))
        runCurrent()
        val vm = GameViewModel(GameService(transport, messages)).also { models += it }
        vm.open(connection("active", true).copy(otherNickname = "Alex")); runCurrent()
        return vm to transport
    }

    @Test fun openingRecoversTheServerMatch() = runTest {
        val (vm, transport) = fixture()
        assertEquals("match:sync", transport.calls.first().first)
        transport.matchStates.emit(snapshot("playing", "other")); runCurrent()
        assertEquals(GameStage.Playing, vm.ui.value.stage)
        assertEquals(1, vm.ui.value.seat)
    }

    @Test fun hubNeedsThePartnerOnline() = runTest {
        val (vm, transport) = fixture(online = false)
        vm.hub(); assertEquals("Games need Alex to be online.", vm.error.value)
        assertEquals(GameStage.Idle, vm.ui.value.stage)
        transport.chat.presence.emit(PresenceUpdate("other", true)); runCurrent()
        vm.hub(); runCurrent()
        assertEquals(GameStage.Hub, vm.ui.value.stage)
        transport.chat.state.value = ConnectionState.Offline; runCurrent()
        vm.dismissHub(); vm.hub(); runCurrent()
        assertEquals(GameStage.Idle, vm.ui.value.stage)
    }

    @Test fun incomingInviteAcceptAndDecline() = runTest {
        val (vm, transport) = fixture()
        transport.matchStates.emit(snapshot("invited", "other")); runCurrent()
        assertEquals(GameStage.Incoming, vm.ui.value.stage)
        vm.accept(); runCurrent()
        assertEquals("match:accept" to "m1", transport.calls.last().first to transport.calls.last().second["matchId"]!!.jsonPrimitive.content)
        vm.decline(); runCurrent()
        assertEquals("match:decline", transport.calls.last().first)
    }

    @Test fun declinedInvitationClosesWithANotice() = runTest {
        val (vm, transport) = fixture()
        transport.reply = { _, _ -> buildJsonObject { put("ok", true); put("match", snapshot("invited", "me")) } }
        vm.hub(); vm.invite(GameKind.Dots); runCurrent()
        assertEquals(GameStage.Outgoing, vm.ui.value.stage)
        transport.matchStates.emit(snapshot("over", "me", reason = "declined", version = 2)); runCurrent()
        assertEquals(GameStage.Idle, vm.ui.value.stage)
        assertEquals("Alex declined the invitation.", vm.error.value)
    }

    @Test fun rejectedMoveShowsAMessageAndResyncs() = runTest {
        val (vm, transport) = fixture()
        transport.matchStates.emit(snapshot("playing", "me")); runCurrent()
        transport.reply = { event, _ -> if (event == "match:move") buildJsonObject { put("ok", false); put("error", "stale") } else buildJsonObject { put("ok", true) } }
        vm.move(buildJsonObject { put("uci", "e2e4") }); runCurrent()
        assertEquals("The game moved on. Showing the latest board.", vm.error.value)
        assertEquals("match:sync", transport.calls.last().first)
    }

    @Test fun tableTennisPausesWhileBackgrounded() = runTest {
        val (vm, transport) = fixture()
        transport.matchStates.emit(snapshot("playing", "me", game = "pong")); runCurrent()
        vm.foreground(false); runCurrent()
        assertEquals("match:away", transport.calls.last().first)
        assertEquals(true, transport.calls.last().second["away"]!!.jsonPrimitive.boolean)
        vm.foreground(true); runCurrent()
        assertEquals(false, transport.calls.last().second["away"]!!.jsonPrimitive.boolean)
    }
}
