package app.web.oneonone.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.web.oneonone.call.CallKind
import app.web.oneonone.R
import coil3.compose.AsyncImage
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.chat.newerTime
import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import app.web.oneonone.ui.chat.cards.AlarmCard
import app.web.oneonone.ui.chat.cards.CallLogCard
import app.web.oneonone.ui.theme.BubblePalette
import app.web.oneonone.ui.theme.BubbleTokens
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun receiptLabel(message: ChatMessage, readAt: String?, deliveredAt: String?): String = when {
    message.id == null -> message.deliveryState
    readAt != null && !Instant.parse(readAt).isBefore(Instant.parse(message.createdAt)) -> "Read"
    deliveredAt != null && !Instant.parse(deliveredAt).isBefore(Instant.parse(message.createdAt)) -> "Delivered"
    else -> "Sent"
}

@Composable
fun ChatScreen(connection: CurrentConnection, vm: ChatViewModel, features: FeatureViewModel, onSettings: () -> Unit, onRefresh: () -> Unit, accountError: String? = null) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val draft by vm.draft.collectAsState()
    val replyTo by vm.replyTo.collectAsState()
    val state by vm.connectionState.collectAsState()
    val error by vm.error.collectAsState()
    val busy by vm.busy.collectAsState()
    val featureBusy by features.busy.collectAsState()
    val featureError by features.error.collectAsState()
    val recording by features.recording.collectAsState()
    val hasOlder by vm.hasOlder.collectAsState()
    val receipts by vm.receipts.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(connection.id, connection.myUserId) { vm.open(connection) }
    LaunchedEffect(vm) { vm.ended.collect { onRefresh() } }
    DisposableEffect(lifecycle, connection.id) {
        val observer = LifecycleEventObserver { _, _ ->
            val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            vm.visible(resumed)
            if (!resumed) features.background()
        }
        lifecycle.addObserver(observer)
        vm.visible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); vm.visible(false); features.background() }
    }
    var query by rememberSaveable(connection.id) { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf(false) }
    var leave by rememberSaveable { mutableStateOf(false) }
    var alarm by rememberSaveable { mutableStateOf(false) }
    var duplicate by rememberSaveable { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var command by rememberSaveable(connection.id) { mutableStateOf<String?>(null) }
    var commands by remember { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    var export by rememberSaveable { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf(false) }
    var reportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var block by rememberSaveable { mutableStateOf(false) }
    var attachmentOpen by rememberSaveable { mutableStateOf(false) }
    val palette = when (connection.wallpaper) {
        "love" -> BubbleTokens.Love
        "samurai" -> BubbleTokens.Samurai
        else -> if (MaterialTheme.colorScheme.background.luminance() < .5f) BubbleTokens.Dark else BubbleTokens.Light
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val filtered = remember(messages, query) { messages.filter { query.isBlank() || (it.content + " " + messageSummary(it)).contains(query, ignoreCase = true) } }
    val wasNearBottom by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: true } }
    LaunchedEffect(filtered.lastOrNull()?.id, filtered.lastOrNull()?.tempId) {
        if (filtered.isNotEmpty() && (wasNearBottom || filtered.last().senderId == connection.myUserId)) {
            list.scrollToItem(filtered.lastIndex + 1)
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(connection.otherNickname ?: "Your One on One", style = MaterialTheme.typography.titleLarge)
            Text(when (state) { ConnectionState.Connected -> "Connected"; ConnectionState.Connecting -> "Connecting…"; ConnectionState.Offline -> "Waiting for connection" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = !recording, onClick = { features.background(); vm.call(CallKind.Audio) }) { Text("Voice call") }
                TextButton(enabled = !recording, onClick = { features.background(); vm.call(CallKind.Video) }) { Text("Video call") }
                Box {
                    TextButton(onClick = { more = true }) { Text("More") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Search") }, onClick = { search = !search; more = false })
                        DropdownMenuItem(text = { Text("Nickname") }, onClick = { nickname = true; more = false })
                        DropdownMenuItem(text = { Text("Appearance") }, onClick = { appearance = true; more = false })
                        DropdownMenuItem(text = { Text("Export conversation") }, onClick = { export = true; more = false })
                        DropdownMenuItem(text = { Text("Report person") }, onClick = { features.clearError(); report = true; more = false })
                        DropdownMenuItem(text = { Text("Block person") }, onClick = { features.clearError(); block = true; more = false })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { onSettings(); more = false })
                        DropdownMenuItem(text = { Text("Leave connection") }, onClick = { leave = true; more = false })
                    }
                }
            }
            if (connection.myLeaveStep > 0 || connection.otherLeaveStep > 0) {
                Text("Leave countdown: you ${connection.myLeaveStep}/5, them ${connection.otherLeaveStep}/5")
            }
            if (search) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search loaded messages") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            accountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            featureError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (featureBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        val wallpaper = when (connection.wallpaper) { "love" -> R.drawable.wallpaper_love; "samurai" -> R.drawable.wallpaper_samurai; else -> null }
        wallpaper?.let { AsyncImage(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "history-control") {
                if (hasOlder) TextButton(onClick = { vm.older() }, enabled = !busy) { Text("Load older messages") }
                else Text("Start of your One on One")
            }
            itemsIndexed(filtered, key = { _, it -> it.id ?: "pending:${it.tempId}" }) { index, message ->
                val day = dayLabel(message.createdAt)
                if (index == 0 || dayLabel(filtered[index - 1].createdAt) != day) {
                    Text(day, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
                val mine = message.senderId == connection.myUserId
                val label = receiptLabel(message, newerTime(connection.otherLastReadAt, receipts?.lastReadAt),
                    newerTime(connection.otherLastDeliveredAt, receipts?.lastDeliveredAt))
                MessageBubble(message, mine, palette, label,
                    reply = messages.find { it.id == message.replyTo },
                    onReply = { vm.reply(message.id) },
                    onReact = { emoji -> vm.react(checkNotNull(message.id), emoji,
                        message.reactions.any { it.emoji == emoji && connection.myUserId in it.userIds }) },
                    onRetry = { if (message.deliveryState == "unknown") duplicate = message.tempId else message.tempId?.let { vm.retry(it) } },
                    onSend = vm::sendCard, connection = connection, features = features,
                    onReport = { features.clearError(); reportMessage = message.id },
                    onQuote = { message.replyTo?.let { id ->
                        val at = filtered.indexOfFirst { it.id == id }
                        if (at >= 0) scope.launch { list.animateScrollToItem(at + 1) }
                        else android.widget.Toast.makeText(context, "Load older messages or clear search to see this reply.", android.widget.Toast.LENGTH_SHORT).show()
                    } })
            }
        }
        }
        replyTo?.let { id ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reply: ${messages.find { it.id == id }?.let(::messageSummary) ?: "earlier message"}", Modifier.weight(1f), maxLines = 2)
                TextButton(onClick = { vm.reply(null) }) { Text("Cancel reply") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Box {
                TextButton(enabled = !recording, onClick = { commands = true }) { Text("Commands") }
                DropdownMenu(commands, onDismissRequest = { commands = false }) {
                    FeatureCommands.forEach { name -> DropdownMenuItem(text = { Text("/$name") }, onClick = {
                        commands = false; features.clearError(); if (name == "alarm") alarm = true else command = name
                    }) }
                }
            }
            TextButton(onClick = { more = false; attachmentOpen = !attachmentOpen }) { Text("Attach") }
        }
        if (attachmentOpen) AttachmentControls(connection, features, replyTo) { vm.reply(null) }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, vm::draft, Modifier.weight(1f), label = { Text("Message") }, maxLines = 5)
            Button(onClick = {
                val name = draft.trim().removePrefix("/")
                if (draft.trim().startsWith("/") && name in FeatureCommands) {
                    features.clearError(); if (name == "alarm") alarm = true else command = name
                } else vm.send()
            }, enabled = draft.isNotBlank() && !busy && !featureBusy) { Text("Send") }
        }
    }
    command?.let { type ->
        val close = { command = null; vm.draft(""); vm.reply(null) }
        if (type == "location") LocationConfirmation(connection, features, replyTo, close)
        else FeatureComposer(type, connection, features, replyTo, close)
    }
    if (appearance) AppearanceDialog(connection, features, onRefresh) { appearance = false }
    if (export) ExportDialog(connection, features) { export = false }
    if (report || reportMessage != null) ReportDialog(connection, reportMessage, features) { report = false; reportMessage = null }
    if (block) BlockDialog(connection, features, close = { block = false }, refresh = onRefresh)
    if (alarm) AlertDialog(onDismissRequest = { alarm = false }, title = { Text("Send an emergency alarm?") },
        text = { Text("This sounds an alarm on their phone, even when it's locked. Use it only for a genuine emergency.") },
        // A raise is never a reply (web sends replyTo null).
        confirmButton = { TextButton(onClick = { vm.sendCard("alarm", JsonObject(emptyMap()), null); vm.draft(""); vm.reply(null); alarm = false }) { Text("Send alarm") } },
        dismissButton = { TextButton(onClick = { alarm = false }) { Text("Cancel") } })
    duplicate?.let { tempId -> AlertDialog(onDismissRequest = { duplicate = null }, title = { Text("Resend this message?") },
        text = { Text("Its delivery is unknown. Check the conversation first; resending can create a duplicate.") },
        confirmButton = { TextButton(onClick = { vm.retry(tempId, true); duplicate = null }) { Text("Resend") } },
        dismissButton = { TextButton(onClick = { duplicate = null }) { Text("Cancel") } }) }
    if (nickname) NicknameDialog(connection, busy, onDismiss = { nickname = false }) {
        vm.nickname(connection.id, it) { nickname = false; onRefresh() }
    }
    if (leave) LeaveDialog(connection, busy, onDismiss = { leave = false }) {
        vm.leave(connection.id, it) { leave = false; onRefresh() }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage, mine: Boolean, palette: BubblePalette, receipt: String, reply: ChatMessage?,
    onReply: () -> Unit, onReact: (String) -> Unit, onRetry: () -> Unit,
    onSend: (String, JsonObject, String?) -> Unit,
    connection: CurrentConnection, features: FeatureViewModel, onReport: () -> Unit, onQuote: () -> Unit,
) {
    val context = LocalContext.current
    if (message.type == "system") {
        Text("${if (mine) "You" else "They"} changed ${message.payload?.get("event")?.jsonPrimitive?.content} to ${message.payload?.get("value")?.jsonPrimitive?.content}",
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium)
        return
    }
    val colors = if (mine) palette.mine else palette.other
    val time = remember(message.createdAt) { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(message.createdAt)) }
    val ticks = when (receipt) { "Read", "Delivered" -> "✓✓"; "Sent" -> "✓"; else -> "◷" }
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth(.82f).widthIn(max = 420.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Box {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.Transparent, border = BorderStroke(1.dp, colors.edge),
                    modifier = Modifier.drawBehind {
                        val x = if (mine) size.width else 0f
                        val direction = if (mine) 1f else -1f
                        val tail = Path().apply {
                            moveTo(x - direction * 4.dp.toPx(), size.height - 12.dp.toPx())
                            lineTo(x + direction * 6.dp.toPx(), size.height)
                            lineTo(x - direction * 8.dp.toPx(), size.height - 2.dp.toPx())
                            close()
                        }
                        drawPath(tail, colors.tail)
                    }.combinedClickable(onClick = { expanded = !expanded }, onLongClick = { menu = true })) {
                    Column(Modifier.drawWithCache {
                        // CSS 135deg: a fixed diagonal, independent of bubble aspect ratio.
                        val half = (size.width + size.height) / 4f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val diagonal = Offset(half, half)
                        val brush = Brush.linearGradient(listOf(colors.backgroundStart, colors.backgroundEnd), center - diagonal, center + diagonal)
                        onDrawBehind { drawRect(brush) }
                    }.padding(12.dp)) {
                        CompositionLocalProvider(LocalContentColor provides colors.text) {
                            if (message.replyTo != null) TextButton(onClick = onQuote) { Text("↪ ${reply?.let(::messageSummary) ?: "Earlier message"}", color = colors.text, style = MaterialTheme.typography.labelMedium, maxLines = 2) }
                            when (message.type) {
                                "alarm" -> AlarmCard(message, mine, onSend)
                                "call" -> CallLogCard(message, mine, onSend)
                                "text" -> Text(buildAnnotatedString {
                                    append(message.content)
                                    Regex("https?://[^\\s<>]+").findAll(message.content).forEach { match ->
                                        addLink(LinkAnnotation.Url(match.value, TextLinkStyles(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))), match.range.first, match.range.last + 1)
                                    }
                                    append("  ")
                                    withStyle(SpanStyle(color = colors.meta, fontSize = 11.sp)) { append(time) }
                                    if (mine) withStyle(SpanStyle(color = if (receipt == "Read") palette.read else palette.ticks, fontSize = 11.sp)) { append(" $ticks") }
                                }, color = colors.text, modifier = Modifier.semantics {
                                    contentDescription = "${message.content}, $time${if (mine) ", $receipt" else ""}"
                                })
                                else -> FeatureCard(message, mine, reply, connection, features)
                            }
                            if (message.type != "text") Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(time, color = colors.meta, fontSize = 11.sp)
                                if (mine && message.type != "call") Text(ticks, color = if (receipt == "Read") palette.read else palette.ticks, fontSize = 11.sp,
                                    modifier = Modifier.semantics { contentDescription = receipt })
                            }
                            if (expanded) Text(message.createdAt, color = colors.meta, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, onClick = { copyMessage(context, message); menu = false })
                    if (message.id != null && !mine) DropdownMenuItem(text = { Text("Report message") }, onClick = { onReport(); menu = false })
                    if (message.id != null && message.type != "call") {
                        DropdownMenuItem(text = { Text("Reply") }, onClick = { onReply(); menu = false })
                        AllowedReactions.forEach { emoji -> DropdownMenuItem(text = { Text("React $emoji") }, onClick = { onReact(emoji); menu = false }) }
                    }
                }
            }
            if (message.reactions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                message.reactions.forEach { reaction ->
                    TextButton(onClick = { onReact(reaction.emoji) }) { Text("${reaction.emoji} ${reaction.userIds.size}") }
                }
            }
            if (message.id == null && message.deliveryState in setOf("failed", "unknown", "queued")) {
                Text(message.error ?: "Queued — sends when connected", style = MaterialTheme.typography.labelSmall)
                if (message.deliveryState in setOf("failed", "unknown")) TextButton(onClick = onRetry) { Text(if (message.deliveryState == "unknown") "Review resend" else "Retry") }
            }
        }
    }
}

private fun dayLabel(value: String) = Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate().toString()

@Composable
private fun NicknameDialog(connection: CurrentConnection, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(connection.id) { mutableStateOf(connection.otherNickname.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("What would you like to call this person?") },
        text = { OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nickname") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = !busy && name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun LeaveDialog(connection: CurrentConnection, busy: Boolean, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Leave connection") },
        text = { Text("Leaving takes five daily steps, one every 24 hours. Ending deletes the conversation and attachments for both of you. Your step: ${connection.myLeaveStep}/5. ${if (connection.bothLeaving) "You both chose to leave; you can end immediately." else ""}") },
        confirmButton = { TextButton(onClick = { onAction(if (connection.bothLeaving) "end" else "advance") },
            enabled = !busy && (connection.bothLeaving || connection.myLeaveStep == 0 || connection.canAdvanceLeave)) {
            Text(if (connection.bothLeaving) "End now" else "Advance one step")
        } },
        dismissButton = { TextButton(onClick = { if (connection.myLeaveStep > 0) onAction("cancel") else onDismiss() }, enabled = !busy) {
            Text(if (connection.myLeaveStep > 0) "Keep connection" else "Cancel")
        } })
}
