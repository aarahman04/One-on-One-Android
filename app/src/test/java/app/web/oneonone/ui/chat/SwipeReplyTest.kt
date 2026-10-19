package app.web.oneonone.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeReplyTest {
    private val slop = 8f

    @Test fun verticalScrollNeverClaims() {
        assertFalse(claimsReplySwipe(2f, 120f, slop, mine = false)); assertFalse(claimsReplySwipe(-3f, -80f, slop, mine = true))
    }
    @Test fun thirtyDegreeDiagonalDoesNotClaim() {
        assertFalse(claimsReplySwipe(86.6f, 50f, slop, mine = false)); assertFalse(claimsReplySwipe(-86.6f, 50f, slop, mine = true))
    }
    @Test fun theirsRightAndMineLeftClaim() {
        assertTrue(claimsReplySwipe(40f, 10f, slop, mine = false)); assertTrue(claimsReplySwipe(-40f, 10f, slop, mine = true))
    }
    @Test fun wrongDirectionIgnored() {
        assertFalse(claimsReplySwipe(-40f, 0f, slop, mine = false)); assertFalse(claimsReplySwipe(40f, 0f, slop, mine = true))
    }
    @Test fun belowSlopDoesNotClaim() = assertFalse(claimsReplySwipe(6f, 0f, slop, mine = false))

    @Test fun rubberBandHalvesTravelPastTriggerAndCaps() {
        assertEquals(40f, rubberBand(40f, 60f, 80f), 0f); assertEquals(70f, rubberBand(80f, 60f, 80f), 0f); assertEquals(80f, rubberBand(500f, 60f, 80f), 0f)
    }
    @Test fun releaseNeedsTriggerOrFastFlingPastHalf() {
        assertTrue(replyTriggered(60f, 0f, 60f, 1000f)); assertFalse(replyTriggered(40f, 0f, 60f, 1000f))
        assertTrue(replyTriggered(40f, 1500f, 60f, 1000f)); assertFalse(replyTriggered(20f, 1500f, 60f, 1000f))
    }
}
