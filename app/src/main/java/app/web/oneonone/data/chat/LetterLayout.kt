package app.web.oneonone.data.chat

/** Heights (points) of one letter at one font size: date + salutation, each body line, and the "With love" block. */
internal class LetterMetrics(val header: Float, val lines: List<Float>, val signature: Float)

/** Body lines [from, to) on one page; the last page also carries the signature at its foot. */
internal data class LetterPage(val from: Int, val to: Int, val signature: Boolean)

/** Space between the last body line and the signature; the signature also keeps this many body lines with it. */
internal const val LetterGap = 18f
internal const val SignatureKeepLines = 3
internal val LetterFontSizes = listOf(20f, 18f, 17f)

/**
 * Greedy pages for [metrics] in a [content]-tall column. The signature is unbreakable and never alone: when it does not
 * fit under the last body line, the last [SignatureKeepLines] body lines move with it to a new page.
 */
internal fun planLetterPages(metrics: LetterMetrics, content: Float): List<LetterPage> {
    val lines = metrics.lines
    val pages = mutableListOf<LetterPage>()
    var from = 0; var used = metrics.header
    for (i in lines.indices) {
        if (used + lines[i] > content && i > from) { pages += LetterPage(from, i, false); from = i; used = 0f }
        used += lines[i]
    }
    val keep = minOf(SignatureKeepLines, lines.size)
    val count = lines.size - from
    if (used + LetterGap + metrics.signature <= content && (from == 0 || count >= keep)) return pages + LetterPage(from, lines.size, true)
    if (count > keep) {
        pages += LetterPage(from, lines.size - keep, false)
        return pages + LetterPage(lines.size - keep, lines.size, true)
    }
    if (from > 0 && count < keep) {
        // The last page holds fewer than the kept lines: borrow the rest from the page before it.
        val previous = pages.removeAt(pages.lastIndex)
        val start = maxOf(previous.from, lines.size - keep)
        if (start > previous.from) pages += LetterPage(previous.from, start, false)
        return pages + LetterPage(start, lines.size, true)
    }
    return pages + LetterPage(from, lines.size, true)
}

/** The largest font size whose letter fits on one page; otherwise the full size, paginated. */
internal fun chooseLetterSize(content: Float, measure: (Float) -> LetterMetrics): Pair<Float, List<LetterPage>> {
    for (size in LetterFontSizes) planLetterPages(measure(size), content).takeIf { it.size == 1 }?.let { return size to it }
    val size = LetterFontSizes.first()
    return size to planLetterPages(measure(size), content)
}
