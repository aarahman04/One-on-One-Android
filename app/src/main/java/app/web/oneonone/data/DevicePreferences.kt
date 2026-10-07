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
}

@Singleton
class DevicePreferences @Inject constructor(@ApplicationContext context: Context) : DeviceStore {
    private val store = context.devicePreferences
    private val age = booleanPreferencesKey("ageVerified")
    private val consent = stringPreferencesKey("termsAcceptedAt")
    private val pushToken = stringPreferencesKey("pushToken")
    override suspend fun gates() = store.data.first().let { Gates(it[age] == true, it[consent] != null) }
    override suspend fun verifyAge() { store.edit { it[age] = true } }
    override suspend fun acceptTerms() { store.edit { it[consent] = Instant.now().toString() } }
    override suspend fun pushToken(): String? = store.data.first()[pushToken]
    override suspend fun savePushToken(token: String?) { store.edit {
        if (token == null) it.remove(pushToken) else it[pushToken] = token
    } }
}

data class Gates(val ageVerified: Boolean = false, val termsAccepted: Boolean = false)
