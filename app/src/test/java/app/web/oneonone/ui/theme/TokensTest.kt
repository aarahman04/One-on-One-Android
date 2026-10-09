package app.web.oneonone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

// Hex values copied from client/src/styles/global.css (:root and the prefers-color-scheme: light block).
class TokensTest {
    private fun assertHex(expected: String, actual: Color) =
        assertEquals(expected, "#%06x".format(actual.toArgb() and 0xFFFFFF))

    @Test
    fun darkMatchesWeb() = with(OneColors.Dark) {
        assertHex("#0d1117", bg); assertHex("#12181f", bgRaised); assertHex("#1f2630", border)
        assertHex("#e6edf3", text); assertHex("#9aa4af", textDim); assertHex("#6e7681", muted)
        assertHex("#7ee787", accentYou); assertHex("#79c0ff", accentOther); assertHex("#f85149", danger)
        assertHex("#04170a", onPrimary); assertHex("#7ee787", sendBg); assertHex("#102719", sendFg)
    }

    @Test
    fun lightMatchesWeb() = with(OneColors.Light) {
        assertHex("#f5f5f7", bg); assertHex("#ffffff", bgRaised); assertHex("#d9d9dd", border)
        assertHex("#1c1c1e", text); assertHex("#5b5b60", textDim); assertHex("#8a8a8e", muted)
        assertHex("#1a7f37", accentYou); assertHex("#0969da", accentOther); assertHex("#cf222e", danger)
        assertHex("#04170a", onPrimary)
    }

    @Test
    fun scrimsMatchWeb() {
        assertEquals(.6f, OneColors.Dark.scrim.alpha, .01f)
        assertEquals(.14f, OneColors.Dark.quoteBg.alpha, .01f)
        assertEquals(.18f, OneColors.Dark.discBg.alpha, .01f)
    }
}
