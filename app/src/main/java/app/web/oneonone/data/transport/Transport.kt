package app.web.oneonone.data.transport

import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import app.web.oneonone.data.games.GameEvent
import kotlinx.serialization.json.JsonElement

interface Transport {
    val state: StateFlow<ConnectionState>
    val incoming: Flow<ChatMessage>
    val receipts: Flow<ReceiptUpdate>
    val reactions: Flow<ReactionUpdate>
    val ended: Flow<Unit>
    val gameEvents: Flow<GameEvent> get() = emptyFlow()
    suspend fun gameInvite(gameId: String, game: String): Unit = error("Not supported.")
    suspend fun gameAccept(gameId: String): Unit = error("Not supported.")
    suspend fun gameDecline(gameId: String): Unit = error("Not supported.")
    suspend fun gameMove(gameId: String, seq: Int, data: JsonElement): Unit = error("Not supported.")
    suspend fun gameEnd(gameId: String, reason: String): Unit = error("Not supported.")
    suspend fun start()
    suspend fun stop()
    suspend fun send(message: SendMessage): ChatMessage
    suspend fun history(connectionId: String, before: String? = null, after: String? = null): List<ChatMessage>
    suspend fun markRead(connectionId: String)
    /** Marks delivered for a notified device (contract §10, called from the FCM receive path). */
    suspend fun markDelivered(connectionId: String) {}
    suspend fun react(messageId: String, emoji: String, remove: Boolean)
    // Edit / delete / pin / typing (contract §5) and server-side link previews (REST).
    val updates: Flow<ChatMessage> get() = emptyFlow()
    val typing: Flow<TypingUpdate> get() = emptyFlow()
    val presence: Flow<PresenceUpdate> get() = emptyFlow()
    val pins: Flow<PinState> get() = emptyFlow()
    suspend fun edit(messageId: String, content: String): ChatMessage = error("Not supported.")
    suspend fun delete(messageId: String): ChatMessage = error("Not supported.")
    suspend fun pin(messageId: String?): PinState = error("Not supported.")
    fun typing(active: Boolean) {}
    fun presence(active: Boolean) {}
    suspend fun linkPreview(url: String): LinkPreview? = null
}
