package app.web.oneonone.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class PartnerPhotoTest {
    @Test fun `sample keeps the longer side at or above the target`() {
        assertEquals(1, sampleSize(400, 300, 512))
        assertEquals(1, sampleSize(1023, 800, 512))
        assertEquals(2, sampleSize(1024, 768, 512))
        assertEquals(4, sampleSize(3000, 4000, 512))
    }
}
