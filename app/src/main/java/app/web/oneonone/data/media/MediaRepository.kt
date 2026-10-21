package app.web.oneonone.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.exifinterface.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import app.web.oneonone.BuildConfig
import app.web.oneonone.data.model.*
import app.web.oneonone.data.chat.archiveFiles
import app.web.oneonone.data.chat.writeConversationArchive
import app.web.oneonone.data.chat.writeLetterPdf
import android.provider.DocumentsContract
import app.web.oneonone.R
import androidx.core.content.res.ResourcesCompat
import android.graphics.Typeface
import app.web.oneonone.data.stickers.STICKER_MAX_BYTES
import app.web.oneonone.data.stickers.STICKER_PX
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.http.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.round
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class UploadedAttachment(val path: String, val mime: String, val size: Int)
@Serializable data class SignedPaths(val paths: List<String>)
@Serializable data class SignedUrls(val urls: Map<String, String>)
@Serializable data class GifResult(val id: String, val title: String = "", val previewUrl: String, val width: Int, val height: Int)
@Serializable data class GifPage(val results: List<GifResult>, val next: Int? = null)
@Serializable data class GifId(val id: String)
interface AttachmentApi {
    // GIPHY through the backend (contract §9): search/trending, then the server stores the chosen GIF as an attachment.
    @GET("api/gifs") suspend fun gifs(@Query("q") query: String, @Query("offset") offset: Int): GifPage
    @POST("api/connections/{id}/gifs") suspend fun gif(@Path("id") connection: String, @Body id: GifId): JsonObject
    @POST("api/connections/{id}/attachments") suspend fun upload(
        @Path("id") connection: String, @Query("kind") kind: String, @Body bytes: RequestBody,
    ): UploadedAttachment
    @POST("api/connections/{id}/attachments/signed") suspend fun signed(@Path("id") connection: String, @Body paths: SignedPaths): SignedUrls
}
data class VoiceClip(val file: File, val duration: Double)
interface MediaGateway {
    suspend fun upload(connection: String, kind: String, uri: Uri, duration: Double? = null): JsonObject
    suspend fun uploadSticker(connection: String, file: File): JsonObject
    suspend fun signedUrl(connection: String, path: String): String
    /** A still-valid signed URL already in memory, without a network call: lets a re-composed photo row draw at once. */
    fun cachedSignedUrl(connection: String, path: String): String? = null
    suspend fun download(connection: String, payload: JsonObject): Uri
    suspend fun location(): JsonObject
    suspend fun startRecording(onLimit: () -> Unit)
    suspend fun stopRecording(): VoiceClip?
    fun releaseRecording()
    suspend fun discardVoice(path: String?)
    suspend fun searchGifs(query: String, offset: Int): GifPage = error("Not supported.")
    /** A ready `image` payload for GIPHY gif [id], stored server-side as this connection's attachment. */
    suspend fun attachGif(connection: String, id: String): JsonObject = error("Not supported.")
    suspend fun save(uri: Uri, text: String)
    suspend fun saveLetterPdf(uri: Uri, message: ChatMessage)
    suspend fun saveAttachment(connection: String, payload: JsonObject, destination: Uri)
    /** Writes the whole conversation as a zip to [uri]; a failed or cancelled save deletes the half-written document. */
    suspend fun saveArchive(uri: Uri, connection: String, messages: List<ChatMessage>, owner: String, other: String,
                            progress: (Int) -> Unit): Unit = error("Not supported.")
    fun playbackFocus(onLost: () -> Unit): Boolean
    fun abandonPlaybackFocus()
}

internal fun isGiphyMedia(url: String): Boolean = url.toHttpUrlOrNull()?.let {
    it.isHttps && it.port == 443 && (it.host == "giphy.com" || it.host.endsWith(".giphy.com"))
} == true

internal fun readBounded(input: InputStream, maximum: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        require(count <= maximum - output.size()) { "Attachment exceeds the size limit." }
        output.write(buffer, 0, count)
    }
    require(output.size() > 0) { "Attachment is empty." }
    return output.toByteArray()
}

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AttachmentApi,
    private val json: Json,
) : MediaGateway, DictationRecorder {
    private val signing = Mutex()
    // Concurrent: cachedSignedUrl reads it from the main thread while signedUrl writes it under [signing].
    private val urls = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private val downloadClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS).build()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStarted = 0L
    private var recordingDictation = false
    private var dictationFile: File? = null
    private var focus: AudioFocusRequest? = null
    override fun playbackFocus(onLost: () -> Unit): Boolean {
        val manager = context.getSystemService(AudioManager::class.java)
        check(manager.mode == AudioManager.MODE_NORMAL) { "Finish your call before playing a voice note." }
        lateinit var request: AudioFocusRequest
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ if (focus === request && it != AudioManager.AUDIOFOCUS_GAIN) onLost() }, android.os.Handler(Looper.getMainLooper())).build()
        focus = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    override fun abandonPlaybackFocus() {
        focus?.let { context.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; focus = null
    }

    override suspend fun upload(connection: String, kind: String, uri: Uri, duration: Double?): JsonObject = withContext(Dispatchers.IO) {
        require(uri.scheme in setOf("content", "file")) { "Unsupported attachment source." }
        val sourceMime = if (kind == "voice") "audio/mp4" else context.contentResolver.getType(uri).orEmpty().lowercase()
        require(sourceMime in attachmentMimes(kind)) { "Unsupported $kind type." }
        if (kind == "voice") require(duration != null && duration.isFinite() && duration > 0 && duration <= 3_600) { "Invalid voice duration." }
        var bytes = checkNotNull(context.contentResolver.openInputStream(uri)) { "Couldn't open that attachment." }
            .use { readBounded(it, attachmentLimit(kind)) }
        var mime = sourceMime
        var width = 0; var height = 0
        if (kind == "image") {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..20_000 && bounds.outHeight in 1..20_000) { "Couldn't read that image." }
            if (sourceMime == "image/gif") {
                require(bounds.outWidth.toLong() * bounds.outHeight <= 4_000_000) { "That animated image is too large to display safely." }
                width = bounds.outWidth; height = bounds.outHeight
            } else {
                var sample = 1
                // ponytail: cap decoded photos at 4 MP for bounded memory; tiled full-resolution re-encoding if needed.
                while (bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 4_000_000) sample *= 2
                val bitmap = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(sample)
                } else {
                    val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
                    orientLegacy(decoded, bytes)
                }
                val fitted = fitPhoto(bitmap)
                try {
                    width = fitted.width; height = fitted.height
                    val encoded = ByteArrayOutputStream()
                    val png = sourceMime == "image/png"
                    check(fitted.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, encoded)) { "Couldn't encode the image." }
                    bytes = encoded.toByteArray() // Re-encoding pixels drops EXIF/GPS metadata.
                    mime = if (png) "image/png" else "image/jpeg"
                    require(bytes.size <= attachmentLimit("image")) { "That photo exceeds 10 MiB after encoding." }
                } finally { fitted.recycle() }
            }
        }
        val uploaded = api.upload(connection, kind, bytes.toRequestBody(mime.toMediaType()))
        val base = json.encodeToJsonElement(uploaded).jsonObject
        val payload = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            when (kind) {
                "image" -> { put("width", width); put("height", height) }
                "voice" -> put("duration", checkNotNull(duration))
                "file" -> put("name", displayName(uri))
            }
        }
        validateFeaturePayload(connection, kind, payload)
        payload
    }

    private fun displayName(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        return name?.trim()?.take(255)?.takeIf { it.isNotEmpty() } ?: "attachment"
    }

    /** The maker already re-encodes pixels; preserve its WebP bytes and alpha instead of converting to JPEG. */
    override suspend fun uploadSticker(connection: String, file: File): JsonObject = withContext(Dispatchers.IO) {
        require(file.canonicalFile.parentFile?.parentFile == File(context.filesDir, "stickers").canonicalFile) { "Invalid sticker file." }
        val bytes = file.inputStream().use { readBounded(it, STICKER_MAX_BYTES) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == "image/webp" && bounds.outWidth == STICKER_PX && bounds.outHeight == STICKER_PX) { "Invalid sticker image." }
        val uploaded = api.upload(connection, "image", bytes.toRequestBody("image/webp".toMediaType()))
        check(uploaded.mime == "image/webp" && uploaded.size == bytes.size) { "Invalid sticker upload." }
        val payload = buildJsonObject {
            put("path", uploaded.path); put("mime", uploaded.mime); put("size", uploaded.size)
            put("width", STICKER_PX); put("height", STICKER_PX)
        }
        validateFeaturePayload(connection, "image", payload)
        payload
    }

    override suspend fun signedUrl(connection: String, path: String): String = signing.withLock {
        require(path.startsWith("$connection/")) { "Attachment belongs to another conversation." }
        urls[path]?.takeIf { it.second > System.currentTimeMillis() }?.let { return@withLock it.first }
        val url = checkNotNull(api.signed(connection, SignedPaths(listOf(path))).urls[path]) { "Attachment is unavailable." }
        require(storageUrl(url)) { "Invalid attachment URL origin." }
        urls[path] = url to System.currentTimeMillis() + 55 * 60_000
        url
    }
    override fun cachedSignedUrl(connection: String, path: String): String? =
        urls[path]?.takeIf { path.startsWith("$connection/") && it.second > System.currentTimeMillis() }?.first
    private fun storageUrl(url: String) = url.toHttpUrlOrNull()?.let { it.isHttps && it.host == BuildConfig.SUPABASE_URL.toHttpUrlOrNull()?.host } == true

    override suspend fun saveArchive(uri: Uri, connection: String, messages: List<ChatMessage>, owner: String, other: String,
                                     progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val handwriting = ResourcesCompat.getFont(context, R.font.dancing_script) ?: Typeface.SERIF
        val paths = archiveFiles(messages).filter { it.message.type != "letter" }.map { it.message.payload.text("path") }
        val signed = mutableMapOf<String, String>()
        try {
            checkNotNull(context.contentResolver.openOutputStream(uri)) { "Couldn't open that file." }.use { output ->
                writeConversationArchive(messages, owner, other, output, letter = { message, out -> writeLetterPdf(message, handwriting, out) },
                    open = open@{ message ->
                        val path = message.payload.text("path")
                        if (!path.startsWith("$connection/")) return@open null
                        if (path !in signed) {
                            // Sign 50 at a time, just before downloading them: URLs last an hour and /api is rate limited.
                            val chunk = paths.drop(paths.indexOf(path).coerceAtLeast(0)).take(50).filter { it.startsWith("$connection/") }
                            signed += api.signed(connection, SignedPaths(chunk)).urls.filter { storageUrl(it.value) }
                            chunk.forEach { signed.putIfAbsent(it, "") }
                        }
                        val url = signed[path]?.takeIf { it.isNotEmpty() } ?: return@open null
                        val response = downloadClient.newCall(Request.Builder().url(url).build()).execute()
                        // A file the server no longer has is listed as "not available" instead of failing the whole save.
                        if (response.isSuccessful) response.body.byteStream() else { response.close(); null }
                    }, progress = progress)
            }
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }

    override suspend fun download(connection: String, payload: JsonObject): Uri = withContext(Dispatchers.IO) {
        val mime = payload.text("mime")
        require(mime in ImageMimes + VoiceMimes + FileMimes) { "Unsupported attachment type." }
        val url = signedUrl(connection, payload.text("path"))
        val name = payload.text("name").ifBlank { payload.text("path").substringAfterLast('/') }
        val extension = name.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60_000L }?.forEach { it.delete() }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        try {
            downloadClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Couldn't download that attachment (${response.code})." }
                val bytes = response.body.byteStream().use { readBounded(it, 25 * MiB) }
                file.writeBytes(bytes)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (error: Exception) { file.delete(); throw error }
    }

    override suspend fun location(): JsonObject {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        check(fine || coarse) { "Location access was denied." }
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (fine) add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        check(providers.isNotEmpty()) { "Turn on location in phone settings and try again." }
        @Suppress("MissingPermission") // checked above
        fun lastKnown(maxAgeMs: Long) = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= maxAgeMs * 1_000_000 }
            .maxByOrNull { it.elapsedRealtimeNanos }
        // A fix from the last 2 minutes is a fine snapshot. Otherwise ask every enabled provider at once and take the
        // first answer: GPS alone often never fixes indoors, which used to time out and send nothing.
        val location = lastKnown(2 * 60_000L) ?: withTimeoutOrNull(20_000) { coroutineScope {
            val answer = CompletableDeferred<Location>()
            val requests = providers.map { provider -> launch { currentLocation(manager, provider)?.let { answer.complete(it) } } }
            launch { requests.joinAll(); answer.completeExceptionally(IllegalStateException("no fix")) }
            runCatching { answer.await() }.getOrNull().also { coroutineContext.cancelChildren() }
        } } ?: lastKnown(30 * 60_000L) ?: error("Couldn't get your location. Go near a window or outside and try again.")
        return buildJsonObject {
            put("lat", round(location.latitude * 100_000) / 100_000)
            put("lng", round(location.longitude * 100_000) / 100_000)
            if (location.hasAccuracy()) put("accuracy", round(location.accuracy.toDouble()))
        }
    }

    @Suppress("MissingPermission") // callers check the permission first
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= 30) {
            val cancellation = CancellationSignal()
            manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { if (continuation.isActive) continuation.resume(it) }
            continuation.invokeOnCancellation { cancellation.cancel() }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
                override fun onProviderEnabled(provider: String) { }
                override fun onProviderDisabled(provider: String) { if (continuation.isActive) continuation.resume(null) }
                @Deprecated("Required on Android 26–28")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) { }
            }
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        }
    }

    override suspend fun startRecording(onLimit: () -> Unit) = startRecorder(false, onLimit)
    override suspend fun startDictation(onLimit: () -> Unit) = startRecorder(true, onLimit)
    @Suppress("DEPRECATION") // Context constructor is API 31+; minSdk remains 26.
    private suspend fun startRecorder(dictation: Boolean, onLimit: () -> Unit) = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone access was denied." }
        synchronized(this@MediaRepository) {
            check(recorder == null) { "Already recording." }
            val file = File(File(context.cacheDir, if (dictation) "dictation" else "recordings").apply { mkdirs() }, "${UUID.randomUUID()}.m4a")
            val recording = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            try {
                recording.setAudioSource(MediaRecorder.AudioSource.MIC)
                recording.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recording.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recording.setAudioEncodingBitRate(64_000)
                recording.setAudioSamplingRate(44_100)
                recording.setOutputFile(file.absolutePath)
                recording.setMaxDuration(if (dictation) DICTATION_MAX_MS else 3_600_000)
                recording.setMaxFileSize(if (dictation) DICTATION_MAX_BYTES else 16L * MiB)
                recording.setOnInfoListener { _, what, _ -> if (what in setOf(MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED, MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)) onLimit() }
                recording.prepare(); recording.start()
                recorder = recording; recordingFile = file; recordingStarted = SystemClock.elapsedRealtime(); recordingDictation = dictation
                if (dictation) dictationFile = file
            } catch (error: Exception) { recording.release(); file.delete(); throw error }
        }
    }
    override suspend fun stopRecording(): VoiceClip? = stopRecorder(false)
    override suspend fun stopDictation(): VoiceClip? = stopRecorder(true)
    private suspend fun stopRecorder(dictation: Boolean): VoiceClip? = withContext(Dispatchers.IO) {
        synchronized(this@MediaRepository) {
            if (recordingDictation != dictation) return@synchronized null
            val recording = recorder ?: return@synchronized null
            val file = checkNotNull(recordingFile)
            val elapsed = (SystemClock.elapsedRealtime() - recordingStarted) / 1_000.0
            try {
                recording.stop()
                if (elapsed < 1 || file.length() == 0L) { file.delete(); return@synchronized null }
                val metadata = MediaMetadataRetriever()
                val duration = try {
                    metadata.setDataSource(file.absolutePath)
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull()?.div(1_000) ?: elapsed
                } finally { metadata.release() }
                VoiceClip(file, if (dictation) duration else duration.coerceAtMost(3_600.0))
            } catch (error: Exception) { file.delete(); throw error }
            finally { recording.release(); recorder = null; recordingFile = null }
        }
    }
    override fun releaseRecording() = releaseRecorder(false)
    override suspend fun cancelDictation() = withContext(Dispatchers.IO) {
        try { releaseRecorder(true) }
        finally { dictationFile?.delete(); dictationFile = null }
    }
    private fun releaseRecorder(dictation: Boolean) = synchronized(this) {
        if (recordingDictation == dictation) {
            recorder?.release(); recorder = null; recordingFile?.delete(); recordingFile = null
        }
    }
    override suspend fun searchGifs(query: String, offset: Int): GifPage = api.gifs(query.trim().take(50), offset.coerceAtLeast(0))
        // Trust boundary: only https GIPHY media hosts are loaded by the grid.
        .let { page -> page.copy(results = page.results.filter { isGiphyMedia(it.previewUrl) && it.width in 1..2_000 && it.height in 1..2_000 }) }
    override suspend fun attachGif(connection: String, id: String): JsonObject {
        require(id.matches(Regex("[A-Za-z0-9]{1,64}"))) { "Invalid GIF." }
        val payload = api.gif(connection, GifId(id))
        validateFeaturePayload(connection, "image", payload)
        return payload
    }
    override suspend fun discardVoice(path: String?) = withContext(Dispatchers.IO) {
        path?.let { File(it) }?.takeIf { it.parentFile?.canonicalFile == File(context.cacheDir, "recordings").canonicalFile }?.delete()
        Unit
    }
    override suspend fun save(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
    }
    override suspend fun saveLetterPdf(uri: Uri, message: ChatMessage) = withContext(Dispatchers.IO) {
        val handwriting = ResourcesCompat.getFont(context, R.font.dancing_script) ?: Typeface.SERIF
        checkNotNull(context.contentResolver.openOutputStream(uri)).use { writeLetterPdf(message, handwriting, it) }
    }
    override suspend fun saveAttachment(connection: String, payload: JsonObject, destination: Uri) {
        val source = download(connection, payload)
        withContext(Dispatchers.IO) {
            checkNotNull(context.contentResolver.openInputStream(source)).use { input ->
                checkNotNull(context.contentResolver.openOutputStream(destination)).use { output -> input.copyTo(output) }
            }
        }
    }
}

/** Photos are sent at most this many pixels on the long side (a phone screen's worth), JPEG at [PHOTO_QUALITY]. */
internal const val PHOTO_MAX_SIDE = 2_048
internal const val PHOTO_QUALITY = 85

/** [width]×[height] scaled down (never up) so the long side is at most [maxSide], keeping the aspect ratio. */
internal fun fittedSize(width: Int, height: Int, maxSide: Int = PHOTO_MAX_SIDE): Pair<Int, Int> {
    val long = maxOf(width, height)
    if (long <= maxSide) return width to height
    val scale = maxSide.toDouble() / long
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/** Downscales a decoded photo to [PHOTO_MAX_SIDE], recycling the original when a copy is made. */
private fun fitPhoto(bitmap: Bitmap): Bitmap {
    val (width, height) = fittedSize(bitmap.width, bitmap.height)
    if (width == bitmap.width && height == bitmap.height) return bitmap
    return bitmap.scale(width, height).also { if (it !== bitmap) bitmap.recycle() }
}

internal fun orientLegacy(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
}
