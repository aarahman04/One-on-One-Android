package app.web.oneonone.data.chat

import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import app.web.oneonone.data.transport.Transport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ChatSession(val ownerId: String, val connectionId: String)

internal fun needsDeliveryConfirmation(firstAttemptAt: Long, now: Long): Boolean =
    firstAttemptAt != 0L && now - firstAttemptAt >= 5 * 60_000

internal fun newerTime(first: String?, second: String?): String? = when {
    first == null -> second
    second == null -> first
    Instant.parse(second).isAfter(Instant.parse(first)) -> second
    else -> first
}

/** UI -> ViewModel -> MessageService -> Transport. Room is a cache, never authorization. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MessageService @Inject constructor(
    private val transport: Transport,
    private val store: MessageStore,
    private val auth: AccountSession,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    private val mutableActive = MutableStateFlow<ChatSession?>(null)
    val active = mutableActive.asStateFlow()
    val messages: Flow<List<ChatMessage>> = active.flatMapLatest {
        if (it == null) flowOf(emptyList()) else store.observe(it.ownerId, it.connectionId)
    }
    val connectionState = transport.state
    private val mutableReceipts = MutableStateFlow<ReceiptUpdate?>(null)
    val receipts = mutableReceipts.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended = mutableEnded.asSharedFlow()
    private var conversationJob: Job? = null
    private val lifecycle = Mutex()
    private val synchronizing = Mutex()
    private val sending = Mutex()
    private var chatResumed = false

    init { scope.launch {
        auth.signedIn.collect { if (!it) deactivate() }
    } }

    suspend fun activate(connection: CurrentConnection) = lifecycle.withLock {
        require(connection.status in setOf("active", "leave_pending"))
        val session = ChatSession(connection.myUserId, connection.id)
        if (session == active.value) return@withLock
        conversationJob?.cancelAndJoin()
        transport.stop()
        mutableActive.value = session
        mutableError.value = null
        mutableReceipts.value = null
        val job = SupervisorJob(scope.coroutineContext[Job])
        conversationJob = job
        val conversation = CoroutineScope(scope.coroutineContext + job)
        conversation.launch { guarded {
            transport.incoming.collect { message ->
                store.reconcile(session.ownerId, session.connectionId, message)
                if (chatResumed && message.senderId != session.ownerId) markRead()
            }
        } }
        conversation.launch { guarded { transport.reactions.collect {
            store.reaction(session.ownerId, session.connectionId, it)
        } } }
        conversation.launch { guarded { transport.receipts.collect { receipt ->
            if (receipt.userId != session.ownerId) mutableReceipts.update { previous ->
                receipt.copy(lastReadAt = newerTime(previous?.lastReadAt, receipt.lastReadAt),
                    lastDeliveredAt = newerTime(previous?.lastDeliveredAt, receipt.lastDeliveredAt))
            }
        } } }
        conversation.launch { transport.ended.collect {
            // Teardown from the parent scope; never join the collector's own job.
            scope.launch { deactivate(clear = true); mutableEnded.emit(Unit) }
        } }
        conversation.launch { transport.state.collect {
            if (it == ConnectionState.Connected) guarded { resync() }
        } }
        conversation.launch {
            while (isActive) {
                if (transport.state.value == ConnectionState.Connected) guarded { flush() }
                delay(5_000)
            }
        }
        transport.start()
        // REST history works even if the socket is temporarily unavailable.
        conversation.launch { guarded { resync() } }
    }

    suspend fun deactivate(clear: Boolean = false) = lifecycle.withLock {
        val previous = active.value
        conversationJob?.cancelAndJoin()
        conversationJob = null
        transport.stop()
        mutableActive.value = null
        mutableReceipts.value = null
        chatResumed = false
        if (clear && previous != null) store.clear(previous.ownerId, previous.connectionId)
    }

    fun visible(resumed: Boolean) {
        chatResumed = resumed
        if (resumed) scope.launch { guarded { markRead() } }
    }

    suspend fun send(content: String, type: String = "text", payload: JsonObject? = null, replyTo: String? = null): String {
        val session = checkNotNull(active.value) { "No active connection." }
        check(auth.accessToken() != null) { "Sign in to continue." }
        require(type !in setOf("call", "system")) { "This message type is server-authored." }
        require(content.trim().length <= 4_000 && (content.isNotBlank() || type in setOf("alarm", "voice", "image", "file"))) {
            "Messages must be 1–4000 characters."
        }
        val tempId = UUID.randomUUID().toString()
        store.enqueue(session.ownerId, session.connectionId, ChatMessage(senderId = session.ownerId,
            content = content.trim(), createdAt = Instant.now().toString(), type = type, payload = payload,
            replyTo = replyTo, tempId = tempId, deliveryState = "queued"))
        scope.launch { guarded { flush() } }
        return tempId
    }

    suspend fun retry(tempId: String, confirmedDuplicateRisk: Boolean = false) {
        val session = checkNotNull(active.value)
        val pending = store.pending(session.ownerId, session.connectionId).find { it.tempId == tempId } ?: return
        check(!needsDeliveryConfirmation(pending.firstAttemptAt, System.currentTimeMillis()) || confirmedDuplicateRisk) {
            "Delivery is unknown. Check history before confirming a resend; it may duplicate the message."
        }
        // The user explicitly chooses a resend after the contract's five-minute window.
        store.fail(pending, "queued", "Retrying…")
        flush(confirmedTempId = tempId.takeIf { confirmedDuplicateRisk })
    }

    suspend fun flush(confirmedTempId: String? = null) = sending.withLock {
        val session = active.value ?: return@withLock
        if (transport.state.value != ConnectionState.Connected) return@withLock
        for (entry in store.pending(session.ownerId, session.connectionId)) {
            if (session != active.value || transport.state.value != ConnectionState.Connected) break
            if (entry.status in setOf("failed", "unknown")) continue
            val now = System.currentTimeMillis()
            // ponytail: server dedupe lasts 5 min; require an explicit resend after that, upgrade with durable backend idempotency.
            if (needsDeliveryConfirmation(entry.firstAttemptAt, now) && confirmedTempId != entry.tempId) {
                store.fail(entry, "unknown", "Delivery unknown — check history before resending.")
                continue
            }
            val message = json.decodeFromString<ChatMessage>(entry.body)
            store.attempt(entry, now)
            try {
                val saved = transport.send(SendMessage(message.content, message.type, message.payload,
                    message.replyTo, checkNotNull(message.tempId)))
                check(saved.senderId == session.ownerId) { "Acknowledgement belongs to another account." }
                store.reconcile(session.ownerId, session.connectionId, saved.copy(tempId = message.tempId))
            } catch (cancelled: CancellationException) {
                throw cancelled // 'sending' is persisted, so process death still replays the same tempId.
            } catch (error: Exception) {
                val transient = error is java.io.IOException
                store.fail(entry, if (transient) "queued" else "failed", error.message ?: "Send failed.")
                if (transient) break
            }
            delay(200) // Stay comfortably below the server's 60-events/10-second flood limit.
        }
    }

    suspend fun resync() = synchronizing.withLock {
        val session = active.value ?: return@withLock
        // Capture REST's checkpoint BEFORE flush; newer acks/live events must not skip a missing interval.
        var cursor = store.watermark(session.ownerId, session.connectionId)
        if (transport.state.value == ConnectionState.Connected) flush()
        while (session == active.value) {
            val batch = transport.history(session.connectionId, after = cursor).sortedBy { Instant.parse(it.createdAt) }
            for (message in batch) store.reconcile(session.ownerId, session.connectionId, message)
            val next = batch.fold(cursor) { at, message -> newerTime(at, message.createdAt) }
            if (next != null) store.position(session.ownerId, session.connectionId, next)
            if (batch.size < 50 || cursor == null) break // Initial page is the newest 50; older pages are explicit.
            check(next != cursor) { "History cursor did not advance." }
            cursor = next
        }
        if (chatResumed) markRead()
        mutableError.value = null
    }

    suspend fun older(): Boolean = synchronizing.withLock {
        val session = checkNotNull(active.value)
        val before = store.oldest(session.ownerId, session.connectionId) ?: return@withLock false
        val batch = transport.history(session.connectionId, before = before)
        for (message in batch) store.reconcile(session.ownerId, session.connectionId, message)
        batch.size == 50
    }
    suspend fun react(messageId: String, emoji: String, remove: Boolean) = transport.react(messageId, emoji, remove)
    suspend fun markRead() { active.value?.let { transport.markRead(it.connectionId) } }

    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutableError.value = error.message ?: "Couldn't sync. Try again." }
    }
}
