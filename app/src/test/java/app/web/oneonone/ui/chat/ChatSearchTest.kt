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
    @Test fun rangesMatchWordStartsOnly() = assertEquals(listOf(0..1, 6..7), matchRanges("aAxaa aa", "aa"))
    @Test fun heyDoesNotMatchThey() = assertEquals(listOf(1), searchMatches(listOf(msg("they said"), msg("Hey!"), msg("okay")), "hey"))
    @Test fun regexCharactersAreLiteral() = assertEquals(listOf(4..5), matchRanges("why ?? ok", "??"))
    @Test fun rangesIgnoreCaseAndTrim() = assertEquals(listOf(4..8), matchRanges("say HELLO", " hello "))
    @Test fun rangesEmptyForBlankOrMissing() {
        assertEquals(emptyList<IntRange>(), matchRanges("text", ""))
        assertEquals(emptyList<IntRange>(), matchRanges("text", "zz"))
    }
}
