package app.web.oneonone.ui.games

import org.junit.Assert.*
import org.junit.Test

class PongTouchTest {
    @Test fun finalUpMovementIsPreservedAndSecondFingerCannotStealStroke() {
        val touch = PongTouch()
        assertTrue(touch.begin(1, .5f, 2f))
        assertFalse(touch.begin(2, .8f, 1.4f))
        touch.move(2, 1f, 1.3f)
        assertEquals(.5f, touch.x, 0f)
        assertNull(touch.end(2, false, .5f, 2f))
        touch.move(1, .7f, 1.3f)
        assertEquals(.7f to 1.3f, touch.end(1, false, .6f, 1.7f))
        assertFalse(touch.held)
    }

    @Test fun pointerHistoryAndRenderingConsumeElapsedMotionOnlyOnce() {
        val touch = PongTouch()
        assertEquals(1f / 60, touch.elapsed(1_000_000_000), 1e-6f)
        assertEquals(.008f, touch.elapsed(1_008_000_000), 1e-6f)
        assertEquals(0f, touch.elapsed(1_004_000_000), 0f)
        assertEquals(0f, touch.elapsed(1_008_000_000), 0f)
        assertEquals(.008f, touch.elapsed(1_016_000_000), 1e-6f)
        assertEquals(.1f, touch.elapsed(2_000_000_000), 0f)
    }

    @Test fun cancelStopsAtActualBatAndRoundResetCannotResumeOldFinger() {
        val touch = PongTouch()
        touch.begin(1, .5f, 2f); touch.move(1, .8f, 1.3f)
        assertEquals(.6f to 1.8f, touch.end(1, true, .6f, 1.8f))
        touch.begin(2, .5f, 2f); touch.cancel(); touch.move(2, .8f, 1.3f)
        assertFalse(touch.held); assertEquals(.5f, touch.x, 0f)
        assertNull(touch.end(2, false, .5f, 2f))
        assertTrue(touch.begin(3, .5f, 2f))
    }
}
