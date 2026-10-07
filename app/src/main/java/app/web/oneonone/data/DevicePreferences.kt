package app.web.oneonone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.devicePreferences by preferencesDataStore("device")

interface DeviceStore {
    suspend fun gates(): Gates
    suspend fun verifyAge()
    suspend fun acceptTerms()
    suspend fun pushToken(): String?
    suspend fun savePushToken(token: String?)
    suspend fun onboardingSeen(): Boolean
    suspend fun markOnboardingSeen()
    suspend fun claimNotification(messageId: String): Boolean
}

@Singleton
class DevicePreferences @Inject constructor(@ApplicationContext context: Context) : DeviceStore {
    private val store = context.devicePreferences
    private val age = booleanPreferencesKey("ageVerified")
    private val consent = stringPreferencesKey("termsAcceptedAt")
    private val pushToken = stringPreferencesKey("pushToken")
    private val onboarding = booleanPreferencesKey("notificationOnboardingSeen")
    private val handledPushes = stringPreferencesKey("handledMessagePushes")
    override suspend fun gates() = store.data.first().let { Gates(it[age] == true, it[consent] != null) }
    override suspend fun verifyAge() { store.edit { it[age] = true } }
    override suspend fun acceptTerms() { store.edit { it[consent] = Instant.now().toString() } }
    override suspend fun pushToken(): String? = store.data.first()[pushToken]
    override suspend fun savePushToken(token: String?) { store.edit {
        if (token == null) it.remove(pushToken) else it[pushToken] = token
    } }
    override suspend fun onboardingSeen() = store.data.first()[onboarding] == true
    override suspend fun markOnboardingSeen() { store.edit { it[onboarding] = true } }
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

data class Gates(val ageVerified: Boolean = false, val termsAccepted: Boolean = false)
