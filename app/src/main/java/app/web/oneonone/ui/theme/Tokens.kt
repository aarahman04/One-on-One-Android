package app.web.oneonone.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Mirrors client/src/styles/global.css (:root and .chat[data-theme]) in the web repo. See docs/design/WEB-STYLE-SPEC.md.

data class OneColors(
    val bg: Color,
    val bgRaised: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val muted: Color,
    val accentYou: Color,
    val accentOther: Color,
    val danger: Color,
    val onPrimary: Color = Color(0xFF04170A),
    val sendBg: Color = Color(0xFF7EE787),
    val sendFg: Color = Color(0xFF102719),
    val scrim: Color = Color.Black.copy(alpha = .6f),
    val quoteBg: Color = Color.Black.copy(alpha = .14f),
    val discBg: Color = Color.Black.copy(alpha = .18f),
) {
    companion object {
        val Dark = OneColors(
            bg = Color(0xFF0D1117), bgRaised = Color(0xFF12181F), border = Color(0xFF1F2630),
            text = Color(0xFFE6EDF3), textDim = Color(0xFF9AA4AF), muted = Color(0xFF6E7681),
            accentYou = Color(0xFF7EE787), accentOther = Color(0xFF79C0FF), danger = Color(0xFFF85149),
        )
        val Light = OneColors(
            bg = Color(0xFFF5F5F7), bgRaised = Color(0xFFFFFFFF), border = Color(0xFFD9D9DD),
            text = Color(0xFF1C1C1E), textDim = Color(0xFF5B5B60), muted = Color(0xFF8A8A8E),
            accentYou = Color(0xFF1A7F37), accentOther = Color(0xFF0969DA), danger = Color(0xFFCF222E),
        )
    }
}

data class OneSpacing(
    val xxs2: Dp = 2.dp, val xs4: Dp = 4.dp, val sm8: Dp = 8.dp, val md12: Dp = 12.dp,
    val lg16: Dp = 16.dp, val xl24: Dp = 24.dp, val xxl32: Dp = 32.dp,
)

data class OneRadii(
    val xs4: Dp = 4.dp,      // buttons, inputs
    val sm6: Dp = 6.dp,
    val bubble8: Dp = 8.dp,
    val md10: Dp = 10.dp,
    val lg16: Dp = 16.dp,
    val pill22: Dp = 22.dp,  // composer pill (--radius-input)
    val full: Dp = 999.dp,   // --radius-pill
)

data class OneSizes(
    val touch40: Dp = 40.dp, val send48: Dp = 48.dp, val header56: Dp = 56.dp, val chatMax720: Dp = 720.dp,
)

data class OneMotion(
    val standard: CubicBezierEasing = CubicBezierEasing(.2f, 0f, 0f, 1f),
    val emphasized: CubicBezierEasing = CubicBezierEasing(.3f, 0f, .1f, 1f),
    val fast120: Int = 120, val enter160: Int = 160, val base200: Int = 200, val slow320: Int = 320,
)

/** --elevation-1..3 blur radii (box-shadow 0 1px 3px / 0 4px 16px / 0 8px 32px; alpha .3/.4/.5 dark, .1/.12/.16 light). */
data class OneElevation(
    val e1: Dp = 3.dp, val e2: Dp = 16.dp, val e3: Dp = 32.dp,
)

val LocalOneColors = staticCompositionLocalOf { OneColors.Dark }
val LocalOneSpacing = staticCompositionLocalOf { OneSpacing() }
val LocalOneRadii = staticCompositionLocalOf { OneRadii() }
val LocalOneSizes = staticCompositionLocalOf { OneSizes() }
val LocalOneMotion = staticCompositionLocalOf { OneMotion() }
val LocalOneElevation = staticCompositionLocalOf { OneElevation() }

object OneTheme {
    val colors: OneColors @Composable @ReadOnlyComposable get() = LocalOneColors.current
    val spacing: OneSpacing @Composable @ReadOnlyComposable get() = LocalOneSpacing.current
    val radii: OneRadii @Composable @ReadOnlyComposable get() = LocalOneRadii.current
    val sizes: OneSizes @Composable @ReadOnlyComposable get() = LocalOneSizes.current
    val motion: OneMotion @Composable @ReadOnlyComposable get() = LocalOneMotion.current
    val elevation: OneElevation @Composable @ReadOnlyComposable get() = LocalOneElevation.current
}
