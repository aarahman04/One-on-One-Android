package app.web.oneonone.data.stickers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiRefNetMemoryTest {
    @Test fun lowMemoryKeepsHeavyInferenceOffWhileManualEditingRemainsAvailable() {
        assertFalse(biRefNetMemorySufficient(0, false))
        assertFalse(biRefNetMemorySufficient(1_749_999_999, false))
        assertTrue(biRefNetMemorySufficient(1_750_000_000, false))
        assertFalse(biRefNetMemorySufficient(4_000_000_000, true))
    }
}
