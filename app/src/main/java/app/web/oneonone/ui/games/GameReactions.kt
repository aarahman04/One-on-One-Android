package app.web.oneonone.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.R
import app.web.oneonone.data.games.GameReaction
import app.web.oneonone.data.games.REACTION_MAX_CHARS
import app.web.oneonone.ui.chat.emojiOnlyCount
import app.web.oneonone.ui.components.OneIconButton
import app.web.oneonone.ui.components.OneTextField
import app.web.oneonone.ui.theme.OneTheme

internal val QuickEmoji = listOf("😂", "🔥", "😮", "👏", "😅", "😤", "❤️", "🎉")
internal val QuickLines = listOf("Nice move!", "Good game", "Your turn 😄", "Oops", "So close!", "Well played", "Hurry up!", "Rematch?")

/** Quick emoji, ready-made lines and a one-line comment, dropped down under the game header. */
@Composable
internal fun ReactionTray(visible: Boolean, onSend: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    BackHandler(visible, onClose)
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(140)) + expandVertically(tween(180), expandFrom = Alignment.Top),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)) {
        var draft by rememberSaveable { mutableStateOf("") }
        val send = { if (draft.isNotBlank()) { onSend(draft); draft = ""; onClose() } }
        Surface(Modifier.fillMaxWidth().padding(horizontal = s.md12, vertical = s.sm8), shape = RoundedCornerShape(OneTheme.radii.lg16),
            color = c.bgRaised, border = BorderStroke(1.dp, c.border), shadowElevation = 6.dp) {
            Column(Modifier.padding(s.md12).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(s.sm8)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(s.xs4)) {
                    QuickEmoji.forEach { emoji ->
                        Box(Modifier.size(OneTheme.sizes.send48).clip(CircleShape).clickable(role = Role.Button) { onSend(emoji); onClose() }
                            .semantics { contentDescription = "Send $emoji" }, contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 26.sp)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.sm8), verticalArrangement = Arrangement.spacedBy(s.sm8)) {
                    QuickLines.forEach { line ->
                        Text(line, Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).background(c.bg)
                            .clickable(role = Role.Button) { onSend(line); onClose() }
                            .padding(horizontal = s.md12, vertical = s.sm8), color = c.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.xs4)) {
                    OneTextField(draft, { draft = limitReaction(it) },
                        Modifier.weight(1f).semantics { contentDescription = "Comment" }, placeholder = "Say something…",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
                    OneIconButton(R.drawable.ic_send, "Send comment", send, tint = c.accentYou, enabled = draft.isNotBlank())
                }
                val used = draft.codePointCount(0, draft.length)
                Text("$used/$REACTION_MAX_CHARS", Modifier.align(Alignment.End).padding(end = OneTheme.sizes.touch40),
                    color = if (used >= REACTION_MAX_CHARS) c.danger else c.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * A reaction shown in a slot the screen already has (the partner's under their name in the header, ours in the
 * controls' hint line), so it never covers the board, a clock or a button. [fallback] returns when it expires.
 */
@Composable
internal fun ReactionSlot(reaction: GameReaction?, mine: Boolean, nickname: String, modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart, fallback: @Composable () -> Unit) {
    val c = OneTheme.colors
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(reaction?.id) { if (reaction != null && !mine) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    AnimatedContent(reaction, modifier, contentAlignment = alignment, label = "reaction",
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = .7f))
                .togetherWith(fadeOut(tween(140)))
        }) { shown ->
        if (shown == null) Box(contentAlignment = alignment) { fallback() }
        // Box does not pass a minimum width on, so the bubble wraps its text even in a full-width slot.
        else Box(contentAlignment = alignment) {
            val accent = if (mine) c.accentYou else c.accentOther
            val big = emojiOnlyCount(shown.text) == 1
            Text(shown.text, Modifier
                .semantics {
                    contentDescription = if (mine) "You: ${shown.text}" else "$nickname: ${shown.text}"
                    if (!mine) liveRegion = LiveRegionMode.Polite
                }
                .background(accent.copy(alpha = .14f), RoundedCornerShape(OneTheme.radii.lg16))
                .border(1.dp, accent.copy(alpha = .5f), RoundedCornerShape(OneTheme.radii.lg16))
                .padding(horizontal = 10.dp, vertical = if (big) 0.dp else 2.dp),
                color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (big) MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp) else MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One line, at most [REACTION_MAX_CHARS] code points (an emoji counts once, as on the server). */
internal fun limitReaction(text: String): String {
    val line = text.replace('\n', ' ')
    return if (line.codePointCount(0, line.length) <= REACTION_MAX_CHARS) line
    else line.substring(0, line.offsetByCodePoints(0, REACTION_MAX_CHARS))
}
