package app.web.oneonone.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

private const val PHOTO_MAX_PX = 512

/**
 * The photo you chose for the other person. Device-only by design: it never leaves this phone, so only
 * you see it (no backend involved). One file per (you, connection); files of other connections are removed.
 */
internal object PartnerPhoto {
    private fun dir(context: Context) = File(context.filesDir, "partner_photos")

    fun file(context: Context, owner: String, connection: String) = File(dir(context), "${owner}_$connection.jpg")

    /** The current photo, or null. Drops leftovers from ended connections or other accounts. Call off the main thread. */
    fun current(context: Context, owner: String, connection: String): File? {
        val keep = file(context, owner, connection)
        dir(context).listFiles()?.filter { it != keep }?.forEach { it.delete() }
        return keep.takeIf { it.isFile && it.length() > 0 }
    }

    /** Decode [uri] downsampled to ≤512px and store it as JPEG. Throws when the image can't be read. Call off the main thread. */
    fun save(context: Context, owner: String, connection: String, uri: Uri) {
        val bytes = downscaledJpeg(context, uri)
        val target = file(context, owner, connection).apply { parentFile?.mkdirs() }
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeBytes(bytes)
            check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) { "Couldn't save that photo." }
        } finally { temp.delete() }
    }

    fun remove(context: Context, owner: String, connection: String) { file(context, owner, connection).delete() }
}

/** [uri] decoded downsampled to ~512px and re-encoded as JPEG (drops EXIF/GPS). Throws when unreadable. Off the main thread. */
internal fun downscaledJpeg(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Couldn't read that photo." }
    val sample = sampleSize(bounds.outWidth, bounds.outHeight, PHOTO_MAX_PX)
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: error("Couldn't read that photo.")
    // Camera photos are stored sideways with an EXIF rotation; apply it before EXIF is dropped.
    val degrees = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } }.getOrNull() ?: 0
    val bitmap = if (degrees == 0) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
        Matrix().apply { postRotate(degrees.toFloat()) }, true).also { if (it !== decoded) decoded.recycle() }
    try {
        val out = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) { "Couldn't save that photo." }
        return out.toByteArray()
    } finally { bitmap.recycle() }
}

/** Largest power-of-two sample that keeps the longer side at or above [maxPx] (BitmapFactory.inSampleSize). */
internal fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxPx) sample *= 2
    return sample
}
