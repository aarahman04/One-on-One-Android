package app.web.oneonone.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.MutableState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.R
import app.web.oneonone.data.model.AllowedReactions
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.ui.theme.BubbleColors
import app.web.oneonone.ui.theme.BubblePalette
import app.web.oneonone.ui.theme.BubbleTokens
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The colours of the bubble currently being composed (text, meta, tail, edge). MessageBubble provides it
 * around the `card` slot, together with LocalContentColor = text colour, so keepsake/alarm/call-log cards
 * can read `LocalBubbleColors.current.meta` etc. instead of hard-coding colours.
 */
val LocalBubbleColors = staticCompositionLocalOf { BubbleTokens.Dark.mine }
internal val LocalMessageSaveAction = staticCompositionLocalOf<androidx.compose.runtime.MutableState<(() -> Unit)?>?> { null }

/** A reply quote shown at the top of a bubble. */
internal data class BubbleQuote(val author: String, val snippet: String)

private val TailSize = 6.dp
private const val SwipeTriggerDp = 60
private const val SwipeMaxDp = 80

/** Cap a child's width to a fraction of the space it is offered (web: .chat__message-body max-width 80%). */
private fun Modifier.maxWidthFraction(fraction: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = (constraints.maxWidth * fraction).roundToInt()))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** A system line ("You changed X to Y"): centred pill like `.chat__system-line`. */
@Composable
internal fun SystemLine(text: String, modifier: Modifier = Modifier) {
    val c = OneTheme.colors
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier
                .clip(RoundedCornerShape(OneTheme.radii.bubble8))
                .background(c.bgRaised)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = c.textDim,
            style = OneTextStyles.cardHint,
        )
    }
}

private enum class Receipt { None, Pending, Sent, Delivered, Read }

private fun receiptOf(label: String): Receipt = when (label) {
    "Read" -> Receipt.Read
    "Delivered" -> Receipt.Delivered
    "Sent" -> Receipt.Sent
    else -> Receipt.Pending
}

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    palette: BubblePalette,
    receipt: String,
    groupStart: Boolean,
    animateIn: Boolean,
    quote: BubbleQuote?,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onRetry: () -> Unit,
    onQuote: () -> Unit,
    card: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String = "",
    currentMatch: Boolean = false,
    selected: Boolean = false,
    selecting: Boolean = false,
    onSelect: () -> Unit = {},
    myReaction: String? = null,
    saveCopy: MutableState<(() -> Unit)?> = remember { mutableStateOf(null) },
) {
    val c = OneTheme.colors
    val motion = OneTheme.motion
    val colors = if (mine) palette.mine else palette.other
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val time = remember(message.createdAt) { TimeFormat.withZone(ZoneId.systemDefault()).format(Instant.parse(message.createdAt)) }
    val failed = message.id == null && message.deliveryState == "failed"
    val status = if (mine && message.type != "call" && !failed) receiptOf(receipt) else Receipt.None
    var reacting by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canReply = message.id != null && message.type != "call"
    val scope = rememberCoroutineScope()
    val currentOnReply by rememberUpdatedState(onReply)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnQuote by rememberUpdatedState(onQuote)
    val currentSelecting by rememberUpdatedState(selecting)
    var dragPx by remember { mutableFloatStateOf(0f) }
    val triggerPx = with(LocalDensity.current) { SwipeTriggerDp.dp.toPx() }
    val shape = RoundedCornerShape(OneTheme.radii.bubble8)
    val alarm = message.type == "alarm"
    BackHandler(reacting) { reacting = false }
    LaunchedEffect(selecting) { if (selecting) reacting = false }

    // One gesture detector per bubble; taps are routed to a link or the quote by where they land.
    var bubbleBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var quoteBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val textLayout = remember { TextLayoutHolder() }
    val links = remember(message.content, message.type) { if (message.type == "text") messageLinks(message.content) else emptyList() }
    fun local(target: LayoutCoordinates?, position: Offset): Offset? {
        val bubble = bubbleBox ?: return null
        if (target == null || !target.isAttached || !bubble.isAttached) return null
        val point = target.localPositionOf(bubble, position)
        return point.takeIf { it.x in 0f..target.size.width.toFloat() && it.y in 0f..target.size.height.toFloat() }
    }
    fun linkAt(position: Offset): Pair<IntRange, String>? {
        val point = local(textBox, position) ?: return null
        val layout = textLayout.result ?: return null
        val line = layout.getLineForVerticalPosition(point.y)
        if (point.x < layout.getLineLeft(line) || point.x > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForPosition(point)
        return links.firstOrNull { offset in it.first }
    }
    fun openLink(target: String) { runCatching { uriHandler.openUri(target) } }
    fun copyLink(link: Pair<IntRange, String>) {
        val phone = link.second.startsWith("tel:")
        val value = if (phone) message.content.substring(link.first) else link.second
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(if (phone) "Phone number" else "Link", value))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, if (phone) "Number copied" else "Link copied", Toast.LENGTH_SHORT).show()
    }

    // message-enter: live rows rise, fade and settle from their own side over 320ms (history never animates).
    val enter = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (enter.value < 1f) enter.animateTo(1f, tween(motion.slow320, easing = motion.standard)) }
    val selection by animateColorAsState(if (selected) c.accentOther.copy(alpha = .16f) else Color.Transparent, tween(motion.base200), label = "selection")

    Box(
        modifier
            .fillMaxWidth()
            .background(selection)
            .padding(top = if (groupStart) 8.dp else 2.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 14.dp.toPx()
                scaleX = .94f + .06f * enter.value
                scaleY = scaleX
                transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
            }
            .pointerInput(canReply, selecting) {
                if (!canReply || selecting) return@pointerInput
                val trigger = SwipeTriggerDp.dp.toPx()
                val limit = SwipeMaxDp.dp.toPx()
                // Right-swipe = reply (web: 60 trigger / 80 max). Settles back whichever way it ends.
                fun settle() { scope.launch { animate(dragPx, 0f, animationSpec = tween(motion.base200, easing = motion.standard)) { v, _ -> dragPx = v } } }
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragPx >= trigger) currentOnReply(); settle() },
                    onDragCancel = { settle() },
                ) { change, dx ->
                    change.consume()
                    val before = dragPx
                    dragPx = (dragPx + dx).coerceIn(0f, limit)
                    if (before < trigger && dragPx >= trigger) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
    ) {
        if (dragPx > 0f) {
            val progress = (dragPx / triggerPx).coerceIn(0f, 1f)
            val ready = dragPx >= triggerPx
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(30.dp)
                    .graphicsLayer { alpha = progress; scaleX = .6f + .4f * progress; scaleY = scaleX }
                    .clip(CircleShape).background(if (ready) c.accentOther else c.bgRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_reply), null, tint = if (ready) c.bg else c.textDim, modifier = Modifier.size(16.dp))
            }
        }
        Column(
            Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                // Web swipes every row to the right. A right-aligned (mine) bubble has no room, so it only nudges into the 6dp row padding;
                // the reply icon and the 60dp trigger still give the feedback.
                .graphicsLayer { translationX = if (mine) minOf(dragPx, 6.dp.toPx()) else dragPx }
                .maxWidthFraction(.8f),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            Box {
                Column(
                    Modifier
                        // Tail first so it is not clipped by the bubble shape: a 6dp right triangle at the top outer
                        // corner on the first bubble of a group (global.css L1489-1505).
                        .drawBehind {
                            if (!groupStart) return@drawBehind
                            val t = TailSize.toPx()
                            val tail = Path().apply {
                                if (mine) { moveTo(size.width, 0f); lineTo(size.width + t, 0f); lineTo(size.width, t) }
                                else { moveTo(0f, 0f); lineTo(-t, 0f); lineTo(0f, t) }
                                close()
                            }
                            drawPath(tail, colors.tail)
                        }
                        .clip(shape)
                        .drawWithCache {
                            // CSS 135deg: a fixed diagonal, independent of bubble aspect ratio.
                            val half = (size.width + size.height) / 4f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val diagonal = Offset(half, half)
                            val brush = Brush.linearGradient(listOf(colors.backgroundStart, colors.backgroundEnd), center - diagonal, center + diagonal)
                            onDrawBehind { drawRect(brush) }
                        }
                        .then(if (currentMatch) Modifier.border(2.dp, c.accentOther, shape) else Modifier.border(1.dp, colors.edge, shape))
                        // .chat__message[data-type='alarm'] .chat__message-body { border-left: 4px solid var(--danger) }
                        .then(if (alarm) Modifier.drawBehind { drawRect(c.danger, size = Size(4.dp.toPx(), size.height)) } else Modifier)
                        .onGloballyPositioned { bubbleBox = it }
                        .pointerInput(canReply, links) {
                            detectTapGestures(
                                onTap = { position ->
                                    val link = linkAt(position)
                                    when {
                                        currentSelecting -> currentOnSelect()
                                        link != null -> openLink(link.second)
                                        local(quoteBox, position) != null -> currentOnQuote()
                                        else -> expanded = !expanded
                                    }
                                },
                                // WhatsApp: double tap = quick reaction bar, long press = select.
                                onDoubleTap = {
                                    if (canReply && !currentSelecting) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); reacting = true }
                                    else if (currentSelecting) currentOnSelect()
                                },
                                onLongPress = { position ->
                                    val link = linkAt(position).takeIf { !currentSelecting }
                                    if (link != null) copyLink(link)
                                    else { haptics.performHapticFeedback(HapticFeedbackType.LongPress); currentOnSelect() }
                                },
                            )
                        }
                        .semantics {
                            onLongClick("Select message") { currentOnSelect(); true }
                            customActions = buildList {
                                if (canReply) {
                                    add(CustomAccessibilityAction("React") { reacting = true; true })
                                    add(CustomAccessibilityAction("Reply") { currentOnReply(); true })
                                }
                                if (quote != null) add(CustomAccessibilityAction("Go to original message") { currentOnQuote(); true })
                                links.firstOrNull()?.let { add(CustomAccessibilityAction("Open link") { openLink(it.second); true }) }
                            }
                        }
                        // Text bubbles shrink-wrap to their widest child so the quote can stretch to the same width.
                        // Cards (S4) keep their own sizing: they may contain subcomposition that has no intrinsics.
                        .then(if (message.type == "text") Modifier.width(IntrinsicSize.Max) else Modifier)
                        .padding(start = if (alarm) 12.5.dp else 8.5.dp, end = 8.5.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides colors.text, LocalBubbleColors provides colors,
                        LocalMessageSaveAction provides saveCopy) {
                        if (quote != null) QuoteBlock(quote, colors, Modifier.onGloballyPositioned { quoteBox = it })
                        when (message.type) {
                            "text" -> {
                                // web: mark = accent-you, current row's marks = accent-other, dark text
                                val mark = if (currentMatch) c.accentOther else c.accentYou
                                val text = remember(message.content, failed, colors, c.danger, highlight, mark) { bubbleText(message.content, failed, c.danger, highlight, mark) }
                                TextWithMeta(
                                    text = text,
                                    textColor = colors.text,
                                    description = "${message.content}, $time${if (mine) ", $receipt" else ""}",
                                    holder = textLayout,
                                    onTextPositioned = { textBox = it },
                                ) { MetaRow(time, status, colors, palette, receipt = null) }
                            }
                            // Voice notes keep the time beside the duration line instead of on a row of its own.
                            "voice" -> Box {
                                card()
                                Box(Modifier.align(Alignment.BottomEnd)) { MetaRow(time, status, colors, palette, receipt = receipt) }
                            }
                            else -> {
                                card()
                                Box(Modifier.align(Alignment.End).padding(top = 2.dp)) { MetaRow(time, status, colors, palette, receipt = receipt) }
                            }
                        }
                        if (expanded) Text(message.createdAt, color = colors.meta, style = OneTextStyles.bubbleMeta)
                    }
                }
                if (reacting) ReactionBar(colors, mine, myReaction, onPick = { onReact(it); reacting = false }, onDismiss = { reacting = false })
            }
            if (message.reactions.isNotEmpty()) {
                // Plain emoji tucked 3dp under the bubble, inset 10dp from its outer edge (L955-985).
                Row(Modifier.offset(y = (-3).dp).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.reactions.forEach { reaction ->
                        Text(
                            if (reaction.userIds.size > 1) "${reaction.emoji} ${reaction.userIds.size}" else reaction.emoji,
                            modifier = Modifier.clickable(role = Role.Button) { onReact(reaction.emoji) }.padding(vertical = 2.dp),
                            color = c.text,
                            style = OneTextStyles.bubbleText,
                        )
                    }
                }
            }
            if (message.id == null && message.deliveryState in setOf("failed", "unknown", "queued")) {
                Text(message.error ?: "Queued — sends when connected", color = c.textDim, style = OneTextStyles.bubbleMeta, modifier = Modifier.padding(top = 2.dp))
                if (message.deliveryState in setOf("failed", "unknown")) {
                    Text(
                        if (message.deliveryState == "unknown") "Review resend" else "Retry",
                        modifier = Modifier.clickable(role = Role.Button, onClick = onRetry).padding(vertical = 6.dp),
                        color = c.accentOther,
                        style = OneTextStyles.cardHint.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/** Opens above the bubble (below when there is no room), on the bubble's own side, so it reads as attached to it. */
private class AboveAnchor(private val alignEnd: Boolean, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gap
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom + gap)
    }
}

/** Quick reactions in the bubble's own colours. Not focusable, so an open keyboard and the draft stay as they are. */
@Composable
private fun ReactionBar(colors: BubbleColors, mine: Boolean, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    Popup(popupPositionProvider = remember(mine, gap) { AboveAnchor(mine, gap) }, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)) {
        AnimatedVisibility(shown,
            enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow), initialScale = .7f,
                transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f))) {
            Row(
                Modifier.padding(6.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.backgroundStart, colors.backgroundEnd)))
                    .border(1.dp, colors.edge, CircleShape).padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AllowedReactions.forEachIndexed { index, emoji ->
                    val pop = remember { Animatable(.4f) }
                    LaunchedEffect(Unit) { delay(index * 28L); pop.animateTo(1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessMedium)) }
                    Box(
                        Modifier.size(42.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape)
                            .background(if (emoji == current) colors.text.copy(alpha = .18f) else Color.Transparent)
                            .clickable(role = Role.Button) { onPick(emoji) }
                            .semantics { contentDescription = if (emoji == current) "Remove reaction $emoji" else "React $emoji" },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 22.sp) }
                }
            }
        }
    }
}

private fun bubbleText(content: String, failed: Boolean, danger: Color, highlight: String, mark: Color): AnnotatedString = buildAnnotatedString {
    append(content)
    matchRanges(content, highlight).forEach { addStyle(SpanStyle(background = mark, color = Color(0xFF04170A)), it.first, it.last + 1) }
    messageLinks(content).forEach { (range, _) ->
        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.first, range.last + 1)
    }
    // .chat__message--failed .chat__message-text::after
    if (failed) withStyle(SpanStyle(color = danger, fontSize = 11.sp)) { append(" · not sent") }
}

private val UrlPattern = Regex("""(?i)\b(?:https?://|www\.)[^\s<>]+|(?<![@\w.-])(?:[a-z0-9-]+\.)+(?:online|site|store|shop|website|tech|space|xyz|live|link|page|blog|club|cloud|digital|world|news|life|fun|art|design|studio|media|today|email|network|agency|company|global|solutions|team|top|pro|biz|com|net|org|io|app|dev|co|in|me|info|edu|gov|ai|uk|us|gg|tv|ly|fm|to|so|sh|am|de|fr|es|it|nl|eu|ca|au|nz|jp|kr|cn|ru|br|mx|pk|bd|lk|np|ae|sa|qa|sg|my|id|ph|za|ng|ke)\b(?:/[^\s<>]*)?""")
private val PhonePattern = Regex("""(?<![\w+(])[+(]?\d[\d ()-]{5,}\d(?!\w)""")
private val DatePattern = Regex("""\d{1,4}[-/]\d{1,2}[-/]\d{1,4}""")

/** Tappable spans in a text message: web links (https:// added when missing) and phone numbers (7–15 digits) as tel: links. */
internal fun messageLinks(text: String): List<Pair<IntRange, String>> {
    val links = UrlPattern.findAll(text).map { match ->
        val url = match.value.trimEnd('.', ',', '!', '?', ':', ';', ')', '\'', '"')
        val target = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else "https://$url"
        (match.range.first until match.range.first + url.length) to target
    }.toList()
    val phones = PhonePattern.findAll(text)
        .filter { match -> match.value.count(Char::isDigit) in 7..15 && !DatePattern.matches(match.value) &&
            links.none { (range, _) -> match.range.first <= range.last && range.first <= match.range.last } }
        .map { match -> match.range to "tel:" + match.value.filter { it.isDigit() || it == '+' } }
    return (links + phones).sortedBy { it.first.first }
}

@Composable
private fun QuoteBlock(quote: BubbleQuote, colors: BubbleColors, modifier: Modifier) {
    val c = OneTheme.colors
    val shape = RoundedCornerShape(OneTheme.radii.xs4)
    // Taps and long presses are handled by the bubble, so a reply can be selected and reacted to like any message.
    Row(
        modifier
            .padding(bottom = 3.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.quoteBg)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.text))
        Column(Modifier.alpha(.85f).padding(horizontal = 8.dp, vertical = 3.dp)) {
            if (quote.author.isNotEmpty()) Text(quote.author, color = colors.text, style = OneTextStyles.bubbleMeta.copy(fontWeight = FontWeight.Bold, lineHeight = 14.sp))
            Text(quote.snippet, color = colors.text, style = OneTextStyles.cardHint.copy(fontSize = 12.sp, lineHeight = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Time + receipt tick. `receipt` (a label) is only used as the tick's accessibility text for non-text bubbles. */
@Composable
private fun MetaRow(time: String, status: Receipt, colors: BubbleColors, palette: BubblePalette, receipt: String?) {
    Row(Modifier.clearAndSetSemantics { if (receipt != null) contentDescription = "$time, $receipt" }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = colors.meta, style = OneTextStyles.bubbleMeta)
        if (status != Receipt.None) {
            Icon(
                painterResource(if (status == Receipt.Delivered || status == Receipt.Read) R.drawable.ic_tick_double else R.drawable.ic_tick_single),
                contentDescription = null,
                tint = if (status == Receipt.Read) palette.read else palette.ticks,
                modifier = Modifier.size(width = 14.dp, height = 13.dp).alpha(if (status == Receipt.Pending) .7f else 1f),
            )
        }
    }
}

/** Last measured text layout, written by Text's onTextLayout during its measure pass and read by the policy right after. */
private class TextLayoutHolder { var result: TextLayoutResult? = null }

/**
 * Message text with the time/ticks "floating" at the end of the last line, like the web's `float: right` meta.
 * If the last line's width + 8dp gap + meta width fits in the available width, the meta shares that line;
 * otherwise it gets its own row under the text. Children: [0] the Text, [1] the meta.
 */
@Composable
private fun TextWithMeta(text: AnnotatedString, textColor: Color, description: String, holder: TextLayoutHolder,
                         onTextPositioned: (LayoutCoordinates) -> Unit = {}, meta: @Composable () -> Unit) {
    val policy = remember(holder) { TextWithMetaPolicy(holder) }
    Layout(
        content = {
            Text(text, color = textColor, style = OneTextStyles.bubbleText, onTextLayout = { holder.result = it },
                modifier = Modifier.onGloballyPositioned(onTextPositioned).semantics { contentDescription = description })
            meta()
        },
        measurePolicy = policy,
    )
}

private class TextWithMetaPolicy(private val holder: TextLayoutHolder) : MeasurePolicy {
    private val gapDp = 8.dp

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val gap = gapDp.roundToPx()
        val meta = measurables[1].measure(Constraints())
        val text = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val layout = holder.result
        val lastLineRight = if (layout != null && layout.lineCount > 0) ceil(layout.getLineRight(layout.lineCount - 1)).toInt() else Int.MAX_VALUE
        val inline = lastLineRight != Int.MAX_VALUE && lastLineRight + gap + meta.width <= constraints.maxWidth
        val width = (if (inline) max(text.width, lastLineRight + gap + meta.width) else max(text.width, meta.width))
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = if (inline) max(text.height, meta.height) else text.height + meta.height
        return layout(width, height) {
            text.place(0, 0)
            meta.place(width - meta.width, height - meta.height)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        max(measurables[0].minIntrinsicWidth(height), measurables[1].minIntrinsicWidth(height))

    // Everything on one line; the bubble is then capped by the 80% limit and the text wraps inside it.
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables[0].maxIntrinsicWidth(height) + gapDp.roundToPx() + measurables[1].maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        measurables[0].minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        measurables[0].maxIntrinsicHeight(width)
}
