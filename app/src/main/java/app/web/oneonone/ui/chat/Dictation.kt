package app.web.oneonone.ui.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.web.oneonone.R
import app.web.oneonone.ui.components.OneIconButton
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/**
 * `/audio`: live dictation into the composer draft. Not a message type; the user edits the text and sends it normally.
 * SpeechRecognizer when the phone has a recognition service, otherwise the system speech dialog.
 */
internal class Dictation(private val context: Context, private val deliver: (String) -> Unit, private val fail: (String) -> Unit) {
    var listening by mutableStateOf(false); private set
    var partial by mutableStateOf(""); private set
    private var recognizer: SpeechRecognizer? = null

    val available get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Main thread only (SpeechRecognizer requirement); the RECORD_AUDIO permission is already granted. */
    fun start() {
        if (recognizer != null) return
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(results: Bundle?) { partial = results.first().orEmpty() }
            override fun onResults(results: Bundle?) { val text = results.first().orEmpty(); cancel(); if (text.isNotBlank()) deliver(text.trim()) }
            override fun onError(error: Int) { cancel(); fail(dictationError(error)) }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listening = true
        created.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
    }

    /** Finish now: the recognizer still delivers the final text. */
    fun stop() { recognizer?.stopListening() }

    /** Cancel / dispose / background: drops the recognizer and any partial text. */
    fun cancel() { recognizer?.destroy(); recognizer = null; listening = false; partial = "" }
}

private fun Bundle?.first() = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

internal fun dictationError(code: Int) = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap /audio and try again."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
        "Speech recognition needs a connection right now. Try again in a moment."
    SpeechRecognizer.ERROR_AUDIO -> "The microphone isn't available. Close other apps using it and try again."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access was denied. Allow it in App info → Permissions to dictate."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Speech recognition is busy. Try again in a moment."
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Your language isn't available for dictation on this phone."
    else -> "Couldn't use speech recognition. Please try again."
}

/** Returns a [Dictation] plus `begin`, which asks for the microphone first. The recognizer is destroyed on dispose and when the app leaves the foreground. */
@Composable
internal fun rememberDictation(onText: (String) -> Unit, onError: (String) -> Unit): Pair<Dictation, () -> Unit> {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onText)
    val fail by rememberUpdatedState(onError)
    val dictation = remember { Dictation(context, { deliver(it) }, { fail(it) }) }
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { deliver(it.trim()) }
    }
    val unavailable = "Speech recognition isn't available on this phone."
    fun systemDialog() = try {
        fallback.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
    } catch (_: ActivityNotFoundException) { fail(unavailable) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) dictation.start() else fail("Microphone access was denied. Allow it in App info → Permissions to dictate.")
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, dictation) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) dictation.cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); dictation.cancel() }
    }
    val begin = {
        when {
            !dictation.available -> systemDialog()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> dictation.start()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return dictation to begin
}

/** Same row as the voice-note [RecordingBar]: cancel, live partial text, and a stop button that drops the text into the draft. */
@Composable
internal fun DictationBar(partial: String, onStop: () -> Unit, onCancel: () -> Unit) {
    val c = OneTheme.colors
    val pulse by rememberInfiniteTransition(label = "dictation-dot").animateFloat(
        initialValue = 1f, targetValue = .3f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(800, easing = OneTheme.motion.standard), RepeatMode.Reverse),
    )
    Row(Modifier.fillMaxWidth().background(c.bg).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OneIconButton(R.drawable.ic_x, "Cancel dictation", onCancel, Modifier.size(OneTheme.sizes.send48))
        Row(Modifier.weight(1f).height(OneTheme.sizes.send48).semantics(mergeDescendants = true) { contentDescription = "Listening" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(c.danger))
            Text(partial.ifBlank { "Listening…" }, color = if (partial.isBlank()) c.muted else c.text, style = OneTextStyles.menuItem, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(OneTheme.sizes.send48).clip(CircleShape).background(c.sendBg)
            .clickable(role = Role.Button, onClick = onStop).semantics { contentDescription = "Finish dictation" }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_stop), null, tint = c.sendFg, modifier = Modifier.size(20.dp))
        }
    }
}
