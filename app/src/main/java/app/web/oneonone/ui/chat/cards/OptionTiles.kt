package app.web.oneonone.ui.chat.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.ui.chat.bubbleClickable
import app.web.oneonone.ui.components.pressScale
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/**
 * /thisorthat: two equal option tiles with an "or" badge between them. [picks] lists who chose each option ("a"/"b" to
 * names, shown as chips); [selected] outlines a tile; [onPick] makes the tiles tappable (null = display only).
 * [text]/[edge]/[surface] come from the host (bubble palette in a card, theme in a dialog).
 */
@Composable
internal fun OptionTiles(a: String, b: String, text: Color, edge: Color, surface: Color, selected: String? = null,
    picks: Map<String, List<String>> = emptyMap(), enabled: Boolean = true, onPick: ((String) -> Unit)? = null) {
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("a" to a, "b" to b).forEach { (key, label) ->
                OptionTile(label, if (key == "a") "Option A" else "Option B", selected == key, picks[key].orEmpty(), text, edge, enabled,
                    onPick?.let { pick -> { pick(key) } }, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Text("or", Modifier.align(Alignment.Center).size(24.dp).clip(CircleShape).background(surface).border(1.dp, edge, CircleShape).wrapContentSize(),
            color = text.copy(alpha = .8f), style = OneTextStyles.cardHint.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center)
    }
}

@Composable
private fun OptionTile(label: String, name: String, selected: Boolean, picks: List<String>, text: Color, edge: Color, enabled: Boolean,
    onPick: (() -> Unit)?, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(OneTheme.radii.bubble8)
    val description = "$name: $label" + if (picks.isEmpty()) "" else ", picked by ${picks.joinToString(" and ")}"
    Column(
        modifier.then(if (onPick != null) Modifier.pressScale(source) else Modifier)
            .clip(shape).background(text.copy(alpha = if (pressed) .22f else if (selected) .16f else .08f))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) text else edge), shape)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.RadioButton; this.selected = selected }
            .then(if (onPick != null) Modifier.bubbleClickable(enabled = enabled, interactionSource = source, onClick = onPick) else Modifier)
            .defaultMinSize(minHeight = OneTheme.sizes.touch40).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text(label, color = text.copy(alpha = if (enabled) 1f else .5f), style = OneTextStyles.subtitle.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center)
        if (picks.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            picks.forEach { who ->
                Text(who, Modifier.clip(CircleShape).background(text.copy(alpha = .2f)).padding(horizontal = 6.dp, vertical = 1.dp), color = text,
                    style = OneTextStyles.bubbleMeta.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
