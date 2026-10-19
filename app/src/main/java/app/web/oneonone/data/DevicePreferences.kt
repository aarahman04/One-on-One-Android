package app.web.oneonone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.devicePreferences by preferencesDataStore("device")

interface DeviceStore {
    /** Age/Terms gates belong to one auth user, so re-signing into the same account skips them. */
    suspend fun gates(owner: String): Gates
    suspend fun verifyAge(owner: String)
    suspend fun acceptTerms(owner: String)
    suspend fun resetGates()
    suspend fun pushToken(): String?
    suspend fun savePushToken(token: String?)
    suspend fun onboardingSeen(): Boolean
    suspend fun markOnboardingSeen()
    suspend fun claimNotification(messageId: String): Boolean
    /** True at most once per [NOTIFICATION_REMINDER_MS]; a true result starts the next wait. */
    suspend fun claimNotificationReminder(now: Long): Boolean = false
    val theme: Flow<String>
    suspend fun setTheme(value: String)
    suspend fun letterSignature(): String
    suspend fun saveLetterSignature(value: String)
    /** Chat text size step (0..3, 1 = default); see ChatTextScales. */
    val chatTextStep: Flow<Int> get() = flowOf(1)
    /** Id of the connection [owner]'s partner left, as last seen from the server; survives process death. */
    suspend fun endedConnection(owner: String): String? = null
    suspend fun saveEndedConnection(owner: String, id: String?) {}
    suspend fun setChatTextStep(value: Int) {}
}

@Singleton
class DevicePreferences @Inject constructor(@ApplicationContext context: Context) : DeviceStore {
    private val store = context.devicePreferences
    private val age = booleanPreferencesKey("ageVerified")
    private val consent = stringPreferencesKey("termsAcceptedAt")
    private val gateOwner = stringPreferencesKey("gatesOwner")
    private val pushToken = stringPreferencesKey("pushToken")
    private val onboarding = booleanPreferencesKey("notificationOnboardingSeen")
    private val handledPushes = stringPreferencesKey("handledMessagePushes")
    private val themeKey = stringPreferencesKey("appearanceTheme")
    private val textStepKey = intPreferencesKey("chatTextStep")
    private val signature = stringPreferencesKey("letterFrom")
    private val reminderAt = longPreferencesKey("notificationReminderAt")
    private val endedKey = stringPreferencesKey("endedConnection")
    override suspend fun endedConnection(owner: String) =
        store.data.first()[endedKey]?.split('|')?.takeIf { it.size == 2 && it[0] == owner }?.get(1)
    override suspend fun saveEndedConnection(owner: String, id: String?) { store.edit {
        if (id == null) it.remove(endedKey) else it[endedKey] = "$owner|$id"
    } }
    override val theme = store.data.map { it[themeKey] ?: "dark" }
    override suspend fun setTheme(value: String) {
        require(value in setOf("light", "dark"))
        store.edit { it[themeKey] = value }
    }
    override val chatTextStep = store.data.map { (it[textStepKey] ?: 1).coerceIn(0, 3) }
    override suspend fun setChatTextStep(value: Int) { store.edit { it[textStepKey] = value.coerceIn(0, 3) } }
    override suspend fun letterSignature() = store.data.first()[signature].orEmpty()
    override suspend fun saveLetterSignature(value: String) { store.edit { it[signature] = value.trim().take(40) } }
    override suspend fun gates(owner: String): Gates {
        var gates = Gates()
        store.edit {
            // Gates saved before owners existed were reset on every sign-out, so they belong to this session.
            if (it[gateOwner] == null && (it[age] == true || it[consent] != null)) it[gateOwner] = owner
            if (it[gateOwner] == owner) gates = Gates(it[age] == true, it[consent] != null)
        }
        return gates
    }
    override suspend fun verifyAge(owner: String) { store.edit {
        if (it[gateOwner] != owner) it.remove(consent)
        it[gateOwner] = owner; it[age] = true
    } }
    override suspend fun acceptTerms(owner: String) { store.edit {
        if (it[gateOwner] != owner) it.remove(age)
        it[gateOwner] = owner; it[consent] = Instant.now().toString()
    } }
    override suspend fun resetGates() { store.edit { it.remove(age); it.remove(consent); it.remove(gateOwner) } }
    override suspend fun pushToken(): String? = store.data.first()[pushToken]
    override suspend fun savePushToken(token: String?) { store.edit {
        if (token == null) it.remove(pushToken) else it[pushToken] = token
    } }
    override suspend fun onboardingSeen() = store.data.first()[onboarding] == true
    // The onboarding page already asked, so the first reminder waits a full interval.
    override suspend fun markOnboardingSeen() { store.edit { it[onboarding] = true; it[reminderAt] = System.currentTimeMillis() } }
    override suspend fun claimNotificationReminder(now: Long): Boolean {
        var claimed = false
        store.edit { if (notificationReminderDue(it[reminderAt], now)) { it[reminderAt] = now; claimed = true } }
        return claimed
    }
    override suspend fun claimNotification(messageId: String): Boolean {
        var claimed = false
        store.edit { preferences ->
            val ids = preferences[handledPushes].orEmpty().split('|').filter { it.isNotEmpty() }
            if (messageId !in ids) {
                // ponytail: remember the newest 256 IDs; use a Room notification ledger if older FCM replay matters.
                preferences[handledPushes] = (ids + messageId).takeLast(256).joinToString("|")
                claimed = true
            }
        }
        return claimed
    }
}

const val NOTIFICATION_REMINDER_MS = 3L * 24 * 60 * 60 * 1000

internal fun notificationReminderDue(last: Long?, now: Long): Boolean = last == null || now - last >= NOTIFICATION_REMINDER_MS || now < last

data class Gates(val ageVerified: Boolean = false, val termsAccepted: Boolean = false)
