package app.web.oneonone.ui

import app.web.oneonone.data.chat.newerTime
import app.web.oneonone.data.realtime.ConnectionState
import app.web.oneonone.ui.chat.ChatPresence
import app.web.oneonone.ui.chat.chatPresence
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ChatPresenceTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private fun presence(lastRead: String?, state: ConnectionState = ConnectionState.Connected,
                         locale: Locale = Locale.US, pattern: String = "HH:mm", zone: ZoneId = ZoneId.of("UTC")) =
        chatPresence(state, lastRead, now, zone, locale, pattern)

    @Test fun onlineExpiresAtExactlyFifteenSeconds() {
        assertEquals(ChatPresence("Online", true), presence(now.minusMillis(14_999).toString()))
        assertEquals(ChatPresence("Last seen today at 11:59"), presence(now.minusSeconds(15).toString()))
    }

    @Test fun formatsTodayYesterdayAndOlderInLocalTime() {
        assertEquals("Last seen today at 08:05", presence("2026-10-10T08:05:00Z").label)
        assertEquals("Last seen yesterday at 23:45", presence("2026-10-09T23:45:00Z").label)
        assertEquals("Last seen 8 Oct", presence("2026-10-08T23:45:00Z").label)
        assertEquals("Last seen today at 03:30", presence("2026-10-09T22:00:00Z", zone = ZoneId.of("Asia/Kolkata")).label)
        assertEquals("Last seen yesterday at 22:30", presence("2026-10-10T02:30:00Z", zone = ZoneId.of("America/New_York")).label)
    }

    @Test fun respectsLocaleAndSystemTimePattern() {
        assertEquals("Last seen 8 oct.", presence("2026-10-08T23:45:00Z", locale = Locale.FRANCE).label)
        assertEquals("Last seen today at 8:05 AM", presence("2026-10-10T08:05:00Z", pattern = "h:mm a").label)
    }

    @Test fun noReceiptMeansOfflineAndOwnSocketTakesPriority() {
        assertEquals(ChatPresence("Offline"), presence(null))
        listOf(null, now.toString(), "2026-10-08T23:45:00Z").forEach { lastRead ->
            assertEquals(ChatPresence("Waiting for network"), presence(lastRead, ConnectionState.Offline))
            assertEquals(ChatPresence("Connecting…"), presence(lastRead, ConnectionState.Connecting))
        }
    }

    @Test fun newestConnectionOrLiveReceiptDrivesPresence() {
        val old = "2026-10-08T23:45:00Z"
        val fresh = now.toString()
        assertTrue(presence(newerTime(old, fresh)).online)
        assertTrue(presence(newerTime(fresh, old)).online)
        assertTrue(presence(newerTime(null, fresh)).online)
        assertTrue(presence(newerTime(fresh, null)).online)
    }
}
