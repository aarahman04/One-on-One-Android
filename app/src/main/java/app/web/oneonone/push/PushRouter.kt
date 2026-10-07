package app.web.oneonone.push

import app.web.oneonone.push.handlers.AlarmPushHandler
import app.web.oneonone.push.handlers.CallPushHandler
import app.web.oneonone.data.api.CurrentConnection
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject

@Serializable data class MessagePush(
    val type: String, val messageId: String, val connectionId: String,
    val senderName: String, val preview: String,
)

internal fun validUuid(value: String?): Boolean = value != null && value.length == 36 &&
    runCatching { UUID.fromString(value).toString().equals(value, ignoreCase = true) }.getOrDefault(false)

internal fun decodeMessagePush(data: Map<String, String>): MessagePush? {
    val type = data["type"] ?: return null
    if (type !in setOf("text", "letter", "voice", "image", "file", "ask", "countdown", "checkin", "thisorthat", "location")) return null
    if (!validUuid(data["messageId"]) || !validUuid(data["connectionId"])) return null
    val preview = data["preview"] ?: return null
    return MessagePush(type, checkNotNull(data["messageId"]), checkNotNull(data["connectionId"]),
        data["senderName"].orEmpty().take(40), preview.take(120))
}

internal fun shouldNotify(push: MessagePush, current: CurrentConnection?, chatResumed: Boolean): Boolean =
    current?.id == push.connectionId && current.status in setOf("active", "leave_pending") && !chatResumed

internal fun acceptsNotificationAction(owner: String, expectedOwner: String?, current: CurrentConnection?, expectedConnection: String?): Boolean =
    owner == expectedOwner && current != null && current.id == expectedConnection && current.status in setOf("active", "leave_pending")

class PushRouter @Inject constructor(private val alarm: AlarmPushHandler, private val call: CallPushHandler) {
    fun route(data: Map<String, String>, message: (MessagePush) -> Unit) {
        when (data["type"]) {
            "alarm" -> alarm.onAlarmPush(data)
            "call" -> call.onCallPush(data)
            "call_end" -> call.onCallEndPush(data)
            else -> decodeMessagePush(data)?.let(message)
        }
    }
}
