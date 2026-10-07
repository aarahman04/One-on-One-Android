package app.web.oneonone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF7EE787)
val BrandBlue = Color(0xFF79C0FF)
val BrandBackground = Color(0xFF0D1117)

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF0F2E1A),
    secondary = BrandBlue,
    onSecondary = Color(0xFF10283F),
    background = BrandBackground,
    onBackground = Color(0xFFF2FFF6),
    surface = BrandBackground,
    onSurface = Color(0xFFF2FFF6),
    surfaceVariant = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFFA9C7E4),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A7F37),
    onPrimary = Color.White,
    secondary = Color(0xFF0969DA),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF10283F),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF10283F),
    surfaceVariant = Color(0xFFE6F2FF),
    onSurfaceVariant = Color(0xFF45637F),
)

@Composable
fun OneOnOneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
