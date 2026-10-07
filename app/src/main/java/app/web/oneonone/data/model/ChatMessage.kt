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
