package app.web.oneonone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val BrandGreen = Color(0xFF7EE787)
val BrandBlue = Color(0xFF79C0FF)
val BrandBackground = Color(0xFF0D1117)

private fun OneColors.toScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = accentYou, onPrimary = onPrimary,
        secondary = accentOther, onSecondary = onPrimary, // dark text reads on pale #79c0ff
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = bgRaised, onSurfaceVariant = textDim,
        surfaceContainerLowest = bgRaised, surfaceContainerLow = bgRaised, surfaceContainer = bgRaised,
        surfaceContainerHigh = bgRaised, surfaceContainerHighest = bgRaised,
        outline = border, outlineVariant = border,
        error = danger, onError = Color.White,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = accentYou, onPrimary = onPrimary,
        secondary = accentOther, onSecondary = Color.White, // white reads on deep #0969da
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = bgRaised, onSurfaceVariant = textDim,
        surfaceContainerLowest = bgRaised, surfaceContainerLow = bgRaised, surfaceContainer = bgRaised,
        surfaceContainerHigh = bgRaised, surfaceContainerHighest = bgRaised,
        outline = border, outlineVariant = border,
        error = danger, onError = Color.White,
        scrim = scrim,
    )
}

private val OneShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun OneOnOneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) OneColors.Dark else OneColors.Light
    CompositionLocalProvider(LocalOneColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toScheme(darkTheme),
            typography = OneTypography,
            shapes = OneShapes,
            content = content,
        )
    }
}
