package app.web.oneonone.data.api

import app.web.oneonone.data.auth.TokenSession
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class BearerInterceptor(private val session: TokenSession) : Interceptor {
    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { session.accessToken() } ?: throw IOException("Sign in to continue.")
        val request = chain.request()
        fun send(bearer: String) = chain.proceed(request.newBuilder()
            .header("Authorization", "Bearer $bearer").build())
        val response = send(token)
        if (response.code != 401) return response
        response.close()
        val refreshed = synchronized(refreshLock) {
            try { runBlocking { session.refreshToken(token) } }
            catch (error: Exception) {
                // A network failure is not evidence that the user's session is invalid.
                throw IOException("Couldn't refresh your session. Try again.", error)
            }
        }
        if (refreshed == null) {
            runBlocking { session.invalidate(token) }
            throw IOException("Session expired. Sign in again.")
        }
        val retried = send(refreshed)
        if (retried.code == 401) runBlocking { session.invalidate(refreshed) }
        return retried
    }
}
