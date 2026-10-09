package app.web.oneonone.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import app.web.oneonone.ui.components.DangerButton
import app.web.oneonone.ui.components.OneTextField
import app.web.oneonone.ui.components.PrimaryButton
import app.web.oneonone.ui.components.SecondaryButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import app.web.oneonone.ui.theme.BubbleTokens
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

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
    // Live-arrival animation: only messages created after this screen opened, and each key at most once.
    val openedAt = remember { Instant.now() }
    val animated = remember { mutableSetOf<String>() }
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val slash = if (recording) emptyList() else slashMatches(draft)
    val runCommand = { name: String -> features.clearError(); if (name == "alarm") alarm = true else command = name }
    Column(Modifier.fillMaxSize().imePadding()) {
        ChatHeader(
            title = connection.otherNickname ?: "Your One on One",
            state = state,
            callsEnabled = !recording,
            onVideo = { features.background(); vm.call(CallKind.Video) },
            onCall = { features.background(); vm.call(CallKind.Audio) },
            onMore = { more = true },
            menu = {
                ChatMenu(
                    expanded = more, onDismiss = { more = false },
                    onSearch = { search = !search; more = false },
                    onAppearance = { appearance = true; more = false },
                    onSettings = { onSettings(); more = false },
                    onNickname = { nickname = true; more = false },
                    onExport = { export = true; more = false },
                    onReport = { features.clearError(); report = true; more = false },
                    onBlock = { features.clearError(); block = true; more = false },
                    onLeave = { leave = true; more = false },
                )
            },
        )
        if (search) ChatSearchBar(query, { query = it }, onClose = { search = false; query = "" })
        if (connection.myLeaveStep > 0 || connection.otherLeaveStep > 0) {
            LeaveBanner("Leave countdown: you ${connection.myLeaveStep}/5, them ${connection.otherLeaveStep}/5")
        }
        error?.let { ErrorLine(it) }
        accountError?.let { ErrorLine(it) }
        featureError?.let { ErrorLine(it) }
        if (featureBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val wallpaper = when (connection.wallpaper) { "love" -> R.drawable.wallpaper_love; "samurai" -> R.drawable.wallpaper_samurai; else -> null }
            wallpaper?.let {
                AsyncImage(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(if (connection.wallpaper == "love") BubbleTokens.LoveWallpaperOverlay else BubbleTokens.SamuraiWallpaperOverlay))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = OneTheme.sizes.chatMax720).fillMaxSize(), state = list,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                    item(key = "history-control") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (hasOlder) LoadOlder(enabled = !busy) { vm.older() }
                            else Text("Start of your One on One", color = OneTheme.colors.muted, style = OneTextStyles.cardHint.copy(fontSize = 12.sp))
                            EncryptionNote()
                        }
                    }
                    itemsIndexed(filtered, key = { _, it -> messageKey(it) }) { index, message ->
                        val day = dayLabel(message.createdAt)
                        if (index == 0 || dayLabel(filtered[index - 1].createdAt) != day) DateSeparator(day)
                        val mine = message.senderId == connection.myUserId
                        if (message.type == "system") {
                            SystemLine("${if (mine) "You" else "They"} changed ${message.payload?.get("event")?.jsonPrimitive?.content} to ${message.payload?.get("value")?.jsonPrimitive?.content}")
                            return@itemsIndexed
                        }
                        val label = receiptLabel(message, newerTime(connection.otherLastReadAt, receipts?.lastReadAt),
                            newerTime(connection.otherLastDeliveredAt, receipts?.lastDeliveredAt))
                        val original = messages.find { it.id == message.replyTo }
                        val key = messageKey(message)
                        val animateIn = remember(key) { (message.id == null || Instant.parse(message.createdAt).isAfter(openedAt)) && animated.add(key) }
                        MessageBubble(message, mine, palette, label,
                            groupStart = isGroupStart(filtered.getOrNull(index - 1), message),
                            animateIn = animateIn,
                            quote = message.replyTo?.let { BubbleQuote(authorOf(original, connection), original?.let(::messageSummary) ?: "Earlier message") },
                            onReply = { vm.reply(message.id) },
                            onReact = { emoji -> vm.react(checkNotNull(message.id), emoji,
                                message.reactions.any { it.emoji == emoji && connection.myUserId in it.userIds }) },
                            onRetry = { if (message.deliveryState == "unknown") duplicate = message.tempId else message.tempId?.let { vm.retry(it) } },
                            onReport = { features.clearError(); reportMessage = message.id },
                            onQuote = { message.replyTo?.let { id ->
                                val at = filtered.indexOfFirst { it.id == id }
                                if (at >= 0) scope.launch { list.animateScrollToItem(at + 1) }
                                else android.widget.Toast.makeText(context, "Load older messages or clear search to see this reply.", android.widget.Toast.LENGTH_SHORT).show()
                            } },
                            card = {
                                when (message.type) {
                                    "alarm" -> AlarmCard(message, mine, vm::sendCard)
                                    "call" -> CallLogCard(message, mine, vm::sendCard)
                                    else -> FeatureCard(message, mine, original, connection, features)
                                }
                            })
                    }
                }
            }
            if (slash.isNotEmpty()) {
                SlashMenu(slash, maxHeight = minOf(220.dp, screenHeight * .4f), onPick = runCommand,
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = OneTheme.sizes.chatMax720).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp))
            }
        }
        replyTo?.let { id ->
            val original = messages.find { it.id == id }
            ReplyBar(authorOf(original, connection).ifEmpty { "Earlier message" }, original?.let(::messageSummary) ?: "earlier message") { vm.reply(null) }
        }
        if (attachmentOpen) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { AttachmentControls(connection, features, replyTo) { vm.reply(null) } }
        ChatComposer(
            draft = draft, onDraft = vm::draft,
            canSend = !busy && !featureBusy,
            recording = recording,
            onAttach = { more = false; attachmentOpen = !attachmentOpen },
            onMic = { attachmentOpen = true },
            onSend = {
                val name = draft.trim().removePrefix("/")
                if (draft.trim().startsWith("/") && name in FeatureCommands) runCommand(name) else vm.send()
            },
        )
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
    if (alarm) FeatureModal(onDismissRequest = { alarm = false }, title = { Text("Send an emergency alarm?") },
        text = { Text("This sounds an alarm on their phone, even when it's locked. Use it only for a genuine emergency.") },
        // A raise is never a reply (web sends replyTo null).
        confirmButton = { DangerButton("Send alarm", onClick = { vm.sendCard("alarm", JsonObject(emptyMap()), null); vm.draft(""); vm.reply(null); alarm = false }) },
        dismissButton = { SecondaryButton("Cancel", onClick = { alarm = false }) })
    duplicate?.let { tempId -> FeatureModal(onDismissRequest = { duplicate = null }, title = { Text("Resend this message?") },
        text = { Text("Its delivery is unknown. Check the conversation first; resending can create a duplicate.") },
        confirmButton = { PrimaryButton("Resend", onClick = { vm.retry(tempId, true); duplicate = null }) },
        dismissButton = { SecondaryButton("Cancel", onClick = { duplicate = null }) }) }
    if (nickname) NicknameDialog(connection, busy, onDismiss = { nickname = false }) {
        vm.nickname(connection.id, it) { nickname = false; onRefresh() }
    }
    if (leave) LeaveDialog(connection, busy, onDismiss = { leave = false }) {
        vm.leave(connection.id, it) { leave = false; onRefresh() }
    }
}

/**
 * Stable list key. tempId survives the server ack (ChatDatabase keeps it), whereas id appears only after it, so keying
 * on id would re-create the row (losing menu/expanded state, replaying the enter animation) when a sent message is
 * acked. Prefixed by sender so an incoming message that carries the sender's tempId cannot collide with ours.
 */
private fun messageKey(message: ChatMessage): String =
    message.tempId?.let { "t:${message.senderId}:$it" } ?: message.id ?: "m:${message.createdAt}:${message.senderId}"

/** Who wrote [message], as shown in a quote / reply bar. Empty when the original isn't loaded. */
private fun authorOf(message: ChatMessage?, connection: CurrentConnection): String = when {
    message == null -> ""
    message.senderId == connection.myUserId -> "You"
    else -> connection.otherNickname ?: "Them"
}

private fun dayLabel(value: String) = Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** .chat__date-separator: centred pill on bg-raised. */
@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, Modifier.clip(RoundedCornerShape(OneTheme.radii.full)).background(OneTheme.colors.bgRaised).padding(horizontal = 12.dp, vertical = 4.dp),
            color = OneTheme.colors.textDim, style = OneTextStyles.cardHint)
    }
}

/** .chat__load-older: small outlined button. */
@Composable
private fun LoadOlder(enabled: Boolean, onClick: () -> Unit) {
    val c = OneTheme.colors
    Text("Load older messages",
        Modifier.padding(bottom = 4.dp).border(1.dp, c.border, RoundedCornerShape(OneTheme.radii.xs4))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (enabled) c.textDim else c.muted, style = OneTextStyles.cardHint.copy(fontSize = 12.sp))
}

/** .chat__enc-note: lock glyph + "Messages are encrypted". */
@Composable
private fun EncryptionNote() {
    val c = OneTheme.colors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = c.muted, modifier = Modifier.size(11.dp))
        Text("Messages are encrypted", color = c.muted, style = OneTextStyles.bubbleMeta)
    }
}

@Composable
private fun NicknameDialog(connection: CurrentConnection, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(connection.id) { mutableStateOf(connection.otherNickname.orEmpty()) }
    FeatureModal(onDismissRequest = onDismiss, title = { Text("What would you like to call this person?") },
        text = { OneTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), placeholder = "Nickname") },
        confirmButton = { PrimaryButton("Save", onClick = { onSave(name) }, enabled = !busy && name.isNotBlank()) },
        dismissButton = { SecondaryButton("Cancel", onClick = onDismiss) })
}

@Composable
private fun LeaveDialog(connection: CurrentConnection, busy: Boolean, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    FeatureModal(onDismissRequest = onDismiss, title = { Text("Leave connection") },
        text = { Text("Leaving takes five daily steps, one every 24 hours. Ending deletes the conversation and attachments for both of you. Your step: ${connection.myLeaveStep}/5. ${if (connection.bothLeaving) "You both chose to leave; you can end immediately." else ""}") },
        confirmButton = { DangerButton(if (connection.bothLeaving) "End now" else "Advance one step",
            onClick = { onAction(if (connection.bothLeaving) "end" else "advance") },
            enabled = !busy && (connection.bothLeaving || connection.myLeaveStep == 0 || connection.canAdvanceLeave)) },
        dismissButton = { SecondaryButton(if (connection.myLeaveStep > 0) "Keep connection" else "Cancel",
            onClick = { if (connection.myLeaveStep > 0) onAction("cancel") else onDismiss() }, enabled = !busy) })
}
