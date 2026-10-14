package app.web.oneonone.data.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import app.web.oneonone.R
import app.web.oneonone.data.media.orientLegacy
import app.web.oneonone.data.media.readBounded
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.max

/** Decode, orient and bound memory before inference. Call on an IO worker. */
internal fun decodeStickerPhoto(context: Context, uri: Uri): Bitmap {
    require(uri.scheme == "content") { "Choose a photo from your gallery." }
    val bytes = checkNotNull(context.contentResolver.openInputStream(uri)) { "Couldn't open that photo." }
        .use { readBounded(it, 10 * 1024 * 1024) }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 1..20_000 && bounds.outHeight in 1..20_000) { "Couldn't read that photo." }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
    val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
    })) { "Couldn't read that photo." }
    val oriented = orientLegacy(decoded, bytes)
    if (max(oriented.width, oriented.height) <= 1024) return oriented
    val (w, h) = stickerDimensions(oriented.width, oriented.height, 1024)
    return oriented.scale(w, h).also { if (it !== oriented) oriented.recycle() }
}

/** Bundled CPU model; no download, service, network client or credential is used. */
internal fun cutOutSticker(context: Context, original: Bitmap): Bitmap? {
    val options = ImageSegmenter.ImageSegmenterOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("stickers/deeplab_v3.tflite").build())
        .setOutputCategoryMask(false).setOutputConfidenceMasks(true).build()
    return ImageSegmenter.createFromOptions(context, options).use { segmenter ->
        // MPImage.close recycles its bitmap; the UI must keep Original available.
        BitmapImageBuilder(original.copy(Bitmap.Config.ARGB_8888, false)).build().use input@ { input ->
            val masks = segmenter.segment(input).confidenceMasks().orElseThrow { IllegalStateException("No subject found.") }
            try {
                val background = masks.first()
                val confidence = ByteBufferExtractor.extract(background).order(ByteOrder.nativeOrder()).asFloatBuffer()
                val pixels = IntArray(background.width * background.height)
                var subjectPixels = 0
                for (i in pixels.indices) {
                    val value = confidence.get(i)
                    if (value.isFinite() && value < .5f) subjectPixels++
                    pixels[i] = (foregroundAlpha(value) shl 24) or 0x00FFFFFF
                }
                if (subjectPixels < max(16, pixels.size / 200)) return@input null
                val mask = Bitmap.createBitmap(pixels, background.width, background.height, Bitmap.Config.ARGB_8888)
                try {
                    val result = original.copy(Bitmap.Config.ARGB_8888, true)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                    Canvas(result).drawBitmap(mask, null, RectF(0f, 0f, result.width.toFloat(), result.height.toFloat()), paint)
                    result
                } finally { mask.recycle() }
            } finally { masks.forEach { it.close() } }
        }
    }.also { check(!original.isRecycled) { "Couldn't keep the original photo." } }
}

internal fun renderSticker(source: Bitmap, crop: StickerCrop): Bitmap {
    val placement = stickerPlacement(source.width, source.height, crop)
    val result = createBitmap(STICKER_PX, STICKER_PX)
    Canvas(result).drawBitmap(source, null, RectF(
        placement.left * STICKER_PX, placement.top * STICKER_PX,
        (placement.left + placement.width) * STICKER_PX, (placement.top + placement.height) * STICKER_PX,
    ), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return result
}

@Suppress("DEPRECATION") // WEBP_LOSSY is API 30+; WEBP retains alpha on API 26–29.
internal fun stickerWebP(bitmap: Bitmap): ByteArray = encodeStickerWebP { quality ->
    ByteArrayOutputStream().use { output ->
        check(bitmap.compress(if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP, quality, output)) {
            "Couldn't encode that sticker."
        }
        output.toByteArray()
    }
}

internal fun builtInStickers(context: Context): List<StickerAsset> = listOf(
    Triple("love", "A little love", R.drawable.sticker_love),
    Triple("shine", "You shine", R.drawable.sticker_shine),
    Triple("hello", "Hello there", R.drawable.sticker_hello),
    Triple("cat", "Cozy cat", R.drawable.sticker_cat),
    Triple("grow", "Growing together", R.drawable.sticker_grow),
    Triple("rest", "Rest well", R.drawable.sticker_rest),
).map { (id, title, drawable) ->
    val bitmap = createBitmap(STICKER_PX, STICKER_PX)
    try {
        checkNotNull(ResourcesCompat.getDrawable(context.resources, drawable, context.theme)).apply { setBounds(0, 0, STICKER_PX, STICKER_PX); draw(Canvas(bitmap)) }
        StickerAsset(StickerEntry("signal-$id", 0, title = title, pack = "Little signals"), stickerWebP(bitmap))
    } finally { bitmap.recycle() }
}
