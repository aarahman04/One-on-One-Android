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
import app.web.oneonone.BuildConfig
import app.web.oneonone.data.model.*
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
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class UploadedAttachment(val path: String, val mime: String, val size: Int)
@Serializable data class SignedPaths(val paths: List<String>)
@Serializable data class SignedUrls(val urls: Map<String, String>)
interface AttachmentApi {
    @POST("api/connections/{id}/attachments") suspend fun upload(
        @Path("id") connection: String, @Query("kind") kind: String, @Body bytes: RequestBody,
    ): UploadedAttachment
    @POST("api/connections/{id}/attachments/signed") suspend fun signed(@Path("id") connection: String, @Body paths: SignedPaths): SignedUrls
}
data class VoiceClip(val file: File, val duration: Double)
interface MediaGateway {
    suspend fun upload(connection: String, kind: String, uri: Uri, duration: Double? = null): JsonObject
    suspend fun signedUrl(connection: String, path: String): String
    suspend fun download(connection: String, payload: JsonObject): Uri
    suspend fun location(): JsonObject
    suspend fun startRecording(onLimit: () -> Unit)
    suspend fun stopRecording(): VoiceClip?
    fun releaseRecording()
    suspend fun discardVoice(path: String?)
    suspend fun save(uri: Uri, text: String)
    suspend fun saveAttachment(connection: String, payload: JsonObject, destination: Uri)
    fun playbackFocus(onLost: () -> Unit): Boolean
    fun abandonPlaybackFocus()
}

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
) : MediaGateway {
    private val signing = Mutex()
    private val urls = mutableMapOf<String, Pair<String, Long>>()
    private val downloadClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS).build()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStarted = 0L
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
                try {
                    width = bitmap.width; height = bitmap.height
                    val encoded = ByteArrayOutputStream()
                    val png = sourceMime == "image/png"
                    check(bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, encoded)) { "Couldn't encode the image." }
                    bytes = encoded.toByteArray() // Re-encoding pixels drops EXIF/GPS metadata.
                    mime = if (png) "image/png" else "image/jpeg"
                    require(bytes.size <= attachmentLimit("image")) { "That photo exceeds 10 MiB after encoding." }
                } finally { bitmap.recycle() }
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

    override suspend fun signedUrl(connection: String, path: String): String = signing.withLock {
        require(path.startsWith("$connection/")) { "Attachment belongs to another conversation." }
        urls[path]?.takeIf { it.second > System.currentTimeMillis() }?.let { return@withLock it.first }
        val url = checkNotNull(api.signed(connection, SignedPaths(listOf(path))).urls[path]) { "Attachment is unavailable." }
        val parsed = checkNotNull(url.toHttpUrlOrNull()) { "Invalid attachment URL." }
        require(parsed.isHttps && parsed.host == BuildConfig.SUPABASE_URL.toHttpUrlOrNull()?.host) { "Invalid attachment URL origin." }
        urls[path] = url to System.currentTimeMillis() + 55 * 60_000
        url
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
        val provider = if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
            else LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) }
        check(provider != null) { "Turn on location in phone settings and try again." }
        val location = withTimeoutOrNull(10_000) { suspendCancellableCoroutine<Location?> { continuation ->
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
                    override fun onProviderDisabled(provider: String) { }
                    @Deprecated("Required on Android 26–28")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) { }
                }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        } } ?: error("Couldn't get your location. Try again.")
        return buildJsonObject {
            put("lat", round(location.latitude * 100_000) / 100_000)
            put("lng", round(location.longitude * 100_000) / 100_000)
            if (location.hasAccuracy()) put("accuracy", round(location.accuracy.toDouble()))
        }
    }

    @Suppress("DEPRECATION") // Context constructor is API 31+; minSdk remains 26.
    override suspend fun startRecording(onLimit: () -> Unit) = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone access was denied." }
        synchronized(this@MediaRepository) {
            check(recorder == null) { "Already recording." }
            val file = File(File(context.cacheDir, "recordings").apply { mkdirs() }, "${UUID.randomUUID()}.m4a")
            val recording = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            try {
                recording.setAudioSource(MediaRecorder.AudioSource.MIC)
                recording.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recording.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recording.setAudioEncodingBitRate(64_000)
                recording.setAudioSamplingRate(44_100)
                recording.setOutputFile(file.absolutePath)
                recording.setMaxDuration(3_600_000)
                recording.setMaxFileSize(16L * MiB)
                recording.setOnInfoListener { _, what, _ -> if (what in setOf(MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED, MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)) onLimit() }
                recording.prepare(); recording.start()
                recorder = recording; recordingFile = file; recordingStarted = SystemClock.elapsedRealtime()
            } catch (error: Exception) { recording.release(); file.delete(); throw error }
        }
    }
    override suspend fun stopRecording(): VoiceClip? = withContext(Dispatchers.IO) {
        synchronized(this@MediaRepository) {
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
                VoiceClip(file, duration.coerceAtMost(3_600.0))
            } catch (error: Exception) { file.delete(); throw error }
            finally { recording.release(); recorder = null; recordingFile = null }
        }
    }
    override fun releaseRecording() = synchronized(this) {
        recorder?.release(); recorder = null; recordingFile?.delete(); recordingFile = null
    }
    override suspend fun discardVoice(path: String?) = withContext(Dispatchers.IO) {
        path?.let { File(it) }?.takeIf { it.parentFile?.canonicalFile == File(context.cacheDir, "recordings").canonicalFile }?.delete()
        Unit
    }
    override suspend fun save(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
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

private fun orientLegacy(bitmap: Bitmap, bytes: ByteArray): Bitmap {
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
