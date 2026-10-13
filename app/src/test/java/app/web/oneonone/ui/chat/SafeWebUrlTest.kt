package app.web.oneonone.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeWebUrlTest {
    @Test fun webLinksPass() = assertEquals("https://open.spotify.com/track/1", safeWebUrl("https://open.spotify.com/track/1"))
    @Test fun otherSchemesAreRejected() {
        for (bad in listOf("intent://x#Intent;end", "javascript:alert(1)", "file:///sdcard/a", "content://x/y", "tel:123", "https:///nohost", "not a url"))
            assertNull(bad, safeWebUrl(bad))
    }
}
