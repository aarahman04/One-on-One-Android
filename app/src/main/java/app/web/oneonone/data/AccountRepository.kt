package app.web.oneonone.data

import app.web.oneonone.data.api.AccountApi
import app.web.oneonone.data.api.ConnectionRequest
import app.web.oneonone.data.api.Me
import app.web.oneonone.data.api.TokenBody
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.stickers.StickerLibrary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException

@Singleton
class AccountMutationLock @Inject constructor() { val mutex = Mutex() }

@Singleton
class AccountRepository @Inject constructor(
    val api: AccountApi,
    private val auth: AccountSession,
    private val preferences: DeviceStore,
    private val mutations: AccountMutationLock = AccountMutationLock(),
    private val stickers: StickerLibrary,
) {
    suspend fun request(code: String) = api.request(ConnectionRequest(normalizeCode(code)))
    @Volatile private var gatesPushed: Pair<String, Gates>? = null

    /**
     * Server gate timestamps (contract §10) are the truth; the device copy is only an offline cache for the same auth
     * user [owner]. A device answer the server lacks (first run after the update, or a fresh accept) is pushed up once.
     */
    suspend fun syncGates(owner: String, me: Me): Gates {
        val local = preferences.gates(owner)
        var age = me.ageVerifiedAt != null
        var terms = me.termsAcceptedAt != null
        val missingAge = local.ageVerified && !age
        val missingTerms = local.termsAccepted && !terms
        if ((missingAge || missingTerms) && gatesPushed != owner to local) {
            try {
                val saved = api.gates(buildJsonObject {
                    if (missingAge) put("ageVerified", true)
                    if (missingTerms) put("termsAccepted", true)
                })
                age = age || saved.ageVerifiedAt != null
                terms = terms || saved.termsAcceptedAt != null
                gatesPushed = owner to local
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error is HttpException && error.code() == 404) gatesPushed = owner to local
            }
        }
        if (age && !local.ageVerified) preferences.verifyAge(owner)
        if (terms && !local.termsAccepted) preferences.acceptTerms(owner)
        return Gates(age || local.ageVerified, terms || local.termsAccepted)
    }
    suspend fun signOut() = mutations.mutex.withLock {
        // Preserve the session on unregister failure so the user can retry safely.
        preferences.pushToken()?.let { api.unregister(TokenBody(it)) }
        preferences.savePushToken(null)
        stickers.clear()
        auth.signOut()
        // Age and Terms gates stay with this account; a different account is asked again (DeviceStore.gates).
    }
    suspend fun deleteAccount() = mutations.mutex.withLock {
        api.deleteAccount()
        preferences.savePushToken(null)
        stickers.clear()
        auth.signOut()
        // A re-created account is asked Age and Terms again.
        preferences.resetGates()
    }
}

internal fun normalizeCode(code: String): String = code.trim().uppercase(java.util.Locale.ROOT).also {
    require(it.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}"))) {
        "Enter the full 8-character connection ID."
    }
}
