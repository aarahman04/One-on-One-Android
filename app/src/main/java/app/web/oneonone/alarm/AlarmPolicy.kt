package app.web.oneonone.alarm

import app.web.oneonone.data.model.ChatMessage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/** What the chat card for an alarm RAISE shows. Derived only from server-backed messages. */
enum class AlarmCardState {
    /** Raise not echoed by the server yet: no id, nothing to ack. */
    Sending,
    /** Raise confirmed, no ack yet, inside the window: tappable. */
    Live,
    /** Ack/cancel queued or in flight: wait for the server. */
    Pending,
    /** Last ack/cancel send failed: tappable again. */
    Failed,
    Acknowledged,
    Cancelled,
    /** Window passed without an ack. */
    Expired,
}

/** Pure alarm rules (mirrors backend checkAlarmAck + web ChatPage alarm flow). No Android types. */
object AlarmPolicy {
    /** Same as backend ALARM_ACK_WINDOW_MS and the native ring's auto-clear. */
    const val WINDOW_MS = 2 * 60_000L

    fun ackOf(message: ChatMessage): String? =
        if (message.type != "alarm") null
        else (message.payload?.get("ack") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun isCancel(message: ChatMessage): Boolean =
        (message.payload?.get("cancelled") as? JsonPrimitive)?.booleanOrNull == true

    fun isRaise(message: ChatMessage): Boolean = message.type == "alarm" && ackOf(message) == null

    fun createdAtMs(message: ChatMessage): Long =
        runCatching { Instant.parse(message.createdAt).toEpochMilli() }.getOrDefault(0L)

    fun cardState(raise: ChatMessage, messages: List<ChatMessage>, now: Long): AlarmCardState {
        val raiseId = raise.id ?: return AlarmCardState.Sending
        val acks = messages.filter { ackOf(it) == raiseId }
        acks.firstOrNull { it.id != null }?.let {
            return if (isCancel(it)) AlarmCardState.Cancelled else AlarmCardState.Acknowledged
        }
        if (acks.any { it.deliveryState !in FAILED_STATES }) return AlarmCardState.Pending
        if (now - createdAtMs(raise) > WINDOW_MS) return AlarmCardState.Expired
        return if (acks.isNotEmpty()) AlarmCardState.Failed else AlarmCardState.Live
    }

    /** Raises from the other member that should be ringing right now (socket/history path). */
    fun raisesToRing(messages: List<ChatMessage>, myUserId: String, now: Long): List<Pair<String, Long>> {
        val confirmed = confirmedAcks(messages)
        return messages.filter {
            isRaise(it) && it.id != null && it.senderId != myUserId && it.id !in confirmed &&
                now - createdAtMs(it) <= WINDOW_MS // a server clock slightly ahead still rings
        }.map { checkNotNull(it.id) to createdAtMs(it) }
    }

    /** Raise ids the server has recorded an ack or cancel for. Only these stop a ring. */
    fun confirmedAcks(messages: List<ChatMessage>): Set<String> =
        messages.filter { it.id != null }.mapNotNull { ackOf(it) }.toSet()

    /** Raise ids this device just acked (queued or sent): the ring stops on the tap, not after the server round trip. */
    fun ownAcks(messages: List<ChatMessage>, myUserId: String): Set<String> =
        messages.filter { it.senderId == myUserId && it.deliveryState !in FAILED_STATES }.mapNotNull { ackOf(it) }.toSet()

    /**
     * Alarms silenced from the notification: which acks to send now and which to forget.
     * Ack only while the server still accepts it (Live/Failed); a raise not in the list yet is kept for a later pass.
     */
    fun silencedAcks(pending: List<String>, messages: List<ChatMessage>, now: Long): Pair<List<String>, List<String>> {
        val send = mutableListOf<String>()
        val drop = mutableListOf<String>()
        pending.forEach { id ->
            val raise = messages.firstOrNull { it.id == id && isRaise(it) } ?: return@forEach
            if (cardState(raise, messages, now) in setOf(AlarmCardState.Live, AlarmCardState.Failed)) send += id else drop += id
        }
        return send to drop
    }

    /** Whether a raise (from any source) may start ringing. */
    fun shouldRing(handled: Boolean, raisedAtMs: Long, now: Long): Boolean =
        !handled && now - raisedAtMs <= WINDOW_MS

    private val FAILED_STATES = setOf("failed", "unknown")
}
