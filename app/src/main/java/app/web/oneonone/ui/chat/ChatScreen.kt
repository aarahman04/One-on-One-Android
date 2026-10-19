package app.web.oneonone.ui.chat

import android.Manifest
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import app.web.oneonone.alarm.AlarmCardState
import app.web.oneonone.alarm.AlarmPolicy
import app.web.oneonone.push.MessageNotifications
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.time.Instant
import java.time.ZoneId

internal fun receiptLabel(message: ChatMessage, readAt: String?, deliveredAt: String?): String = when {
    message.id == null -> message.deliveryState
    readAt != null && !Instant.parse(readAt).isBefore(Instant.parse(message.createdAt)) -> "Read"
    deliveredAt != null && !Instant.parse(deliveredAt).isBefore(Instant.parse(message.createdAt)) -> "Delivered"
    else -> "Sent"
}

@Composable
fun ChatScreen(connection: CurrentConnection, vm: ChatViewModel, features: FeatureViewModel, stickers: StickerViewModel, onSettings: () -> Unit, onAccount: () -> Unit, onRefresh: () -> Unit, accountError: String? = null,
               partnerAvatar: Pair<String, String>? = null) {
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
    val presence by vm.presence.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(connection.id, connection.myUserId) { vm.open(connection) }
    LaunchedEffect(vm) { vm.ended.collect { onRefresh() } }
    DisposableEffect(lifecycle, connection.id) {
        val observer = LifecycleEventObserver { _, _ ->
            val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            vm.visible(resumed)
            if (resumed) MessageNotifications.clear(context, connection.id)
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
    var report by rememberSaveable { mutableStateOf(false) }
    var reportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var block by rememberSaveable { mutableStateOf(false) }
    var attachmentOpen by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(connection.id) { mutableStateOf<String?>(null) }
    var kind by rememberSaveable(connection.id) { mutableStateOf("image") }
    // Long-press selection (WhatsApp): keys of the selected rows, in selection order.
    var selectedKeys by rememberSaveable(connection.id) { mutableStateOf(emptyList<String>()) }
    val selecting = selectedKeys.isNotEmpty()
    val clearSelection = { selectedKeys = emptyList() }
    BackHandler(selecting, clearSelection)
    var selectionMenu by remember { mutableStateOf(false) }
    val saves = remember(connection.id) { mutableMapOf<String, MutableState<(() -> Unit)?>>() }
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Reply opens the keyboard on the composer, like swiping to reply in WhatsApp.
    val startReply = { id: String? -> vm.reply(id); if (id != null) runCatching { composerFocus.requestFocus(); keyboard?.show() } }
    // A new person with no nickname yet: ask once per visit what to call them.
    var nicknameAsked by rememberSaveable(connection.id) { mutableStateOf(false) }
    LaunchedEffect(connection.id, connection.otherNickname) {
        if (connection.otherNickname == null && !nicknameAsked) { nicknameAsked = true; nickname = true }
    }
    val editing by vm.editing.collectAsState()
    val otherTyping by vm.otherTyping.collectAsState()
    val pinState by vm.pin.collectAsState()
    LaunchedEffect(connection.pinnedMessageId, connection.pinnedAt) { vm.syncPin(connection) }
    var confirmDelete by remember { mutableStateOf<List<ChatMessage>?>(null) }
    val shared by vm.shared.collectAsState()
    LaunchedEffect(shared) {
        val content = shared ?: return@LaunchedEffect
        content.text?.let { text -> vm.draft(listOf(draft.trimEnd(), text).filter { it.isNotEmpty() }.joinToString(" ")) }
        if (content.uri != null && content.kind != null) { features.clearError(); kind = content.kind; selected = content.uri }
        vm.consumeShared()
    }
    val featureErrorNow by features.error.collectAsState()
    val pendingMedia by features.pending.collectAsState()
    fun keepUri(uri: Uri, picked: String) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        features.clearError(); kind = picked; selected = uri.toString(); attachmentOpen = false
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) keepUri(uri, "image") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) keepUri(uri, "file") }
    // Camera: a photo into cache/shared (FileProvider "attachments"), then the same preview-and-send as a picked photo.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraUri?.let { uri -> if (taken) { features.clearError(); kind = "image"; selected = uri } }
        cameraUri = null
    }
    fun takePhoto() {
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "camera-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        cameraUri = uri.toString()
        runCatching { camera.launch(uri) }.onFailure { cameraUri = null; features.showError("No camera app is available.") }
    }
    // CAMERA is declared (video calls), so TakePicture needs it granted first.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) takePhoto() else features.showError("Camera access was denied. Allow it in App info → Permissions to take photos.")
    }
    // Your private photo for them (this phone only). Read/written off the main thread.
    var partnerPhoto by remember(connection.id) { mutableStateOf<File?>(null) }
    var photoVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(connection.id, connection.myUserId, photoVersion) {
        partnerPhoto = withContext(Dispatchers.IO) { PartnerPhoto.current(context, connection.myUserId, connection.id) }
    }
    val photoScope = rememberCoroutineScope()
    fun updatePartnerPhoto(change: () -> Unit) = photoScope.launch {
        runCatching { withContext(Dispatchers.IO) { change() } }.onFailure { features.showError(it.message ?: "Couldn't save that photo.") }
        photoVersion++
    }
    val partnerPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) updatePartnerPhoto { PartnerPhoto.save(context, connection.myUserId, connection.id, uri) }
    }
    var reactingTo by remember { mutableStateOf<String?>(null) }
    var composerEmoji by remember { mutableStateOf(false) }
    val stickerMaker by stickers.makerOpen.collectAsState()
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) stickers.pick(uri) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording) { elapsed = 0; while (recording) { delay(1_000); elapsed++ } }
    val sendRecording: () -> Unit = { features.stopAndSend(connection, replyTo) { vm.reply(null) } }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) features.record(sendRecording) else features.showError("Microphone access was denied. Allow it in App info → Permissions to record voice notes.")
    }
    val palette = when (connection.wallpaper) {
        "love" -> BubbleTokens.Love
        "samurai" -> BubbleTokens.Samurai
        else -> if (MaterialTheme.colorScheme.background.luminance() < .5f) BubbleTokens.Dark else BubbleTokens.Light
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val wasNearBottom by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } ?: true } }
    // Pinned = stay on the newest message; a user scroll that ends short of the bottom unpins.
    var pinned by remember(connection.id) { mutableStateOf(true) }
    suspend fun scrollToEnd() {
        list.scrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        // scrollToItem top-aligns, so a tall last bubble needs the rest scrolled in (bounded).
        repeat(10) { if (list.canScrollForward) list.scrollBy(list.layoutInfo.viewportSize.height.toFloat()) }
    }
    LaunchedEffect(list, connection.id) { snapshotFlow { list.isScrollInProgress }.drop(1).collect { if (!it) pinned = !list.canScrollForward } }
    // Content grew or the viewport shrank (cache/server load, keyboard, images): stay on the end while pinned.
    LaunchedEffect(list, connection.id) {
        snapshotFlow { list.canScrollForward }.collect { if (it && pinned && !list.isScrollInProgress && query.isBlank()) scrollToEnd() }
    }
    LaunchedEffect(pendingMedia.size) { if (pendingMedia.isNotEmpty()) { pinned = true; scrollToEnd() } }
    LaunchedEffect(messages.lastOrNull()?.id, messages.lastOrNull()?.tempId) {
        val mine = messages.lastOrNull()?.senderId == connection.myUserId
        if (messages.isNotEmpty() && (mine || (wasNearBottom && query.isBlank()))) { pinned = true; scrollToEnd() }
    }
    // Search (web parity): full log stays, matches highlighted, start on newest, ▲ older / ▼ newer, wrapping.
    val matches = remember(messages, query) { searchMatches(messages, query) }
    var current by remember(matches) { mutableIntStateOf(matches.lastIndex) }
    LaunchedEffect(matches, current) { matches.getOrNull(current)?.let { list.animateScrollToItem(it + 1, -list.layoutInfo.viewportSize.height / 3) } }
    val step = { dir: Int -> if (matches.isNotEmpty()) current = (current + dir + matches.size) % matches.size }
    val closeSearch = { search = false; query = "" }
    BackHandler(search, closeSearch)
    // Live-arrival animation: only messages created after this screen opened, and each key at most once.
    val openedAt = remember { Instant.now() }
    val animated = remember { mutableSetOf<String>() }
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    var alarmNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val liveAlarm = messages.lastOrNull {
        AlarmPolicy.isRaise(it) && it.id != null && it.senderId != connection.myUserId &&
            AlarmPolicy.cardState(it, messages, alarmNow) in setOf(AlarmCardState.Live, AlarmCardState.Failed)
    }
    LaunchedEffect(liveAlarm?.id) {
        val raise = liveAlarm ?: return@LaunchedEffect
        delay((AlarmPolicy.createdAtMs(raise) + AlarmPolicy.WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0) + 500)
        alarmNow = System.currentTimeMillis()
    }
    val acknowledgeAlarm = { liveAlarm?.id?.let { id -> vm.sendCard("alarm", JsonObject(mapOf("ack" to JsonPrimitive(id))), id) }; Unit }
    val alarmGlow = if (liveAlarm != null) rememberInfiniteTransition(label = "alarm-glow").animateFloat(.35f, 1f,
        infiniteRepeatable(tween(900, easing = OneTheme.motion.standard), RepeatMode.Reverse), label = "alarm-glow-alpha").value else 0f
    val danger = OneTheme.colors.danger
    val slash = if (recording) emptyList() else slashMatches(draft)
    // /audio dictates into the draft; it is never a message type.
    val (dictation, beginDictation) = rememberDictation(
        onText = { text -> vm.draft(if (draft.isBlank()) text else draft.trimEnd() + " " + text) }, onError = features::showError)
    val runCommand = { name: String -> features.clearError()
        if (name == "audio") { vm.draft(""); if (!recording) beginDictation() } else if (name == "alarm") alarm = true else command = name }
    Column(Modifier.fillMaxSize().drawWithContent {
        drawContent()
        if (alarmGlow > 0f) {
            val edge = 28.dp.toPx()
            val glow = danger.copy(alpha = .55f * alarmGlow)
            drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), 0f, edge), size = Size(size.width, edge))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), size.height - edge, size.height), Offset(0f, size.height - edge), Size(size.width, edge))
            drawRect(Brush.horizontalGradient(listOf(glow, Color.Transparent), 0f, edge), size = Size(edge, size.height))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, glow), size.width - edge, size.width), Offset(size.width - edge, 0f), Size(edge, size.height))
            drawRect(danger.copy(alpha = alarmGlow), style = Stroke(4.dp.toPx()))
        }
    }) {
        AnimatedContent(selecting, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) }, label = "header") { selectingNow ->
        if (selectingNow) {
            val picked = selectedKeys.mapNotNull { key -> messages.find { messageKey(it) == key } }
            val single = picked.singleOrNull()
            val now = System.currentTimeMillis()
            fun recentMine(m: ChatMessage) = m.id != null && m.senderId == connection.myUserId && m.deletedAt == null &&
                m.type !in setOf("call", "system", "alarm") && now - Instant.parse(m.createdAt).toEpochMilli() <= EditWindowMs
            fun unsentMine(m: ChatMessage) = m.id == null && m.tempId != null && m.senderId == connection.myUserId && m.deliveryState in setOf("failed", "unknown")
            SelectionBar(
                onEdit = single?.takeIf { recentMine(it) && it.type == "text" }?.let { m -> { vm.startEdit(checkNotNull(m.id), m.content); clearSelection()
                    runCatching { composerFocus.requestFocus(); keyboard?.show() } } },
                // Unsent (failed / unknown) bubbles, text or media, can be removed too; they only exist on this phone.
                onDelete = picked.takeIf { it.isNotEmpty() && it.all { m -> recentMine(m) || unsentMine(m) } }?.let { all -> { confirmDelete = all } },
                count = selectedKeys.size,
                onClose = clearSelection,
                onReply = single?.takeIf { it.id != null && it.type != "call" && it.deletedAt == null }?.let { { startReply(it.id); clearSelection() } },
                onCopy = {
                    copyMessages(context, picked.sortedBy { Instant.parse(it.createdAt) })
                    if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(context, if (picked.size > 1) "Messages copied" else "Message copied", android.widget.Toast.LENGTH_SHORT).show()
                    clearSelection()
                },
                onMore = { selectionMenu = true },
                menu = {
                    val save = single?.let { saves[messageKey(it)]?.value }
                    val reportable = single?.takeIf { it.id != null && it.senderId != connection.myUserId }
                    app.web.oneonone.ui.components.OneMenu(selectionMenu, { selectionMenu = false }) {
                        if (single?.id != null && single.deletedAt == null && single.type != "system") {
                            val isPinned = pinState.messageId == single.id
                            app.web.oneonone.ui.components.OneMenuItem(if (isPinned) "Unpin" else "Pin", onClick = {
                                vm.pin(if (isPinned) null else single.id); selectionMenu = false; clearSelection() })
                        }
                        if (save != null) app.web.oneonone.ui.components.OneMenuItem("Save a copy", onClick = { save(); selectionMenu = false; clearSelection() })
                        app.web.oneonone.ui.components.OneMenuItem("Report message", enabled = reportable != null, danger = true,
                            onClick = { features.clearError(); reportMessage = reportable?.id; selectionMenu = false; clearSelection() })
                    }
                },
            )
        } else ChatHeader(
            title = connection.otherNickname ?: "Your One on One",
            presence = rememberChatPresence(state, newerTime(connection.otherLastReadAt, receipts?.lastReadAt), presence)
                .let { if (otherTyping) ChatPresence("typing…", online = true) else it },
            callsEnabled = !recording,
            onVideo = { features.background(); vm.call(CallKind.Video) },
            onCall = { features.background(); vm.call(CallKind.Audio) },
            onMore = { more = true },
            menu = {
                ChatMenu(
                    expanded = more, onDismiss = { more = false },
                    onSearch = { search = !search; more = false },
                    onSettings = { onSettings(); more = false },
                    onNickname = { nickname = true; more = false },
                    onAccount = { onAccount(); more = false },
                    onReport = { features.clearError(); report = true; more = false },
                    onBlock = { features.clearError(); block = true; more = false },
                    onLeave = { leave = true; more = false },
                    onPhoto = { more = false; partnerPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemovePhoto = partnerPhoto?.let { { more = false; updatePartnerPhoto { PartnerPhoto.remove(context, connection.myUserId, connection.id) } } },
                )
            },
            // Your private photo for them wins, then their own photo, then the initial.
            photo = partnerPhoto?.let { it to "${it.path}@${it.lastModified()}" } ?: partnerAvatar,
        )
        }
        Reveal(search) { ChatSearchBar(query, { query = it }, count = if (matches.isEmpty()) "0/0" else "${current + 1}/${matches.size}",
            onOlder = { step(-1) }, onNewer = { step(1) }, onClose = closeSearch) }
        Reveal(connection.myLeaveStep > 0 || connection.otherLeaveStep > 0) {
            LeaveBanner("Leave countdown: you ${connection.myLeaveStep}/5, them ${connection.otherLeaveStep}/5")
        }
        var lastPinId by remember { mutableStateOf(pinState.messageId) }
        if (pinState.messageId != null) lastPinId = pinState.messageId
        Reveal(pinState.messageId != null) { lastPinId?.let { pinnedId ->
            val pinnedMessage = messages.find { it.id == pinnedId }
            PinnedBanner(
                author = if (pinState.pinnedBy == connection.myUserId) "you" else connection.otherNickname ?: "them",
                snippet = pinnedMessage?.let { it.content.ifBlank { messageSummary(it) } } ?: "Pinned message",
                onOpen = {
                    val at = messages.indexOfFirst { it.id == pinnedId }
                    if (at >= 0) scope.launch { list.animateScrollToItem(at + 1, -list.layoutInfo.viewportSize.height / 3) }
                    else android.widget.Toast.makeText(context, "Load older messages to see it.", android.widget.Toast.LENGTH_SHORT).show()
                },
                onUnpin = { vm.pin(null) },
            )
        } }
        Reveal(liveAlarm != null) { AlarmBanner(connection.otherNickname ?: "Your One on One", acknowledgeAlarm) }
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
                            if (hasOlder && messages.size >= 50) LoadOlder(enabled = !busy) { vm.older() }
                            else Text("Start of your One on One", color = OneTheme.colors.muted, style = OneTextStyles.cardHint.copy(fontSize = 12.sp))
                            EncryptionNote()
                        }
                    }
                    itemsIndexed(messages, key = { _, it -> messageKey(it) }) { index, message ->
                        val day = dayLabel(message.createdAt)
                        if (index == 0 || dayLabel(messages[index - 1].createdAt) != day) DateSeparator(day)
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
                        val save = remember(key) { mutableStateOf<(() -> Unit)?>(null) }.also { saves[key] = it }
                        MessageBubble(message, mine, palette, label,
                            groupStart = isGroupStart(messages.getOrNull(index - 1), message),
                            highlight = query.trim(),
                            currentMatch = matches.getOrNull(current) == index,
                            animateIn = animateIn,
                            quote = message.replyTo?.let { BubbleQuote(authorOf(original, connection), original?.let(::messageSummary) ?: "Earlier message") },
                            onReply = { startReply(message.id) },
                            selected = key in selectedKeys,
                            selecting = selecting,
                            onSelect = { selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key },
                            myReaction = message.reactions.firstOrNull { connection.myUserId in it.userIds }?.emoji,
                            saveCopy = save,
                            preview = remember(message.content, message.type, message.deletedAt) {
                                if (message.type != "text" || message.deletedAt != null) null
                                else messageLinks(message.content).firstOrNull { !it.second.startsWith("tel:") }?.second
                            }?.let { url -> { LinkPreviewCard(url, vm::linkPreview) } },
                            onMoreReactions = { reactingTo = message.id },
                            onReact = { emoji -> selectedKeys = emptyList(); vm.react(checkNotNull(message.id), emoji,
                                message.reactions.any { it.emoji == emoji && connection.myUserId in it.userIds }) },
                            onRetry = { if (message.deliveryState == "unknown") duplicate = message.tempId else message.tempId?.let { vm.retry(it) } },
                            onQuote = { message.replyTo?.let { id ->
                                val at = messages.indexOfFirst { it.id == id }
                                if (at >= 0) scope.launch { list.animateScrollToItem(at + 1) }
                                else android.widget.Toast.makeText(context, "Load older messages to see this reply.", android.widget.Toast.LENGTH_SHORT).show()
                            } },
                            card = {
                                when (message.type) {
                                    "alarm" -> AlarmCard(message, mine, vm::sendCard)
                                    "call" -> CallLogCard(message, mine, vm::sendCard)
                                    else -> FeatureCard(message, mine, original, connection, features)
                                }
                            })
                    }
                    items(pendingMedia, key = { "pending-" + it.id }) { PendingMediaBubble(it, palette) }
                    item(key = "typing") { TypingBubble(otherTyping, palette) }
                }
            }
            // Offline: one calm status pill (debounced so brief reconnects don't flash). Otherwise errors show for a few seconds.
            var offline by remember { mutableStateOf(false) }
            LaunchedEffect(state) { if (state == ConnectionState.Connected) offline = false else { delay(2_000); offline = true } }
            val notice = error ?: featureError ?: accountError
            var shown by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(notice) { shown = notice; if (notice != null) { delay(4_000); shown = null } }
            ChatNotice(if (offline) "No connection · Reconnecting…" else null, Modifier.align(Alignment.TopCenter))
            ChatNotice(shown.takeIf { !offline }, Modifier.align(Alignment.BottomCenter), warning = true)
            if (attachmentOpen) Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { attachmentOpen = false })
            AttachSheet(attachmentOpen, Modifier.align(Alignment.BottomCenter).widthIn(max = OneTheme.sizes.chatMax720).padding(bottom = 6.dp),
                onPhoto = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFile = { filePicker.launch(FileMimes.toTypedArray()) })
            var lastSlash by remember { mutableStateOf(slash) }
            if (slash.isNotEmpty()) lastSlash = slash
            androidx.compose.animation.AnimatedVisibility(visible = slash.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(160)) + slideInVertically(tween(200, easing = OneTheme.motion.standard)) { it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 4 }) {
                SlashMenu(lastSlash, maxHeight = minOf(220.dp, screenHeight * .4f), onPick = runCommand,
                    modifier = Modifier.widthIn(max = OneTheme.sizes.chatMax720).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp))
            }
        }
        var lastEdit by remember { mutableStateOf(editing) }
        if (editing != null) lastEdit = editing
        Reveal(editing != null) { lastEdit?.let { id ->
            ReplyBar("Edit message", messages.find { it.id == id }?.content ?: "", onCancel = vm::cancelEdit)
        } }
        val replying = replyTo?.takeIf { editing == null }
        var lastReply by remember { mutableStateOf(replying) }
        if (replying != null) lastReply = replying
        Reveal(replying != null) { lastReply?.let { id ->
            val original = messages.find { it.id == id }
            ReplyBar(authorOf(original, connection).ifEmpty { "Earlier message" }, original?.let(::messageSummary) ?: "earlier message") { vm.reply(null) }
        } }
        if (dictation.listening) DictationBar(dictation.partial, onStop = dictation::stop, onCancel = dictation::cancel)
        else ChatComposer(
            draft = draft, onDraft = vm::draft,
            canSend = !busy, // Text is queued instantly; an uploading voice note or sticker must not block it.
            recording = recording,
            onAttach = { more = false; attachmentOpen = !attachmentOpen },
            onEmoji = { attachmentOpen = false; stickers.clearError(); features.clearError(); composerEmoji = true },
            onCamera = {
                attachmentOpen = false; features.clearError()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onMic = {
                attachmentOpen = false; features.clearError()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) features.record(sendRecording)
                else microphone.launch(Manifest.permission.RECORD_AUDIO)
            },
            elapsedSeconds = elapsed,
            onStop = sendRecording,
            onDiscard = features::cancelRecording,
            onFocus = { pinned = true; scope.launch { scrollToEnd() } },
            focusRequester = composerFocus,
            onSend = {
                val name = draft.trim().removePrefix("/")
                if (draft.trim().startsWith("/") && name in FeatureCommands) runCommand(name) else vm.send()
            },
        )
    }
    selected?.let { source -> UploadContent(kind, source, featureBusy, featureErrorNow,
        send = { features.upload(connection, kind, source.toUri(), replyTo) { selected = null; vm.reply(null) } }, cancel = { selected = null }) }
    command?.let { type ->
        val close = { command = null; vm.draft(""); vm.reply(null) }
        if (type == "location") LocationConfirmation(connection, features, replyTo, close)
        else FeatureComposer(type, connection, features, replyTo, close)
    }
    reactingTo?.let { id ->
        EmojiPickerSheet(onDismiss = { reactingTo = null }, onPick = { emoji ->
            reactingTo = null; selectedKeys = emptyList()
            val message = messages.find { it.id == id }
            vm.react(id, emoji, message?.reactions?.any { it.emoji == emoji && connection.myUserId in it.userIds } == true)
        })
    }
    if (composerEmoji) ComposerStickerSheet(search = features::gifs, sending = featureBusy,
        onGif = { gif -> features.sendGif(connection, gif.id, replyTo) { composerEmoji = false; vm.reply(null) } },
        stickers = {
            StickerGrid(connection.myUserId, stickers, featureBusy,
                onCreate = { stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSend = { sticker -> features.sendSticker(connection, stickers.file(connection.myUserId, sticker.id), replyTo) {
                    stickers.used(sticker.id); composerEmoji = false; vm.reply(null)
                } })
        },
        onDismiss = { composerEmoji = false }, error = featureErrorNow)
    if (stickerMaker) StickerMaker(stickers)
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
    confirmDelete?.let { picked ->
        FeatureModal(onDismissRequest = { confirmDelete = null },
            title = { Text(if (picked.size > 1) "Delete ${picked.size} messages?" else "Delete message?") },
            text = { Text(if (picked.all { it.id == null }) "They were never sent, so only this phone has them." else "They'll be deleted for both of you. This can't be undone.") },
            confirmButton = { DangerButton(if (picked.all { it.id == null }) "Delete" else "Delete for everyone", onClick = { vm.delete(picked.mapNotNull { it.id }, picked.filter { it.id == null }.mapNotNull { it.tempId }); confirmDelete = null; clearSelection() }) },
            dismissButton = { SecondaryButton("Cancel", onClick = { confirmDelete = null }) })
    }
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

/** Indices of [messages] with a word starting with [query] (trimmed, any case) in text or card summary; system rows never match. */
internal fun searchMatches(messages: List<ChatMessage>, query: String): List<Int> {
    if (query.isBlank()) return emptyList()
    return messages.indices.filter { messages[it].type != "system" && messages[it].deletedAt == null && matchRanges(messages[it].content + " " + messageSummary(messages[it]), query).isNotEmpty() }
}

/** Ranges where [query] (trimmed, any case) starts a word in [text], like WhatsApp: "hey" finds "Hey!" and "heyy", not "they". */
internal fun matchRanges(text: String, query: String): List<IntRange> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(q), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList()
}

internal fun dayLabel(value: String, today: java.time.LocalDate = java.time.LocalDate.now(),
                      zone: ZoneId = ZoneId.systemDefault(), locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val date = Instant.parse(value).atZone(zone).toLocalDate()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.isBefore(today) && !date.isBefore(today.minusDays(6)) -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", locale))
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}

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
        dismissButton = { SecondaryButton(if (connection.otherNickname == null) "Not now" else "Cancel", onClick = onDismiss) })
}

/** Red strip under the header while their alarm is live: the one tap that acknowledges it and stops the ring. */
@Composable
private fun AlarmBanner(from: String, onAcknowledge: () -> Unit) {
    val c = OneTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.danger).clickable(role = Role.Button, onClickLabel = "Acknowledge alarm", onClick = onAcknowledge)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_siren), null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text("Emergency alarm from $from", color = Color.White, style = OneTextStyles.cardHint.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text("Tap to acknowledge and stop the alarm", color = Color.White.copy(alpha = .9f), style = OneTextStyles.cardHint.copy(fontSize = 12.sp))
        }
        Text("Acknowledge", Modifier.clip(RoundedCornerShape(OneTheme.radii.full)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp),
            color = c.danger, style = OneTextStyles.cardHint.copy(fontWeight = FontWeight.Bold))
    }
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
