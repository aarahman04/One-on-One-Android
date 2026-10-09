package app.web.oneonone.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors docs/ARCHITECTURE.md in the web repo, 2026-10-07. Backgrounds run at 135 degrees.
data class BubbleColors(
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val tail: Color,
    val text: Color,
    val meta: Color,
    val edge: Color,
)

data class BubblePalette(val mine: BubbleColors, val other: BubbleColors, val ticks: Color, val read: Color)

object BubbleTokens {
    /** .chat__log wallpaper overlays (global.css L1542 love, L1573 samurai). */
    val LoveWallpaperOverlay = Color(0xFF0A0E14).copy(alpha = .22f)
    val SamuraiWallpaperOverlay = Color(0xFF0A0808).copy(alpha = .30f)

    val Dark = BubblePalette(
        mine = BubbleColors(Color(0xFF1B6841), Color(0xFF15523A), Color(0xFF185D3D),
            Color(0xFFF2FFF6), Color(0xFFCDEED8), BrandGreen.copy(alpha = .38f)),
        other = BubbleColors(Color(0xFF1F4468), Color(0xFF183552), Color(0xFF1B3C5D),
            Color(0xFFEAF4FF), Color(0xFFA9C7E4), BrandBlue.copy(alpha = .34f)),
        ticks = Color(0xFFCDEED8), read = Color(0xFF8FD0FF),
    )
    val Light = BubblePalette(
        mine = BubbleColors(Color(0xFFD6F6D8), Color(0xFFC3EEC8), Color(0xFFCBF1CF),
            Color(0xFF0F2E1A), Color(0xFF3A5F45), Color(0xFF1A7F37).copy(alpha = .4f)),
        other = BubbleColors(Color(0xFFE6F2FF), Color(0xFFD5E8FB), Color(0xFFDDEDFC),
            Color(0xFF10283F), Color(0xFF45637F), Color(0xFF0969DA).copy(alpha = .32f)),
        ticks = Color(0xFF4A6B53), read = Color(0xFF005B94),
    )
    val Love = BubblePalette(
        mine = BubbleColors(Color(0xFFEFD08A), Color(0xFFF5E6B5), Color(0xFFF2DBA0),
            Color(0xFF26323B), Color(0xFF4A5A64), Color(0xFF26323B).copy(alpha = .18f)),
        other = BubbleColors(Color(0xFF476A80), Color(0xFF476A80), Color(0xFF476A80),
            Color.White, Color(0xFFEAF3F8), Color.White.copy(alpha = .2f)),
        ticks = Color(0xFF4B5B66), read = Color(0xFF0B4F7A),
    )
    val Samurai = BubblePalette(
        mine = BubbleColors(Color(0xFFB0003A), Color(0xFFD10A4A), Color(0xFFC10543),
            Color.White, Color(0xFFFFF1F5), Color.White.copy(alpha = .22f)),
        other = BubbleColors(Color(0xFF232323), Color(0xFF232323), Color(0xFF232323),
            Color(0xFFD9D3BE), Color(0xFFB3AD98), Color(0xFFD9D3BE).copy(alpha = .2f)),
        ticks = Color(0xFFFFF1F5), read = Color(0xFFC7EBFF),
    )
}
