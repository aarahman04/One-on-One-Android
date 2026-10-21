package app.web.oneonone.data.stickers

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.max

private const val MODEL_BYTES = 191_877_254L
private const val MODEL_SHA = "1cb0fb360dadd15af77c639085d77a9df67db0c64315560c3de005f676345ac2"
private const val MODEL_URL = "https://huggingface.co/studioludens/birefnet-lite-512/resolve/962e67969ec6acec9c19fab76acee4f5e22e990c/onnx/model.onnx"
private const val MODEL_SIDE = 512
private val inference = Mutex()

/** Pinned 512 export of upstream Lite weights. Photos and inference stay on the device. */
internal suspend fun cutOutSticker(context: Context, original: Bitmap, progress: suspend (String) -> Unit): Bitmap? =
    withContext(Dispatchers.IO) {
        inference.withLock {
            val job = currentCoroutineContext()
            requireBiRefNetMemory(context)
            val directory = File(context.noBackupFilesDir, "sticker-models").apply {
                check(isDirectory || mkdirs()) { "Couldn't store the cut-out model." }
            }
            val model = File(directory, "birefnet-lite-$MODEL_SHA.onnx")
            if (!verifiedBiRefNetModel(model)) {
                job.ensureActive()
                progress("Downloading cut-out model… 0% (192 MB, once)")
                // Reuse one temporary path so process death cannot accumulate partial models.
                val temporary = File(directory, "birefnet-lite.download")
                try {
                    val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                    connection.connectTimeout = 20_000; connection.readTimeout = 30_000
                    try {
                        check(connection.responseCode == 200) { "Couldn't download the cut-out model. Try again." }
                        val length = connection.contentLengthLong
                        check(length == -1L || length == MODEL_BYTES) { "Unexpected cut-out model size." }
                        connection.inputStream.use { input -> temporary.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            var percent = -1
                            while (true) {
                                job.ensureActive()
                                val count = input.read(buffer)
                                job.ensureActive()
                                if (count == -1) break
                                total += count
                                check(total <= MODEL_BYTES) { "Cut-out model download is too large." }
                                output.write(buffer, 0, count)
                                val next = (total * 100 / MODEL_BYTES).toInt()
                                if (next != percent) {
                                    percent = next
                                    progress("Downloading cut-out model… $percent% (192 MB, once)")
                                }
                            }
                        } }
                    } finally { connection.disconnect() }
                    job.ensureActive()
                    check(verifiedBiRefNetModel(temporary)) { "Cut-out model download is incomplete or invalid. Try again." }
                    check(temporary.renameTo(model)) { "Couldn't store the cut-out model." }
                } finally { temporary.delete() }
            }
            job.ensureActive()
            requireBiRefNetMemory(context)
            progress("Removing background on this device…")
            val scaled = original.scale(MODEL_SIDE, MODEL_SIDE)
            val pixels = IntArray(MODEL_SIDE * MODEL_SIDE)
            try { scaled.getPixels(pixels, 0, MODEL_SIDE, 0, 0, MODEL_SIDE, MODEL_SIDE) }
            finally { if (scaled !== original) scaled.recycle() }
            val environment = OrtEnvironment.getEnvironment()
            val mask = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                options.setCPUArenaAllocator(false)
                options.setMemoryPatternOptimization(false)
                environment.createSession(model.absolutePath, options).use { session ->
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(biRefNetRgb(pixels)),
                        longArrayOf(1, 3, MODEL_SIDE.toLong(), MODEL_SIDE.toLong())).use { input ->
                        job.ensureActive()
                        OrtSession.RunOptions().use { runOptions ->
                            val cancellation = CoroutineScope(job).launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                                try { awaitCancellation() }
                                finally { runOptions.setTerminate(true) }
                            }
                            try { session.run(mapOf("input_image" to input), runOptions).use { output ->
                            job.ensureActive()
                            val logits = (output[0] as OnnxTensor).floatBuffer
                            check(logits.remaining() == pixels.size) { "Unexpected cut-out mask." }
                            var subject = 0
                            for (i in pixels.indices) {
                                val alpha = biRefNetAlpha(logits.get(i))
                                if (alpha >= 128) subject++
                                pixels[i] = (alpha shl 24) or 0x00FFFFFF
                            }
                            if (subject < max(16, pixels.size / 200)) return@withContext null
                            Bitmap.createBitmap(pixels, MODEL_SIDE, MODEL_SIDE, Bitmap.Config.ARGB_8888)
                            } } finally { withContext(NonCancellable) { cancellation.cancelAndJoin() } }
                        }
                    }
                }
            }
            try {
                job.ensureActive()
                val result = original.copy(Bitmap.Config.ARGB_8888, true)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                Canvas(result).drawBitmap(mask, null, RectF(0f, 0f, result.width.toFloat(), result.height.toFloat()), paint)
                result
            } finally { mask.recycle() }
        }
    }

private fun requireBiRefNetMemory(context: Context) {
    val manager = context.getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    check(biRefNetMemorySufficient(memory.availMem, memory.lowMemory || manager.isLowRamDevice)) {
        "Not enough free memory for automatic cut-out. You can still use Erase and Restore."
    }
}

// A smaller emulator was killed after reaching 1.5 GB; this preflight cannot reserve memory.
internal fun biRefNetMemorySufficient(availableBytes: Long, lowMemory: Boolean): Boolean =
    !lowMemory && availableBytes >= 1_750_000_000L

internal fun verifiedBiRefNetModel(file: File): Boolean {
    if (!file.isFile || file.length() != MODEL_BYTES) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA
}
