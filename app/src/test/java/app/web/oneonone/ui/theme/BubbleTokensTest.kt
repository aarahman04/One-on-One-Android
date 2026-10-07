package app.web.oneonone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleTokensTest {
    @Test
    fun textAndMetadataStayReadableAcrossEveryGradient() {
        for (palette in listOf(BubbleTokens.Dark, BubbleTokens.Light, BubbleTokens.Love, BubbleTokens.Samurai)) {
            for (bubble in listOf(palette.mine, palette.other)) {
                for (background in listOf(bubble.backgroundStart, bubble.backgroundEnd)) {
                    assertTrue("Message text needs AA contrast", contrast(bubble.text, background) >= 4.5)
                    assertTrue("Timestamps need AA contrast", contrast(bubble.meta, background) >= 4.5)
                }
            }
            for (background in listOf(palette.mine.backgroundStart, palette.mine.backgroundEnd)) {
                assertTrue("Sent ticks must be visible", contrast(palette.ticks, background) >= 3)
                assertTrue("Read ticks must be visible", contrast(palette.read, background) >= 3)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }
}
