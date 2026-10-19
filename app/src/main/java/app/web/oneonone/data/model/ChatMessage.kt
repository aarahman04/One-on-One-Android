package app.web.oneonone.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatMessage(
    val id: String? = null,
    val senderId: String,
    val content: String,
    val createdAt: String,
    val type: String = "text",
    val payload: JsonObject? = null,
    val replyTo: String? = null,
    val reactions: List<ReactionSummary> = emptyList(),
    val tempId: String? = null,
    // Server-set (migration 036): edited text / deleted for everyone (content then empty, payload null).
    val editedAt: String? = null,
    val deletedAt: String? = null,
    // Local presentation only; never sent as part of message:send.
    val deliveryState: String = "sent",
    val error: String? = null,
)

@Serializable data class ReactionSummary(val emoji: String, val userIds: List<String>)
@Serializable data class SendMessage(
    val content: String,
    val type: String,
    val payload: JsonObject?,
    val replyTo: String?,
    val tempId: String,
)
@Serializable data class SendAck(
    val ok: Boolean = false,
    val message: ChatMessage? = null,
    val error: String? = null,
    val duplicate: Boolean = false,
)
@Serializable data class MessagePage(val messages: List<ChatMessage>)
@Serializable data class ReceiptUpdate(val userId: String, val lastReadAt: String? = null, val lastDeliveredAt: String? = null)
@Serializable data class ReactionUpdate(val messageId: String, val emoji: String, val userId: String, val op: String)

val AllowedReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")
@Serializable data class TypingUpdate(val userId: String, val typing: Boolean)
@Serializable data class PresenceUpdate(val userId: String, val online: Boolean, val lastSeenAt: String? = null)
/** The connection's one shared pin; messageId null = nothing pinned. */
@Serializable data class PinState(val messageId: String? = null, val pinnedBy: String? = null, val pinnedAt: String? = null)
@Serializable data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val siteName: String,
    val image: String? = null,
    val imageMime: String? = null,
    val imageKind: String? = null,
)
@Serializable data class LinkPreviewResult(val preview: LinkPreview? = null)

/** Edit and delete-for-everyone window (server: messageService.EDIT_WINDOW_MS). */
const val EditWindowMs = 30 * 60_000L
