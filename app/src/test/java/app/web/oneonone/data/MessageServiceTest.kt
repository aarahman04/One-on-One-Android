package app.web.oneonone.data

import app.web.oneonone.data.chat.*
import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import app.web.oneonone.data.transport.Transport
import app.web.oneonone.data.transport.decodeSendAck
import app.web.oneonone.ui.chat.ChatViewModel
import app.web.oneonone.ui.chat.receiptLabel
import app.web.oneonone.call.CallKind
import app.web.oneonone.call.CallLauncher
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

private val testJson = Json { ignoreUnknownKeys = true }

class MemoryMessageStore : MessageStore {
    val rows = MutableStateFlow<List<MessageEntity>>(emptyList())
    val positions = mutableMapOf<Pair<String, String>, String>()
    override fun observe(owner: String, connection: String) = rows.map { all -> all.filter {
        it.ownerId == owner && it.connectionId == connection
    }.sortedBy { Instant.parse(it.createdAt) }.map {
        testJson.decodeFromString<ChatMessage>(it.body).copy(deliveryState = it.status, error = it.error)
    } }
    private fun put(row: MessageEntity) { rows.value = rows.value.filterNot {
        it.ownerId == row.ownerId && it.connectionId == row.connectionId && it.key == row.key
    } + row }
    override suspend fun enqueue(owner: String, connection: String, message: ChatMessage) {
        put(MessageEntity(owner, connection, "pending:${message.tempId}", message.tempId, message.createdAt,
            testJson.encodeToString(message), "queued"))
    }
    override suspend fun pending(owner: String, connection: String) = rows.value.filter {
        it.ownerId == owner && it.connectionId == connection && it.status != "sent"
    }
    override suspend fun attempt(entity: MessageEntity, now: Long) {
        rows.value.find { it.key == entity.key }?.let { put(it.copy(status = "sending", firstAttemptAt = it.firstAttemptAt.takeIf { it != 0L } ?: now)) }
    }
    override suspend fun fail(entity: MessageEntity, status: String, error: String) {
        rows.value.find { it.key == entity.key }?.let { put(it.copy(status = status, error = error)) }
    }
    override suspend fun reconcile(owner: String, connection: String, message: ChatMessage) {
        val pendingKey = resolvedPendingKey(owner, message)
        rows.value = rows.value.filterNot { it.ownerId == owner && it.connectionId == connection && it.key == pendingKey }
        val existing = rows.value.find { it.ownerId == owner && it.connectionId == connection && it.key == message.id }
            ?.let { testJson.decodeFromString<ChatMessage>(it.body) }
        val canonical = reconcileStored(existing, message)
        put(MessageEntity(owner, connection, checkNotNull(message.id), canonical.tempId, message.createdAt,
            testJson.encodeToString(canonical), "sent"))
    }
    override suspend fun reaction(owner: String, connection: String, update: ReactionUpdate) { }
    override suspend fun watermark(owner: String, connection: String) = positions[owner to connection]
    override suspend fun position(owner: String, connection: String, at: String) { positions[owner to connection] = at }
    override suspend fun oldest(owner: String, connection: String) = rows.value.filter {
        it.ownerId == owner && it.connectionId == connection && it.status == "sent"
    }.minByOrNull { Instant.parse(it.createdAt) }?.createdAt
    override suspend fun clear(owner: String, connection: String) {
        rows.value = rows.value.filterNot { it.ownerId == owner && it.connectionId == connection }
        positions.remove(owner to connection)
    }
    override suspend fun byTempId(owner: String, connection: String, tempId: String) = rows.value.find {
        it.ownerId == owner && it.connectionId == connection && it.tempId == tempId
    }?.let { testJson.decodeFromString<ChatMessage>(it.body).copy(deliveryState = it.status, error = it.error) }
}

class FakeTransport : Transport {
    override val state = MutableStateFlow(ConnectionState.Offline)
    override val incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 32)
    override val receipts = MutableSharedFlow<ReceiptUpdate>(extraBufferCapacity = 32)
    override val reactions = MutableSharedFlow<ReactionUpdate>(extraBufferCapacity = 32)
    override val ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sends = mutableListOf<SendMessage>()
    val canonical = mutableMapOf<String, ChatMessage>()
    val historyCalls = mutableListOf<Pair<String?, String?>>()
    var loseAckOnce = false
    var echoFirst = false
    var history: (String?, String?) -> List<ChatMessage> = { _, _ -> emptyList() }
    override suspend fun start() { state.value = ConnectionState.Connected }
    override suspend fun stop() { state.value = ConnectionState.Offline }
    override suspend fun send(message: SendMessage): ChatMessage {
        sends += message
        val saved = canonical.getOrPut(message.tempId) { ChatMessage(id = "server-${message.tempId}", senderId = "me",
            content = message.content.trim(), type = message.type, payload = message.payload, replyTo = message.replyTo,
            createdAt = "2026-10-07T00:10:00Z", tempId = message.tempId) }
        if (echoFirst) { incoming.emit(saved); yield() }
        if (loseAckOnce) { loseAckOnce = false; throw IOException("lost ack") }
        return saved
    }
    override suspend fun history(connectionId: String, before: String?, after: String?): List<ChatMessage> {
        historyCalls += before to after
        return history(before, after)
    }
    override suspend fun markRead(connectionId: String) { }
    override suspend fun react(messageId: String, emoji: String, remove: Boolean) { }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageServiceTest {
    @Test fun connectionEndedPurgesCacheAndPendingSends() = runTest {
        val transport = FakeTransport().apply { loseAckOnce = true }
        val store = MemoryMessageStore()
        val service = MessageService(transport, store, FakeSession(), testJson, backgroundScope)
        service.activate(connection("active", true)); runCurrent()
        service.send("unsent"); runCurrent()
        assertTrue(store.rows.value.isNotEmpty())
        transport.ended.emit(Unit); runCurrent()
        assertNull(service.active.value)
        assertTrue(store.rows.value.isEmpty())
        assertEquals(ConnectionState.Offline, transport.state.value)
    }

    @Test fun ackValidationAcceptsOriginalDuplicateAndRejectsErrorsOrMissingMessage() {
        val message = ChatMessage("server", "me", "hello", "2026-10-07T00:00:00Z")
        assertEquals(message, decodeSendAck(testJson.encodeToString(SendAck(true, message, duplicate = true)), testJson))
        listOf("{\"error\":\"slow down\"}", "{\"ok\":true}", "{\"ok\":false}").forEach {
            assertTrue(runCatching { decodeSendAck(it, testJson) }.isFailure)
        }
    }

    @Test fun lostAckRetriesSameTempIdAndEchoPlusAckHasOneCanonicalRow() = runTest {
        val transport = FakeTransport().apply { loseAckOnce = true; echoFirst = true }
        val store = MemoryMessageStore()
        val service = MessageService(transport, store, FakeSession(), testJson, backgroundScope)
        service.activate(connection("active", true)); runCurrent()
        service.send(" hello "); runCurrent()
        assertEquals(1, store.rows.value.size)
        assertEquals("sent", store.rows.value.single().status)
        service.flush(); runCurrent()
        assertEquals(1, transport.sends.size) // Echo already reconciled despite the lost ack.
        assertEquals("hello", testJson.decodeFromString<ChatMessage>(store.rows.value.single().body).content)
        service.deactivate()
    }

    @Test fun lostAckWithoutEchoReplaysPersistedTempIdAfterRestart() = runTest {
        val transport = FakeTransport().apply { loseAckOnce = true }
        val store = MemoryMessageStore()
        val auth = FakeSession()
        val first = MessageService(transport, store, auth, testJson, backgroundScope)
        first.activate(connection("active", true)); runCurrent()
        val id = first.send("hello"); runCurrent()
        assertEquals("queued", store.rows.value.single().status)
        first.deactivate()
        val restarted = MessageService(transport, store, auth, testJson, backgroundScope)
        restarted.activate(connection("active", true)); runCurrent(); advanceTimeBy(250); runCurrent()
        assertEquals(listOf(id, id), transport.sends.map { it.tempId })
        assertEquals(1, transport.canonical.size)
        assertEquals(1, store.rows.value.size)
        assertEquals("sent", store.rows.value.single().status)
        restarted.deactivate()
    }

    @Test fun resyncKeepsPreFlushCheckpointAndPagesForwardUntilShortPage() = runTest {
        val start = Instant.parse("2026-10-07T00:00:00Z")
        fun message(n: Long) = ChatMessage("id-$n", "other", "message $n", start.plusSeconds(n).toString())
        val store = MemoryMessageStore().apply { positions["me" to "connection"] = start.toString() }
        store.enqueue("me", "connection", ChatMessage(senderId = "me", content = "new send", createdAt = start.plusSeconds(600).toString(), tempId = "persisted"))
        val transport = FakeTransport().apply {
            history = { _, after -> when (after) {
                start.toString() -> (1L..50L).map(::message)
                start.plusSeconds(50).toString() -> listOf(message(51)) + canonical.values
                else -> emptyList()
            } }
        }
        val service = MessageService(transport, store, FakeSession(), testJson, backgroundScope)
        service.activate(connection("active", true)); runCurrent(); advanceTimeBy(250); runCurrent()
        assertEquals(start.toString(), transport.historyCalls.first().second)
        assertEquals(start.plusSeconds(50).toString(), transport.historyCalls[1].second)
        assertEquals(52, store.rows.value.size)
        assertEquals("2026-10-07T00:10:00Z", store.positions["me" to "connection"])
        service.deactivate()
    }

    @Test fun oldUnacknowledgedAttemptNeedsExplicitResendConfirmation() = runTest {
        val store = MemoryMessageStore()
        val pending = ChatMessage(senderId = "me", content = "maybe sent", createdAt = "2026-10-07T00:00:00Z", tempId = "old-attempt")
        store.enqueue("me", "connection", pending)
        store.attempt(store.rows.value.single(), System.currentTimeMillis() - 6 * 60_000)
        val transport = FakeTransport()
        val service = MessageService(transport, store, FakeSession(), testJson, backgroundScope)
        service.activate(connection("active", true)); runCurrent()
        assertEquals("unknown", store.rows.value.single().status)
        assertTrue(transport.sends.isEmpty())
        assertTrue(runCatching { service.retry("old-attempt") }.isFailure)
        service.retry("old-attempt", confirmedDuplicateRisk = true)
        assertEquals("old-attempt", transport.sends.single().tempId)
        assertEquals("sent", store.rows.value.single().status)
        service.deactivate()
    }

    @Test fun reconcileOnlyResolvesOwnTempIdAndReactionsReplacePerUser() {
        val own = ChatMessage("server", "me", "same content", "2026-10-07T00:00:00Z", tempId = "temp")
        assertEquals("pending:temp", resolvedPendingKey("me", own))
        assertNull(resolvedPendingKey("me", own.copy(senderId = "other")))
        assertNull(resolvedPendingKey("me", own.copy(tempId = null)))
        val reactions = listOf(ReactionSummary("👍", listOf("a", "b")))
        val replaced = updateReactions(reactions, ReactionUpdate("server", "❤️", "a", "add"))
        assertEquals(listOf("b"), replaced.first { it.emoji == "👍" }.userIds)
        assertEquals(listOf("a"), replaced.first { it.emoji == "❤️" }.userIds)
        assertEquals(listOf(ReactionSummary("👍", listOf("b"))), updateReactions(replaced, ReactionUpdate("server", "❤️", "a", "remove")))
    }

    @Test fun receiptPositionsAreMonotonicAndDistinguishAllThreeTicks() {
        val message = ChatMessage("server", "me", "hello", "2026-10-07T00:00:00.001Z")
        assertEquals("Sent", receiptLabel(message, null, "2026-10-07T00:00:00Z"))
        assertEquals("Delivered", receiptLabel(message, null, message.createdAt))
        assertEquals("Read", receiptLabel(message, message.createdAt, message.createdAt))
        assertEquals(message.createdAt, newerTime(message.createdAt, "2026-10-07T00:00:00Z"))
        assertFalse(needsDeliveryConfirmation(0, Long.MAX_VALUE))
        assertFalse(needsDeliveryConfirmation(1, 300_000))
        assertTrue(needsDeliveryConfirmation(1, 300_001))
    }

    @Test fun chatViewModelClearsDraftOnlyAfterEnqueueAndUsesCallSeam() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val transport = FakeTransport()
            val store = MemoryMessageStore()
            val service = MessageService(transport, store, session, testJson, backgroundScope)
            val calls = mutableListOf<CallKind>()
            val saved = SavedStateHandle()
            val vm = ChatViewModel(service, FakeAccountApi(session), object : CallLauncher {
                override fun start(kind: CallKind) { calls += kind }
            }, saved)
            vm.open(connection("active", true)); runCurrent()
            vm.draft("hello"); vm.reply("reply-target"); vm.send(); runCurrent()
            assertEquals("", saved.get<String>("draft"))
            assertNull(saved.get<String>("replyTo"))
            assertEquals("reply-target", transport.sends.single().replyTo)
            vm.call(CallKind.Audio)
            assertEquals(listOf(CallKind.Audio), calls)
            service.deactivate()
            vm.viewModelScope.cancel()
            runCurrent()
        } finally { Dispatchers.resetMain() }
    }
}
