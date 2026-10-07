package app.web.oneonone.call

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import app.web.oneonone.MainActivity
import app.web.oneonone.R
import app.web.oneonone.push.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/** Incoming-call (full-screen CallStyle) and ongoing-call notifications. */
object CallNotifications {
    const val INCOMING_ID = 3
    const val ONGOING_ID = 4
    const val ACTION_ANSWER = "app.web.oneonone.call.ANSWER"
    const val ACTION_SHOW = "app.web.oneonone.call.SHOW"
    const val ACTION_DECLINE = "app.web.oneonone.call.DECLINE"
    const val ACTION_HANGUP = "app.web.oneonone.call.HANGUP"
    const val EXTRA_CALL_ID = "callId"
    const val EXTRA_TOKEN = "callToken"

    /**
     * Per-ring secret in the Answer/full-screen PendingIntents. MainActivity is exported, so
     * another app could otherwise send it an "answer" intent and pick up a call silently.
     */
    @Volatile private var token: String? = null
    @Volatile private var ringingCallId: String? = null

    fun tokenValid(value: String?, callId: String?): Boolean =
        token != null && token == value && callId != null && callId == ringingCallId

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun showIncoming(context: Context, callId: String, kind: CallKind, peer: String) {
        token = UUID.randomUUID().toString()
        ringingCallId = callId
        fun activity(code: Int, action: String) = PendingIntent.getActivity(context, code,
            Intent(context, MainActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CALL_ID, callId).putExtra(EXTRA_TOKEN, token), FLAGS)
        val decline = PendingIntent.getBroadcast(context, 3_003,
            Intent(context, CallActionReceiver::class.java).setAction(ACTION_DECLINE).putExtra(EXTRA_CALL_ID, callId), FLAGS)
        val person = Person.Builder().setName(peer).setImportant(true).build()
        val notification = NotificationCompat.Builder(context, NotificationChannels.CALLS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(peer)
            .setContentText(if (kind == CallKind.Video) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setTimeoutAfter(RING_TIMEOUT_MS)
            .setFullScreenIntent(activity(3_002, ACTION_SHOW), true)
            .setContentIntent(activity(3_002, ACTION_SHOW))
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, activity(3_001, ACTION_ANSWER))
                .setIsVideo(kind == CallKind.Video))
            .build().apply { flags = flags or Notification.FLAG_INSISTENT } // ring until answered/declined/ended
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(INCOMING_ID, notification) }
    }

    /** [callId] null cancels whatever is ringing. */
    fun cancelIncoming(context: Context, callId: String? = null) {
        if (callId != null && callId != ringingCallId) return
        token = null
        ringingCallId = null
        context.getSystemService(NotificationManager::class.java)?.cancel(INCOMING_ID)
    }

    fun ongoing(context: Context, kind: CallKind, peer: String): Notification {
        val open = PendingIntent.getActivity(context, 3_004,
            Intent(context, MainActivity::class.java).setAction(ACTION_SHOW).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS)
        val hangup = PendingIntent.getBroadcast(context, 3_005,
            Intent(context, CallActionReceiver::class.java).setAction(ACTION_HANGUP), FLAGS)
        return NotificationCompat.Builder(context, NotificationChannels.CALLS_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(peer)
            .setContentText(if (kind == CallKind.Video) "Video call" else "Voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(Person.Builder().setName(peer).build(), hangup))
            .build()
    }

    private const val RING_TIMEOUT_MS = 50_000L // server rings for 45 s, then call_end
}

/** Decline (incoming) and Hang up (ongoing) from notifications. Not exported. */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {
    @Inject lateinit var calls: CallManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifications.ACTION_DECLINE -> {
                val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID) ?: return
                val pending = goAsync()
                calls.declineFromNotification(callId).invokeOnCompletion { pending.finish() }
            }
            CallNotifications.ACTION_HANGUP -> calls.hangup()
        }
    }
}
