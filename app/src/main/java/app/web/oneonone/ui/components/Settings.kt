package app.web.oneonone.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.R
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/** What sits at the end of a [SettingsRow]. */
sealed interface SettingsTrailing {
    data object None : SettingsTrailing
    data object Chevron : SettingsTrailing
    data object External : SettingsTrailing
    data class Value(val text: String) : SettingsTrailing
    /** Small status pill; [positive] tints it accent, otherwise muted. */
    data class Chip(val text: String, val positive: Boolean) : SettingsTrailing
    /** Inline text button (e.g. Unblock); the row itself is not clickable. */
    data class Action(val text: String, val enabled: Boolean = true, val onClick: () -> Unit) : SettingsTrailing
}

/**
 * Shared frame for Settings / Account / Blocked accounts / Notifications: 56dp top bar (status-bar inset) with a back arrow and a
 * left-aligned title, then scrolling content on a 16dp gutter. [bottomBar] stays pinned under the scroll area.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: String? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OneTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().background(c.bgRaised).windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = OneTheme.sizes.header56).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OneIconButton(R.drawable.ic_arrow_left, "Back", onBack)
            Text(title, Modifier.weight(1f).semantics { heading() }, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = OneTextStyles.screenTitle.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp), color = c.accentYou, trackColor = c.border)
        else HorizontalDivider(color = c.border)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = OneTheme.spacing.lg16, vertical = OneTheme.spacing.lg16)
                    .then(if (bottomBar == null) Modifier.navigationBarsPadding() else Modifier),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                error?.let { Text(it, color = c.danger, style = OneTextStyles.cardHint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                content()
            }
        }
        bottomBar?.let {
            Box(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(OneTheme.spacing.lg16)) { it() }
            }
        }
    }
}

/** Uppercase caption, then a bg-raised card (1dp border, radius 10) of rows separated by hairlines inset past the icon. */
@Composable
fun SettingsGroup(label: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = OneTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
        if (label != null) Text(label.uppercase(), Modifier.padding(start = OneTheme.spacing.xs4).semantics { heading() },
            style = OneTextStyles.groupLabel, color = c.muted)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(OneTheme.radii.md10), color = c.bgRaised, border = BorderStroke(1.dp, c.border)) {
            // Every row draws a hairline on top; shift up 1dp (and trim the height) so the first one is clipped away.
            Column(Modifier.clip(RoundedCornerShape(OneTheme.radii.md10)).layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                val h = (p.height - 1.dp.roundToPx()).coerceAtLeast(0)
                layout(p.width, h) { p.place(0, -1.dp.roundToPx()) }
            }, content = content)
        }
    }
}

/** One tappable row: 20dp icon, title (+ subtitle), trailing content. Min 56dp. Pass `onClick = null` for a static row. */
@Composable
fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: SettingsTrailing = SettingsTrailing.None,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = OneTheme.colors
    val tint = if (danger) c.danger else c.textDim
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(start = 52.dp), color = c.border)
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = OneTheme.spacing.lg16, vertical = OneTheme.spacing.md12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (enabled) tint else c.muted)
            Spacer(Modifier.width(OneTheme.spacing.md12 + 4.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = OneTextStyles.subtitle, color = if (!enabled) c.muted else if (danger) c.danger else c.text)
                subtitle?.let { Text(it, style = OneTextStyles.cardHint, color = c.textDim) }
            }
            when (trailing) {
                SettingsTrailing.None -> Unit
                SettingsTrailing.Chevron -> TrailingIcon(R.drawable.ic_chevron_right)
                SettingsTrailing.External -> TrailingIcon(R.drawable.ic_external_link)
                is SettingsTrailing.Value -> {
                    Text(trailing.text, Modifier.padding(start = OneTheme.spacing.sm8), style = OneTextStyles.cardHint, color = c.textDim)
                    // A tappable row still signals that it leads somewhere.
                    if (onClick != null) TrailingIcon(R.drawable.ic_chevron_right)
                }
                is SettingsTrailing.Chip -> StatusChip(trailing.text, trailing.positive)
                is SettingsTrailing.Action -> Text(trailing.text,
                    Modifier.padding(start = OneTheme.spacing.sm8)
                        .clickable(enabled = trailing.enabled, role = Role.Button, onClick = trailing.onClick)
                        .defaultMinSize(minHeight = OneTheme.sizes.touch40).wrapContentHeight(Alignment.CenterVertically),
                    style = OneTextStyles.cardHeading, color = if (trailing.enabled) c.accentOther else c.muted)
            }
        }
    }
}

@Composable
private fun TrailingIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), null, Modifier.padding(start = OneTheme.spacing.sm8).size(18.dp), tint = OneTheme.colors.muted)
}

@Composable
private fun StatusChip(text: String, positive: Boolean) {
    val c = OneTheme.colors
    val fg = if (positive) c.accentYou else c.muted
    Text(text, Modifier.padding(start = OneTheme.spacing.sm8).clip(RoundedCornerShape(OneTheme.radii.full))
        .background(fg.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 3.dp),
        style = OneTextStyles.cardHint.copy(fontSize = 12.sp), color = fg)
}

/** Muted explanatory text under a group. */
@Composable
fun SettingsCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = OneTheme.spacing.xs4), style = OneTextStyles.cardHint, color = OneTheme.colors.textDim)
}

/** Collapsed-by-default caption: a "title ▾" toggle that reveals [text]. */
@Composable
fun SettingsExpandable(title: String, text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
        Row(Modifier.clickable(role = Role.Button) { open = !open }.defaultMinSize(minHeight = OneTheme.sizes.touch40)
            .padding(horizontal = OneTheme.spacing.xs4), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.xs4)) {
            Text(title, style = OneTextStyles.cardHeading, color = OneTheme.colors.accentOther)
            Icon(painterResource(if (open) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right), null, Modifier.size(16.dp), tint = OneTheme.colors.accentOther)
        }
        if (open) SettingsCaption(text)
    }
}
