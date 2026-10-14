package app.web.oneonone.data

import app.web.oneonone.data.media.isGiphyMedia
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiphyMediaTest {
    @Test fun `only https giphy media hosts load in the picker`() {
        assertTrue(isGiphyMedia("https://media2.giphy.com/media/abc/200w.gif?cid=1"))
        assertFalse(isGiphyMedia("http://media2.giphy.com/media/abc/200w.gif"))
        assertFalse(isGiphyMedia("https://giphy.com.evil.example/a.gif"))
        assertFalse(isGiphyMedia("https://media.giphy.com:8443/a.gif"))
        assertFalse(isGiphyMedia("not a url"))
    }
}
