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

/** Retry without going below the requested quality floor or silently saving an oversized sticker. */
internal fun encodeStickerWebP(encode: (Int) -> ByteArray): ByteArray {
    for (quality in listOf(100, 95, 90, 85, 80)) {
        val bytes = encode(quality)
        check(bytes.isNotEmpty()) { "Couldn't encode that sticker." }
        if (bytes.size <= STICKER_MAX_BYTES) return bytes
    }
    error("That crop is too detailed to fit 100 KB. Try a tighter crop.")
}

internal fun foregroundAlpha(background: Float): Int {
    if (!background.isFinite()) return 0
    return (((1f - background - .2f) / .6f).coerceIn(0f, 1f) * 255).roundToInt()
}
