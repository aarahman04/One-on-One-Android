package app.web.oneonone.ui.chat

import android.net.Uri
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.DeviceStore
import app.web.oneonone.data.api.*
import app.web.oneonone.data.chat.*
import app.web.oneonone.data.media.MediaGateway
import app.web.oneonone.data.model.*
import app.web.oneonone.ui.userError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject

@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val service: MessageService, private val account: AccountApi, val media: MediaGateway,
    private val preferences: DeviceStore, private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val recording = MutableStateFlow(false)
    val playing = MutableStateFlow<String?>(null)
    private val mutablePlaybackPosition = MutableStateFlow(0)
    val playbackPosition = mutablePlaybackPosition.asStateFlow()
    private val mutablePlaybackDuration = MutableStateFlow(0)
    val playbackDuration = mutablePlaybackDuration.asStateFlow()
    private val mutablePlaybackPaused = MutableStateFlow(false)
    val playbackPaused = mutablePlaybackPaused.asStateFlow()
    val voicePath = saved.getStateFlow<String?>("voicePath", null)
    val signature = MutableStateFlow("")
    private var player: MediaPlayer? = null
    private var playbackJob: Job? = null
    private var progressJob: Job? = null
    private var playbackPrepared = false
    private var recordingJob: Job? = null

    init { viewModelScope.launch {
        signature.value = preferences.letterSignature()
        var hadSession = false
        service.active.collect { session ->
            if (session == null && !hadSession) return@collect // Await restored boot routing before clearing a saved clip.
            if (session != null) hadSession = true
            val owner = session?.let { "${it.ownerId}/${it.connectionId}" }
            if (saved.get<String>("mediaOwner") != owner) {
                background(); discardVoice(); saved["mediaOwner"] = owner
            }
        }
    } }
    fun clearError() { mutableError.value = null }
    fun showError(value: String) { mutableError.value = value }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        if (mutableBusy.value) return@launch
        mutableBusy.value = true; mutableError.value = null
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutableError.value = userError(error) }
        finally { mutableBusy.value = false }
    }
    fun send(type: String, content: String, payload: JsonObject, reply: String?, onDone: () -> Unit) = action {
        service.send(content.ifBlank { featureContent(type, payload) }, type, payload, reply)
        if (type == "letter") { signature.value = payload.text("from"); preferences.saveLetterSignature(signature.value) }
        onDone()
    }
    fun upload(connection: CurrentConnection, kind: String, uri: Uri, reply: String?, onDone: () -> Unit) = action {
        sendMedia(connection, kind, uri, reply); onDone()
    }
    private suspend fun sendMedia(connection: CurrentConnection, kind: String, uri: Uri, reply: String?) {
        val session = ChatSession(connection.myUserId, connection.id)
        check(service.active.value == session) { "Conversation changed." }
        val duration = if (kind == "voice") saved.get<Double>("voiceDuration") else null
        val payload = media.upload(connection.id, kind, uri, duration)
        check(service.active.value == session) { "Conversation changed during upload." }
        service.send("", kind, payload, reply)
        if (kind == "voice") discardVoice()
    }
    fun location(connection: CurrentConnection, reply: String?, onDone: () -> Unit) = action {
        val session = ChatSession(connection.myUserId, connection.id)
        check(service.active.value == session) { "Conversation changed." }
        val payload = media.location()
        check(service.active.value == session) { "Conversation changed while locating." }
        service.send(featureContent("location", payload), "location", payload, reply); onDone()
    }
    /** [onLimit] runs when the recorder hits its length/size cap; the screen uses it to send what was recorded. */
    fun record(onLimit: () -> Unit) {
        if (recordingJob?.isActive == true || recording.value || mutableBusy.value) return
        mutableError.value = null
        recordingJob = viewModelScope.launch {
            try {
                media.startRecording { viewModelScope.launch { onLimit() } }
                recording.value = true
            } catch (cancelled: CancellationException) { media.releaseRecording(); throw cancelled }
            catch (error: Exception) { mutableError.value = userError(error) }
        }
    }
    /** Stops the recorder and sends the clip straight away; a clip that fails to send is dropped, not kept for a retry. */
    fun stopAndSend(connection: CurrentConnection, reply: String?, onDone: () -> Unit) = action {
        recordingJob?.join()
        val clip = try { media.stopRecording() } finally { recording.value = false }
        check(clip != null) { "Record for at least one second." }
        discardVoice(); saved["voicePath"] = clip.file.absolutePath; saved["voiceDuration"] = clip.duration
        try { sendMedia(connection, "voice", Uri.fromFile(clip.file), reply) } catch (error: Exception) { discardVoice(); throw error }
        onDone()
    }
    /** Stops the recorder and deletes the clip without uploading. */
    fun cancelRecording() {
        viewModelScope.launch {
            recordingJob?.join()
            try { media.stopRecording()?.let { media.discardVoice(it.file.absolutePath) } } finally { recording.value = false }
        }
    }
    fun discardVoice() {
        val path = saved.get<String>("voicePath")
        saved["voicePath"] = null; saved["voiceDuration"] = null
        viewModelScope.launch { media.discardVoice(path) }
    }
    fun background() {
        recordingJob?.cancel(); recording.value = false
        viewModelScope.launch(Dispatchers.IO) { media.releaseRecording() }
        stopPlayback()
    }
    fun play(connection: String, path: String) {
        if (recording.value) { showError("Stop recording before playing a voice note."); return }
        if (playing.value == path) {
            val current = player
            if (current == null || !playbackPrepared) { stopPlayback(); return }
            try {
                if (mutablePlaybackPaused.value) {
                    current.start(); mutablePlaybackPaused.value = false; pollPlayback(current)
                } else {
                    current.pause(); progressJob?.cancel()
                    mutablePlaybackPosition.value = current.currentPosition
                    mutablePlaybackPaused.value = true
                }
            } catch (error: Exception) { stopPlayback(); mutableError.value = userError(error) }
            return
        }
        stopPlayback(); playing.value = path
        playbackJob = viewModelScope.launch {
            try {
                val url = media.signedUrl(connection, path)
                if (playing.value != path) return@launch
                check(media.playbackFocus { stopPlayback() }) { "Audio is busy. Try again after other audio finishes." }
                val next = MediaPlayer()
                player = next // Assign before configuring so every failure releases the native player.
                next.apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(url)
                    setOnPreparedListener { if (player === it) {
                        playbackPrepared = true; mutablePlaybackDuration.value = it.duration
                        it.start(); pollPlayback(it)
                    } }
                    setOnCompletionListener { if (player === it) stopPlayback() }
                    setOnErrorListener { failed, _, _ ->
                        if (player === failed) { mutableError.value = "Couldn't play this voice note. Try again."; stopPlayback() }
                        true
                    }
                    prepareAsync()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { stopPlayback(); mutableError.value = userError(error) }
        }
    }
    private fun pollPlayback(current: MediaPlayer) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            try {
                while (player === current && current.isPlaying) {
                    mutablePlaybackPosition.value = current.currentPosition
                    delay(100)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { stopPlayback(); mutableError.value = userError(error) }
        }
    }
    private fun stopPlayback() {
        playbackJob?.cancel(); progressJob?.cancel(); player?.release(); player = null
        playbackPrepared = false; playing.value = null
        mutablePlaybackPosition.value = 0; mutablePlaybackDuration.value = 0; mutablePlaybackPaused.value = false
        media.abandonPlaybackFocus()
    }
    fun openFile(connection: String, payload: JsonObject, onReady: (Uri) -> Unit) = action { onReady(media.download(connection, payload)) }
    fun saveAttachment(connection: String, payload: JsonObject, destination: Uri) = action { media.saveAttachment(connection, payload, destination) }
    fun theme(value: String) = action { preferences.setTheme(value) }
    fun wallpaper(connection: String, value: String, done: () -> Unit) = action {
        require(value in setOf("off", "love", "samurai")); account.wallpaper(connection, WallpaperBody(value)); done()
    }
    fun report(connection: String, message: String?, category: String, reason: String, done: () -> Unit) = action {
        require(category in ReportCategories && reason.trim().length <= 1_000)
        val body = ReportBody(category, reason.trim())
        if (message == null) account.reportConnection(connection, body) else account.reportMessage(message, body)
        done()
    }
    fun block(connection: String, done: () -> Unit) = action {
        account.block(connection); service.deactivate(clear = true); done()
    }
    fun export(uri: Uri, connection: CurrentConnection, format: String) = action {
        val messages = service.exportHistory()
        val text = withContext(Dispatchers.Default) { exportChat(messages, connection.myUserId, connection.otherNickname ?: "Them", format) }
        media.save(uri, text)
    }
    fun saveLetter(uri: Uri, message: ChatMessage) = action { media.save(uri, letterHtml(message)) }
    override fun onCleared() {
        recordingJob?.cancel(); stopPlayback()
        // The application-scoped recorder must release even after this ViewModel's scope is cancelled.
        CoroutineScope(Dispatchers.IO).launch { media.releaseRecording() }
    }
}
