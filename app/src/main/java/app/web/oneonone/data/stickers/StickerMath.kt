package app.web.oneonone.data.stickers

import java.io.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

const val STICKER_PX = 512
const val STICKER_MAX_BYTES = 100_000

/** Pan is measured in square-frame widths, so rotation and export use the same crop. */
data class StickerCrop(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) : Serializable
data class StickerPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

internal fun stickerDimensions(width: Int, height: Int, maximum: Int = STICKER_PX): Pair<Int, Int> {
    require(width > 0 && height > 0 && maximum > 0)
    val scale = maximum.toFloat() / max(width, height)
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

fun boundedStickerCrop(width: Int, height: Int, crop: StickerCrop): StickerCrop {
    require(width > 0 && height > 0 && crop.zoom.isFinite() && crop.x.isFinite() && crop.y.isFinite())
    val zoom = crop.zoom.coerceIn(1f, 6f)
    val longest = max(width, height).toFloat()
    val limitX = max(0f, (width / longest * zoom - 1f) / 2)
    val limitY = max(0f, (height / longest * zoom - 1f) / 2)
    return StickerCrop(zoom,
        if (limitX == 0f) 0f else crop.x.coerceIn(-limitX, limitX),
        if (limitY == 0f) 0f else crop.y.coerceIn(-limitY, limitY))
}

fun stickerPlacement(width: Int, height: Int, crop: StickerCrop): StickerPlacement {
    val bounded = boundedStickerCrop(width, height, crop)
    val longest = max(width, height).toFloat()
    val w = width / longest * bounded.zoom
    val h = height / longest * bounded.zoom
    return StickerPlacement((1 - w) / 2 + bounded.x, (1 - h) / 2 + bounded.y, w, h)
}

fun transformStickerCrop(
    width: Int, height: Int, crop: StickerCrop, zoomChange: Float,
    panX: Float, panY: Float, focusX: Float = 0f, focusY: Float = 0f,
): StickerCrop {
    require(zoomChange.isFinite() && zoomChange > 0 && panX.isFinite() && panY.isFinite() && focusX.isFinite() && focusY.isFinite())
    val before = boundedStickerCrop(width, height, crop)
    val zoom = (before.zoom * zoomChange).coerceIn(1f, 6f)
    val ratio = zoom / before.zoom
    return boundedStickerCrop(width, height, StickerCrop(zoom,
        focusX - (focusX - before.x) * ratio + panX,
        focusY - (focusY - before.y) * ratio + panY))
}

/** Free crop of the photo as 0..1 fractions; the cropped part is then placed in the square frame by [StickerCrop]. */
data class StickerTrim(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) : Serializable

private const val TRIM_MIN = .1f

fun boundedStickerTrim(trim: StickerTrim): StickerTrim {
    require(listOf(trim.left, trim.top, trim.right, trim.bottom).all { it.isFinite() })
    val left = trim.left.coerceIn(0f, 1f - TRIM_MIN)
    val top = trim.top.coerceIn(0f, 1f - TRIM_MIN)
    return StickerTrim(left, top, trim.right.coerceIn(left + TRIM_MIN, 1f), trim.bottom.coerceIn(top + TRIM_MIN, 1f))
}

/** [handle]: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right corner; anything else moves the whole box. */
fun dragStickerTrim(trim: StickerTrim, handle: Int, du: Float, dv: Float): StickerTrim {
    val t = boundedStickerTrim(trim)
    fun left() = (t.left + du).coerceIn(0f, t.right - TRIM_MIN)
    fun right() = (t.right + du).coerceIn(t.left + TRIM_MIN, 1f)
    fun top() = (t.top + dv).coerceIn(0f, t.bottom - TRIM_MIN)
    fun bottom() = (t.bottom + dv).coerceIn(t.top + TRIM_MIN, 1f)
    return when (handle) {
        0 -> t.copy(left = left(), top = top())
        1 -> t.copy(right = right(), top = top())
        2 -> t.copy(left = left(), bottom = bottom())
        3 -> t.copy(right = right(), bottom = bottom())
        else -> {
            val dx = du.coerceIn(-t.left, 1f - t.right); val dy = dv.coerceIn(-t.top, 1f - t.bottom)
            StickerTrim(t.left + dx, t.top + dy, t.right + dx, t.bottom + dy)
        }
    }
}

/** Pixel [x, y, width, height] of [trim] inside a width×height bitmap; never empty or out of bounds. */
fun stickerTrimPixels(width: Int, height: Int, trim: StickerTrim): IntArray {
    val t = boundedStickerTrim(trim)
    val x = (t.left * width).toInt().coerceIn(0, width - 1)
    val y = (t.top * height).toInt().coerceIn(0, height - 1)
    val right = (t.right * width).roundToInt().coerceIn(x + 1, width)
    val bottom = (t.bottom * height).roundToInt().coerceIn(y + 1, height)
    return intArrayOf(x, y, right - x, bottom - y)
}

/** Retry without going below the requested quality floor or silently saving an oversized sticker. */
internal fun encodeStickerWebP(encode: (Int) -> ByteArray): ByteArray {
    for (quality in listOf(100, 95, 90, 85, 80)) {
        val bytes = encode(quality)
        check(bytes.isNotEmpty()) { "Couldn't encode that sticker." }
        if (bytes.size <= STICKER_MAX_BYTES) return bytes
    }
    error("That crop is too detailed to fit 100 KB. Try a tighter crop.")
}

internal fun biRefNetAlpha(logit: Float): Int {
    if (!logit.isFinite()) return 0
    return (255.0 / (1.0 + kotlin.math.exp(-logit.toDouble()))).roundToInt()
}

internal fun biRefNetRgb(pixels: IntArray): FloatArray {
    val result = FloatArray(pixels.size * 3)
    val means = floatArrayOf(.485f, .456f, .406f)
    val deviations = floatArrayOf(.229f, .224f, .225f)
    for (channel in 0..2) for (i in pixels.indices) {
        val value = (pixels[i] ushr (16 - channel * 8)) and 255
        result[channel * pixels.size + i] = (value / 255f - means[channel]) / deviations[channel]
    }
    return result
}
