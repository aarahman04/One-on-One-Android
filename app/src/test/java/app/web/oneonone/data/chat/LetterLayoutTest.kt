package app.web.oneonone.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterLayoutTest {
    private val content = 714f // A4 842 - 2 * 64

    // 28-point lines, 100-point header, 40-point signature; the page holds (714 - 100) / 28 = 21 body lines alone.
    private fun metrics(lines: Int, size: Float = 20f) = (size / 20f).let { k -> LetterMetrics(100f * k, List(lines) { 28f * k }, 40f * k) }

    private fun List<LetterPage>.assertSound(lines: Int) {
        assertEquals(lines, sumOf { it.to - it.from })
        assertEquals(1, count { it.signature }); assertTrue(last().signature)
        // The signature is never alone: its page keeps body lines (or the whole letter is that page).
        assertTrue(last().to - last().from >= minOf(SignatureKeepLines, lines))
    }

    @Test fun shortLetterFitsOnePage() {
        val pages = planLetterPages(metrics(10), content)
        assertEquals(listOf(LetterPage(0, 10, true)), pages)
    }

    @Test fun overflowBySignatureShrinksToOnePage() {
        // 20 lines fill the page (100 + 560 = 660) but the signature (18 + 40) does not fit.
        assertEquals(2, planLetterPages(metrics(20), content).size)
        val (size, pages) = chooseLetterSize(content) { metrics(20, it) }
        assertEquals(1, pages.size); assertTrue(size < 20f)
    }

    @Test fun overflowTooBigStaysFullSizeAndPaginates() {
        val (size, pages) = chooseLetterSize(content) { metrics(40, it) }
        assertEquals(20f, size); assertTrue(pages.size > 1); pages.assertSound(40)
    }

    @Test fun signatureMovesWithLastThreeLines() {
        val pages = planLetterPages(metrics(20), content)
        assertEquals(listOf(LetterPage(0, 17, false), LetterPage(17, 20, true)), pages)
    }

    @Test fun longLetterNeverStrandsSignature() {
        for (lines in 1..120) planLetterPages(metrics(lines), content).assertSound(lines)
    }

    @Test fun lastPageWithOneLineBorrowsFromThePageBefore() {
        // 21 lines fill page 1; line 22 starts page 2 and the signature needs company.
        val pages = planLetterPages(metrics(22), content)
        assertEquals(LetterPage(19, 22, true), pages.last()); pages.assertSound(22)
    }
}
