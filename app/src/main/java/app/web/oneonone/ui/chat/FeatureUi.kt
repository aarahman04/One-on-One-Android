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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import app.web.oneonone.ui.chat.cards.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
fun FeatureComposer(type: String, connection: CurrentConnection, vm: FeatureViewModel, reply: String?, close: () -> Unit) {
    val signature by vm.signature.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    FeatureComposerContent(type, connection, signature, busy, error, reply,
        onSend = { t, body, payload, r, done -> vm.send(t, body, payload, r, done) }, close)
}

@Composable
internal fun FeatureComposerContent(type: String, connection: CurrentConnection, signature: String, busy: Boolean, error: String?, reply: String?,
    onSend: (String, String, JsonObject, String?, () -> Unit) -> Unit, close: () -> Unit) {
    var first by rememberSaveable(type) { mutableStateOf("") }
    var second by rememberSaveable(type) { mutableStateOf("") }
    var third by rememberSaveable(type) { mutableStateOf("") }
    var from by rememberSaveable(type) { mutableStateOf(signature.ifBlank { "me" }) }
    var to by rememberSaveable(type) { mutableStateOf(connection.otherNickname?.take(40) ?: "you") }
    var choice by rememberSaveable(type) { mutableStateOf(if (type == "letter") "dawn" else if (type == "checkin") "good" else "a") }
    var target by rememberSaveable(type) { mutableStateOf("") }
    var preview by rememberSaveable(type) { mutableStateOf(false) }
    LaunchedEffect(signature) { if (from == "me" && third.isEmpty() && signature.isNotBlank()) from = signature }
    val context = LocalContext.current
    val payload = buildJsonObject {
        when (type) {
            "letter" -> { put("appearance", choice); put("from", from.trim()); put("to", to.trim()) }
            "ask" -> { put("question", first.trim()); put("answerA", second.trim()) }
            "thisorthat" -> { put("optionA", first.trim()); put("optionB", second.trim()); put("pickSender", choice) }
            "countdown" -> { put("label", first.trim()); put("targetIso", target) }
            "checkin" -> { put("mood", choice); put("note", first.trim()) }
        }
    }
    val valid = runCatching { validateFeaturePayload(connection.id, type, payload); require(type != "letter" || third.isNotBlank()) }.isSuccess
    FeatureModal(onDismissRequest = { if (!busy) close() }, title = { Text(if (preview) "Preview letter" else "/$type") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (preview) LetterBody(ChatMessage(senderId = connection.myUserId, content = third, createdAt = Instant.now().toString(), type = "letter", payload = payload))
            else when (type) {
                "letter" -> {
                    Field("From", from, 40) { from = it }; Field("To", to, 40) { to = it }
                    Choices(listOf("dawn", "botanical"), choice) { choice = it }
                    Field("Your letter", third, 4_000) { third = it }
                }
                "ask" -> { Field("Question", first, 300) { first = it }; Field("Your answer (sealed until they answer)", second, 500) { second = it } }
                "thisorthat" -> {
                    Field("Option A", first, 100) { first = it }; Field("Option B", second, 100) { second = it }
                    Text("Your choice stays sealed until they choose."); Choices(listOf("a", "b"), choice) { choice = it }
                }
                "countdown" -> {
                    Field("Countdown label", first, 100) { first = it }
                    SecondaryButton(if (target.isBlank()) "Choose date and time" else Instant.parse(target).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(), onClick = {
                        val initial = target.takeIf { it.isNotBlank() }?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: LocalDateTime.now().plusHours(1)
                        DatePickerDialog(context, { _, y, m, d ->
                            TimePickerDialog(context, { _, hour, minute ->
                                target = LocalDateTime.of(y, m + 1, d, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toString()
                            }, initial.hour, initial.minute, true).show()
                        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis(); show() }
                    })
                }
                "checkin" -> { Choices(Moods, choice) { choice = it }; Field("A note about your day", first, 300) { first = it } }
            }
            error?.let { Text(it, style = OneTextStyles.cardHint, color = OneTheme.colors.danger) }
        } },
        confirmButton = { PrimaryButton(if (type == "letter" && !preview) "Preview" else "Send", enabled = valid && !busy, onClick = {
            if (type == "letter" && !preview) preview = true
            else onSend(type, if (type == "letter") third.trim() else "", payload, reply, close)
        }) },
        dismissButton = { SecondaryButton(if (preview) "Edit" else "Cancel", enabled = !busy, onClick = { if (preview) preview = false else close() }) })
}

@Composable private fun Field(label: String, value: String, maximum: Int, bubble: Boolean = false, change: (String) -> Unit) {
    val hint = if (bubble) LocalBubbleColors.current.text.copy(alpha = .8f) else OneTheme.colors.textDim
    val counter = if (bubble) LocalBubbleColors.current.text.copy(alpha = .8f) else OneTheme.colors.muted
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = OneTextStyles.cardHint, color = hint)
        OneTextField(value, { change(it.take(maximum)) }, Modifier.fillMaxWidth().heightIn(max = if (maximum > 100) 212.dp else 68.dp).semantics { contentDescription = label },
            singleLine = false, textStyle = MaterialTheme.typography.bodyLarge)
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
            Text(if (option in Moods) "${moodEmoji(option)} $option" else option.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium, color = (if (selected) OneTheme.colors.text else OneTheme.colors.textDim).copy(alpha = if (enabled) 1f else .5f))
        }
    }
}

@Composable
internal fun FeatureModal(onDismissRequest: () -> Unit, title: @Composable () -> Unit = {}, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit = {}) {
    OneModal(onDismiss = onDismissRequest) {
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

private fun moodEmoji(mood: String) = listOf("😄", "🙂", "😐", "😔", "😞").getOrElse(Moods.indexOf(mood)) { "" }

object LetterThemes {
    val DawnStart = Color(0xFFFFE7D0)
    val DawnMiddle = Color(0xFFFFD1DC)
    val DawnEnd = Color(0xFFCFE6FF)
    val DawnText = Color(0xFF3A2E3A)
    val BotanicalBackground = Color(0xFFF7F3E8)
    val BotanicalText = Color(0xFF34432F)
    val BotanicalBorder = Color(0xFFB9C9A6)
    val BotanicalInset = Color(0xFF78965A).copy(alpha = .12f)
}

@Composable internal fun LetterBody(message: ChatMessage) {
    val botanical = message.payload.text("appearance") == "botanical"
    val shape = RoundedCornerShape(OneTheme.radii.md10)
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(shape).drawWithCache {
        val vector = Offset(sin(Math.toRadians(160.0)).toFloat(), -cos(Math.toRadians(160.0)).toFloat())
        val half = (kotlin.math.abs(size.width * vector.x) + kotlin.math.abs(size.height * vector.y)) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val brush = Brush.linearGradient(0f to LetterThemes.DawnStart, .38f to LetterThemes.DawnMiddle, 1f to LetterThemes.DawnEnd,
            start = center - vector * half, end = center + vector * half)
        onDrawBehind { if (botanical) drawRect(LetterThemes.BotanicalBackground) else drawRect(brush) }
    }.then(if (botanical) Modifier.border(6.dp, LetterThemes.BotanicalInset, shape).border(1.dp, LetterThemes.BotanicalBorder, shape) else Modifier)
        .padding(horizontal = 36.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, lineHeight = 28.sp,
            color = if (botanical) LetterThemes.BotanicalText else LetterThemes.DawnText)
        Text("Dear ${message.payload.text("to")},", style = style)
        Text(message.content, style = style)
        Text("With love,\n${message.payload.text("from")}", Modifier.align(Alignment.End), style = style, textAlign = TextAlign.End)
    }
}

/** A single bounded tile, as in the web card. OSM sees this approximate area and the device IP. */
internal fun locationTile(lat: Double, lng: Double): String {
    require(lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)
    val n = 1 shl 15
    val radians = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
    val x = floor((lng + 180) / 360 * n).toInt().coerceIn(0, n - 1)
    val y = floor((1 - ln(tan(radians) + 1 / cos(radians)) / Math.PI) / 2 * n).toInt().coerceIn(0, n - 1)
    return "https://tile.openstreetmap.org/15/$x/$y.png"
}

@Composable
fun FeatureCard(message: ChatMessage, mine: Boolean, original: ChatMessage?, connection: CurrentConnection, vm: FeatureViewModel) {
    val payload = message.payload
    val busy by vm.busy.collectAsState()
    val path = payload.text("path")
    val playing by vm.playing.collectAsState()
    var url by remember(path) { mutableStateOf<String?>(null) }
    var urlError by remember(path) { mutableStateOf<String?>(null) }
    var retryUrl by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val letterDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri -> uri?.let { vm.saveLetter(it, message) } }
    val attachmentDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null && payload != null) vm.saveAttachment(connection.id, payload, uri)
    }
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
        onPlay = { vm.play(connection.id, it) },
        onLetterDownload = { letterDownload.launch("one-on-one-letter.html") },
        onOpenFile = { payload?.let { p -> vm.openFile(connection.id, p) { openAttachment(context, it, p.text("mime")) } } },
        onImageError = { urlError = "Photo unavailable. Tap retry." },
        onRetryPhoto = { url = null; urlError = null; retryUrl++ },
        onSaveCopy = { attachmentDownload.launch(payload.text("name").ifBlank { path.substringAfterLast('/') }) })
}

@Composable
internal fun FeatureCardContent(message: ChatMessage, mine: Boolean, original: ChatMessage?, connection: CurrentConnection,
    busy: Boolean, playing: String?, url: String?, urlError: String?,
    onSend: (String, String, JsonObject, String?, () -> Unit) -> Unit, onPlay: (String) -> Unit,
    onLetterDownload: () -> Unit, onOpenFile: () -> Unit, onImageError: () -> Unit, onRetryPhoto: () -> Unit, onSaveCopy: () -> Unit) {
    val payload = message.payload
    val path = payload.text("path")
    val uriHandler = LocalUriHandler.current
    var reveal by rememberSaveable(message.id, message.tempId) { mutableStateOf(false) }
    var answer by rememberSaveable(message.id, message.tempId) { mutableStateOf("") }
    CompositionLocalProvider(LocalContentColor provides LocalBubbleColors.current.text, LocalTextStyle provides OneTextStyles.subtitle) {
    Column(Modifier.widthIn(min = if (message.type == "image") 0.dp else 224.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (message.type) {
                "letter" -> {
                    CardHeading("✉", "A letter for ${payload.text("to")}")
                    CardAction("Open letter", onClick = { reveal = true })
                    if (reveal) LetterViewer(message, onLetterDownload, close = { reveal = false })
                }
                "ask" -> {
                    CardHeading(if (payload.text("answerB").isNotBlank()) "💌" else "🔒", payload.text("question"))
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
                    CardHeading("🎲", "$a or $b?")
                    if (payload.text("pickRecipient").isNotBlank()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Text(a, Modifier.weight(1f), style = OneTextStyles.subtitle.copy(fontWeight = FontWeight.SemiBold))
                        CardHint("vs")
                        Text(b, Modifier.weight(1f), style = OneTextStyles.subtitle.copy(fontWeight = FontWeight.SemiBold))
                    }
                        val author = original?.senderId ?: if (mine) "other" else connection.myUserId
                        Text("${if (author == connection.myUserId) "You" else connection.otherNickname ?: "Them"}: ${if (payload.text("pickSender") == "a") a else b}")
                        Text("${if (mine) "You" else connection.otherNickname ?: "Them"}: ${if (payload.text("pickRecipient") == "a") a else b}")
                    } else if (mine) CardHint("Sealed — waiting for their choice.")
                    else {
                        CardHint("Their choice is sealed.")
                        if (message.id != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("a" to a, "b" to b).forEach { (pick, label) ->
                            CardAction(label, enabled = !busy, onClick = {
                                val response = buildJsonObject { payload?.forEach { (k, v) -> put(k, v) }; put("pickRecipient", pick) }
                                onSend("thisorthat", "", response, message.id) { }
                            })
                        } }
                    }
                }
                "checkin" -> { CardHeading(moodEmoji(payload.text("mood")), payload.text("mood")); Text(payload.text("note")) }
                "countdown" -> {
                    var remaining by remember(payload.text("targetIso")) { mutableStateOf("") }
                    LaunchedEffect(payload.text("targetIso")) { while (true) {
                        remaining = runCatching { countdownText(payload.text("targetIso")) }.getOrDefault("Unavailable")
                        delay(1_000)
                    } }
                    CardHeading("⏳", payload.text("label")); CardHint(remaining)
                    CardHint(runCatching { Instant.parse(payload.text("targetIso")).atZone(ZoneId.systemDefault()).toLocalDateTime().toString() }.getOrDefault(""))
                }
                "location" -> {
                    val lat = payload.number("lat"); val lng = payload.number("lng")
                    if (lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(OneTheme.radii.sm6)).background(OneTheme.colors.bg)) {
                                AsyncImage(if (LocalInspectionMode.current) null else locationTile(lat, lng), "Map preview of shared location", Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    placeholder = ColorPainter(OneTheme.colors.bgRaised))
                                Text("📍", Modifier.align(Alignment.Center), fontSize = 20.sp)
                            }
                            CardHeading("📍", "Snapshot, not live tracking")
                        }
                        Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
                        CardHint("$lat, $lng${payload.number("accuracy")?.let { " · ±${it.toInt()} m" } ?: ""}")

                        FlowRow {
                            CardAction("View map", onClick = { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$lat,$lng") }, location = true)
                            CardAction("Directions", onClick = { uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng") }, location = true)
                        }
                    } else Text("Location unavailable")
                }
                "image" -> {
                    if (url == null && urlError == null) Text("Loading photo…")
                    url?.let { AsyncImage(if (LocalInspectionMode.current) null else it, "Shared photo", Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp), contentScale = ContentScale.Crop,
                placeholder = ColorPainter(OneTheme.colors.bgRaised), error = if (LocalInspectionMode.current) ColorPainter(OneTheme.colors.bgRaised) else null,
                        onError = { onImageError() }) }
                    urlError?.let { Text(it); CardAction("Retry photo", onClick = { onRetryPhoto() }) }
                    CardAction("View photo", enabled = !busy, onClick = { reveal = true })
                    if (reveal) PhotoViewer(url, busy, onOpenFile, close = { reveal = false })
                }
                "voice" -> {
                    CardHint("Voice note · ${payload.number("duration")?.toInt() ?: 0}s")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(onClick = { onPlay(path) }, enabled = path.isNotBlank(), modifier = Modifier.size(40.dp)
                            .semantics { contentDescription = if (playing == path) "Stop" else "Play" },
                            shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, LocalBubbleColors.current.edge)) {
                            Box(contentAlignment = Alignment.Center) { Text(if (playing == path) "■" else "▶", color = LocalBubbleColors.current.text) }
                        }
                        Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                            // shortcut: playback has no position flow, add a progress fill when the ViewModel exposes one.
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(LocalBubbleColors.current.text.copy(alpha = .3f)))
                        }
                        CardHint(if (playing == path) "Stop" else "Play")
                    }
                }
                "file" -> {
                    CardHeading("📄", payload.text("name")); CardHint("${((payload.number("size") ?: 0.0) / 1024).toInt()} KiB · ${payload.text("mime")}")
                    CardAction("Download and open", enabled = !busy, onClick = { onOpenFile() })
                }
                else -> Text(message.content.ifBlank { message.type })
            }
            if (message.type in setOf("image", "voice", "file") && path.isNotBlank()) CardAction("Save a copy", enabled = !busy, onClick = {
                onSaveCopy()
            })
    }
    }
}

private fun openAttachment(context: Context, uri: Uri, mime: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { context.startActivity(Intent.createChooser(intent, "Open attachment")) }
    catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No app can open this file.", android.widget.Toast.LENGTH_LONG).show() }
}

@Composable
fun AttachmentControls(connection: CurrentConnection, vm: FeatureViewModel, reply: String?, done: () -> Unit) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val recording by vm.recording.collectAsState()
    val voice by vm.voicePath.collectAsState()
    var selected by rememberSaveable(connection.id) { mutableStateOf<String?>(null) }
    var kind by rememberSaveable(connection.id) { mutableStateOf("image") }
    fun keepUri(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        selected = uri.toString()
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { kind = "image"; keepUri(uri) } }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { kind = "file"; keepUri(uri) } }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) vm.record() else vm.showError("Microphone access was denied. Allow it in App info → Permissions to record voice notes.")
    }
    DisposableEffect(vm) { onDispose { vm.background() } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Photo", enabled = !busy && !recording, onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        SecondaryButton("File", enabled = !busy && !recording, onClick = { filePicker.launch(FileMimes.toTypedArray()) })
        SecondaryButton(if (recording) "Stop recording" else "Voice note", enabled = !busy && voice == null, onClick = {
            if (recording) vm.stopRecording()
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.record()
            else microphone.launch(Manifest.permission.RECORD_AUDIO)
        })
    }
    if (recording) Text("Recording… Keep this chat open. Leaving discards the recording.", style = MaterialTheme.typography.labelMedium)
    voice?.let { path -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Send voice note", enabled = !busy, onClick = { vm.upload(connection, "voice", Uri.fromFile(java.io.File(path)), reply, done) })
        SecondaryButton("Discard", enabled = !busy, onClick = vm::discardVoice)
    } }
    val error by vm.error.collectAsState()
    selected?.let { source -> UploadContent(kind, source, busy, error,
        send = { vm.upload(connection, kind, source.toUri(), reply) { selected = null; done() } }, cancel = { selected = null }) }
}

@Composable
internal fun UploadContent(kind: String, source: String?, busy: Boolean, error: String?, send: () -> Unit, cancel: () -> Unit) {
    FeatureModal(onDismissRequest = { if (!busy) cancel() }, title = { Text("Send $kind?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kind == "image") AsyncImage(source, "Selected photo", Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
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
fun AppearanceDialog(connection: CurrentConnection, vm: FeatureViewModel, refresh: () -> Unit, close: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    AppearanceContent(connection.wallpaper, busy, error, onTheme = { vm.theme(it) },
        onWallpaper = { vm.wallpaper(connection.id, it, refresh) }, close)
}

@Composable
internal fun AppearanceContent(wallpaper: String, busy: Boolean, error: String?, onTheme: (String) -> Unit, onWallpaper: (String) -> Unit, close: () -> Unit) {
    FeatureModal(onDismissRequest = close, title = { Text("Appearance") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Theme on this device")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark", "light").forEach { option -> ChoiceButton(option,
                selected = (OneTheme.colors.bg == app.web.oneonone.ui.theme.OneColors.Dark.bg) == (option == "dark"),
                enabled = !busy, onClick = { onTheme(option) }) }
        }
        Text("Wallpaper shared by both of you")
        Choices(listOf("off", "love", "samurai"), wallpaper) { if (!busy) onWallpaper(it) }
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

fun copyMessage(context: Context, message: ChatMessage) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", message.content.ifBlank { messageSummary(message) }))
}

@Composable
private fun ErrorLine(error: String?) {
    error?.let { Text(it, style = OneTextStyles.cardHint, color = OneTheme.colors.danger) }
}

@Composable
internal fun LetterViewer(message: ChatMessage, download: () -> Unit, close: () -> Unit) {
    FeatureModal(onDismissRequest = close, title = { Text("Letter") }, text = { LetterBody(message) },
        confirmButton = { PrimaryButton("Download HTML", download) }, dismissButton = { SecondaryButton("Close", close) })
}

@Composable
internal fun PhotoViewer(url: String?, busy: Boolean, open: () -> Unit, close: () -> Unit) {
    FeatureModal(onDismissRequest = close, text = {
        AsyncImage(url, "Shared photo", Modifier.fillMaxWidth().heightIn(max = 480.dp), contentScale = ContentScale.Fit)
    }, confirmButton = { SecondaryButton("Close", close) }, dismissButton = { SecondaryButton("Open in app", open, enabled = !busy) })
}
