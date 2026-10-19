package app.web.oneonone.ui.chat.cards

import app.web.oneonone.ui.chat.bubbleClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.web.oneonone.ui.components.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.ui.chat.LocalBubbleColors
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

@Composable
internal fun CardHeading(@DrawableRes icon: Int, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(18.dp), tint = LocalBubbleColors.current.text)
        Text(title, style = OneTextStyles.cardHeading, color = LocalBubbleColors.current.text)
    }
}

@Composable
internal fun CardHint(text: String) {
    Text(text, style = OneTextStyles.cardHint, color = LocalBubbleColors.current.text.copy(alpha = .8f))
}

@Composable
internal fun CardAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, location: Boolean = false) {
    val colors = LocalBubbleColors.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (location) OneTheme.radii.sm6 else OneTheme.radii.xs4)
    Surface(modifier = modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40).pressScale(source).clip(shape)
            .bubbleClickable(enabled = enabled, interactionSource = source, onClick = onClick),
        color = if (location) Color.White.copy(alpha = .06f) else Color.Transparent,
        shape = shape, border = BorderStroke(1.dp, colors.edge), contentColor = colors.text) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(text, style = OneTextStyles.cardHint.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text.copy(alpha = if (enabled) 1f else .5f), textAlign = TextAlign.Center)
        }
    }
}
