package app.web.oneonone.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTextScaleTest {
    @Test fun stepsMapToScales() {
        assertEquals(listOf(0.9f, 1f, 1.15f, 1.3f), (0..3).map(::chatTextScale))
    }

    @Test fun outOfRangeStepsClamp() {
        assertEquals(0.9f, chatTextScale(-5), 0f)
        assertEquals(1.3f, chatTextScale(9), 0f)
        assertEquals(1f, chatTextScale(DEFAULT_CHAT_TEXT_STEP), 0f)
    }
}
