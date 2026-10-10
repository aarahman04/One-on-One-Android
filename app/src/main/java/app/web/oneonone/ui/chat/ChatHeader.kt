package app.web.oneonone.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.R
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
) {
    var danger by remember { mutableStateOf(false) }
    OneMenu(expanded, onDismiss) {
        OneMenuItem("Search", onClick = onSearch)
        OneMenuItem("Nickname", onClick = onNickname)
        OneMenuItem("Settings", onClick = onSettings)
        OneMenuItem("Account", onClick = onAccount)
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = OneTheme.colors.border)
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { danger = !danger }.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Connection", style = OneTextStyles.menuItem, color = OneTheme.colors.text)
            Text(if (danger) "▴" else "▾", style = OneTextStyles.menuItem, color = OneTheme.colors.muted)
        }
        if (danger) {
            OneMenuItem("Report person", onClick = onReport, danger = true)
            OneMenuItem("Block person", onClick = onBlock, danger = true)
            OneMenuItem("Leave connection", onClick = onLeave, danger = true)
        }
    }
}

/** .chat__search: input + close, bottom border. Closing clears the filter (web search-close). */
@Composable
internal fun ChatSearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val c = OneTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OneTextField(query, onQuery, Modifier.weight(1f), placeholder = "Search messages…")
        OneIconButton(R.drawable.ic_x, "Close search", onClose)
    }
    HorizontalDivider(color = c.border)
}

/** .chat__leave-banner: danger text on danger@8%, bottom border. */
@Composable
internal fun LeaveBanner(text: String) {
    val c = OneTheme.colors
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(c.danger.copy(alpha = .08f)).padding(horizontal = 20.dp, vertical = 10.dp),
        color = c.danger, style = OneTextStyles.cardHint, textAlign = TextAlign.Center,
    )
    HorizontalDivider(color = c.border)
}

/** Inline error line (chat / account / feature errors). */
@Composable
internal fun ErrorLine(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), color = OneTheme.colors.danger, style = OneTextStyles.cardHint)
}
