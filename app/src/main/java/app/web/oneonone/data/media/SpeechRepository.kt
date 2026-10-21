package app.web.oneonone.data.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import java.io.File
import java.io.IOException
import javax.inject.Inject

// Under the speech service's 120 s limit, so a clip stopped at the cap is still transcribed.
const val DICTATION_MAX_MS = 118_000
const val DICTATION_MAX_BYTES = 3_000_000L

@Serializable data class Transcription(val text: String, val language: String, val durationMs: Long)
data class NarrationPart(val file: File, val index: Int, val total: Int)
class SpeechFailure(val status: Int, val code: String) : IOException(code)

internal fun canUseDeviceSpeech(error: Exception) = when (error) {
    // Includes the server's daily speech budget (contract §11): the device voice/recognizer is free.
    is SpeechFailure -> error.status == 503 || error.status == 504 || (error.status == 429 && error.code == "quota_exceeded")
    is IOException -> true
    else -> false
}

interface SpeechApi {
    @Streaming @GET("api/connections/{connectionId}/messages/{messageId}/narration")
    suspend fun narration(@Path("connectionId") connection: String, @Path("messageId") message: String,
                          @Query("part") part: Int): Response<ResponseBody>
    @POST("api/transcribe") suspend fun transcribe(@Body audio: RequestBody): Response<Transcription>
}

interface SpeechGateway {
    suspend fun narration(connection: String, message: String, part: Int): NarrationPart
    suspend fun transcribe(file: File): Transcription
}

/** Uses the authenticated Retrofit client. Letter text never leaves the device in a narration request. */
class SpeechRepository @Inject constructor(@ApplicationContext private val context: Context, private val api: SpeechApi) : SpeechGateway {
    override suspend fun narration(connection: String, message: String, part: Int): NarrationPart {
        require(part >= 0)
        val response = api.narration(connection, message, part)
        if (!response.isSuccessful) throw failure(response)
        val body = response.body() ?: throw IOException("Empty narration.")
        var file: File? = null
        try {
            val index = response.headers()["X-Narration-Part"]?.toIntOrNull()
            val total = response.headers()["X-Narration-Parts"]?.toIntOrNull()
            if (index != part || total == null || total <= part || body.contentType()?.subtype != "ogg") throw IOException("Invalid narration response.")
            // Closing the streaming body on cancellation also interrupts an in-flight socket read.
            return coroutineScope {
                val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { body.close() }
                }
                try {
                    withContext(Dispatchers.IO) {
                        val directory = File(context.cacheDir, "narration").apply { mkdirs() }
                        val target = File.createTempFile("letter-", ".ogg", directory).also { file = it }
                        body.byteStream().use { input -> target.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        } }
                        if (target.length() == 0L) throw IOException("Empty narration.")
                        NarrationPart(target, part, total)
                    }
                } finally { closer.cancelAndJoin() }
            }
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { file?.delete() }
            throw error
        } finally { withContext(NonCancellable + Dispatchers.IO) { body.close() } }
    }

    override suspend fun transcribe(file: File): Transcription {
        withContext(Dispatchers.IO) {
            if (file.length() !in 1..DICTATION_MAX_BYTES) throw SpeechFailure(413, "too_large")
        }
        val response = api.transcribe(file.asRequestBody("audio/mp4".toMediaType()))
        if (!response.isSuccessful) throw failure(response)
        val result = response.body() ?: throw IOException("Empty transcription.")
        if (result.text.isBlank()) throw SpeechFailure(422, "no_speech")
        return result.copy(text = result.text.trim())
    }

    private suspend fun failure(response: Response<*>): SpeechFailure = withContext(Dispatchers.IO) {
        val code = response.errorBody()?.use { body -> runCatching {
            Json.parseToJsonElement(body.string()).jsonObject["code"]?.jsonPrimitive?.content
        }.getOrNull() }.orEmpty()
        SpeechFailure(response.code(), code)
    }
}

interface DictationRecorder {
    suspend fun startDictation(onLimit: () -> Unit)
    suspend fun stopDictation(): VoiceClip?
    suspend fun cancelDictation()
}

internal fun appendDictationText(draft: String, text: String): String {
    val words = text.trim()
    return when {
        words.isEmpty() -> draft
        draft.isEmpty() || draft.last().isWhitespace() -> draft + words
        else -> "$draft $words"
    }.take(4_000)
}
