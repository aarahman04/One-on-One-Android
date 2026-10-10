package app.web.oneonone.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.R
import app.web.oneonone.data.model.FeatureCommands
import app.web.oneonone.ui.components.OneIconButton
import app.web.oneonone.ui.components.pressScale
import app.web.oneonone.ui.theme.FontFamilies
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/** Slash-command descriptions, copied from client/src/features/slashCommands.ts. */
private val CommandDescriptions = mapOf(
    "letter" to "Write a letter",
    "countdown" to "Start a shared countdown",
    "checkin" to "How are you, really?",
    "ask" to "A sealed question, revealed together",
    "thisorthat" to "A playful pick, revealed together",
    "alarm" to "Emergency — alert them right now",
    "location" to "Share where you are right now",
)

/** The commands the "/" menu offers for this draft: prefix match on the text after "/" (slashCommands.ts render). */
internal fun slashMatches(draft: String): List<String> =
    if (!draft.startsWith("/")) emptyList() else draft.drop(1).lowercase().let { q -> FeatureCommands.filter { it.startsWith(q) } }

/** .chat__reply-bar: 56dp strip above the composer with the quoted author/snippet and a close button. */
@Composable
internal fun ReplyBar(author: String, snippet: String, onCancel: () -> Unit) {
    val c = OneTheme.colors
    Row(
        Modifier.fillMaxWidth().height(OneTheme.sizes.header56).background(c.bgRaised).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(author, color = c.accentOther, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(snippet, color = c.textDim, style = OneTextStyles.cardHint.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OneIconButton(R.drawable.ic_x, "Cancel reply", onCancel)
    }
}

/** .slash-menu: drop-up list of commands, radius 6, max height min(220dp, 40% of the screen). */
@Composable
internal fun SlashMenu(names: List<String>, maxHeight: androidx.compose.ui.unit.Dp, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = OneTheme.colors
    Column(
        modifier
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(OneTheme.radii.sm6))
            .background(c.bgRaised)
            .border(1.dp, c.border, RoundedCornerShape(OneTheme.radii.sm6))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        names.forEach { name ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(name) }.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("/$name", color = c.accentOther, style = OneTextStyles.menuItem.copy(fontWeight = FontWeight.SemiBold))
                Text(CommandDescriptions[name].orEmpty(), color = c.muted, style = OneTextStyles.cardHint.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * .chat__input-bar: [ pill: paperclip + growing text ] and a 48dp send circle (text present) or mic (empty).
 * While [recording] the row is a [trash | dot + timer | stop] bar instead.
 * Pure presentation: every action is a callback supplied by ChatScreen.
 */
@Composable
internal fun ChatComposer(
    draft: String,
    onDraft: (String) -> Unit,
    canSend: Boolean,
    recording: Boolean,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    elapsedSeconds: Int = 0,
    onStop: () -> Unit = {},
    onDiscard: () -> Unit = {},
) {
    val c = OneTheme.colors
    val pillShape = RoundedCornerShape(OneTheme.radii.pill22)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val textStyle = TextStyle(fontFamily = FontFamilies.Body, fontSize = 16.sp, lineHeight = 22.4.sp, color = c.text)
    Row(
        modifier.fillMaxWidth().background(c.bg).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recording) {
            RecordingBar(elapsedSeconds, onStop, onDiscard)
            return@Row
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = OneTheme.sizes.send48)
                .clip(pillShape)
                .background(c.bgRaised)
                .then(if (focused) Modifier.border(1.dp, c.accentYou, pillShape) else Modifier)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OneIconButton(R.drawable.ic_paperclip, "Attach", onAttach, Modifier.padding(bottom = 4.dp))
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.weight(1f).heightIn(max = 120.dp),
                textStyle = textStyle,
                cursorBrush = SolidColor(c.accentYou),
                interactionSource = source,
                decorationBox = { inner ->
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                        if (draft.isEmpty()) Text("Message", style = textStyle, color = c.muted)
                        inner()
                    }
                },
            )
        }
        if (draft.isNotBlank()) {
            val sendSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(OneTheme.sizes.send48)
                    .pressScale(sendSource, .94f)
                    .alpha(if (canSend) 1f else .5f)
                    .clip(CircleShape)
                    .background(c.sendBg)
                    .clickable(interactionSource = sendSource, indication = null, enabled = canSend, role = Role.Button, onClick = onSend)
                    .semantics { contentDescription = "Send" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_send), null, tint = c.sendFg, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(Modifier.size(OneTheme.sizes.send48), contentAlignment = Alignment.Center) {
                OneIconButton(R.drawable.ic_mic, "Record voice note", onMic)
            }
        }
    }
}

/** Same 48dp row as the composer while recording: discard, live timer, and a stop button that sends the voice note. */
@Composable
private fun RowScope.RecordingBar(elapsedSeconds: Int, onStop: () -> Unit, onDiscard: () -> Unit) {
    val c = OneTheme.colors
    val pulse by rememberInfiniteTransition(label = "recording-dot").animateFloat(
        initialValue = 1f, targetValue = .3f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(800, easing = OneTheme.motion.standard), RepeatMode.Reverse),
    )
    val stopSource = remember { MutableInteractionSource() }
    OneIconButton(R.drawable.ic_trash, "Discard recording", onDiscard, Modifier.size(OneTheme.sizes.send48))
    Row(
        Modifier.weight(1f).height(OneTheme.sizes.send48).semantics(mergeDescendants = true) { contentDescription = "Recording" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(c.danger))
        Text("%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), color = c.text, style = OneTextStyles.menuItem)
    }
    Box(
        Modifier
            .size(OneTheme.sizes.send48)
            .pressScale(stopSource, .94f)
            .clip(CircleShape)
            .background(c.sendBg)
            .clickable(interactionSource = stopSource, indication = null, role = Role.Button, onClick = onStop)
            .semantics { contentDescription = "Stop and send voice note" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_stop), null, tint = c.sendFg, modifier = Modifier.size(20.dp))
    }
}
