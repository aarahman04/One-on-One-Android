package app.web.oneonone.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.net.toUri
import app.web.oneonone.R

object NotificationChannels {
    const val MESSAGES = "messages"
    /** Silent: AlarmService's MediaPlayer (USAGE_ALARM, looping) owns the sound and vibration. */
    const val ALARM_RING = "alarm_ring"
    /** Own alarm sound: used only when a foreground-service start is refused and nothing else rings. */
    const val ALARM_FALLBACK = "alarm_fallback"
    const val CALLS = "calls"
    // Channel settings are fixed once created on a device, so the old sounding "alarm"
    // channel (from earlier builds) is removed rather than reused.
    private const val LEGACY_ALARM = "alarm"
    private val ALARM_VIBRATION = longArrayOf(0, 300, 150, 300, 150, 300, 150, 300)

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_ALARM)
        val alarmAudio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        manager.createNotificationChannels(listOf(
            NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Messages from your One on One"
            },
            NotificationChannel(ALARM_RING, "Emergency alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Emergency alarms from your One on One"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            },
            NotificationChannel(ALARM_FALLBACK, "Emergency alarms (backup)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Used when the alarm can't start its own ringing"
                setSound("android.resource://${context.packageName}/${R.raw.alarm}".toUri(), alarmAudio)
                enableVibration(true)
                vibrationPattern = ALARM_VIBRATION
                setBypassDnd(true)
            },
            NotificationChannel(CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming voice and video calls"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            },
        ))
    }
}
