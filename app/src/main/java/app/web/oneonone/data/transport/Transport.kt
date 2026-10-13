package app.web.oneonone.data.transport

import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

interface Transport {
    val state: StateFlow<ConnectionState>
    val incoming: Flow<ChatMessage>
    val receipts: Flow<ReceiptUpdate>
    val reactions: Flow<ReactionUpdate>
    val ended: Flow<Unit>
    suspend fun start()
    suspend fun stop()
    suspend fun send(message: SendMessage): ChatMessage
    suspend fun history(connectionId: String, before: String? = null, after: String? = null): List<ChatMessage>
    suspend fun markRead(connectionId: String)
    suspend fun react(messageId: String, emoji: String, remove: Boolean)
    // Edit / delete / pin / typing (contract §5) and server-side link previews (REST).
    val updates: Flow<ChatMessage> get() = emptyFlow()
    val typing: Flow<TypingUpdate> get() = emptyFlow()
    val pins: Flow<PinState> get() = emptyFlow()
    suspend fun edit(messageId: String, content: String): ChatMessage = error("Not supported.")
    suspend fun delete(messageId: String): ChatMessage = error("Not supported.")
    suspend fun pin(messageId: String?): PinState = error("Not supported.")
    fun typing(active: Boolean) {}
    suspend fun linkPreview(url: String): LinkPreview? = null
}
