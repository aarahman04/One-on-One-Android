package app.web.oneonone.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.web.oneonone.MainActivity
import app.web.oneonone.R
import app.web.oneonone.push.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

/**
 * Rings the emergency alarm natively: looping USAGE_ALARM sound + vibration, an ongoing
 * notification with a full-screen intent. Ported from the Capacitor AlarmForegroundService.
 * Every stop path ends in [stopRinging], which removes the notification with the service.
 */
@AndroidEntryPoint
class AlarmService : Service() {
    @Inject lateinit var handled: HandledAlarms

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoClear = Runnable { ringingId.value?.let { handled.add(it) }; stopRinging() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID)
        when (intent?.action) {
            ACTION_RING -> if (alarmId != null) ring(alarmId, intent.getLongExtra(EXTRA_RAISED_AT, System.currentTimeMillis()))
            ACTION_STOP -> {
                // A stop for an older alarm must not silence the live one.
                if (alarmId == null || ringingId.value == null || alarmId == ringingId.value) {
                    alarmId?.let { handled.add(it) }
                    ringingId.value?.let { handled.add(it) }
                    stopRinging()
                } else if (ringingId.value == null) stopSelf()
            }
            else -> if (ringingId.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(alarmId: String, raisedAt: Long) {
        val remaining = raisedAt + AlarmPolicy.WINDOW_MS - System.currentTimeMillis()
        if (handled.contains(alarmId) || remaining <= 0) {
            if (ringingId.value == null) stopSelf()
            return
        }
        if (ringingId.value == alarmId) return // repeat raise via FCM + socket: already ringing
        activeToken = UUID.randomUUID().toString()
        ringingId.value = alarmId
        fallbackId = null // the service notification replaces it (same id)
        val notification = buildNotification(this, alarmId, fallback = false)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        startSound()
        handler.removeCallbacks(autoClear)
        handler.postDelayed(autoClear, remaining)
    }

    private fun startSound() {
        stopSound()
        player = runCatching {
            // Attributes BEFORE setDataSource/prepare route it to the ALARM stream (loud, DND-aware).
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                resources.openRawResourceFd(R.raw.alarm).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull() // best effort: the heads-up/full-screen notification still alerts
        vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN, 0))
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
    }

    private fun stopRinging() {
        handler.removeCallbacks(autoClear)
        stopSound()
        ringingId.value = null
        fallbackId = null
        activeToken = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) // also a fallback notification
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoClear)
        stopSound()
        ringingId.value = null
        activeToken = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_RING = "app.web.oneonone.alarm.RING"
        const val ACTION_STOP = "app.web.oneonone.alarm.STOP"
        const val EXTRA_ALARM_ID = "alarmId"
        const val EXTRA_RAISED_AT = "raisedAt"
        // MainActivity extras. SILENCE = user tapped (stop, open chat). SHOW = full-screen intent.
        const val EXTRA_SILENCE = "alarmSilence"
        const val EXTRA_SHOW = "alarmShow"
        const val EXTRA_TOKEN = "alarmToken"
        const val NOTIFICATION_ID = 2
        private val VIBRATE_PATTERN = longArrayOf(0, 300, 150, 300, 150, 300, 150, 300)

        private val ringingId = MutableStateFlow<String?>(null)
        /** The alarm currently ringing on this device, or null. */
        val ringing: StateFlow<String?> = ringingId.asStateFlow()

        /**
         * Per-ring secret, embedded only in PendingIntents this app builds. MainActivity is
         * exported, so it acts on alarm extras only when the token matches the live one.
         */
        @Volatile var activeToken: String? = null
            private set

        fun tokenValid(token: String?): Boolean = activeToken != null && activeToken == token

        fun ringIntent(context: Context, alarmId: String, raisedAt: Long) =
            Intent(context, AlarmService::class.java).setAction(ACTION_RING)
                .putExtra(EXTRA_ALARM_ID, alarmId).putExtra(EXTRA_RAISED_AT, raisedAt)

        fun stopIntent(context: Context, alarmId: String?) =
            Intent(context, AlarmService::class.java).setAction(ACTION_STOP).apply { alarmId?.let { putExtra(EXTRA_ALARM_ID, it) } }

        /** Used when a foreground-service start is refused: a loud notification instead of the player. */
        internal fun postFallback(context: Context, alarmId: String) {
            activeToken = UUID.randomUUID().toString()
            fallbackId = alarmId
            runCatching {
                context.getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(context, alarmId, fallback = true))
            } // POST_NOTIFICATIONS denied: nothing more we can do
        }

        /** The alarm whose fallback notification is showing (no service running), or null. */
        @Volatile var fallbackId: String? = null
            private set

        internal fun clearFallback(context: Context) {
            fallbackId = null
            if (ringingId.value == null) activeToken = null
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }

        internal fun buildNotification(context: Context, alarmId: String, fallback: Boolean): Notification {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            // Own action + request code: PendingIntent identity ignores extras, so without them this
            // would overwrite (or be overwritten by) the message notification's MainActivity intent.
            fun activity(code: Int, extra: String) = PendingIntent.getActivity(context, code,
                Intent(context, MainActivity::class.java).setAction("app.web.oneonone.alarm.$extra")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(extra, true).putExtra(EXTRA_TOKEN, activeToken).putExtra(EXTRA_ALARM_ID, alarmId),
                flags)
            val silence = PendingIntent.getService(context, 2_003, stopIntent(context, alarmId), flags)
            return NotificationCompat.Builder(context,
                if (fallback) NotificationChannels.ALARM_FALLBACK else NotificationChannels.ALARM_RING)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(context.getString(R.string.alarm_title))
                .setContentText(context.getString(R.string.alarm_text))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(!fallback)
                .setAutoCancel(fallback)
                .setTimeoutAfter(AlarmPolicy.WINDOW_MS)
                .setContentIntent(activity(2_001, EXTRA_SILENCE))
                .setFullScreenIntent(activity(2_002, EXTRA_SHOW), true)
                .addAction(0, context.getString(R.string.alarm_silence), silence)
                .build()
        }
    }
}
