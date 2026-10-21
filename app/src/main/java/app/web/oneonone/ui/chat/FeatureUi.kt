package app.web.oneonone.ui.chat

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.web.oneonone.data.media.NarrationPhase
import app.web.oneonone.data.media.NarrationState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import app.web.oneonone.ui.chat.cards.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import app.web.oneonone.data.chat.letterDate
import app.web.oneonone.ui.theme.FontFamilies
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.web.oneonone.R
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.model.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.*

@Composable
fun FeatureComposer(type: String, connection: CurrentConnection, vm: FeatureViewModel, reply: String?, close: () -> Unit,
    letterMic: (@Composable (body: String, set: (String) -> Unit) -> Unit)? = null,
    speech: SpeechViewModel? = null, dictating: Boolean = false) {
    val signature by vm.signature.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    FeatureComposerContent(type, connection, signature, busy, error, reply,
        onSend = { t, body, payload, r, done -> vm.send(t, body, payload, r, done) }, close, letterMic, speech, dictating)
}

@Composable
internal fun FeatureComposerContent(type: String, connection: CurrentConnection, signature: String, busy: Boolean, error: String?, reply: String?,
    onSend: (String, String, JsonObject, String?, () -> Unit) -> Unit, close: () -> Unit,
    letterMic: (@Composable (body: String, set: (String) -> Unit) -> Unit)? = null,
    speech: SpeechViewModel? = null, dictating: Boolean = false) {
    var first by rememberSaveable(type) { mutableStateOf("") }
    var second by rememberSaveable(type) { mutableStateOf("") }
    var third by rememberSaveable(type) { mutableStateOf("") }
    val defaultFrom = connection.myNickname?.takeIf { it.isNotBlank() } ?: signature.ifBlank { "me" }
    var from by rememberSaveable(type) { mutableStateOf(defaultFrom.take(40)) }
    var fromEdited by rememberSaveable(type) { mutableStateOf(false) }
    var to by rememberSaveable(type) { mutableStateOf(connection.otherNickname?.take(40) ?: "you") }
    var choice by rememberSaveable(type) { mutableStateOf(if (type == "letter") "dawn" else "a") }
    var target by rememberSaveable(type) { mutableStateOf("") }
    var preview by rememberSaveable(type) { mutableStateOf(false) }
    LaunchedEffect(defaultFrom) { if (!fromEdited) from = defaultFrom.take(40) }
    val previewKey = "${connection.id}/${SpeechViewModel.PREVIEW}"
    val narration = speech?.narration?.collectAsState()?.value?.takeIf { it.key == previewKey } ?: NarrationState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(preview, speech, lifecycle) {
        if (preview) speech?.prewarmVoice()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) speech?.stopNarration(previewKey) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speech?.stopNarration(previewKey) }
    }
    val dismiss = { speech?.stopNarration(previewKey); close() }
    val context = LocalContext.current
    val payload = buildJsonObject {
        when (type) {
            "letter" -> { put("appearance", choice); put("from", from.trim()); put("to", to.trim()) }
            "ask" -> { put("question", first.trim()); put("answerA", second.trim()) }
            "thisorthat" -> { put("optionA", first.trim()); put("optionB", second.trim()); put("pickSender", choice) }
            "countdown" -> { put("label", first.trim()); put("targetIso", target) }
        }
    }
    val valid = runCatching { validateFeaturePayload(connection.id, type, payload); require(type != "letter" || third.isNotBlank()) }.isSuccess
    FeatureModal(onDismissRequest = { if (!busy && !dictating) dismiss() }, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (preview) "Preview letter" else CommandTitles[type] ?: type, Modifier.weight(1f))
            if (preview && speech != null) LetterSpeaker(narration,
                read = { speech.readPreview(connection.id, to.trim(), third.trim(), from.trim()) },
                stop = { speech.stopNarration(previewKey) })
        }
    },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (preview) {
                ChatNotice(narration.notice, warning = true)
                LetterBody(ChatMessage(senderId = connection.myUserId, content = third, createdAt = Instant.now().toString(), type = "letter", payload = payload))
            }
            else when (type) {
                "letter" -> {
                    // Every letter renders as one white handwritten page; the contract still needs an appearance, so it stays "dawn".
                    Field("From", from, 40) { from = it; fromEdited = true }; Field("To", to, 40) { to = it }
                    Field("Your letter", third, 4_000, trailing = letterMic?.let { mic -> {
                        mic(third) { words -> third = words.take(4_000) }
                    } }, enabled = !dictating) { third = it }
                }
                "ask" -> { Field("Question", first, 300) { first = it }; Field("Your answer (sealed until they answer)", second, 500) { second = it } }
                "thisorthat" -> {
                    Field("Option A", first, 100) { first = it }; Field("Option B", second, 100) { second = it }
                    Text("Your choice stays sealed until they choose.")
                    OptionTiles(first.trim().ifBlank { "Option A" }, second.trim().ifBlank { "Option B" }, OneTheme.colors.text, OneTheme.colors.border,
                        OneTheme.colors.bgRaised, selected = choice, onPick = { choice = it })
                }
                "countdown" -> {
                    Field("Countdown label", first, 100) { first = it }
                    SecondaryButton(if (target.isBlank()) "Choose date and time" else FullFormat.withZone(ZoneId.systemDefault()).format(Instant.parse(target)), onClick = {
                        val initial = target.takeIf { it.isNotBlank() }?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: LocalDateTime.now().plusHours(1)
                        DatePickerDialog(context, { _, y, m, d ->
                            TimePickerDialog(context, { _, hour, minute ->
                                target = LocalDateTime.of(y, m + 1, d, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toString()
                            }, initial.hour, initial.minute, true).show()
                        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis(); show() }
                    })
                }
            }
            error?.let { Text(it, style = OneTextStyles.cardHint, color = OneTheme.colors.danger) }
        } },
        confirmButton = { PrimaryButton(if (type == "letter" && !preview) "Preview" else "Send", enabled = valid && !busy && !dictating, onClick = {
            if (type == "letter" && !preview) preview = true
            else { speech?.stopNarration(previewKey); onSend(type, if (type == "letter") third.trim() else "", payload, reply, dismiss) }
        }) },
        dismissButton = { SecondaryButton(if (preview) "Edit" else "Cancel", enabled = !busy && !dictating, onClick = {
            speech?.stopNarration(previewKey); if (preview) preview = false else dismiss()
        }) })
}

@Composable private fun Field(label: String, value: String, maximum: Int, bubble: Boolean = false,
    trailing: (@Composable () -> Unit)? = null, enabled: Boolean = true, change: (String) -> Unit) {
    val hint = if (bubble) LocalBubbleColors.current.text.copy(alpha = .8f) else OneTheme.colors.textDim
    val counter = if (bubble) LocalBubbleColors.current.text.copy(alpha = .8f) else OneTheme.colors.muted
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (trailing == null) Text(label, style = OneTextStyles.cardHint, color = hint)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = OneTextStyles.cardHint, color = hint)
            trailing()
        }
        OneTextField(value, { change(it.take(maximum)) }, Modifier.fillMaxWidth().then(if (maximum > 1_000) Modifier.heightIn(min = 132.dp, max = 212.dp) else Modifier.heightIn(max = if (maximum > 100) 212.dp else 68.dp)).semantics { contentDescription = label },
            singleLine = false, enabled = enabled, textStyle = MaterialTheme.typography.bodyLarge)
        Text("${value.length}/$maximum", style = MaterialTheme.typography.labelMedium, color = counter)
    }
}
@Composable private fun Choices(options: List<String>, selected: String, choose: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> ChoiceButton(option, selected == option, onClick = { choose(option) }) }
    }
}

@Composable
private fun ChoiceButton(option: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = onClick, modifier = Modifier.pressScale(source).semantics { this.selected = selected }, enabled = enabled, interactionSource = source,
        shape = RoundedCornerShape(OneTheme.radii.xs4), color = OneTheme.colors.bg,
        border = BorderStroke(1.dp, if (selected) OneTheme.colors.accentOther else OneTheme.colors.border)) {
        Box(Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(choiceLabel(option),
                style = MaterialTheme.typography.labelMedium, color = (if (selected) OneTheme.colors.text else OneTheme.colors.textDim).copy(alpha = if (enabled) 1f else .5f))
        }
    }
}

@Composable
internal fun FeatureModal(onDismissRequest: () -> Unit, title: @Composable () -> Unit = {}, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit = {}, placement: ModalPlacement = ModalPlacement.Center) {
    OneModal(onDismiss = onDismissRequest, placement = placement) {
        CompositionLocalProvider(LocalContentColor provides OneTheme.colors.text) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CompositionLocalProvider(LocalTextStyle provides OneTextStyles.subtitle.copy(fontWeight = FontWeight.Bold)) { title() }
                CompositionLocalProvider(LocalTextStyle provides OneTextStyles.cardHint, LocalContentColor provides OneTheme.colors.textDim) { text() }
                FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) { dismissButton(); confirmButton() }
            }
        }
    }
}

/** Human labels for payload values shown as choices; unknown values fall back to readable text. */
internal fun choiceLabel(option: String): String = when (option) {
    in Moods -> "${moodEmoji(option)} ${option.replaceFirstChar { it.uppercase() }}"
    "a" -> "Option A"; "b" -> "Option B"
    "off" -> "None"; "love" -> "Love"; "samurai" -> "Samurai"; "dark" -> "Dark"; "light" -> "Light"
    "txt" -> "Plain text"; "json" -> "JSON"; "html" -> "Web page"
    "child_safety" -> "Child safety"; "hate" -> "Hate speech"; "sexual" -> "Sexual content"
    else -> option.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** "You changed the wallpaper to Samurai" instead of raw payload keys. */
internal fun systemEventText(who: String, event: String?, value: String?): String = when (event) {
    "wallpaper" -> if (value == null || value == "off") "$who turned off the wallpaper" else "$who changed the wallpaper to ${choiceLabel(value)}"
    null -> "$who changed a chat setting"
    else -> "$who changed the ${event.replace('_', ' ')}${value?.let { " to ${choiceLabel(it)}" } ?: ""}"
}

private val CommandTitles = mapOf("letter" to "Write a letter", "ask" to "Ask a sealed question", "thisorthat" to "This or that",
    "countdown" to "Start a countdown")

private fun moodEmoji(mood: String) = listOf("😄", "🙂", "😐", "😔", "😞").getOrElse(Moods.indexOf(mood)) { "" }

/** A full white page (A4 proportions, grows for long letters) in black handwriting, scrolling inside the modal. */
@Composable internal fun LetterBody(message: ChatMessage) {
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .62f).toDp() }
    BoxWithConstraints(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
        // Readable on-screen sizes; the PDF paginates and sizes itself (LetterLayout).
        val style = TextStyle(fontFamily = FontFamilies.Letter, fontSize = 18.sp, lineHeight = 28.sp, color = Color.Black)
        Column(Modifier.fillMaxWidth().heightIn(min = maxWidth * 1.414f).shadow(2.dp, RoundedCornerShape(2.dp)).background(Color.White)
            .padding(horizontal = 28.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(letterDate(message), Modifier.align(Alignment.End), style = style.copy(fontSize = 15.sp))
            Text("Dear ${message.payload.text("to")},", style = style)
            Text(message.content, Modifier.padding(start = 28.dp), style = style)
            // Signature sits at the foot of the page, so even a one-line letter reads as a full page.
            Spacer(Modifier.weight(1f))
            Text("With love,\n${message.payload.text("from")}", Modifier.align(Alignment.End), style = style, textAlign = TextAlign.End)
        }
    }
}

/** A single bounded tile, as in the web card. OSM sees this approximate area and the device IP. */
internal val StickerSize = 150.dp

/**
 * Stickers travel as ordinary images (the contract has no sticker type); the maker's exact 512×512 WebP marks one.
 * Android photos are re-encoded to JPEG/PNG and GIFs stay GIF, so only stickers match here.
 * shortcut: a 512×512 WebP photo sent from the web also renders bare; upgrade with a payload flag (codex Prompt 2).
 */
internal fun isSticker(message: ChatMessage): Boolean = message.type == "image" && message.deletedAt == null &&
    message.payload.text("mime") == "image/webp" && message.payload.number("width") == 512.0 && message.payload.number("height") == 512.0

internal fun locationTile(lat: Double, lng: Double): String {
    require(lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)
    val n = 1 shl 15
    val radians = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
    val x = floor((lng + 180) / 360 * n).toInt().coerceIn(0, n - 1)
    val y = floor((1 - ln(tan(radians) + 1 / cos(radians)) / Math.PI) / 2 * n).toInt().coerceIn(0, n - 1)
    return "https://tile.openstreetmap.org/15/$x/$y.png"
}

@Composable
fun FeatureCard(message: ChatMessage, mine: Boolean, original: ChatMessage?, connection: CurrentConnection, vm: FeatureViewModel, speech: SpeechViewModel) {
    val payload = message.payload
    val busy by vm.busy.collectAsState()
    val path = payload.text("path")
    val playing by vm.playing.collectAsState()
    val position = if (message.type == "voice") vm.playbackPosition.collectAsState().value else 0
    val duration = if (message.type == "voice") vm.playbackDuration.collectAsState().value else 0
    val paused = if (message.type == "voice") vm.playbackPaused.collectAsState().value else false
    var url by remember(path) { mutableStateOf<String?>(null) }
    var urlError by remember(path) { mutableStateOf<String?>(null) }
    var retryUrl by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val letterDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { vm.saveLetter(it, message) } }
    val attachmentDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null && payload != null) vm.saveAttachment(connection.id, payload, uri)
    }
    val saveCopy = { attachmentDownload.launch(payload.text("name").ifBlank { path.substringAfterLast('/') }) }
    val currentSaveCopy by rememberUpdatedState(saveCopy)
    val menuSave = LocalMessageSaveAction.current
    DisposableEffect(menuSave, message.type, path) {
        if (message.type == "voice" && path.isNotBlank()) menuSave?.value = { if (!vm.busy.value) currentSaveCopy() }
        onDispose { if (message.type == "voice") menuSave?.value = null }
    }
    var documentUri by rememberSaveable(message.id, message.tempId) { mutableStateOf<Uri?>(null) }
    if (message.type == "image" && path.isNotBlank()) LaunchedEffect(path, connection.id, retryUrl) {
        while (true) {
            try { url = vm.media.signedUrl(connection.id, path); urlError = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { urlError = "Photo unavailable. Tap retry."; break }
            delay(55 * 60_000L)
        }
    }
    FeatureCardContent(message, mine, original, connection, busy, playing, url, urlError,
        onSend = { t, body, response, r, done -> vm.send(t, body, response, r, done) },
        onPlay = { if (speech.dictation.value.active) speech.showNotice("Stop dictation before playing a voice note.")
            else { speech.stopNarration(); vm.play(connection.id, it) } },
        onLetterDownload = { letterDownload.launch("one-on-one-letter.pdf") },
        onOpenFile = { payload?.let { p -> vm.openFile(connection.id, p) { documentUri = it } } },
        onImageError = { urlError = "Photo unavailable. Tap retry." },
        onRetryPhoto = { url = null; urlError = null; retryUrl++ },
        onSaveCopy = saveCopy, playbackPosition = position, playbackDuration = duration, playbackPaused = paused,
        speech = speech, onReadLetter = {
            if (!vm.recording.value && !speech.dictation.value.active) vm.stopPlayback()
            speech.readLetter(connection.id, message, vm.recording.value)
        })
    documentUri?.let { uri ->
        DocumentViewer(uri, payload.text("mime"), payload.text("name"), busy, saveCopy,
            openWith = { openAttachment(context, uri, payload.text("mime")) }, close = { documentUri = null })
    }
}

@Composable
internal fun FeatureCardContent(message: ChatMessage, mine: Boolean, original: ChatMessage?, connection: CurrentConnection,
    busy: Boolean, playing: String?, url: String?, urlError: String?,
    onSend: (String, String, JsonObject, String?, () -> Unit) -> Unit, onPlay: (String) -> Unit,
    onLetterDownload: () -> Unit, onOpenFile: () -> Unit, onImageError: () -> Unit, onRetryPhoto: () -> Unit, onSaveCopy: () -> Unit,
    playbackPosition: Int = 0, playbackDuration: Int = 0, playbackPaused: Boolean = false,
    speech: SpeechViewModel? = null, onReadLetter: () -> Unit = {}) {
    val payload = message.payload
    val path = payload.text("path")
    val uriHandler = LocalUriHandler.current
    var reveal by rememberSaveable(message.id, message.tempId) { mutableStateOf(false) }
    var answer by rememberSaveable(message.id, message.tempId) { mutableStateOf("") }
    CompositionLocalProvider(LocalContentColor provides LocalBubbleColors.current.text, LocalTextStyle provides OneTextStyles.subtitle) {
    Column(Modifier.widthIn(min = if (message.type == "image") 0.dp else 224.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (message.type) {
                "letter" -> {
                    CardHeading(R.drawable.ic_mail, "A letter for ${payload.text("to")}")
                    CardAction("Open letter", onClick = { reveal = true })
                    if (reveal) LetterViewer(message, onLetterDownload, close = { reveal = false }, connection = connection.id, speech = speech, read = onReadLetter)
                }
                "ask" -> {
                    CardHeading(if (payload.text("answerB").isNotBlank()) R.drawable.ic_heart else R.drawable.ic_lock, payload.text("question"))
                    if (payload.text("answerB").isNotBlank()) {
                        val author = original?.senderId ?: if (mine) "other" else connection.myUserId
                        Text("${if (author == connection.myUserId) "You" else connection.otherNickname ?: "Them"}: ${payload.text("answerA")}")
                        Text("${if (message.senderId == connection.myUserId) "You" else connection.otherNickname ?: "Them"}: ${payload.text("answerB")}")
                    } else {
                        CardHint("Answers sealed until both reply.")
                        if (mine) CardHint("Waiting for their answer.")
                        if (!mine && message.id != null) {
                            Field("Your answer", answer, 500, bubble = true) { answer = it }
                            CardAction("Reveal both answers", enabled = answer.isNotBlank() && !busy, onClick = {
                                val response = buildJsonObject { payload?.forEach { (k, v) -> put(k, v) }; put("answerB", answer.trim()) }
                                onSend("ask", "", response, message.id) { answer = "" }
                            })
                        }
                    }
                }
                "thisorthat" -> {
                    val a = payload.text("optionA"); val b = payload.text("optionB")
                    val colors = LocalBubbleColors.current
                    val revealed = payload.text("pickRecipient").isNotBlank()
                    if (revealed) CardHeading(R.drawable.ic_shuffle, "This or that?")
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_lock), null, tint = colors.text, modifier = Modifier.size(16.dp))
                        Text("This or that?", style = OneTextStyles.cardHeading, color = colors.text)
                    }
                    if (revealed) {
                        // The sender's pick is pickSender; the pick in the reply belongs to whoever sent that reply.
                        val author = original?.senderId ?: if (mine) "other" else connection.myUserId
                        val senderIsMe = author == connection.myUserId
                        val partner = connection.otherNickname ?: "Them"
                        val picks = mutableMapOf<String, List<String>>()
                        fun mark(pick: String, who: String) { picks[pick] = picks[pick].orEmpty() + who }
                        mark(payload.text("pickSender"), if (senderIsMe) "You" else partner)
                        mark(payload.text("pickRecipient"), if (mine) "You" else partner)
                        OptionTiles(a, b, colors.text, colors.edge, colors.backgroundStart, picks = picks)
                        if (payload.text("pickSender") == payload.text("pickRecipient")) Text("You matched!", style = OneTextStyles.cardHeading)
                    } else {
                        OptionTiles(a, b, colors.text, colors.edge, colors.backgroundStart, enabled = !busy,
                            onPick = if (!mine && message.id != null) { pick ->
                                val response = buildJsonObject { payload?.forEach { (k, v) -> put(k, v) }; put("pickRecipient", pick) }
                                onSend("thisorthat", "", response, message.id) { }
                            } else null)
                        CardHint(if (mine) "Sealed — waiting for their choice." else "Their choice is sealed. Tap yours.")
                    }
                }
                "checkin" -> { CardHeading(R.drawable.ic_smile, "${moodEmoji(payload.text("mood"))} ${payload.text("mood").replaceFirstChar { it.uppercase() }}".trim()); Text(payload.text("note")) }
                "countdown" -> {
                    var remaining by remember(payload.text("targetIso")) { mutableStateOf("") }
                    LaunchedEffect(payload.text("targetIso")) { while (true) {
                        remaining = runCatching { countdownText(payload.text("targetIso")) }.getOrDefault("Unavailable")
                        delay(1_000)
                    } }
                    CardHeading(R.drawable.ic_hourglass, payload.text("label")); CardHint(remaining)
                    CardHint(runCatching { FullFormat.withZone(ZoneId.systemDefault()).format(Instant.parse(payload.text("targetIso"))) }.getOrDefault(""))
                }
                "location" -> {
                    val lat = payload.number("lat"); val lng = payload.number("lng")
                    if (lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(OneTheme.radii.sm6)).background(OneTheme.colors.bg)) {
                                AsyncImage(if (LocalInspectionMode.current) null else locationTile(lat, lng), "Map preview of shared location", Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    placeholder = ColorPainter(OneTheme.colors.bgRaised))
                                Icon(painterResource(R.drawable.ic_map_pin), null, Modifier.align(Alignment.Center).size(24.dp), tint = OneTheme.colors.danger)
                            }
                            Text("Snapshot, not live tracking", Modifier.weight(1f).padding(end = 4.dp), style = OneTextStyles.cardHeading,
                                color = LocalBubbleColors.current.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
                        CardHint("$lat, $lng${payload.number("accuracy")?.let { " · ±${it.toInt()} m" } ?: ""}")
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            CardAction("Directions", onClick = { uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng") },
                                Modifier.defaultMinSize(minHeight = 48.dp), location = true)
                            CardAction("View map", onClick = { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$lat,$lng") },
                                Modifier.defaultMinSize(minHeight = 48.dp), location = true)
                        }
                    } else Text("Location unavailable")
                }
                "image" -> if (isSticker(message)) {
                    // Bare sticker: transparent, fitted, no photo viewer; the bubble draws only a small time pill.
                    Box(Modifier.size(StickerSize), contentAlignment = Alignment.Center) {
                        url?.let { AsyncImage(if (LocalInspectionMode.current) null else it, "Sticker", Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit, onError = { onImageError() }) }
                        if (url == null && urlError == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        urlError?.let { CardAction("Retry", onClick = { onRetryPhoto() }) }
                    }
                } else {
                    if (url == null && urlError == null) Text("Loading photo…")
                    url?.let { AsyncImage(if (LocalInspectionMode.current) null else it, "View shared photo", Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp).clip(RoundedCornerShape(OneTheme.radii.sm6))
                        .bubbleClickable { reveal = true }, contentScale = ContentScale.Crop,
                placeholder = ColorPainter(OneTheme.colors.bgRaised), error = if (LocalInspectionMode.current) ColorPainter(OneTheme.colors.bgRaised) else null,
                        onError = { onImageError() }) }
                    urlError?.let { Text(it); CardAction("Retry photo", onClick = { onRetryPhoto() }) }
                    if (reveal) PhotoViewer(url, busy, onSaveCopy, close = { reveal = false })
                }
                "voice" -> {
                    val active = playing == path && path.isNotBlank()
                    val running = active && !playbackPaused
                    val length = if (active && playbackDuration > 0) playbackDuration else ((payload.number("duration") ?: 0.0) * 1_000).toInt()
                    val elapsed = if (active) playbackPosition else 0
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(onClick = { onPlay(path) }, enabled = path.isNotBlank(), modifier = Modifier.size(44.dp)
                            .semantics { contentDescription = if (running) "Pause voice note" else "Play voice note" },
                            shape = CircleShape, color = LocalBubbleColors.current.text.copy(alpha = .18f)) {
                            Box(contentAlignment = Alignment.Center) {
                                val fast = OneTheme.motion.fast120
                                AnimatedContent(running, transitionSpec = { (fadeIn(tween(fast)) + scaleIn(tween(fast), initialScale = .6f)) togetherWith
                                    (fadeOut(tween(fast)) + scaleOut(tween(fast), targetScale = .6f)) }, label = "playPause") { isRunning ->
                                    Icon(painterResource(if (isRunning) R.drawable.ic_pause else R.drawable.ic_play), null,
                                        Modifier.size(24.dp).offset(x = if (isRunning) 0.dp else 2.dp), tint = LocalBubbleColors.current.text)
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val bar = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                            // Still uploading (no server id yet, not failed): indeterminate instead of a static empty bar.
                            if (message.id == null && message.deliveryState != "failed")
                                LinearProgressIndicator(modifier = bar, color = LocalBubbleColors.current.text,
                                    trackColor = LocalBubbleColors.current.text.copy(alpha = .25f), gapSize = 0.dp)
                            else {
                                val progress by animateFloatAsState(playbackProgress(elapsed, length), tween(OneTheme.motion.base200), label = "voiceProgress")
                                LinearProgressIndicator(progress = { progress }, modifier = bar,
                                    color = LocalBubbleColors.current.text, trackColor = LocalBubbleColors.current.text.copy(alpha = .25f),
                                    gapSize = 0.dp, drawStopIndicator = {})
                            }
                            Text(playbackTime(if (active) elapsed else length), color = LocalBubbleColors.current.meta,
                                style = OneTextStyles.bubbleMeta)
                        }
                    }
                }
                "file" -> {
                    val type = attachmentType(payload.text("mime"))
                    // The whole row opens the viewer; the trailing icon saves a copy.
                    Row(Modifier.clip(RoundedCornerShape(OneTheme.radii.sm6))
                        .bubbleClickable(enabled = !busy && path.isNotBlank(), onClickLabel = "View", onClick = onOpenFile),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(OneTheme.radii.sm6))
                            .background(LocalBubbleColors.current.text.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                            Text(type, color = LocalBubbleColors.current.text, style = OneTextStyles.bubbleMeta.copy(fontWeight = FontWeight.Bold))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(payload.text("name"), style = OneTextStyles.cardHeading, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            CardHint("${attachmentSize(payload.number("size") ?: 0.0)} · $type")
                        }
                        OneIconButton(R.drawable.ic_download, "Save a copy", onSaveCopy,
                            tint = LocalBubbleColors.current.text, enabled = !busy && path.isNotBlank())
                    }
                }
                else -> Text(message.content.ifBlank { message.type })
            }
    }
    }
}

internal fun openAttachment(context: Context, uri: Uri, mime: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri("Attachment", uri) }
    try { context.startActivity(Intent.createChooser(intent, "Open with…")) }
    catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No app can open this file.", android.widget.Toast.LENGTH_LONG).show() }
}

/** Photo / File chooser: two tiles anchored above the composer. The pickers live in ChatScreen so a pick closes the sheet at once. */
@Composable
internal fun AttachSheet(visible: Boolean, modifier: Modifier, onPhoto: () -> Unit, onFile: () -> Unit) {
    val motion = OneTheme.motion
    AnimatedVisibility(visible, modifier,
        enter = slideInVertically(tween(motion.enter160, easing = motion.standard)) { it } + fadeIn(tween(motion.enter160)),
        exit = slideOutVertically(tween(motion.fast120, easing = motion.standard)) { it } + fadeOut(tween(motion.fast120))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachTile(R.drawable.ic_image, "Photo", onPhoto, Modifier.weight(1f))
            AttachTile(R.drawable.ic_file, "File", onFile, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AttachTile(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OneTheme.colors
    val shape = RoundedCornerShape(OneTheme.radii.md10)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.pressScale(source).clip(shape).background(c.bgRaised).border(1.dp, c.border, shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), null, tint = c.textDim, modifier = Modifier.size(24.dp))
        Text(label, style = OneTextStyles.menuItem, color = c.text)
    }
}


@Composable
internal fun UploadContent(kind: String, source: String?, busy: Boolean, error: String?, send: () -> Unit, cancel: () -> Unit) {
    FeatureModal(onDismissRequest = { if (!busy) cancel() }, placement = ModalPlacement.Bottom, title = { Text("Send $kind?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kind == "image") AsyncImage(source, "Selected photo", Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(OneTheme.radii.md10)), contentScale = ContentScale.Fit)
            Text(if (kind == "image") "Up to 10 MiB. Static photos have location metadata removed; very large photos are resized." else "Up to 25 MiB. PDF, text, CSV, Word, Excel and PowerPoint.")
            ErrorLine(error)
        } },
        confirmButton = { PrimaryButton(if (busy) "Uploading…" else "Send", enabled = !busy, onClick = send) },
        dismissButton = { SecondaryButton("Cancel", enabled = !busy, onClick = cancel) })
}
@Composable
fun LocationConfirmation(connection: CurrentConnection, vm: FeatureViewModel, reply: String?, close: () -> Unit) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed ->
        if (allowed.values.any { it }) vm.location(connection, reply, close)
        else vm.showError("Location access was denied. Allow approximate or precise location in App info → Permissions to share a snapshot.")
    }
    val error by vm.error.collectAsState()
    LocationContent(busy, error, onShare = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                vm.location(connection, reply, close)
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, close)
}

@Composable
internal fun LocationContent(busy: Boolean, error: String?, onShare: () -> Unit, close: () -> Unit) {
    FeatureModal(onDismissRequest = { if (!busy) close() }, title = { Text("Share your location?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Send one location snapshot to this person. It stays in your conversation until the connection ends. Approximate access works too. Map previews use OpenStreetMap, which sees the map area and your IP address."); ErrorLine(error) } },
        confirmButton = { PrimaryButton(if (busy) "Locating…" else "Share snapshot", enabled = !busy, onClick = onShare) },
        dismissButton = { SecondaryButton("Cancel", enabled = !busy, onClick = close) })
}

@Composable
fun AppearanceDialog(connection: CurrentConnection?, vm: FeatureViewModel, refresh: () -> Unit, close: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    AppearanceContent(connection?.wallpaper ?: "off", busy, error, onTheme = { vm.theme(it) },
        onWallpaper = { if (connection != null) vm.wallpaper(connection.id, it, refresh) }, close, showWallpaper = connection != null)
}

@Composable
internal fun AppearanceContent(wallpaper: String, busy: Boolean, error: String?, onTheme: (String) -> Unit, onWallpaper: (String) -> Unit, close: () -> Unit, showWallpaper: Boolean = true) {
    FeatureModal(onDismissRequest = close, title = { Text("Appearance") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Theme on this device")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark", "light").forEach { option -> ChoiceButton(option,
                selected = (OneTheme.colors.bg == app.web.oneonone.ui.theme.OneColors.Dark.bg) == (option == "dark"),
                enabled = !busy, onClick = { onTheme(option) }) }
        }
        if (showWallpaper) {
            Text("Wallpaper shared by both of you")
            Choices(listOf("off", "love", "samurai"), wallpaper) { if (!busy) onWallpaper(it) }
        }
        ErrorLine(error)
    } }, confirmButton = { SecondaryButton("Done", onClick = close) })
}

@Composable
fun ReportDialog(connection: CurrentConnection, message: String?, vm: FeatureViewModel, close: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    ReportContent(message, busy, error, onReport = { category, reason, done -> vm.report(connection.id, message, category, reason, done) }, close)
}

@Composable
internal fun ReportContent(message: String?, busy: Boolean, error: String?, onReport: (String, String, () -> Unit) -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf("other") }
    var reason by rememberSaveable { mutableStateOf("") }
    FeatureModal(onDismissRequest = { if (!busy) close() }, title = { Text(if (message == null) "Report this person" else "Report this message") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reports go to the safety team. Reporting does not block or end your connection.")
            Choices(ReportCategories, category) { category = it }; Field("Details (optional)", reason, 1_000) { reason = it }
            ErrorLine(error)
        } }, confirmButton = { PrimaryButton("Submit report", enabled = !busy, onClick = { onReport(category, reason) {
            android.widget.Toast.makeText(context, "Report submitted.", android.widget.Toast.LENGTH_SHORT).show()
            close()
        } }) }, dismissButton = { SecondaryButton("Cancel", enabled = !busy, onClick = close) })
}

@Composable
fun BlockDialog(connection: CurrentConnection, vm: FeatureViewModel, close: () -> Unit, refresh: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    BlockContent(busy, error, onBlock = { vm.block(connection.id) { close(); refresh() } }, close)
}

@Composable
internal fun BlockContent(busy: Boolean, error: String?, onBlock: () -> Unit, close: () -> Unit) {
    FeatureModal(onDismissRequest = { if (!busy) close() }, title = { Text("Block this person?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Blocking immediately ends this connection and deletes its conversation and attachments for both of you. Export first if you want to keep a copy. You can unblock in Settings, but the conversation cannot be restored."); ErrorLine(error) } },
        confirmButton = { DangerButton("Block and end", enabled = !busy, onClick = onBlock) },
        dismissButton = { SecondaryButton("Cancel", enabled = !busy, onClick = close) })
}

@Composable
fun ExportDialog(connection: CurrentConnection, vm: FeatureViewModel, close: () -> Unit) {
    var format by rememberSaveable { mutableStateOf("txt") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let { vm.export(it, connection, format) }; close()
    }
    ExportContent(format, choose = { format = it }, onSave = { launcher.launch("one-on-one-conversation.$format") }, close)
}

@Composable
internal fun ExportContent(format: String, choose: (String) -> Unit, onSave: () -> Unit, close: () -> Unit) {
    FeatureModal(onDismissRequest = close, title = { Text("Export conversation") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Save messages from server history as text, JSON or HTML. Attachments are represented by metadata; their signed links expire. Download attachments separately before ending the connection. Keep exports somewhere private.")
        Choices(listOf("txt", "json", "html"), format, choose)
    } }, confirmButton = { PrimaryButton("Choose where to save", onClick = onSave) },
        dismissButton = { SecondaryButton("Cancel", onClick = close) })
}

/** Several selected messages, oldest first, one per line (WhatsApp copies them the same way). */
fun copyMessages(context: Context, messages: List<ChatMessage>) {
    if (messages.size == 1) return copyMessage(context, messages[0])
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Messages",
        messages.joinToString("\n") { it.content.ifBlank { messageSummary(it) } }))
}

fun copyMessage(context: Context, message: ChatMessage) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", message.content.ifBlank { messageSummary(message) }))
}

@Composable
private fun ErrorLine(error: String?) {
    error?.let { Text(it, style = OneTextStyles.cardHint, color = OneTheme.colors.danger) }
}

@Composable
internal fun LetterViewer(message: ChatMessage, download: () -> Unit, close: () -> Unit,
                          connection: String = "", speech: SpeechViewModel? = null, read: () -> Unit = {}) {
    val key = "$connection/${message.id}"
    val state = speech?.narration?.collectAsState()?.value?.takeIf { it.key == key } ?: NarrationState()
    val speechNotice = speech?.notice?.collectAsState()?.value
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(key, speech, lifecycle) {
        speech?.prewarmVoice()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) speech?.stopNarration(key)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); if (activity?.isChangingConfigurations != true) speech?.stopNarration(key) }
    }
    val dismiss = { speech?.stopNarration(key); close() }
    val c = OneTheme.colors
    val s = OneTheme.spacing
    FeatureModal(onDismissRequest = dismiss, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(s.xxs2)) {
                    Text("Letter")
                    if (this@BoxWithConstraints.maxWidth >= OneTheme.sizes.touch40 * 3 && state.total > 0 && !state.device && state.phase != NarrationPhase.Idle) {
                        val seconds = state.positionMs / 1_000
                        Text("Part ${state.part + 1} of ${state.total} \u00b7 ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                            color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(s.xxs2), color = c.accentYou, trackColor = c.border)
                    }
                }
            }
            LetterSpeaker(state, read, stop = { speech?.stopNarration(key) })
        }
    }, text = { Column(verticalArrangement = Arrangement.spacedBy(s.sm8)) { ChatNotice(state.notice ?: speechNotice, warning = true); LetterBody(message) } },
        confirmButton = { PrimaryButton("Download PDF", download) }, dismissButton = { SecondaryButton("Close", dismiss) })
}

@Composable
private fun LetterSpeaker(state: NarrationState, read: () -> Unit, stop: () -> Unit) {
    if (state.phase == NarrationPhase.Loading) Box(Modifier.size(OneTheme.sizes.touch40).semantics {
        contentDescription = "Loading narration"; liveRegion = LiveRegionMode.Polite
    }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(OneTheme.spacing.xl24), color = OneTheme.colors.accentYou, strokeWidth = OneTheme.spacing.xxs2)
    } else OneIconButton(when (state.phase) {
        NarrationPhase.Playing -> R.drawable.ic_pause
        NarrationPhase.Paused -> R.drawable.ic_play
        else -> R.drawable.ic_volume
    }, when (state.phase) {
        NarrationPhase.Playing -> "Pause"
        NarrationPhase.Paused -> "Resume"
        else -> "Read letter aloud"
    }, read)
    if (state.phase != NarrationPhase.Idle) OneIconButton(R.drawable.ic_stop, "Stop reading", stop)
}

@Composable
internal fun PhotoViewer(url: String?, busy: Boolean, save: () -> Unit, close: () -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var pan by remember(url) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember(url) { mutableStateOf(Size.Zero) }
    var photoError by remember(url) { mutableStateOf(false) }
    AttachmentViewerFrame("Photo", busy, save, close) {
        Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged {
                viewport = it
                pan = photoPan(pan, scale, Size(it.width.toFloat(), it.height.toFloat()), imageSize)
            }
            .semantics {
                customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction("Zoom in") { scale = (scale * 1.5f).coerceAtMost(5f); true },
                    androidx.compose.ui.semantics.CustomAccessibilityAction("Reset zoom") { scale = 1f; pan = Offset.Zero; true })
            }
            .pointerInput(url) { detectTapGestures(onDoubleTap = { point ->
                scale = if (scale > 1f) 1f else 2.5f
                pan = photoPan((Offset(viewport.width / 2f, viewport.height / 2f) - point) * (scale - 1), scale,
                    Size(viewport.width.toFloat(), viewport.height.toFloat()), imageSize)
            }) }
            .pointerInput(url) { detectTransformGestures { centroid, delta, zoom, _ ->
                val oldScale = scale
                scale = (scale * zoom).coerceIn(1f, 5f)
                val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
                pan = photoPan(pan * (scale / oldScale) + delta + focus * (1 - scale / oldScale), scale,
                    Size(viewport.width.toFloat(), viewport.height.toFloat()), imageSize)
            } }, contentAlignment = Alignment.Center) {
            AsyncImage(url, "Shared photo. Pinch or double tap to zoom.", Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
            }, contentScale = ContentScale.Fit, onSuccess = { imageSize = it.painter.intrinsicSize; photoError = false }, onError = { photoError = true })
            if (photoError || url == null) Text(if (photoError) "Photo unavailable. Close and retry." else "Loading photo…",
                color = Color.White, style = OneTextStyles.cardHint)
        }
    }
}
