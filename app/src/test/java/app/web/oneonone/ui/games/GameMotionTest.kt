package app.web.oneonone.ui.games

import org.junit.Assert.*
import org.junit.Test

class GameMotionTest {
    @Test fun longerMovesTakeLongerButStayQuick() {
        val step = slideMillis(52, 44)      // e2-e3
        val knight = slideMillis(57, 42)    // b1-c3
        val diagonal = slideMillis(56, 7)   // a1-h8
        assertTrue(step in 150..220)
        assertTrue(knight > step)
        assertTrue(diagonal > knight)
        assertTrue(diagonal <= 280)
        assertEquals(150, slideMillis(10, 10))
    }

    @Test fun connectFourDiscsFallLongerFromHigherUp() {
        val times = (1..6).map(::dropMillis)
        assertEquals(times.sorted(), times)
        assertTrue(times.first() in 150..220 && times.last() <= 400)
        assertEquals(dropMillis(6), dropMillis(9))
    }
}
