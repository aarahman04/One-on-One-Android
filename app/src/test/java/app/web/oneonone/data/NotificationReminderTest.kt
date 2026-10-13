package app.web.oneonone.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReminderTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test fun firstReminderIsDue() = assertTrue(notificationReminderDue(null, 0L))
    @Test fun waitsThreeDays() {
        assertFalse(notificationReminderDue(10 * day, 12 * day))
        assertTrue(notificationReminderDue(10 * day, 13 * day))
    }
    @Test fun clockMovedBackStillReminds() = assertTrue(notificationReminderDue(10 * day, 9 * day))
}
