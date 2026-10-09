package app.web.oneonone.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import app.web.oneonone.ui.theme.OneTheme

/** button:active { transform: scale(.97) } with --duration-fast / --ease-standard. */
fun Modifier.pressScale(interactionSource: InteractionSource, scale: Float = .97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = OneTheme.motion
    val value by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = tween(motion.fast120, easing = motion.standard),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = value
        scaleY = value
    }
}
