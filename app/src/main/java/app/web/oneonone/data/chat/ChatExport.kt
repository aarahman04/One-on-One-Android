package app.web.oneonone.data.chat

import app.web.oneonone.data.model.*
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
private val exportJson = Json { prettyPrint = true; encodeDefaults = true }

internal fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** "October 19, 2026" in the device zone; blank when createdAt is not an instant. */
internal fun letterDate(message: ChatMessage, zone: ZoneId = ZoneId.systemDefault()): String = runCatching {
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()).format(Instant.parse(message.createdAt).atZone(zone))
}.getOrDefault("")

/** The letter as white A4 pages in black handwriting; long letters continue onto further pages. */
fun writeLetterPdf(message: ChatMessage, handwriting: Typeface, output: OutputStream) {
    val pageWidth = 595; val pageHeight = 842; val margin = 64f // A4 in PostScript points
    val width = (pageWidth - 2 * margin).toInt()
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = handwriting; textSize = 20f; color = Color.BLACK }
    fun block(text: String, align: Layout.Alignment, indent: Int = 0) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width - indent).setAlignment(align).setLineSpacing(8f, 1f).build() to indent
    val blocks = listOf(block(letterDate(message), Layout.Alignment.ALIGN_OPPOSITE), block("Dear ${message.payload.text("to")},", Layout.Alignment.ALIGN_NORMAL),
        block(message.content, Layout.Alignment.ALIGN_NORMAL, 28), block("With love,\n${message.payload.text("from")}", Layout.Alignment.ALIGN_OPPOSITE))
    val document = PdfDocument()
    try {
        var number = 1
        fun start() = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number++).create()).also { it.canvas.drawColor(Color.WHITE) }
        var page = start()
        var y = margin
        blocks.forEachIndexed { index, (layout, indent) ->
            if (index > 0) y += 18f
            // The signature drops to the foot of the (last) page, like the on-screen letter.
            if (index == blocks.lastIndex) y = maxOf(y, pageHeight - margin - layout.height)
            // Line by line, so a long letter breaks cleanly between pages.
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line); val bottom = layout.getLineBottom(line)
                if (y + bottom - top > pageHeight - margin) { document.finishPage(page); page = start(); y = margin }
                page.canvas.save()
                page.canvas.translate(margin + indent, y - top)
                page.canvas.clipRect(0, top, layout.width, bottom)
                layout.draw(page.canvas)
                page.canvas.restore()
                y += bottom - top
            }
        }
        document.finishPage(page)
        document.writeTo(output)
    } finally { document.close() }
}

fun exportChat(messages: List<ChatMessage>, owner: String, other: String, format: String): String {
    fun author(m: ChatMessage) = if (m.senderId == owner) "You" else other
    fun body(m: ChatMessage) = if (m.type == "text" || m.type == "letter") m.content else
        "[${m.type}] ${m.content.ifBlank { messageSummary(m) }}\n${m.payload ?: ""}"
    return when (format) {
        "json" -> exportJson.encodeToString(messages)
        "txt" -> messages.joinToString("\n\n") { "[${it.createdAt}] ${author(it)}: ${body(it)}" }
        "html" -> "<!doctype html><html><head><meta charset=\"utf-8\"><title>One on One conversation</title></head><body>" +
            messages.joinToString("") { "<article><p><b>${htmlEscape(author(it))}</b> · ${htmlEscape(it.createdAt)}</p><pre style=\"white-space:pre-wrap\">${htmlEscape(body(it))}</pre></article>" } + "</body></html>"
        else -> error("Unsupported export format.")
    }
}
