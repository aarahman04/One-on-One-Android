package app.web.oneonone.ui.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.web.oneonone.R
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.media.DictationPhase
import app.web.oneonone.data.media.DictationState
import app.web.oneonone.ui.components.OneIconButton
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/**
 * Permission and external Activity launching stay in Compose; recording/recognition live behind the ViewModel.
 * The returned starter takes the field's current text: dictated words are added after it.
 */
@Composable
internal fun rememberDictation(vm: SpeechViewModel, voiceRecording: () -> Boolean, onBegin: () -> Unit): (field: String) -> Unit {
    val context = LocalContext.current
    val recording by rememberUpdatedState(voiceRecording)
    val beforeStart by rememberUpdatedState(onBegin)
    val state by vm.dictation.collectAsState()
    var launchedToken by rememberSaveable { mutableLongStateOf(0L) }
    var field by rememberSaveable { mutableStateOf("") }
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.deviceResult(launchedToken, result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())
    }
    LaunchedEffect(state.phase, state.token) {
        if (state.phase == DictationPhase.Idle) launchedToken = 0L
        if (state.phase == DictationPhase.DeviceDialog && state.token != launchedToken) {
            launchedToken = state.token
            try {
                fallback.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
            } catch (_: ActivityNotFoundException) { vm.cancelDictation(); vm.showNotice("Speech recognition isn't available on this phone.") }
        }
    }
    val begin = {
        if (recording()) vm.showNotice("Stop your voice note before using /audio.")
        else { beforeStart(); vm.startDictation(voiceRecording = false, field = field) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) begin() else vm.showNotice("Microphone access was denied. Allow it in App info → Permissions to dictate.")
    }
    return { text ->
        field = text
        when {
            recording() -> vm.showNotice("Stop your voice note before using /audio.")
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> begin()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Status and Stop above the composer; the words appear live in the composer itself. Nothing dictated is ever discarded. */
@Composable
internal fun DictationBar(state: DictationState, hint: Boolean, onStop: () -> Unit) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val busy = state.phase == DictationPhase.Starting || state.phase == DictationPhase.Transcribing
    val pulse by rememberInfiniteTransition(label = "dictation-dot").animateFloat(1f, .3f,
        infiniteRepeatable(tween(OneTheme.motion.slow320 * 2, easing = OneTheme.motion.standard), RepeatMode.Reverse), label = "alpha")
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        if (hint && state.phase == DictationPhase.Recording) Text("Works best in English.", Modifier.padding(horizontal = s.lg16, vertical = s.xs4),
            color = c.textDim, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(start = s.lg16, end = s.sm8, top = s.xs4), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
            Row(Modifier.weight(1f).height(OneTheme.sizes.send48), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.md12)) {
                if (busy) CircularProgressIndicator(Modifier.size(s.lg16), color = c.accentYou, strokeWidth = s.xxs2)
                else Box(Modifier.size(s.sm8).alpha(pulse).clip(CircleShape).background(c.danger))
                Text(when (state.phase) {
                    DictationPhase.Transcribing -> "Adding your words…"
                    DictationPhase.Starting -> "Starting…"
                    DictationPhase.Recording -> "Listening… ${state.seconds / 60}:${(state.seconds % 60).toString().padStart(2, '0')}"
                    else -> "Listening…"
                }, color = if (busy) c.textDim else c.text, style = OneTextStyles.menuItem, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (!busy) OneIconButton(R.drawable.ic_stop, "Stop dictation", onStop,
                Modifier.size(OneTheme.sizes.send48).clip(CircleShape).background(c.sendBg), tint = c.sendFg)
        }
    }
}

/**
 * Mic beside the "Your letter" label: the /audio pipeline, but the words land live in the letter body ([set] receives the
 * whole body), not the composer.
 */
@Composable
internal fun LetterDictation(vm: SpeechViewModel, connection: CurrentConnection, body: String, begin: (String) -> Unit, set: (String) -> Unit) {
    val c = OneTheme.colors
    val state by vm.dictation.collectAsState()
    val dictated by vm.text.collectAsState()
    val apply by rememberUpdatedState(set)
    LaunchedEffect(dictated?.id) {
        val result = dictated ?: return@LaunchedEffect
        if (result.owner.ownerId == connection.myUserId && result.owner.connectionId == connection.id) apply(result.text)
        vm.consumeText(result.id)
    }
    // Closing the letter turns the mic off; words already in the letter stay. Rotation keeps listening.
    val activity = LocalActivity.current
    DisposableEffect(vm) { onDispose { if (vm.dictation.value.active && activity?.isChangingConfigurations != true) vm.cancelDictation() } }
    val busy = state.phase == DictationPhase.Starting || state.phase == DictationPhase.Transcribing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
        if (state.active) Text(when (state.phase) {
            DictationPhase.Transcribing -> "Adding your words…"
            DictationPhase.Starting -> "Starting…"
            DictationPhase.Recording -> "Listening ${state.seconds / 60}:${(state.seconds % 60).toString().padStart(2, '0')}"
            else -> "Listening…"
        }, style = OneTextStyles.cardHint, color = c.textDim, maxLines = 1,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        when {
            busy -> LabelIcon(null, null) {
                CircularProgressIndicator(Modifier.size(LabelIconSize), color = c.accentYou, strokeWidth = OneTheme.spacing.xxs2)
            }
            state.active -> LabelIcon("Stop and keep your words", vm::stopDictation) { LabelGlyph(R.drawable.ic_stop, c.danger) }
            else -> LabelIcon("Speak your letter", { begin(body) }) { LabelGlyph(R.drawable.ic_mic, c.accentYou) }
        }
    }
}

private val LabelIconSize = 16.dp

@Composable private fun LabelGlyph(icon: Int, tint: Color) = Icon(painterResource(icon), null, Modifier.size(LabelIconSize), tint = tint)

/**
 * A label-sized icon with a full 40dp touch target that adds no height to the label row: it lays out as 16dp wide and 0
 * tall, and the 40dp target is centred on the row's midline, overflowing it (hit-testing follows the placed bounds).
 */
@Composable
private fun LabelIcon(description: String?, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(Modifier
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val width = LabelIconSize.roundToPx()
            layout(width, 0) { placeable.place((width - placeable.width) / 2, -placeable.height / 2) }
        }
        .size(OneTheme.sizes.touch40)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center) { content() }
}
