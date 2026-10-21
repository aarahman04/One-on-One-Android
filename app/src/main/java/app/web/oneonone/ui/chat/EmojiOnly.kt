package app.web.oneonone.ui.chat

/**
 * How many emoji (1..[max]) [text] holds when it is nothing but emoji and spaces, else 0. Such messages draw large
 * and without a bubble, like WhatsApp. Counts whole graphemes: ZWJ families, skin tones, flags, keycaps, tag flags.
 * Written by hand because android.icu is unavailable in JVM unit tests and java.text.BreakIterator splits ZWJ
 * sequences on older runtimes.
 */
internal fun emojiOnlyCount(text: String, max: Int = 3): Int {
    var i = 0
    var count = 0
    while (i < text.length) {
        val c = text.codePointAt(i)
        if (Character.isWhitespace(c)) { i += Character.charCount(c); continue }
        val end = emojiCluster(text, i)
        if (end == i || ++count > max) return 0
        i = end
    }
    return count
}

/** End of the emoji grapheme at [start] (element, then any ZWJ-joined elements), or [start] when none starts there. */
private fun emojiCluster(s: String, start: Int): Int {
    var end = emojiElement(s, start)
    if (end == start) return start
    while (end < s.length && s.codePointAt(end) == ZWJ) {
        val next = emojiElement(s, end + 1)
        if (next == end + 1) return start // a ZWJ joining nothing is not an emoji
        end = next
    }
    return end
}

private const val ZWJ = 0x200D
private const val VS16 = 0xFE0F
private const val KEYCAP = 0x20E3

private fun emojiElement(s: String, start: Int): Int {
    if (start >= s.length) return start
    fun at(index: Int) = if (index < s.length) s.codePointAt(index) else -1
    val c = at(start)
    var i = start + Character.charCount(c)
    when {
        // Flags: two regional indicators.
        c in 0x1F1E6..0x1F1FF -> return if (at(i) in 0x1F1E6..0x1F1FF) i + 2 else i
        // Keycaps: 0-9 # * then an optional VS16 and the combining keycap; a bare digit is text.
        c in '0'.code..'9'.code || c == '#'.code || c == '*'.code -> {
            if (at(i) == VS16) i++
            return if (at(i) == KEYCAP) i + 1 else start
        }
        pictographic(c) -> Unit
        // A text-default symbol asked to draw as emoji (©️ ™️ ↔️ ▶️).
        c >= 0xA9 && at(i) == VS16 && !Character.isLetterOrDigit(c) -> Unit
        else -> return start
    }
    // Variation selectors, skin tones and tag sequences (🏴󠁧󠁢󠁳󠁣󠁴󠁿) stay part of the emoji.
    while (true) {
        val e = at(i)
        if (e == VS16 || e == 0xFE0E || e in 0x1F3FB..0x1F3FF || e in 0xE0020..0xE007F || e == KEYCAP) i += Character.charCount(e) else break
    }
    return i
}

// shortcut: whole blocks (so a few non-emoji symbols such as ✓ also count); use android.icu's EMOJI property if that ever matters.
private fun pictographic(c: Int): Boolean = c in 0x1F000..0x1FAFF || c in 0x2600..0x27BF || c in 0x23E9..0x23F3 || c in 0x23F8..0x23FA ||
    c in intArrayOf(0x231A, 0x231B, 0x2328, 0x23CF, 0x2934, 0x2935, 0x25FD, 0x25FE, 0x2B05, 0x2B06, 0x2B07, 0x2B1B, 0x2B1C, 0x2B50, 0x2B55,
        0x3030, 0x303D, 0x3297, 0x3299)
