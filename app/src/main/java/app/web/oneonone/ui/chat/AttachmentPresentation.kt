package app.web.oneonone.ui.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

internal fun playbackTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

internal fun playbackProgress(position: Int, duration: Int): Float =
    if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)

internal fun photoPan(pan: Offset, scale: Float, viewport: Size, image: Size): Offset {
    if (viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
    val fit = if (image.width > 0 && image.height > 0) minOf(viewport.width / image.width, viewport.height / image.height) else 1f
    val width = if (image.width > 0) image.width * fit else viewport.width
    val height = if (image.height > 0) image.height * fit else viewport.height
    val maxX = ((width * scale - viewport.width) / 2).coerceAtLeast(0f)
    val maxY = ((height * scale - viewport.height) / 2).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

internal val PhotoMaxWidth = 260.dp
internal val PhotoMaxHeight = 340.dp
internal val GifMaxSide = 200.dp

/**
 * The box a photo or GIF takes in its bubble, from the payload's pixel size, so the placeholder and the loaded image
 * are the same size (no jump). Unknown sizes are square. GIFs: 200dp long side.
 */
internal fun mediaSize(width: Double?, height: Double?, gif: Boolean, maxWidth: Dp): DpSize {
    val ratio = if (width != null && height != null && width.isFinite() && height.isFinite() && width > 0 && height > 0) (width / height).toFloat() else 1f
    if (gif) {
        val side = minOf(GifMaxSide, maxWidth)
        return if (ratio >= 1f) DpSize(side, side / ratio) else DpSize(side * ratio, side)
    }
    val w = minOf(PhotoMaxWidth, maxWidth)
    return if (w / ratio <= PhotoMaxHeight) DpSize(w, w / ratio) else DpSize(PhotoMaxHeight * ratio, PhotoMaxHeight)
}

/** Offscreen rows are estimated from visible row heights so the arrow appears about 1.5 viewports from the end. */
internal fun showScrollToLatest(viewportHeight: Int, visibleBottom: Int, viewportEnd: Int, itemsAfter: Int, averageRowHeight: Double): Boolean =
    viewportHeight > 0 && itemsAfter * averageRowHeight + (visibleBottom - viewportEnd).coerceAtLeast(0) > viewportHeight * 1.5

internal fun attachmentType(mime: String): String = when (mime.substringBefore(';').trim().lowercase()) {
    "application/pdf" -> "PDF"
    "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOC"
    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLS"
    "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPT"
    "text/plain" -> "TXT"
    "text/csv" -> "CSV"
    else -> "FILE"
}

internal fun attachmentSize(bytes: Double, locale: Locale = Locale.getDefault()): String {
    val size = if (bytes.isFinite()) bytes.coerceAtLeast(0.0) else 0.0
    val unit = when { size >= 1_000_000 -> 1_000_000; size >= 1_000 -> 1_000; else -> 1 }
    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (unit == 1) 0 else 1 }
    return "${format.format(size / unit)} ${when (unit) { 1_000_000 -> "MB"; 1_000 -> "kB"; else -> "B" }}"
}
