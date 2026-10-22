package app.web.oneonone.data.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidNarrationAudio @Inject constructor(@ApplicationContext private val context: Context) : NarrationAudio {
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var request: AudioFocusRequest? = null
    override fun focus(onLost: () -> Unit): Boolean {
        val manager = context.getSystemService(AudioManager::class.java)
        check(manager.mode == AudioManager.MODE_NORMAL) { "Finish your call before reading a letter aloud." }
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ if (request != null && it != AudioManager.AUDIOFOCUS_GAIN) onLost() }, main).build()
        request = next
        if (manager.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true
        request = null; return false
    }
    override fun abandonFocus() {
        val old = request; request = null
        if (old != null) context.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(old)
    }
    override suspend fun prepare(file: File, completed: () -> Unit, failed: () -> Unit): NarrationPlayer {
        var input: FileInputStream? = null
        var pending: MediaNarrationPlayer? = null
        try {
            withContext(Dispatchers.IO) { input = FileInputStream(file) }
            // Keep every MediaPlayer call on its owning Looper; preparation itself is asynchronous.
            val player = MediaNarrationPlayer(MediaPlayer()).also { pending = it }
            player.player.setAudioAttributes(attributes)
            player.player.setDataSource(checkNotNull(input).fd)
            withContext(Dispatchers.IO) { input.close(); input = null }
            return suspendCancellableCoroutine<NarrationPlayer> { continuation ->
                var prepared = false
                player.player.setOnPreparedListener {
                    prepared = true
                    if (continuation.isActive) continuation.resume(player)
                }
                player.player.setOnCompletionListener { completed() }
                player.player.setOnErrorListener { _, _, _ ->
                    if (prepared) failed()
                    else if (continuation.isActive) continuation.resumeWithException(IOException("Couldn't prepare narration."))
                    true
                }
                player.player.prepareAsync()
            }.also { pending = null }
        } finally {
            pending?.release()
            if (input != null) withContext(NonCancellable + Dispatchers.IO) { input.close() }
        }
    }
    override suspend fun deviceVoice(text: String, completed: () -> Unit, failed: () -> Unit): NarrationPlayer {
        val ready = CompletableDeferred<Int>()
        val tts = TextToSpeech(context) { ready.complete(it) }
        try {
            if (withTimeoutOrNull(10_000) { ready.await() } != TextToSpeech.SUCCESS) throw IOException("Device voice unavailable.")
            tts.setAudioAttributes(attributes)
            return DeviceVoicePlayer(tts, text, main, completed, failed)
        } catch (error: Exception) { tts.shutdown(); throw error }
        finally { ready.cancel() }
    }
}

private class MediaNarrationPlayer(val player: MediaPlayer) : NarrationPlayer {
    private var released = false
    override val positionMs get() = if (released) 0 else runCatching { player.currentPosition }.getOrDefault(0)
    override val durationMs get() = if (released) 0 else runCatching { player.duration }.getOrDefault(0)
    override fun start() { if (!released) player.start() }
    override fun pause() { if (!released && player.isPlaying) player.pause() }
    override fun link(next: NarrationPlayer?) { if (!released) player.setNextMediaPlayer((next as? MediaNarrationPlayer)?.player) }
    override fun release() { if (!released) { released = true; player.release() } }
}

/** Device engines have no pause API; resume from their latest reported word boundary. */
private class DeviceVoicePlayer(private val tts: TextToSpeech, private val text: String, private val main: Handler,
                                private val completed: () -> Unit, private val failed: () -> Unit) : NarrationPlayer {
    override val positionMs = 0
    override val durationMs = 0
    private var offset = 0
    private var run = 0
    private var released = false
    private var playing = false
    override fun start() {
        if (released || playing) return
        playing = true
        val token = ++run
        val base = offset
        val chunks = text.drop(base).chunked(TextToSpeech.getMaxSpeechInputLength() - 1)
        val offsets = IntArray(chunks.size)
        for (i in 1 until chunks.size) offsets[i] = offsets[i - 1] + chunks[i - 1].length
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val index = utteranceId?.substringAfterLast('-')?.toIntOrNull() ?: return
                main.post { if (run == token && !released && index in offsets.indices) offset = base + offsets[index] + start }
            }
            override fun onDone(utteranceId: String?) {
                val index = utteranceId?.substringAfterLast('-')?.toIntOrNull() ?: return
                main.post { if (run == token && !released && index in chunks.indices) {
                    offset = base + offsets[index] + chunks[index].length
                    if (index == chunks.lastIndex) { playing = false; completed() }
                } }
            }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {
                main.post { if (run == token && !released) { playing = false; failed() } }
            }
        })
        chunks.forEachIndexed { i, chunk ->
            if (tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$token-$i") == TextToSpeech.ERROR) {
                playing = false; failed(); return
            }
        }
        if (chunks.isEmpty()) { playing = false; completed() }
    }
    override fun pause() { if (!released && playing) { playing = false; run++; tts.stop() } }
    override fun link(next: NarrationPlayer?) {}
    override fun release() { if (!released) { released = true; run++; tts.shutdown() } }
}
