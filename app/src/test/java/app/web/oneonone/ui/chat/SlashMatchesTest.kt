package app.web.oneonone.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SlashMatchesTest {
    @Test fun noSlashNoMenu() = assertEquals(emptyList<String>(), slashMatches("hello"))
    @Test fun bareSlashListsEverything() = assertEquals(7, slashMatches("/").size)
    @Test fun prefixFiltersCaseInsensitively() = assertEquals(listOf("letter"), slashMatches("/LE"))
    @Test fun noMatchHidesMenu() = assertEquals(emptyList<String>(), slashMatches("/zzz"))
}
