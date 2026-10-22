package app.web.oneonone.data

import app.web.oneonone.data.api.BearerInterceptor
import app.web.oneonone.data.media.SpeechApi
import app.web.oneonone.data.media.LetterDraft
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SpeechApiTest {
    @Test fun previewPostsDraftWithTheSamePartProtocolAndAuthentication() = runBlocking {
        val draft = LetterDraft("you", "Hello, world!", "me")
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(FakeSession())).addInterceptor { chain ->
            val request = chain.request()
            assertEquals("POST", request.method)
            assertEquals("Bearer old", request.header("Authorization"))
            assertEquals("/api/connections/connection/narration-preview?part=2", request.url.encodedPath + "?" + request.url.encodedQuery)
            val body = Buffer(); request.body!!.writeTo(body)
            assertEquals(Json.encodeToString(LetterDraft.serializer(), draft), body.readUtf8())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("X-Narration-Part", "2").header("X-Narration-Parts", "3")
                .body(byteArrayOf(1, 2).toResponseBody("audio/ogg".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(SpeechApi::class.java)
        val response = api.previewNarration("connection", draft, 2)
        assertEquals("2", response.headers()["X-Narration-Part"])
        response.body()!!.close()
    }

    @Test fun narrationUsesAuthenticatedGetWithIdsAndPartOnly() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(FakeSession())).addInterceptor { chain ->
            val request = chain.request()
            assertEquals("GET", request.method)
            assertEquals("Bearer old", request.header("Authorization"))
            assertEquals("/api/connections/connection/messages/letter/narration?part=2", request.url.encodedPath + "?" + request.url.encodedQuery)
            assertNull(request.body)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("X-Narration-Part", "2").header("X-Narration-Parts", "3")
                .body(byteArrayOf(1, 2).toResponseBody("audio/ogg".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(SpeechApi::class.java)
        val response = api.narration("connection", "letter", 2)
        assertEquals("2", response.headers()["X-Narration-Part"])
        assertEquals("3", response.headers()["X-Narration-Parts"])
        response.body()!!.use { assertArrayEquals(byteArrayOf(1, 2), it.bytes()) }
    }

    @Test fun transcriptionUsesAuthenticatedRawMp4WithoutMultipartOrLetterText() = runBlocking {
        val bytes = byteArrayOf(4, 5, 6)
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(FakeSession())).addInterceptor { chain ->
            val request = chain.request()
            assertEquals("POST", request.method); assertEquals("/api/transcribe", request.url.encodedPath)
            assertNull(request.url.encodedQuery); assertEquals("Bearer old", request.header("Authorization"))
            assertEquals("audio/mp4", request.body!!.contentType().toString())
            val buffer = Buffer(); request.body!!.writeTo(buffer); assertArrayEquals(bytes, buffer.readByteArray())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"text":"Good morning","language":"en","durationMs":2000}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(SpeechApi::class.java)
        val result = api.transcribe(bytes.toRequestBody("audio/mp4".toMediaType())).body()!!
        assertEquals("Good morning", result.text); assertEquals("en", result.language); assertEquals(2_000L, result.durationMs)
    }
}
