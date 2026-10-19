package app.web.oneonone.data.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The existing platform recognizer remains the fallback, owned by the speech ViewModel. */
class AndroidDeviceSpeech @Inject constructor(@ApplicationContext private val context: Context) : DeviceSpeech {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    override val available get() = SpeechRecognizer.isRecognitionAvailable(context)
    override fun start(text: (String) -> Unit, partial: (String) -> Unit, failed: (String) -> Unit) {
        if (recognizer != null) return
        val token = ++generation
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            private fun Bundle?.first() = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            override fun onPartialResults(results: Bundle?) { if (token == generation) partial(results.first().orEmpty()) }
            override fun onResults(results: Bundle?) {
                if (token != generation) return
                val words = results.first().orEmpty(); cancel()
                if (words.isBlank()) failed("Didn't catch that. Try again.") else text(words.trim())
            }
            override fun onError(error: Int) {
                if (token != generation) return
                cancel()
                failed(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access was denied. Allow it in App info → Permissions to dictate."
                    SpeechRecognizer.ERROR_AUDIO -> "The microphone isn't available. Close other apps using it and try again."
                    else -> "Couldn't use speech recognition. Please try again."
                })
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        created.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
    }
    override fun stop() { recognizer?.stopListening() }
    override fun cancel() { generation++; recognizer?.destroy(); recognizer = null }
}
