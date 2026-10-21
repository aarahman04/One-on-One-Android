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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * App-wide (singleton) so the device TextToSpeech engine is created once, warmed when a letter opens and reused by every
 * play: binding an engine takes a second or more. Releasing a player stops its speech and keeps the engine warm.
 * Every engine call is on the main thread.
 */
@Singleton
class AndroidNarrationAudio @Inject constructor(@ApplicationContext private val context: Context) : NarrationAudio {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var engineReady: CompletableDeferred<Int>? = null
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
    override fun prewarm() { warmEngine() }
    private fun warmEngine(): Pair<TextToSpeech, CompletableDeferred<Int>> {
        val existing = engine; val ready = engineReady
        if (existing != null && ready != null) return existing to ready
        val started = CompletableDeferred<Int>()
        // The constructor only binds the engine service; initialisation completes on this callback.
        val tts = TextToSpeech(context) { started.complete(it) }
        engine = tts; engineReady = started
        return tts to started
    }
    private fun shutdownEngine() {
        val old = engine; engine = null
        engineReady?.complete(TextToSpeech.ERROR); engineReady = null
        old?.shutdown()
    }
    override suspend fun deviceVoice(text: String, completed: () -> Unit, failed: () -> Unit): NarrationPlayer {
        val (tts, ready) = warmEngine()
        if (withTimeoutOrNull(10_000) { ready.await() } != TextToSpeech.SUCCESS) {
            // A failed or stuck engine is dropped so the next attempt binds a fresh one.
            if (engine === tts) shutdownEngine()
            throw IOException("Device voice unavailable.")
        }
        if (engine !== tts) throw IOException("Device voice unavailable.") // shut down while starting
        tts.setAudioAttributes(attributes)
        return DeviceVoicePlayer(tts, text, main, completed, failed)
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
    private val utterancePrefix = UUID.randomUUID().toString()
    override fun start() {
        if (released || playing) return
        playing = true
        val token = ++run
        val base = offset
        val chunks = text.drop(base).chunked(TextToSpeech.getMaxSpeechInputLength() - 1)
        val offsets = IntArray(chunks.size)
        for (i in 1 until chunks.size) offsets[i] = offsets[i - 1] + chunks[i - 1].length
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            private fun index(id: String?) = id?.takeIf { it.startsWith("$utterancePrefix-$token-") }?.substringAfterLast('-')?.toIntOrNull()
            override fun onStart(utteranceId: String?) {}
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val index = index(utteranceId) ?: return
                main.post { if (run == token && !released && index in offsets.indices) offset = base + offsets[index] + start }
            }
            override fun onDone(utteranceId: String?) {
                val index = index(utteranceId) ?: return
                main.post { if (run == token && !released && index in chunks.indices) {
                    offset = base + offsets[index] + chunks[index].length
                    if (index == chunks.lastIndex) { playing = false; completed() }
                } }
            }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {
                if (index(utteranceId) == null) return
                main.post { if (run == token && !released) { playing = false; failed() } }
            }
        })
        chunks.forEachIndexed { i, chunk ->
            if (tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$utterancePrefix-$token-$i") == TextToSpeech.ERROR) {
                tts.stop(); playing = false; failed(); return
            }
        }
        if (chunks.isEmpty()) { playing = false; completed() }
    }
    override fun pause() { if (!released && playing) { playing = false; run++; tts.stop() } }
    override fun link(next: NarrationPlayer?) {}
    // The engine is shared (AndroidNarrationAudio), so a released player only silences it.
    override fun release() { if (!released) { released = true; if (playing) tts.stop(); playing = false; run++ } }
}
