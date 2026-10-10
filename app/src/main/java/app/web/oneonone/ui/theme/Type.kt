package app.web.oneonone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.web.oneonone.R

// --font-body / --font-mono. Static TTFs generated from the web variable woff2s by scripts/make_fonts.py (latin + latin-ext).
// Headings use the body family (Figtree) at heavier weights with tighter tracking — the serif display face was dropped on Android.
object FontFamilies {
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

/** Heading style: Figtree semibold, slightly tightened. */
private fun heading(size: Float, line: Float, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = FontFamilies.Body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.01).em)

private fun body(size: Float, weight: FontWeight = FontWeight.Normal, line: Float = size * 1.5f) =
    TextStyle(fontFamily = FontFamilies.Body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** Web type scale: 11 meta / 12 xs / 13 sm / 15 base / 16 md / 20 lg / 28 xl / 40 display. Body line-height 1.5. */
val OneTypography = Typography(
    displayLarge = heading(40f, 44f),
    displayMedium = heading(40f, 44f),
    displaySmall = heading(28f, 31f),
    headlineLarge = heading(28f, 34f),
    headlineMedium = heading(28f, 34f),
    headlineSmall = heading(20f, 30f),
    titleLarge = heading(20f, 30f),
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
    val screenTitle = heading(28f, 36f, FontWeight.Bold)
    val subtitle = body(15f, line = 22.5f)
    val connectionId = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 48.sp, letterSpacing = .12.em)
    val bubbleText = body(15f, line = 22.5f)
    val bubbleMeta = body(11f, line = 11f)
    val cardHeading = body(13f, FontWeight.SemiBold)
    val cardHint = body(13f)
    val menuItem = body(13f)
    val groupLabel = body(11f).copy(letterSpacing = .08.em)
    val callName = heading(28f, 30.8f)
    val callStatus = TextStyle(fontFamily = FontFamilies.Mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp, letterSpacing = .06.em, fontFeatureSettings = "tnum")
}
