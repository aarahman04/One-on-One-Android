package app.web.oneonone.data.media

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class DictationPhase { Idle, Starting, Recording, Transcribing, DeviceListening, DeviceDialog }
data class DictationState(val phase: DictationPhase = DictationPhase.Idle, val seconds: Int = 0, val token: Long = 0) {
    val active get() = phase != DictationPhase.Idle
}
interface DeviceSpeech {
    val available: Boolean
    /** One recognition segment: [text] once with its final words (blank when nothing was heard), or [failed]. */
    fun start(text: (String) -> Unit, partial: (String) -> Unit, failed: (message: String, fatal: Boolean) -> Unit)
    fun stop()
    fun cancel()
}

/**
 * The device recognizer is preferred: it is restarted after every pause and streams [live] text (every final word plus the
 * current partial) until Stop, which [deliver]s the final words. Without one, server builds record one clip and upload it.
 * Successful text is delivered, never sent.
 */
class SpeechTranscriber(private val api: SpeechGateway, private val recorder: DictationRecorder, private val device: DeviceSpeech,
                        private val scope: CoroutineScope, private val deliver: (String) -> Unit, private val notice: (String) -> Unit,
                        private val io: CoroutineDispatcher = Dispatchers.IO, private val server: Boolean = true,
                        private val live: (String) -> Unit = {}) {
    private val mutableState = MutableStateFlow(DictationState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var session: Job? = null
    private var timer: Job? = null
    private var cleanup: Job? = null
    private var clip: File? = null
    private var heard = "" // final words of this device session
    private var pending = "" // the current segment's latest partial
    private var failures = 0

    fun start() {
        if (state.value.active) return
        if (!server || device.available) { listen(announce = false); return }
        val token = ++generation
        mutableState.value = DictationState(DictationPhase.Starting, token = token)
        val previous = cleanup
        val work = newSession()
        work.launch {
            try {
                previous?.join()
                recorder.startDictation { scope.launch { if (token == generation) stop() } }
                ensureActive()
                if (token != generation) return@launch
                mutableState.value = state.value.copy(phase = DictationPhase.Recording)
                timer = work.launch {
                    while (isActive) {
                        delay(1_000)
                        mutableState.value = state.value.copy(seconds = state.value.seconds + 1)
                        // At the cap the clip is transcribed, not discarded.
                        if (state.value.seconds >= DICTATION_MAX_MS / 1_000) { stop(); break }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (token == generation) { this@SpeechTranscriber.cancel(); notice(error.message ?: "Couldn't start recording.") } }
        }
    }

    fun stop() {
        if (state.value.phase == DictationPhase.DeviceListening) {
            val token = generation
            mutableState.value = state.value.copy(phase = DictationPhase.Transcribing)
            device.stop()
            // The last segment's result normally arrives at once; never leave the session hanging if it doesn't.
            session?.let { CoroutineScope(scope.coroutineContext + it).launch { delay(STOP_GRACE_MS); finish(token, null) } }
            return
        }
        if (state.value.phase != DictationPhase.Recording) return
        val token = generation
        timer?.cancel()
        mutableState.value = state.value.copy(phase = DictationPhase.Transcribing)
        val work = CoroutineScope(scope.coroutineContext + checkNotNull(session))
        work.launch {
            try {
                val recorded = recorder.stopDictation()
                if (recorded == null) throw SpeechFailure(422, "no_speech")
                clip = recorded.file
                val result = api.transcribe(recorded.file)
                if (token == generation) {
                    if (result.text.isBlank()) throw SpeechFailure(422, "no_speech")
                    this@SpeechTranscriber.cancel(); deliver(result.text.trim())
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token != generation) return@launch
                if (canUseDeviceSpeech(error)) listen()
                else {
                    this@SpeechTranscriber.cancel()
                    notice(when ((error as? SpeechFailure)?.status) {
                        422 -> "Didn't catch that. Try again."
                        413 -> "That's too long. Keep it under 2 minutes."
                        429 -> "Speech recognition is busy. Try again in a moment."
                        415 -> "Couldn't read that recording. Try again."
                        else -> "Couldn't transcribe that. Try again."
                    })
                }
            }
        }
    }

    private fun listen(announce: Boolean = true) {
        cancel()
        val previous = cleanup
        val token = ++generation
        mutableState.value = DictationState(if (device.available) DictationPhase.DeviceListening else DictationPhase.DeviceDialog, token = token)
        if (announce) notice("Using device speech recognition")
        newSession().launch {
            previous?.join()
            if (token == generation && device.available) segment(token)
        }
    }

    // The platform recognizer ends a segment at every pause; it is restarted until Stop or a fatal error.
    private fun segment(token: Long) {
        pending = ""
        try {
            device.start({ words -> segmentEnded(token, words) }, { words ->
                if (token == generation) { failures = 0; pending = words.trim(); live(appendDictationText(heard, pending)) }
            }, { message, fatal -> segmentFailed(token, message, fatal) })
        } catch (error: Exception) { finish(token, "Speech recognition isn't available on this phone.") }
    }

    private fun segmentEnded(token: Long, words: String) {
        if (token != generation) return
        // A blank final after partials keeps the partial: words already shown are never dropped.
        val final = words.trim().ifEmpty { pending }
        pending = ""
        if (final.isNotEmpty()) { failures = 0; heard = appendDictationText(heard, final); live(heard) }
        if (state.value.phase == DictationPhase.DeviceListening) segment(token) else finish(token, null)
    }

    private fun segmentFailed(token: Long, message: String, fatal: Boolean) {
        if (token != generation) return
        heard = appendDictationText(heard, pending); pending = ""
        when {
            state.value.phase != DictationPhase.DeviceListening -> finish(token, null)
            fatal || ++failures >= MAX_DEVICE_RETRIES -> finish(token, message)
            else -> session?.let { CoroutineScope(scope.coroutineContext + it).launch {
                delay(RETRY_DELAY_MS)
                if (token == generation && state.value.phase == DictationPhase.DeviceListening) segment(token)
            } }
        }
    }

    /** Ends the device session, keeping every word heard so far. */
    private fun finish(token: Long, message: String?) {
        if (token != generation) return
        val words = heard
        cancel()
        if (words.isNotBlank()) deliver(words)
        message?.let(notice)
    }

    fun deviceResult(token: Long, text: String?) {
        if (token != generation || !state.value.active) return
        cancel()
        if (!text.isNullOrBlank()) deliver(text.trim())
    }
    private fun newSession(): CoroutineScope {
        val job = SupervisorJob(scope.coroutineContext[Job]).also { session = it }
        return CoroutineScope(scope.coroutineContext + job)
    }
    fun cancel() {
        generation++; timer?.cancel(); device.cancel()
        heard = ""; pending = ""; failures = 0
        val old = session; session = null; old?.cancel()
        val file = clip; clip = null
        mutableState.value = DictationState()
        val previous = cleanup
        cleanup = scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) {
            previous?.join(); old?.join()
            withContext(io) {
                try { recorder.cancelDictation() } finally { file?.delete() }
            }
        } }
    }

    private companion object {
        const val STOP_GRACE_MS = 3_000L
        const val RETRY_DELAY_MS = 300L
        // Transient recognizer errors in a row (busy, network, client) before the session ends.
        const val MAX_DEVICE_RETRIES = 3
    }
}
