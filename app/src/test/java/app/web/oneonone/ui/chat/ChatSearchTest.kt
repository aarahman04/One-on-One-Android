package app.web.oneonone.ui.chat

import app.web.oneonone.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSearchTest {
    private fun msg(content: String, type: String = "text") =
        ChatMessage(id = content + type, senderId = "a", content = content, createdAt = "2026-10-10T10:00:00Z", type = type)

    private val log = listOf(msg("Hello there"), msg("hello", "system"), msg("", "image"), msg("say HELLO"))

    @Test fun caseInsensitiveAndSkipsSystem() = assertEquals(listOf(0, 3), searchMatches(log, "hello"))
    @Test fun queryIsTrimmed() = assertEquals(listOf(0, 3), searchMatches(log, "  hello "))
    @Test fun matchesCardSummary() = assertEquals(listOf(2), searchMatches(log, "photo"))
    @Test fun blankQueryMatchesNothing() = assertEquals(emptyList<Int>(), searchMatches(log, "  "))
    @Test fun rangesFindEveryHitWithoutOverlap() = assertEquals(listOf(0..1, 3..4), matchRanges("aAxaa", "aa"))
    @Test fun rangesIgnoreCaseAndTrim() = assertEquals(listOf(4..8), matchRanges("say HELLO", " hello "))
    @Test fun rangesEmptyForBlankOrMissing() {
        assertEquals(emptyList<IntRange>(), matchRanges("text", ""))
        assertEquals(emptyList<IntRange>(), matchRanges("text", "zz"))
    }
}
