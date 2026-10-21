package app.web.oneonone.ui.chat

import androidx.lifecycle.ViewModel
import app.web.oneonone.BuildConfig
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.DeviceStore
import app.web.oneonone.data.chat.ChatSession
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.media.*
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.data.model.text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.io.IOException

/** The whole field value to show: the text from before dictation began plus the dictated words. Live values precede the [final] one. */
data class DictatedText(val id: Long, val owner: ChatSession, val text: String, val final: Boolean = true)

@HiltViewModel
class SpeechViewModel @Inject constructor(api: SpeechGateway, private val audio: NarrationAudio, recorder: DictationRecorder, device: DeviceSpeech,
    private val preferences: DeviceStore, private val messages: MessageService) : ViewModel() {
    private val narrator = LetterNarrator(api, audio, viewModelScope, server = BuildConfig.SERVER_SPEECH)
    val narration = narrator.state
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()
    private var noticeJob: Job? = null
    private val mutableHint = MutableStateFlow(false)
    val dictationHint = mutableHint.asStateFlow()
    private val mutableText = MutableStateFlow<DictatedText?>(null)
    val text = mutableText.asStateFlow()
    private var textId = 0L
    private var dictationOwner: ChatSession? = null
    private var base = ""
    private val transcriber = SpeechTranscriber(api, recorder, device, viewModelScope, { words -> dictated(words, true) }, ::showNotice,
        server = BuildConfig.SERVER_SPEECH, live = { words -> dictated(words, false) })
    val dictation = transcriber.state

    init {
        viewModelScope.launch {
            var previous: ChatSession? = null
            messages.active.collect { active -> if (active != previous) {
                closeChat(); mutableText.value = null; previous = active
            } }
        }
        viewModelScope.launch { dictation.collect { if (!it.active) mutableHint.value = false } }
    }
    private fun dictated(words: String, final: Boolean) {
        dictationOwner?.takeIf { it == messages.active.value }?.let {
            mutableText.value = DictatedText(++textId, it, appendDictationText(base, words).take(DICTATION_FIELD_MAX), final)
        }
    }
    fun readLetter(connection: String, message: ChatMessage, voiceRecording: Boolean = false) {
        if (dictation.value.active || voiceRecording) { showNotice("Stop recording before reading a letter aloud."); return }
        if (messages.active.value?.connectionId != connection) { showNotice("This letter can't be read aloud"); return }
        narrator.toggle(connection, message.id, "Dear ${message.payload.text("to")}. ${message.content}. With love, ${message.payload.text("from")}.")
    }
    /** An unsent letter is read by the device voice only: server narration reads saved letters and unsent text stays on the phone. */
    fun readPreview(connection: String, to: String, body: String, from: String, voiceRecording: Boolean = false) {
        if (dictation.value.active || voiceRecording) { showNotice("Stop recording before reading a letter aloud."); return }
        narrator.toggle(connection, PREVIEW, "Dear $to. $body. With love, $from.", device = true)
    }
    /** Starts the device voice engine early so the first tap on a speaker plays without the engine's start-up wait. */
    fun prewarmVoice() = audio.prewarm()
    fun stopNarration(key: String? = null) { if (key == null || narration.value.key == key) narrator.stop() }
    fun startDictation(voiceRecording: Boolean, field: String = "") {
        if (voiceRecording) { showNotice("Stop your voice note before using /audio."); return }
        if (dictation.value.active) return
        dictationOwner = messages.active.value ?: return
        base = field
        narrator.stop(); transcriber.start()
        // "Works best in English" is about the server recognizer, so it is only claimed when that one records.
        viewModelScope.launch {
            if (dictation.first { it.phase != DictationPhase.Starting }.phase != DictationPhase.Recording) return@launch
            try { if (preferences.claimDictationHint() && dictation.value.active) mutableHint.value = true }
            catch (_: IOException) { if (dictation.value.active) mutableHint.value = true }
        }
    }
    fun stopDictation() = transcriber.stop()
    fun cancelDictation() { transcriber.cancel(); mutableHint.value = false }
    fun deviceResult(token: Long, text: String?) = transcriber.deviceResult(token, text)
    fun consumeText(id: Long) { if (mutableText.value?.id == id) mutableText.value = null }
    fun showNotice(message: String) {
        noticeJob?.cancel(); mutableNotice.value = message
        noticeJob = viewModelScope.launch { delay(4_000); mutableNotice.value = null }
    }
    fun background() {
        narrator.stop()
        // The recognizer Activity owns this one external flow; its result is still checked against the session token.
        if (dictation.value.phase != DictationPhase.DeviceDialog) transcriber.background()
    }
    fun closeChat() { narrator.stop(); cancelDictation(); dictationOwner = null }
    override fun onCleared() { narrator.stop(); transcriber.cancel() }

    companion object {
        const val PREVIEW = "preview"
        // The composer draft and the letter body share this limit.
        const val DICTATION_FIELD_MAX = 4_000
    }
}
