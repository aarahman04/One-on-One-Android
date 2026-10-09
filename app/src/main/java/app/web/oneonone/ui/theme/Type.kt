package app.web.oneonone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.web.oneonone.R

// --font-display / --font-body / --font-mono. Static TTFs generated from the web variable woff2s by scripts/make_fonts.py (latin + latin-ext; Fraunces pinned wght 600 / opsz 28).
object FontFamilies {
    val Display = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))
    val Body = FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
        Font(R.font.figtree_bold, FontWeight.Bold),
    )
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
}

private fun body(size: Float, weight: FontWeight = FontWeight.Normal, line: Float = size * 1.5f) =
    TextStyle(fontFamily = FontFamilies.Body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** Web type scale: 11 meta / 12 xs / 13 sm / 15 base / 16 md / 20 lg / 28 xl / 40 display. Body line-height 1.5. */
val OneTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp),
    displaySmall = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 31.sp),
    headlineLarge = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = body(16f, FontWeight.SemiBold),
    titleSmall = body(13f, FontWeight.SemiBold),
    bodyLarge = body(16f),
    bodyMedium = body(15f, line = 22.5f),
    bodySmall = body(13f),
    labelLarge = body(15f, FontWeight.SemiBold, 22.5f),
    labelMedium = body(12f),
    labelSmall = body(11f),
)

object OneTextStyles {
    /** Uppercase is applied by the caller; color = muted. */
    val eyebrow = body(13f).copy(letterSpacing = .08.em)
    val screenTitle = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 42.sp)
    val subtitle = body(15f, line = 22.5f)
    val connectionId = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 48.sp, letterSpacing = .12.em)
    val bubbleText = body(15f, line = 22.5f)
    val bubbleMeta = body(11f, line = 11f)
    val cardHeading = body(13f, FontWeight.SemiBold)
    val cardHint = body(13f)
    val menuItem = body(13f)
    val groupLabel = body(11f).copy(letterSpacing = .08.em)
    val callName = TextStyle(fontFamily = FontFamilies.Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 30.8.sp)
    val callStatus = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp, letterSpacing = .06.em, fontFeatureSettings = "tnum")
}
