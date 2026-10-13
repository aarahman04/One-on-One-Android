package app.web.oneonone.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLinksTest {
    private fun targets(text: String) = messageLinks(text).map { (range, url) -> text.substring(range) to url }

    @Test fun schemeLinksKeepTheirUrlAndDropTrailingPunctuation() =
        assertEquals(listOf("https://a.com/x?y=1" to "https://a.com/x?y=1"), targets("see https://a.com/x?y=1."))

    @Test fun bareDomainsGetHttps() = assertEquals(
        listOf("www.example.org" to "https://www.example.org", "google.com/maps" to "https://google.com/maps"),
        targets("try www.example.org or google.com/maps"))

    @Test fun emailDomainIsNotALink() = assertEquals(emptyList<Pair<String, String>>(), targets("mail me at a@b.com"))

    @Test fun phoneNumbersBecomeTelLinks() = assertEquals(
        listOf("+91 98765-43210" to "tel:+919876543210", "(022) 2345 6789" to "tel:02223456789"),
        targets("call +91 98765-43210 or (022) 2345 6789"))

    @Test fun shortNumbersDatesAndUrlDigitsAreIgnored() =
        assertEquals(listOf("https://x.io/1234567" to "https://x.io/1234567"), targets("on 2026-10-13 bring 12 of https://x.io/1234567"))
}
