package app.web.oneonone.alarm

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarm ids already dealt with on this device (acked, cancelled, silenced, auto-cleared).
 * A handled id never rings again — on resync, relaunch, history load or a late FCM.
 * SharedPreferences rather than DataStore: the FCM thread and the service need a
 * synchronous answer before deciding to start a foreground service.
 */
@Singleton
class HandledAlarms @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("alarm_handled", Context.MODE_PRIVATE)

    @Synchronized fun contains(alarmId: String): Boolean = alarmId in ids()

    @Synchronized fun add(alarmId: String) {
        val ids = ids().filter { it != alarmId } + alarmId
        prefs.edit { putString(KEY, ids.takeLast(MAX).joinToString(",")) }
    }

    private fun ids(): List<String> = prefs.getString(KEY, "").orEmpty().split(',').filter { it.isNotBlank() }

    private companion object {
        const val KEY = "ids"
        const val MAX = 50
    }
}
