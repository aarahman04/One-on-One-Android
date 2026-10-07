package app.web.oneonone.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

object NotificationChannels {
    const val MESSAGES = "messages"
    const val ALARM = "alarm"
    const val CALLS = "calls"
    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Messages from your One on One"
            },
            NotificationChannel(ALARM, "Emergency alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Emergency alarms from your One on One"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            },
            NotificationChannel(CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming voice and video calls"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            },
        ))
    }
}
