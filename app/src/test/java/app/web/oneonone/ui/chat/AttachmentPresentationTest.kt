package app.web.oneonone.ui.chat

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

class AttachmentPresentationTest {
    @Test fun dateLabelsUseLocalCalendarAndSevenDayBoundary() {
        val today = LocalDate.of(2026, 10, 10)
        fun label(value: String, locale: Locale = Locale.US, zone: ZoneId = ZoneId.of("UTC")) = dayLabel(value, today, zone, locale)
        assertEquals("Today", label("2026-10-10T00:00:00Z"))
        assertEquals("Yesterday", label("2026-10-09T00:00:00Z"))
        assertEquals("Thursday", label("2026-10-08T00:00:00Z"))
        assertEquals("Sunday", label("2026-10-04T00:00:00Z"))
        assertEquals("3 Oct 2026", label("2026-10-03T00:00:00Z"))
        assertEquals("11 Oct 2026", label("2026-10-11T00:00:00Z"))
        assertEquals("jeudi", label("2026-10-08T00:00:00Z", Locale.FRANCE))
        assertEquals("3 oct. 2026", label("2026-10-03T00:00:00Z", Locale.FRANCE))
        assertEquals("Today", label("2026-10-09T22:00:00Z", zone = ZoneId.of("Asia/Kolkata")))
        assertEquals("Yesterday", label("2026-10-10T02:00:00Z", zone = ZoneId.of("America/New_York")))
        assertEquals("Yesterday", dayLabel("2025-12-31T23:59:59Z", LocalDate.of(2026, 1, 1), ZoneId.of("UTC"), Locale.US))
    }

    @Test fun voiceTimeAndProgressHandleZeroPausedAndCompletionPositions() {
        assertEquals("0:08", playbackTime(8_999))
        assertEquals("1:05", playbackTime(65_000))
        assertEquals("60:00", playbackTime(3_600_000))
        assertEquals("0:00", playbackTime(-1))
        assertEquals(0f, playbackProgress(100, 0))
        assertEquals(.5f, playbackProgress(4_000, 8_000))
        assertEquals(1f, playbackProgress(9_000, 8_000))
        assertEquals(0f, playbackProgress(-1, 8_000))
    }

    @Test fun fileTypesAndSizesAreReadableAndLocaleAware() {
        mapOf("application/pdf" to "PDF", "text/plain" to "TXT", "text/csv" to "CSV",
            "application/msword" to "DOC", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "DOC",
            "application/vnd.ms-excel" to "XLS", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "XLS",
            "application/vnd.ms-powerpoint" to "PPT", "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "PPT")
            .forEach { (mime, type) -> assertEquals(type, attachmentType(mime)) }
        assertEquals("FILE", attachmentType("unknown"))
        assertEquals("999 B", attachmentSize(999.0, Locale.US))
        assertEquals("1 kB", attachmentSize(1_000.0, Locale.US))
        assertEquals("1.2 MB", attachmentSize(1_200_000.0, Locale.US))
        assertEquals("1,2 MB", attachmentSize(1_200_000.0, Locale.FRANCE))
        assertEquals("0 B", attachmentSize(Double.NaN, Locale.US))
    }

    @Test fun documentTextReadsUtf8AndCapsPreviewWithoutReadingTheWholeFile() {
        val source = "\uFEFFHello, café\nname,value\nA,1"
        assertEquals(DocumentText(source.removePrefix("\uFEFF"), false), readDocumentText(ByteArrayInputStream(source.toByteArray())))
        assertEquals(DocumentText("", false), readDocumentText(ByteArrayInputStream(byteArrayOf())))
        val exact = ByteArrayInputStream(ByteArray(1_048_576) { 'x'.code.toByte() })
        assertFalse(readDocumentText(exact).truncated)
        val large = ByteArrayInputStream(ByteArray(1_048_600) { 'x'.code.toByte() })
        val result = readDocumentText(large)
        assertTrue(result.truncated)
        assertEquals(1_048_576, result.text.length)
        assertEquals(23, large.available())
    }

    @Test fun pdfRasterSizeKeepsAspectRatioAndRejectsUnboundedPages() {
        assertEquals(1_528, pdfBitmapHeight(1_080, 595, 842))
        assertEquals(764, pdfBitmapHeight(1_080, 842, 596))
        listOf(Triple(0, 595, 842), Triple(1_080, 0, 842), Triple(1_080, 1, Int.MAX_VALUE)).forEach { (width, pageWidth, height) ->
            assertTrue(runCatching { pdfBitmapHeight(width, pageWidth, height) }.isFailure)
        }
    }

    @Test fun textRowsAndPhotoPanningStayBounded() {
        assertEquals(listOf("a", "", "b"), documentLines("a\n\nb"))
        val longLine = "x".repeat(10_000)
        val rows = documentLines(longLine)
        assertTrue(rows.all { it.length <= 4_096 })
        assertEquals(longLine, rows.joinToString(""))
        assertEquals(Offset.Zero, photoPan(Offset(1_000f, 1_000f), 1f, Size(400f, 800f), Size(800f, 400f)))
        assertEquals(Offset(200f, 0f), photoPan(Offset(1_000f, 1_000f), 2f, Size(400f, 800f), Size(800f, 400f)))
        assertEquals(Offset.Zero, photoPan(Offset(1f, 1f), 2f, Size.Zero, Size.Zero))
    }
}
