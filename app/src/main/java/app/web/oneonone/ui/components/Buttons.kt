package app.web.oneonone.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)

@Composable
private fun OneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    content: Color,
    border: Color,
    weight: FontWeight,
) {
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(source),
        enabled = enabled,
        shape = RoundedCornerShape(OneTheme.radii.xs4),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
        interactionSource = source,
    ) {
        Text(
            text,
            modifier = Modifier.padding(ButtonPadding),
            style = OneTextStyles.subtitle.copy(fontWeight = weight),
            color = if (enabled) content else content.copy(alpha = .5f),
        )
    }
}

/** button.primary: accent-you fill, #04170a text, weight 600. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = OneTheme.colors
    OneButton(text, onClick, modifier, enabled, c.accentYou, c.onPrimary, c.accentYou, FontWeight.SemiBold)
}

/** Base button: bg-raised, 1px border, text. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = OneTheme.colors
    OneButton(text, onClick, modifier, enabled, c.bgRaised, c.text, c.border, FontWeight.Normal)
}

/** button.danger: danger border + text. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = OneTheme.colors
    OneButton(text, onClick, modifier, enabled, c.bgRaised, c.danger, c.danger, FontWeight.Normal)
}

/** .screen__alt: borderless quiet action, text-dim 13sp. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = OneTextStyles.cardHint.copy(letterSpacing = .01.em),
        color = OneTheme.colors.textDim,
    )
}

/** 40dp round icon button, text-dim glyph, 8% text tint while pressed. */
@Composable
fun OneIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = OneTheme.colors.textDim,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val c = OneTheme.colors
    Box(
        modifier = modifier
            .size(OneTheme.sizes.touch40)
            .clip(CircleShape)
            .background(if (pressed) c.text.copy(alpha = .08f) else Color.Transparent)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) tint else c.muted,
            modifier = Modifier.size(22.dp),
        )
    }
}
