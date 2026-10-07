package app.web.oneonone.data

import app.web.oneonone.data.api.BearerInterceptor
import app.web.oneonone.data.auth.TokenSession
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.CyclicBarrier

class BearerInterceptorTest {
    @Test fun transientRefreshFailurePreservesSession() {
        val session = FakeSession()
        val unavailable = object : TokenSession by session {
            override suspend fun refreshToken(rejectedToken: String): String? = throw java.io.IOException("offline")
        }
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(unavailable))
            .addInterceptor { response(it.request(), 401) }.build()
        assertTrue(runCatching {
            client.newCall(Request.Builder().url("https://example.com/api/me").build()).execute()
        }.isFailure)
        assertEquals("old", session.token)
        assertEquals(0, session.invalidations)
    }

    @Test fun one401RefreshesOnceAndRetriesWithFreshBearer() {
        val session = FakeSession()
        val headers = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(session))
            .addInterceptor { chain ->
                headers += chain.request().header("Authorization")
                response(chain.request(), if (headers.size == 1) 401 else 200)
            }.build()
        client.newCall(Request.Builder().url("https://example.com/api/me").build()).execute().use {
            assertEquals(200, it.code)
        }
        assertEquals(listOf("Bearer old", "Bearer fresh"), headers)
        assertEquals(1, session.refreshes)
        assertEquals(0, session.invalidations)
    }

    @Test fun second401SignsOutWithoutThirdAttempt() {
        val session = FakeSession()
        var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(session))
            .addInterceptor { attempts++; response(it.request(), 401) }.build()
        client.newCall(Request.Builder().url("https://example.com/api/me").build()).execute().use {
            assertEquals(401, it.code)
        }
        assertEquals(2, attempts)
        assertEquals(1, session.invalidations)
        assertNull(session.token)
    }

    @Test fun simultaneous401sShareOneRefresh() {
        val session = FakeSession()
        val barrier = CyclicBarrier(2)
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(session))
            .addInterceptor {
                if (it.request().header("Authorization") == "Bearer old") {
                    barrier.await(); response(it.request(), 401)
                } else response(it.request(), 200)
            }.build()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { executor.submit<Int> {
                client.newCall(Request.Builder().url("https://example.com/api/me").build()).execute().use { it.code }
            } }
            results.forEach { assertEquals(200, it.get(10, java.util.concurrent.TimeUnit.SECONDS).toInt()) }
            assertEquals(1, session.refreshes)
        } finally { executor.shutdownNow() }
    }

    private fun response(request: Request, code: Int) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(code).message("test").body("{}".toResponseBody()).build()
}
