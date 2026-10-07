package app.web.oneonone.alarm

import android.content.Context
import androidx.core.content.ContextCompat
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.push.handlers.AlarmPushHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

    fun start() {
        scope.launch {
            combine(messages.active, messages.messages) { session, list -> session to list }.collect { (session, list) ->
                if (session == null) return@collect
                val now = System.currentTimeMillis()
                AlarmPolicy.confirmedAcks(list).forEach { stop(it) }
                AlarmPolicy.raisesToRing(list, session.ownerId, now).forEach { (id, at) -> raise(id, at) }
            }
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
        try {
            ContextCompat.startForegroundService(context, AlarmService.ringIntent(context, alarmId, raisedAt))
        } catch (refused: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) outside an FCM high-priority window.
            AlarmService.postFallback(context, alarmId)
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
