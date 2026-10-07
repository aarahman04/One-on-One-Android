package app.web.oneonone.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import app.web.oneonone.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// The interceptor uses this tiny seam to test retries without an Android/Auth runtime.
interface TokenSession {
    suspend fun accessToken(): String?
    suspend fun refreshToken(rejectedToken: String): String?
    suspend fun invalidate(rejectedToken: String)
}

interface AccountSession : TokenSession {
    val signedIn: Flow<Boolean>
    suspend fun signIn(activityContext: Context)
    suspend fun signOut()
}

@Singleton
class AuthRepository @Inject constructor(@ApplicationContext context: Context) : AccountSession {
    private val credentials = CredentialManager.create(context)
    private val client = if (BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            httpEngine = OkHttp.create()
            install(Auth) {
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
        }
    } else null
    override val signedIn: Flow<Boolean> = client?.auth?.sessionStatus?.mapNotNull {
        when (it) {
            is SessionStatus.Authenticated -> true
            is SessionStatus.NotAuthenticated -> false
            else -> null // Refresh failures retain storage and do not mean sign-out.
        }
    } ?: flowOf(false)

    override suspend fun accessToken(): String? {
        client?.auth?.awaitInitialization()
        if (client != null && client.auth.sessionStatus.value !is SessionStatus.Authenticated &&
            client.auth.sessionStatus.value !is SessionStatus.NotAuthenticated) {
            throw IOException("Session refresh is temporarily unavailable.")
        }
        return client?.auth?.currentAccessTokenOrNull()
    }

    override suspend fun signIn(activityContext: Context) {
        val auth = checkNotNull(client) { "Configure Supabase before signing in." }.auth
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Configure the Google web client ID." }
        check(BuildConfig.API_URL.startsWith("https://")) { "Configure the deployed HTTPS API URL." }
        val rawNonce = UUID.randomUUID().toString()
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(sha256Hex(rawNonce)).build()
        val result = try {
            credentials.getCredential(activityContext,
                GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (error: NoCredentialException) {
            throw IllegalStateException("No Google account is available. Add an account and check this build's OAuth registration.", error)
        }
        val credential = result.credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google returned an unsupported credential."
        }
        auth.signInWith(IDToken) {
            provider = Google
            idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            nonce = rawNonce
        }
    }

    override suspend fun refreshToken(rejectedToken: String): String? {
        val auth = client?.auth ?: return null
        val current = accessToken() ?: return null
        if (current != rejectedToken) return current
        try { auth.refreshCurrentSession() } catch (error: RestException) {
            if (error.statusCode in setOf(400, 401, 403)) {
                invalidate(rejectedToken)
                return null
            }
            throw error
        }
        return auth.currentAccessTokenOrNull()
    }

    override suspend fun invalidate(rejectedToken: String) {
        val auth = client?.auth ?: return
        if (auth.currentAccessTokenOrNull() == null || auth.currentAccessTokenOrNull() == rejectedToken) auth.clearSession()
    }

    override suspend fun signOut() {
        // Drop local credentials even when Supabase's revocation request is unavailable.
        try { client?.auth?.signOut() } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            // Local sign-out still completes offline; access tokens expire server-side.
        } finally { client?.auth?.clearSession() }
        try { credentials.clearCredentialState(ClearCredentialStateRequest()) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { /* The Supabase session is already removed. */ }
    }
}

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
