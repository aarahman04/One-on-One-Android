package app.web.oneonone.data.media

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class DictationPhase { Idle, Starting, Recording, Transcribing, DeviceListening, DeviceDialog }
data class DictationState(val phase: DictationPhase = DictationPhase.Idle, val seconds: Int = 0,
                          val partial: String = "", val token: Long = 0) {
    val active get() = phase != DictationPhase.Idle
}
interface DeviceSpeech {
    val available: Boolean
    fun start(text: (String) -> Unit, partial: (String) -> Unit, failed: (String) -> Unit)
    fun stop()
    fun cancel()
}

/** Recording and upload share one cancellable session; successful text is delivered, never sent. */
class SpeechTranscriber(private val api: SpeechGateway, private val recorder: DictationRecorder, private val device: DeviceSpeech,
                        private val scope: CoroutineScope, private val deliver: (String) -> Unit, private val notice: (String) -> Unit,
                        private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val mutableState = MutableStateFlow(DictationState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var session: Job? = null
    private var timer: Job? = null
    private var cleanup: Job? = null
    private var clip: File? = null

    fun start() {
        if (state.value.active) return
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
                        if (state.value.seconds >= DICTATION_MAX_MS / 1_000) { stop(); break }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (token == generation) { this@SpeechTranscriber.cancel(); notice(error.message ?: "Couldn't start recording.") } }
        }
    }

    fun stop() {
        if (state.value.phase == DictationPhase.DeviceListening) {
            device.stop(); mutableState.value = state.value.copy(phase = DictationPhase.Transcribing); return
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
                if (!recorded.duration.isFinite() || recorded.duration > 120) throw SpeechFailure(413, "too_large")
                val result = api.transcribe(recorded.file)
                if (token == generation) {
                    if (result.text.isBlank()) throw SpeechFailure(422, "no_speech")
                    this@SpeechTranscriber.cancel(); deliver(result.text.trim())
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token != generation) return@launch
                if (canUseDeviceSpeech(error)) fallback()
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

    private fun fallback() {
        cancel()
        val previous = cleanup
        val token = ++generation
        mutableState.value = DictationState(if (device.available) DictationPhase.DeviceListening else DictationPhase.DeviceDialog, token = token)
        notice("Using device speech recognition")
        newSession().launch {
            previous?.join()
            if (token != generation || !device.available) return@launch
            try {
                device.start({ text -> deviceResult(token, text) }, { text ->
                    if (token == generation) mutableState.value = state.value.copy(partial = text)
                }, { message -> if (token == generation) { this@SpeechTranscriber.cancel(); notice(message) } })
            } catch (error: Exception) { if (token == generation) { this@SpeechTranscriber.cancel(); notice("Speech recognition isn't available on this phone.") } }
        }
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
}
