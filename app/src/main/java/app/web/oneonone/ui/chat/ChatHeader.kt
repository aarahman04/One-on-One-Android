package app.web.oneonone.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.web.oneonone.R
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import java.io.File
import app.web.oneonone.ui.components.OneIconButton
import app.web.oneonone.ui.components.OneMenu
import app.web.oneonone.ui.components.OneMenuItem
import app.web.oneonone.ui.components.OneTextField
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/** .chat__nav: min 56dp (+ status bar inset), bg-raised, avatar + title/status on the left, video / phone / more on the right. */
@Composable
internal fun ChatHeader(
    title: String,
    presence: ChatPresence,
    callsEnabled: Boolean,
    onVideo: () -> Unit,
    onCall: () -> Unit,
    onMore: () -> Unit,
    menu: @Composable () -> Unit,
    /** Image model (File or URL) to its memory-cache key; null shows the initial. */
    photo: Pair<Any, String>? = null,
) {
    val c = OneTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bgRaised)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = OneTheme.sizes.header56)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(OneTheme.sizes.touch40).clip(CircleShape).background(c.accentYou.copy(alpha = .18f).compositeOver(c.bg)),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.trim().firstOrNull()?.uppercase() ?: "?", color = c.accentYou, fontSize = 20.sp)
                // Your private photo for them over the initial (shown only on this phone).
                if (photo != null) {
                    val context = LocalContext.current
                    // Keyed by content (file time / avatar id), not by path or the hourly-changing signed URL.
                    val request = remember(photo.second) {
                        ImageRequest.Builder(context).data(photo.first).memoryCacheKey(photo.second).diskCacheKey(photo.second)
                            .diskCachePolicy(if (photo.first is File) CachePolicy.DISABLED else CachePolicy.ENABLED).build()
                    }
                    AsyncImage(request, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Crossfade(presence, animationSpec = tween(200), label = "Chat presence") { status ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = if (status.online) c.accentYou else c.muted)) { append("● ") }
                            append(status.label)
                        },
                        color = c.textDim, style = OneTextStyles.cardHint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            OneIconButton(R.drawable.ic_video, "Start video call", onVideo, enabled = callsEnabled)
            OneIconButton(R.drawable.ic_phone, "Start audio call", onCall, enabled = callsEnabled)
            Box {
                OneIconButton(R.drawable.ic_more_vertical, "More options", onMore)
                menu()
            }
        }
    }
    HorizontalDivider(color = c.border)
}

/**
 * Replaces the header while messages are selected (long press): close, count, then reply (one message),
 * copy and a More menu. Same height and colours as [ChatHeader] so the swap doesn't shift the list.
 */
@Composable
internal fun SelectionBar(count: Int, onClose: () -> Unit, onReply: (() -> Unit)?, onCopy: () -> Unit, onMore: () -> Unit, menu: @Composable () -> Unit,
                          onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    val c = OneTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bgRaised)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = OneTheme.sizes.header56)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        OneIconButton(R.drawable.ic_x, "Clear selection", onClose)
        AnimatedContent(count, transitionSpec = { (fadeIn(tween(160)) + slideInVertically { -it / 2 }) togetherWith fadeOut(tween(120)) }, label = "Selected count",
            modifier = Modifier.weight(1f).padding(start = 8.dp)) { n ->
            Text("$n", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = if (n == 1) "1 message selected" else "$n messages selected" })
        }
        if (onReply != null) OneIconButton(R.drawable.ic_reply, "Reply", onReply)
        if (onEdit != null) OneIconButton(R.drawable.ic_pencil, "Edit", onEdit)
        OneIconButton(R.drawable.ic_copy, "Copy", onCopy)
        if (onDelete != null) OneIconButton(R.drawable.ic_trash, "Delete", onDelete)
        Box {
            OneIconButton(R.drawable.ic_more_vertical, "More options", onMore)
            menu()
        }
    }
    HorizontalDivider(color = c.border)
}

/**
 * The More menu: Search / Nickname / Settings / Account, a divider, then a
 * collapsible "Connection" group with the danger actions. Every entry calls the same handler as before.
 */
@Composable
internal fun ChatMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onNickname: () -> Unit,
    onAccount: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onLeave: () -> Unit,
    onPhoto: () -> Unit = {},
    onRemovePhoto: (() -> Unit)? = null,
) {
    var danger by remember { mutableStateOf(false) }
    OneMenu(expanded, onDismiss) {
        OneMenuItem("Search", onClick = onSearch)
        OneMenuItem("Nickname", onClick = onNickname)
        OneMenuItem(if (onRemovePhoto == null) "Set their photo" else "Change their photo", onClick = onPhoto)
        onRemovePhoto?.let { OneMenuItem("Remove their photo", onClick = it) }
        OneMenuItem("Settings", onClick = onSettings)
        OneMenuItem("Account", onClick = onAccount)
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = OneTheme.colors.border)
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { danger = !danger }.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Connection", style = OneTextStyles.menuItem, color = OneTheme.colors.text)
            val turn by animateFloatAsState(if (danger) 180f else 0f, tween(240, easing = OneTheme.motion.standard), label = "chevron")
            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = OneTheme.colors.muted, modifier = Modifier.size(18.dp).rotate(turn))
        }
        Reveal(danger) {
            Column {
                OneMenuItem("Report person", onClick = onReport, danger = true)
                OneMenuItem("Block person", onClick = onBlock, danger = true)
                OneMenuItem("Leave connection", onClick = onLeave, danger = true)
            }
        }
    }
}

/** .chat__search: input, N/M count, ▲ older / ▼ newer, close. Enter steps to the older match (web). */
@Composable
internal fun ChatSearchBar(query: String, onQuery: (String) -> Unit, count: String, onOlder: () -> Unit, onNewer: () -> Unit, onClose: () -> Unit) {
    val c = OneTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OneTextField(query, onQuery, Modifier.weight(1f), placeholder = "Search messages…",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onOlder() }))
        Text(count, color = c.muted, style = OneTextStyles.cardHint)
        OneIconButton(R.drawable.ic_chevron_down, "Previous match", onOlder, Modifier.rotate(180f))
        OneIconButton(R.drawable.ic_chevron_down, "Next match", onNewer)
        OneIconButton(R.drawable.ic_x, "Close search", onClose)
    }
    HorizontalDivider(color = c.border)
}

/** Floating pill over the message list: connection status or a short-lived notice. */
@Composable
internal fun ChatNotice(text: String?, modifier: Modifier = Modifier, warning: Boolean = false) {
    val c = OneTheme.colors
    var last by remember { mutableStateOf(text) }
    if (text != null) last = text
    AnimatedVisibility(text != null, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
        Text(last.orEmpty(),
            Modifier.widthIn(max = 340.dp).padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(c.bgRaised).border(1.dp, if (warning) c.danger.copy(alpha = .5f) else c.border, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = if (warning) c.danger else c.text, style = OneTextStyles.cardHint, textAlign = TextAlign.Center)
    }
}
