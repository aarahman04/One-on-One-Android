package app.web.oneonone.data.transport

import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
}
