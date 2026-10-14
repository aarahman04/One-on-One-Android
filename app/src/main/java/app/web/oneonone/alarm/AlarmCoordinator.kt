package app.web.oneonone.alarm

import android.content.Context
import androidx.core.content.ContextCompat
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.push.handlers.AlarmPushHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of "should this device be ringing". Two inputs, deduped by alarmId:
 *  - FCM data (works backgrounded or killed): [onAlarmPush]
 *  - the live chat's messages (socket + history): raises from the other member ring,
 *    and a SERVER-CONFIRMED ack/cancel (message with an id) stops the ring.
 * A handled alarm never rings again; see [HandledAlarms].
 */
@Singleton
class AlarmCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val handled: HandledAlarms,
    private val messages: MessageService,
    private val scope: CoroutineScope,
) : AlarmPushHandler {
    private val acking = Mutex()

    fun start() {
        scope.launch {
            combine(messages.active, messages.messages) { session, list -> session to list }.collect { (session, list) ->
                if (session == null) return@collect
                val now = System.currentTimeMillis()
                AlarmPolicy.confirmedAcks(list).forEach { stop(it) }
                AlarmPolicy.raisesToRing(list, session.ownerId, now).forEach { (id, at) -> raise(id, at) }
                sendSilencedAcks(list)
            }
        }
    }

    /** "Silence" on the notification is the acknowledgment: the ring is already stopped, the ack follows through MessageService. */
    fun silenced(alarmId: String) {
        handled.addPendingAck(alarmId)
        scope.launch { if (messages.active.value != null) sendSilencedAcks(messages.messages.first()) }
    }

    // shortcut: with no chat session (process woken only by FCM) the ack waits until the app next opens the chat.
    private suspend fun sendSilencedAcks(list: List<ChatMessage>) = acking.withLock {
        val (send, drop) = AlarmPolicy.silencedAcks(handled.pendingAcks(), list, System.currentTimeMillis())
        drop.forEach(handled::removePendingAck)
        send.forEach { id ->
            // Fixed temp id: a second pass before the queued ack shows up in the list is deduped by MessageService.
            runCatching { messages.send("", "alarm", JsonObject(mapOf("ack" to JsonPrimitive(id))), replyTo = id, clientTempId = "silence-$id") }
                .onSuccess { handled.removePendingAck(id) }
        }
    }

    override fun onAlarmPush(data: Map<String, String>) {
        val alarmId = data["alarmId"]?.takeIf { it.length in 1..64 } ?: return
        // Backend alarmFcmData: alarmId is always the RAISE id, also for ack/cancel.
        if (data["ack"] == "true") stop(alarmId) else raise(alarmId, System.currentTimeMillis())
    }

    fun raise(alarmId: String, raisedAt: Long) {
        if (!AlarmPolicy.shouldRing(handled.contains(alarmId), raisedAt, System.currentTimeMillis())) return
        if (AlarmService.ringing.value == alarmId) return
        // Chat open on screen: the red banner is the acknowledgment UI, so the ring's notification stays quiet.
        val inChat = messages.active.value?.let { messages.isChatResumed(it.connectionId) } == true
        try {
            ContextCompat.startForegroundService(context, AlarmService.ringIntent(context, alarmId, raisedAt, inChat))
        } catch (refused: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) outside an FCM high-priority window.
            AlarmService.postFallback(context, alarmId, raisedAt)
        }
    }

    /** Server-confirmed ack/cancel, or the user silenced it: stop and never ring this id again. */
    fun stop(alarmId: String) {
        if (!handled.contains(alarmId)) handled.add(alarmId)
        when (alarmId) {
            AlarmService.ringing.value -> try {
                context.startService(AlarmService.stopIntent(context, alarmId))
            } catch (_: IllegalStateException) { /* service already gone */ }
            AlarmService.fallbackId -> AlarmService.clearFallback(context)
        }
    }
}
