package app.web.oneonone.data.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import app.web.oneonone.R
import app.web.oneonone.data.media.orientLegacy
import app.web.oneonone.data.media.readBounded
import java.io.ByteArrayOutputStream
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

internal fun trimSticker(source: Bitmap, trim: StickerTrim): Bitmap {
    if (trim == StickerTrim()) return source
    val (x, y, w, h) = stickerTrimPixels(source.width, source.height, trim)
    return Bitmap.createBitmap(source, x, y, w, h)
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
    Triple("signal-love", "A little love", R.drawable.sticker_love),
    Triple("signal-shine", "You shine", R.drawable.sticker_shine),
    Triple("signal-hello", "Hello there", R.drawable.sticker_hello),
    Triple("signal-cat", "Cozy cat", R.drawable.sticker_cat),
    Triple("signal-grow", "Growing together", R.drawable.sticker_grow),
    Triple("signal-rest", "Rest well", R.drawable.sticker_rest),
    Triple("cat-happy", "Happy cat", R.drawable.sticker_cat_happy),
    Triple("cat-love", "Love you", R.drawable.sticker_cat_love),
    Triple("cat-sad", "Miss you", R.drawable.sticker_cat_sad),
    Triple("cat-grumpy", "Grumpy cat", R.drawable.sticker_cat_grumpy),
    Triple("cat-sleepy", "Sleepy cat", R.drawable.sticker_cat_sleepy),
).let { all -> all.mapIndexed { index, (id, title, drawable) ->
    val bitmap = createBitmap(STICKER_PX, STICKER_PX)
    try {
        checkNotNull(ResourcesCompat.getDrawable(context.resources, drawable, context.theme)).apply { setBounds(0, 0, STICKER_PX, STICKER_PX); draw(Canvas(bitmap)) }
        // Sections list newest first, so a descending createdAt keeps the order written above.
        StickerAsset(StickerEntry(id, (all.size - index).toLong(), title = title, pack = if (id.startsWith("cat-")) "Cats" else "Little signals"), stickerWebP(bitmap))
    } finally { bitmap.recycle() }
} } + packStickers(context)

/** Licensed Craftwork "Open Stickers": assets/stickers/packs/<Pack>/<NN>_<name>.webp, pre-encoded 512×512. */
private fun packStickers(context: Context): List<StickerAsset> {
    val root = "stickers/packs"
    val files = context.assets.list(root).orEmpty().sorted().flatMap { pack ->
        context.assets.list("$root/$pack").orEmpty().sorted().map { pack to it }
    }
    return files.mapIndexed { index, (pack, name) ->
        val stem = name.removeSuffix(".webp")
        val title = stem.substringAfter('_').replace('_', ' ').replaceFirstChar { it.uppercase() }
        val bytes = context.assets.open("$root/$pack/$name").use { readBounded(it, STICKER_MAX_BYTES) }
        StickerAsset(StickerEntry("${pack.lowercase()}-${stem.replace('_', '-')}", (files.size - index).toLong(), title = title, pack = pack), bytes)
    }
}
