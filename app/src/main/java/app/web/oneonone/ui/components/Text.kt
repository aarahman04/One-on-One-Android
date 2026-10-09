package app.web.oneonone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/** .screen__eyebrow: muted, 13sp, .08em tracking. Caller passes already-uppercased text if wanted. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, modifier, color = OneTheme.colors.muted, style = OneTextStyles.eyebrow, textAlign = textAlign)
}

/** .screen__title: Fraunces 600, 28sp. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = TextAlign.Center) {
    Text(text, modifier, color = OneTheme.colors.text, style = OneTextStyles.screenTitle, textAlign = textAlign)
}

/** .screen__subtitle: text-dim, 15sp. */
@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = TextAlign.Center) {
    Text(text, modifier, color = OneTheme.colors.textDim, style = OneTextStyles.subtitle, textAlign = textAlign)
}

/** input/textarea: bg-raised, 1px border, radius 4, padding 10/12; focus switches the border to accent-other. */
@Composable
fun OneTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    textStyle: TextStyle = OneTextStyles.subtitle,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = OneTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = c.text),
        cursorBrush = SolidColor(c.accentOther),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = source,
        decorationBox = { inner ->
            Surface(
                shape = RoundedCornerShape(OneTheme.radii.xs4),
                color = c.bgRaised,
                border = BorderStroke(1.dp, if (focused) c.accentOther else c.border),
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            }
        },
    )
}
