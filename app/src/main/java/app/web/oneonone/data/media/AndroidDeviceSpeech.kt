package app.web.oneonone.data.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One platform recognizer per segment (main thread only); a destroyed one can't deliver stale callbacks into the next. */
class AndroidDeviceSpeech @Inject constructor(@ApplicationContext private val context: Context) : DeviceSpeech {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    override val available get() = SpeechRecognizer.isRecognitionAvailable(context)
    override fun start(text: (String) -> Unit, partial: (String) -> Unit, failed: (message: String, fatal: Boolean) -> Unit) {
        cancel()
        val token = ++generation
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            private fun Bundle?.first() = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            override fun onPartialResults(results: Bundle?) { if (token == generation) partial(results.first().orEmpty()) }
            override fun onResults(results: Bundle?) {
                if (token != generation) return
                val words = results.first().orEmpty(); cancel()
                text(words.trim())
            }
            override fun onError(error: Int) {
                if (token != generation) return
                cancel()
                when (error) {
                    // Silence or nothing recognisable just ends the segment.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> text("")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        failed("Microphone access was denied. Allow it in App info → Permissions to dictate.", true)
                    SpeechRecognizer.ERROR_AUDIO -> failed("The microphone isn't available. Close other apps using it and try again.", true)
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        failed("Speech recognition doesn't support your phone's language yet.", true)
                    else -> failed("Couldn't use speech recognition. Please try again.", false)
                }
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Ask the recognizer for punctuation and capitals; older engines simply ignore it.
        if (Build.VERSION.SDK_INT >= 33) intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        created.startListening(intent)
    }
    override fun stop() { recognizer?.stopListening() }
    override fun cancel() { generation++; recognizer?.destroy(); recognizer = null }
}
