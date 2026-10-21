package app.web.oneonone.data.stickers

import org.junit.Assert.*
import org.junit.Test

class StickerBrushTest {
    private fun assertPoint(expected: StickerBrushPoint, actual: StickerBrushPoint) {
        assertEquals(expected.x, actual.x, .0001f); assertEquals(expected.y, actual.y, .0001f)
    }
    @Test fun offsetAndPhotoFitUseTheSameCoordinatesInPortraitAndLandscape() {
        assertPoint(StickerBrushPoint(200f, 100f), stickerBrushPoint(400, 200, 300f, 300f, 150f, 200f, 50f))
        assertPoint(StickerBrushPoint(100f, 200f), stickerBrushPoint(200, 400, 300f, 300f, 150f, 200f, 50f))
        assertPoint(StickerBrushPoint(200f, 100f), stickerBrushPoint(400, 200, 240f, 360f, 120f, 240f, 60f))
        assertPoint(StickerBrushPoint(200f, 100f), stickerBrushPoint(400, 200, 600f, 600f, 300f, 400f, 100f))
        // Doubling density doubles both the displayed circle and fit scale, leaving its image-space radius unchanged.
        assertEquals(16f / stickerBrushScale(400, 200, 300f, 300f), 32f / stickerBrushScale(400, 200, 600f, 600f), 0f)
    }

    @Test fun outsideCursorIsNotClampedOntoThePhotoEdgeAndInvalidGeometryIsRejected() {
        assertTrue(stickerBrushPoint(400, 200, 300f, 300f, 10f, 20f, 64f).y < 0)
        assertTrue(stickerBrushPoint(200, 400, 300f, 300f, 0f, 150f, 0f).x < 0)
        assertTrue(runCatching { stickerBrushScale(0, 100, 300f, 300f) }.isFailure)
        assertTrue(runCatching { stickerBrushPoint(100, 100, 300f, 300f, Float.NaN, 0f, 64f) }.isFailure)
    }

    @Test fun tapsStaySinglePointsAndLongContinuousStrokesKeepTheirEndWithinTheMemoryBound() {
        val tap = StickerBrushPoint(2f, 3f)
        assertEquals(listOf(tap), appendStickerBrushPoint(listOf(tap), tap))
        var points = emptyList<StickerBrushPoint>()
        repeat(10_000) { index ->
            points = appendStickerBrushPoint(points, StickerBrushPoint(index.toFloat(), 1f))
            assertTrue(points.size <= STICKER_BRUSH_POINTS)
        }
        assertEquals(StickerBrushPoint(0f, 1f), points.first())
        assertEquals(StickerBrushPoint(9_999f, 1f), points.last())
    }
}
