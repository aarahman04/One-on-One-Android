package app.web.oneonone.data

import app.web.oneonone.data.api.AccountApi
import app.web.oneonone.data.api.ConnectionRequest
import app.web.oneonone.data.api.TokenBody
import app.web.oneonone.data.auth.AccountSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AccountMutationLock @Inject constructor() { val mutex = Mutex() }

@Singleton
class AccountRepository @Inject constructor(
    val api: AccountApi,
    private val auth: AccountSession,
    private val preferences: DeviceStore,
    private val mutations: AccountMutationLock = AccountMutationLock(),
) {
    suspend fun request(code: String) = api.request(ConnectionRequest(normalizeCode(code)))
    suspend fun signOut() = mutations.mutex.withLock {
        // Preserve the session on unregister failure so the user can retry safely.
        preferences.pushToken()?.let { api.unregister(TokenBody(it)) }
        preferences.savePushToken(null)
        auth.signOut()
    }
    suspend fun deleteAccount() = mutations.mutex.withLock {
        api.deleteAccount()
        preferences.savePushToken(null)
        auth.signOut()
    }
}

internal fun normalizeCode(code: String): String = code.trim().uppercase(java.util.Locale.ROOT).also {
    require(it.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}"))) {
        "Enter the full 8-character connection ID."
    }
}
