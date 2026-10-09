package app.web.oneonone.ui.chat

import app.web.oneonone.data.model.ChatMessage
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleGroupingTest {
    private val utc = ZoneId.of("UTC")
    private fun msg(sender: String, at: String, type: String = "text") =
        ChatMessage(id = at + sender, senderId = sender, content = "x", createdAt = at, type = type)

    private fun start(prev: ChatMessage?, cur: ChatMessage) = isGroupStart(prev, cur, utc)

    @Test fun firstMessageStartsGroup() = assertEquals(true, start(null, msg("a", "2026-10-10T10:00:00Z")))

    @Test fun sameSenderWithinMinuteContinues() =
        assertEquals(false, start(msg("a", "2026-10-10T10:00:00Z"), msg("a", "2026-10-10T10:00:30Z")))

    @Test fun boundaryOfSixtySecondsContinuesButOneMoreMillisecondStarts() {
        val prev = msg("a", "2026-10-10T10:00:00Z")
        assertEquals(false, start(prev, msg("a", "2026-10-10T10:01:00Z")))
        assertEquals(true, start(prev, msg("a", "2026-10-10T10:01:00.001Z")))
    }

    @Test fun sameInstantContinues() =
        assertEquals(false, start(msg("a", "2026-10-10T10:00:00Z"), msg("a", "2026-10-10T10:00:00Z")))

    @Test fun otherSenderStartsGroup() =
        assertEquals(true, start(msg("a", "2026-10-10T10:00:00Z"), msg("b", "2026-10-10T10:00:05Z")))

    @Test fun negativeDeltaStartsGroup() =
        assertEquals(true, start(msg("a", "2026-10-10T10:00:30Z"), msg("a", "2026-10-10T10:00:00Z")))

    @Test fun crossingMidnightStartsGroupEvenWithinMinute() =
        assertEquals(true, start(msg("a", "2026-10-10T23:59:50Z"), msg("a", "2026-10-11T00:00:10Z")))

    @Test fun localDayIsUsedNotUtcDay() {
        val prev = msg("a", "2026-10-10T18:59:50Z")
        val cur = msg("a", "2026-10-10T19:00:10Z")
        assertEquals(false, isGroupStart(prev, cur, utc)) // same UTC day
        assertEquals(true, isGroupStart(prev, cur, ZoneId.of("Asia/Karachi"))) // UTC+5: crosses local midnight
    }

    @Test fun systemLineBreaksGroup() {
        assertEquals(true, start(msg("a", "2026-10-10T10:00:00Z", "system"), msg("a", "2026-10-10T10:00:05Z")))
        assertEquals(true, start(msg("a", "2026-10-10T10:00:00Z"), msg("a", "2026-10-10T10:00:05Z", "system")))
    }
}
