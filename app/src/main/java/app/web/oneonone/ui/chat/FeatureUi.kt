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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    var first by rememberSaveable(type) { mutableStateOf("") }
    var second by rememberSaveable(type) { mutableStateOf("") }
    var third by rememberSaveable(type) { mutableStateOf("") }
    var from by rememberSaveable(type) { mutableStateOf(vm.signature.value.ifBlank { "me" }) }
    var to by rememberSaveable(type) { mutableStateOf(connection.otherNickname?.take(40) ?: "you") }
    var choice by rememberSaveable(type) { mutableStateOf(if (type == "letter") "dawn" else if (type == "checkin") "good" else "a") }
    var target by rememberSaveable(type) { mutableStateOf("") }
    var preview by rememberSaveable(type) { mutableStateOf(false) }
    val signature by vm.signature.collectAsState()
    LaunchedEffect(signature) { if (from == "me" && third.isEmpty() && signature.isNotBlank()) from = signature }
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
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
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text(if (preview) "Preview letter" else "/$type") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    OutlinedButton(onClick = {
                        val initial = target.takeIf { it.isNotBlank() }?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: LocalDateTime.now().plusHours(1)
                        DatePickerDialog(context, { _, y, m, d ->
                            TimePickerDialog(context, { _, hour, minute ->
                                target = LocalDateTime.of(y, m + 1, d, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toString()
                            }, initial.hour, initial.minute, true).show()
                        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis(); show() }
                    }) { Text(if (target.isBlank()) "Choose date and time" else Instant.parse(target).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()) }
                }
                "checkin" -> { Choices(Moods, choice) { choice = it }; Field("A note about your day", first, 300) { first = it } }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = valid && !busy, onClick = {
            if (type == "letter" && !preview) preview = true
            else vm.send(type, if (type == "letter") third.trim() else "", payload, reply, close)
        }) { Text(if (type == "letter" && !preview) "Preview" else "Send") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { if (preview) preview = false else close() }) { Text(if (preview) "Edit" else "Cancel") } })
}

@Composable private fun Field(label: String, value: String, maximum: Int, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(maximum)) }, Modifier.fillMaxWidth(), label = { Text(label) }, maxLines = if (maximum > 100) 8 else 2,
        supportingText = { Text("${value.length}/$maximum") })
}
@Composable private fun Choices(options: List<String>, selected: String, choose: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option -> FilterChip(selected == option, onClick = { choose(option) }, label = { Text(if (option in Moods) "${moodEmoji(option)} $option" else option.replace('_', ' ')) }) }
    }
}
private fun moodEmoji(mood: String) = listOf("😄", "🙂", "😐", "😔", "😞").getOrElse(Moods.indexOf(mood)) { "" }

@Composable private fun LetterBody(message: ChatMessage) {
    val botanical = message.payload.text("appearance") == "botanical"
    val colors = if (botanical) listOf(Color(0xFFF7F3E8), Color(0xFFF7F3E8)) else listOf(Color(0xFFFFE7D0), Color(0xFFFFD1DC), Color(0xFFCFE6FF))
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(0f to colors.first(), .38f to colors[colors.size / 2], 1f to colors.last()))
        .then(if (botanical) Modifier.border(1.dp, Color(0xFFB9C9A6)) else Modifier).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, color = if (botanical) Color(0xFF34432F) else Color(0xFF3A2E3A))
        Text("Dear ${message.payload.text("to")},", style = style)
        Text(message.content, style = style)
        Text("With love,\n${message.payload.text("from")}", style = style)
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
    var reveal by rememberSaveable(message.id, message.tempId) { mutableStateOf(false) }
    var answer by rememberSaveable(message.id, message.tempId) { mutableStateOf("") }
    var url by remember(path) { mutableStateOf<String?>(null) }
    var urlError by remember(path) { mutableStateOf<String?>(null) }
    var retryUrl by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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
    when (message.type) {
        "letter" -> {
            Text("A letter for ${payload.text("to")}")
            TextButton(onClick = { reveal = true }) { Text("Open letter") }
            if (reveal) AlertDialog(onDismissRequest = { reveal = false }, title = { Text("Letter") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { LetterBody(message) } },
                confirmButton = { TextButton(onClick = { letterDownload.launch("one-on-one-letter.html") }) { Text("Download HTML") } },
                dismissButton = { TextButton(onClick = { reveal = false }) { Text("Close") } })
        }
        "ask" -> {
            Text(payload.text("question"), style = MaterialTheme.typography.titleMedium)
            if (payload.text("answerB").isNotBlank()) {
                val author = original?.senderId ?: if (mine) "other" else connection.myUserId
                Text("${if (author == connection.myUserId) "You" else connection.otherNickname ?: "Them"}: ${payload.text("answerA")}")
                Text("${if (message.senderId == connection.myUserId) "You" else connection.otherNickname ?: "Them"}: ${payload.text("answerB")}")
            } else {
                Text("Answers sealed until both reply.")
                if (mine) Text("Waiting for their answer.")
                if (!mine && message.id != null) {
                    Field("Your answer", answer, 500) { answer = it }
                    TextButton(enabled = answer.isNotBlank() && !busy, onClick = {
                        val response = buildJsonObject { payload?.forEach { (k, v) -> put(k, v) }; put("answerB", answer.trim()) }
                        vm.send("ask", "", response, message.id) { answer = "" }
                    }) { Text("Reveal both answers") }
                }
            }
        }
        "thisorthat" -> {
            val a = payload.text("optionA"); val b = payload.text("optionB")
            Text("$a or $b?", style = MaterialTheme.typography.titleMedium)
            if (payload.text("pickRecipient").isNotBlank()) {
                val author = original?.senderId ?: if (mine) "other" else connection.myUserId
                Text("${if (author == connection.myUserId) "You" else connection.otherNickname ?: "Them"}: ${if (payload.text("pickSender") == "a") a else b}")
                Text("${if (mine) "You" else connection.otherNickname ?: "Them"}: ${if (payload.text("pickRecipient") == "a") a else b}")
            } else if (mine) Text("Sealed — waiting for their choice.")
            else {
                Text("Their choice is sealed.")
                if (message.id != null) FlowRow { listOf("a" to a, "b" to b).forEach { (pick, label) ->
                    TextButton(enabled = !busy, onClick = {
                        val response = buildJsonObject { payload?.forEach { (k, v) -> put(k, v) }; put("pickRecipient", pick) }
                        vm.send("thisorthat", "", response, message.id) { }
                    }) { Text(label) }
                } }
            }
        }
        "checkin" -> { Text("${moodEmoji(payload.text("mood"))} ${payload.text("mood")}"); Text(payload.text("note")) }
        "countdown" -> {
            var remaining by remember(payload.text("targetIso")) { mutableStateOf("") }
            LaunchedEffect(payload.text("targetIso")) { while (true) {
                remaining = runCatching { countdownText(payload.text("targetIso")) }.getOrDefault("Unavailable")
                delay(1_000)
            } }
            Text(payload.text("label"), style = MaterialTheme.typography.titleMedium); Text(remaining)
            Text(runCatching { Instant.parse(payload.text("targetIso")).atZone(ZoneId.systemDefault()).toLocalDateTime().toString() }.getOrDefault(""))
        }
        "location" -> {
            val lat = payload.number("lat"); val lng = payload.number("lng")
            if (lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) {
                AsyncImage(locationTile(lat, lng), "Map preview of shared location", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
                Text("$lat, $lng${payload.number("accuracy")?.let { " · ±${it.toInt()} m" } ?: ""}")
                Text("Snapshot, not live tracking", style = MaterialTheme.typography.labelSmall)
                FlowRow {
                    TextButton(onClick = { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$lat,$lng") }) { Text("View map") }
                    TextButton(onClick = { uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng") }) { Text("Directions") }
                }
            } else Text("Location unavailable")
        }
        "image" -> {
            if (url == null && urlError == null) Text("Loading photo…")
            url?.let { AsyncImage(it, "Shared photo", Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp), contentScale = ContentScale.Fit,
                onError = { urlError = "Photo unavailable. Tap retry." }) }
            urlError?.let { Text(it); TextButton(onClick = { url = null; urlError = null; retryUrl++ }) { Text("Retry photo") } }
            TextButton(enabled = !busy, onClick = { reveal = true }) { Text("View photo") }
            if (reveal) AlertDialog(onDismissRequest = { reveal = false }, text = {
                AsyncImage(url, "Shared photo", Modifier.fillMaxWidth().heightIn(max = 480.dp), contentScale = ContentScale.Fit)
            }, confirmButton = { TextButton(onClick = { reveal = false }) { Text("Close") } },
                dismissButton = { TextButton(enabled = !busy, onClick = { payload?.let { p -> vm.openFile(connection.id, p) { openAttachment(context, it, p.text("mime")) } } }) { Text("Open in app") } })
        }
        "voice" -> {
            Text("Voice note · ${payload.number("duration")?.toInt() ?: 0}s")
            TextButton(enabled = path.isNotBlank(), onClick = { vm.play(connection.id, path) }) { Text(if (playing == path) "Stop" else "Play") }
        }
        "file" -> {
            Text(payload.text("name")); Text("${((payload.number("size") ?: 0.0) / 1024).toInt()} KiB · ${payload.text("mime")}", style = MaterialTheme.typography.labelSmall)
            TextButton(enabled = !busy, onClick = { payload?.let { p -> vm.openFile(connection.id, p) { openAttachment(context, it, p.text("mime")) } } }) { Text("Download and open") }
        }
        else -> Text(message.content.ifBlank { message.type })
    }
    if (message.type in setOf("image", "voice", "file") && path.isNotBlank()) TextButton(enabled = !busy, onClick = {
        attachmentDownload.launch(payload.text("name").ifBlank { path.substringAfterLast('/') })
    }) { Text("Save a copy") }
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(enabled = !busy && !recording, onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Photo") }
        TextButton(enabled = !busy && !recording, onClick = { filePicker.launch(FileMimes.toTypedArray()) }) { Text("File") }
        TextButton(enabled = !busy && voice == null, onClick = {
            if (recording) vm.stopRecording()
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.record()
            else microphone.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text(if (recording) "Stop recording" else "Voice note") }
    }
    if (recording) Text("Recording… Keep this chat open. Leaving discards the recording.", style = MaterialTheme.typography.labelMedium)
    voice?.let { path -> Row {
        TextButton(enabled = !busy, onClick = { vm.upload(connection, "voice", Uri.fromFile(java.io.File(path)), reply, done) }) { Text("Send voice note") }
        TextButton(enabled = !busy, onClick = vm::discardVoice) { Text("Discard") }
    } }
    selected?.let { source -> AlertDialog(onDismissRequest = { if (!busy) selected = null }, title = { Text("Send $kind?") },
        text = { Column {
            if (kind == "image") AsyncImage(source, "Selected photo", Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
            Text(if (kind == "image") "Up to 10 MiB. Static photos have location metadata removed; very large photos are resized." else "Up to 25 MiB. PDF, text, CSV, Word, Excel and PowerPoint.")
            FeatureError(vm)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { vm.upload(connection, kind, source.toUri(), reply) { selected = null; done() } }) { Text(if (busy) "Uploading…" else "Send") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Cancel") } }) }
}

@Composable
fun LocationConfirmation(connection: CurrentConnection, vm: FeatureViewModel, reply: String?, close: () -> Unit) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed ->
        if (allowed.values.any { it }) vm.location(connection, reply, close)
        else vm.showError("Location access was denied. Allow approximate or precise location in App info → Permissions to share a snapshot.")
    }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("Share your location?") },
        text = { Column { Text("Send one location snapshot to this person. It stays in your conversation until the connection ends. Approximate access works too. Map previews use OpenStreetMap, which sees the map area and your IP address."); FeatureError(vm) } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                vm.location(connection, reply, close)
            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }) { Text(if (busy) "Locating…" else "Share snapshot") } },
        dismissButton = { TextButton(enabled = !busy, onClick = close) { Text("Cancel") } })
}

@Composable
fun AppearanceDialog(connection: CurrentConnection, vm: FeatureViewModel, refresh: () -> Unit, close: () -> Unit) {
    val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest = close, title = { Text("Appearance") }, text = { Column {
        Text("Theme on this device")
        FlowRow { listOf("dark", "light").forEach { TextButton(enabled = !busy, onClick = { vm.theme(it) }) { Text(it) } } }
        Text("Wallpaper shared by both of you")
        Choices(listOf("off", "love", "samurai"), connection.wallpaper) { if (!busy) vm.wallpaper(connection.id, it, refresh) }
        FeatureError(vm)
    } }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
}

@Composable
fun ReportDialog(connection: CurrentConnection, message: String?, vm: FeatureViewModel, close: () -> Unit) {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf("other") }
    var reason by rememberSaveable { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text(if (message == null) "Report this person" else "Report this message") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Reports go to the safety team. Reporting does not block or end your connection.")
            Choices(ReportCategories, category) { category = it }; Field("Details (optional)", reason, 1_000) { reason = it }
            FeatureError(vm)
        } }, confirmButton = { TextButton(enabled = !busy, onClick = { vm.report(connection.id, message, category, reason) {
            android.widget.Toast.makeText(context, "Report submitted.", android.widget.Toast.LENGTH_SHORT).show()
            close()
        } }) { Text("Submit report") } }, dismissButton = { TextButton(enabled = !busy, onClick = close) { Text("Cancel") } })
}

@Composable
fun BlockDialog(connection: CurrentConnection, vm: FeatureViewModel, close: () -> Unit, refresh: () -> Unit) {
    val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("Block this person?") },
        text = { Column { Text("Blocking immediately ends this connection and deletes its conversation and attachments for both of you. Export first if you want to keep a copy. You can unblock in Settings, but the conversation cannot be restored."); FeatureError(vm) } },
        confirmButton = { TextButton(enabled = !busy, onClick = { vm.block(connection.id) { close(); refresh() } }) { Text("Block and end") } },
        dismissButton = { TextButton(enabled = !busy, onClick = close) { Text("Cancel") } })
}

@Composable
fun ExportDialog(connection: CurrentConnection, vm: FeatureViewModel, close: () -> Unit) {
    var format by rememberSaveable { mutableStateOf("txt") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let { vm.export(it, connection, format) }; close()
    }
    AlertDialog(onDismissRequest = close, title = { Text("Export conversation") }, text = { Column {
        Text("Save messages from server history as text, JSON or HTML. Attachments are represented by metadata; their signed links expire. Download attachments separately before ending the connection. Keep exports somewhere private.")
        Choices(listOf("txt", "json", "html"), format) { format = it }
    } }, confirmButton = { TextButton(onClick = { launcher.launch("one-on-one-conversation.$format") }) { Text("Choose where to save") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

fun copyMessage(context: Context, message: ChatMessage) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", message.content.ifBlank { messageSummary(message) }))
}

@Composable private fun FeatureError(vm: FeatureViewModel) {
    val error by vm.error.collectAsState()
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
