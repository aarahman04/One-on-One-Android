package app.web.oneonone.data.stickers

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val STICKER_BRUSH_STROKES = 32
internal const val STICKER_BRUSH_POINTS = 1024
internal data class StickerBrushPoint(val x: Float, val y: Float)
internal data class StickerBrushStroke(val points: List<StickerBrushPoint>, val radius: Float, val restore: Boolean)

internal fun stickerBrushScale(width: Int, height: Int, frameWidth: Float, frameHeight: Float): Float {
    require(width > 0 && height > 0 && frameWidth.isFinite() && frameHeight.isFinite() && frameWidth > 0 && frameHeight > 0)
    return minOf(frameWidth / width, frameHeight / height)
}

/** Keep an outside cursor outside: clipping the brush, rather than clamping its centre, avoids erasing along an edge. */
internal fun stickerBrushPoint(width: Int, height: Int, frameWidth: Float, frameHeight: Float,
    touchX: Float, touchY: Float, distance: Float): StickerBrushPoint {
    require(touchX.isFinite() && touchY.isFinite() && distance.isFinite() && distance >= 0)
    val scale = stickerBrushScale(width, height, frameWidth, frameHeight)
    return StickerBrushPoint((touchX - (frameWidth - width * scale) / 2) / scale,
        (touchY - distance - (frameHeight - height * scale) / 2) / scale)
}

internal fun appendStickerBrushPoint(points: List<StickerBrushPoint>, point: StickerBrushPoint): List<StickerBrushPoint> {
    require(point.x.isFinite() && point.y.isFinite())
    if (points.lastOrNull() == point) return points
    // shortcut: decimate exceptionally long strokes to bound memory; use a simplified vector path if detail loss is reported.
    return (if (points.size >= STICKER_BRUSH_POINTS) points.filterIndexed { index, _ -> index % 2 == 0 } else points) + point
}

internal fun stickerBrushPath(stroke: StickerBrushStroke): Path {
    require(stroke.points.isNotEmpty() && stroke.points.size <= STICKER_BRUSH_POINTS && stroke.radius.isFinite() && stroke.radius > 0)
    val result = Path()
    val first = stroke.points.first()
    if (stroke.points.size == 1) result.addCircle(first.x, first.y, stroke.radius, Path.Direction.CW)
    else {
        val line = Path().apply {
            moveTo(first.x, first.y)
            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke.radius * 2
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }.getFillPath(line, result)
    }
    return result
}

/** Only called on a worker; restore samples the original photo, including its original alpha. */
internal suspend fun brushSticker(base: Bitmap, original: Bitmap, strokes: List<StickerBrushStroke>): Bitmap {
    require(base.width == original.width && base.height == original.height && strokes.size <= STICKER_BRUSH_STROKES)
    val result = checkNotNull(base.copy(Bitmap.Config.ARGB_8888, true)) { "Couldn't edit that photo." }
    try {
        val canvas = Canvas(result)
        val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val restore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(original, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        for (stroke in strokes) {
            currentCoroutineContext().ensureActive()
            canvas.drawPath(stickerBrushPath(stroke), if (stroke.restore) restore else erase)
        }
        return result
    } catch (failure: Throwable) { result.recycle(); throw failure }
}
