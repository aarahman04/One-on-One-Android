package app.web.oneonone.data.media

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

enum class NarrationPhase { Idle, Loading, Playing, Paused }
data class NarrationState(val key: String = "", val phase: NarrationPhase = NarrationPhase.Idle,
    val part: Int = 0, val total: Int = 0, val positionMs: Int = 0, val durationMs: Int = 0,
    val device: Boolean = false, val notice: String? = null)

interface NarrationPlayer {
    val positionMs: Int
    val durationMs: Int
    fun start()
    fun pause()
    fun link(next: NarrationPlayer?)
    fun release()
}
interface NarrationAudio {
    fun focus(onLost: () -> Unit): Boolean
    fun abandonFocus()
    suspend fun prepare(file: File, completed: () -> Unit, failed: () -> Unit): NarrationPlayer
    suspend fun deviceVoice(text: String, completed: () -> Unit, failed: () -> Unit): NarrationPlayer
}

/** One letter session, at most two prepared players: the current part and its prefetched successor. */
class LetterNarrator(private val api: SpeechGateway, private val audio: NarrationAudio, private val scope: CoroutineScope,
                     private val io: CoroutineDispatcher = Dispatchers.IO, private val server: Boolean = true) {
    private val mutableState = MutableStateFlow(NarrationState())
    val state = mutableState.asStateFlow()
    private data class Track(val player: NarrationPlayer, val part: NarrationPart?)
    private var current: Track? = null
    private var next: Track? = null
    private var linked = false
    private var wanted = true
    private var generation = 0L
    private var session: Job? = null
    private var noticeJob: Job? = null
    private var connection = ""
    private var message = ""
    private var text = ""

    fun toggle(connection: String, message: String?, localText: String, device: Boolean = !server) {
        val key = "$connection/$message"
        if (state.value.key == key && state.value.phase != NarrationPhase.Idle) {
            try { when (state.value.phase) {
                NarrationPhase.Playing -> { wanted = false; current?.player?.pause(); next?.player?.pause(); update(NarrationPhase.Paused) }
                NarrationPhase.Paused -> { wanted = true; current?.player?.start(); update(NarrationPhase.Playing) }
                else -> Unit // Repeated taps during download/prepare never start another session.
            } } catch (error: Exception) { failed(generation, error) }
            return
        }
        stop()
        mutableState.value = NarrationState(key = key)
        if (message == null) { notice("This letter can't be read aloud"); return }
        this.connection = connection; this.message = message; text = localText
        begin(device)
    }

    private fun begin(device: Boolean) {
        val token = ++generation
        wanted = true
        mutableState.value = state.value.copy(phase = NarrationPhase.Loading, device = device, part = 0, total = 0, positionMs = 0, durationMs = 0)
        try {
            check(audio.focus { if (token == generation) stop() }) { "Audio is busy. Try again after other audio finishes." }
        } catch (error: Exception) { stop(); notice(error.message ?: "Audio is busy."); return }
        val job = SupervisorJob(scope.coroutineContext[Job]).also { session = it }
        val work = CoroutineScope(scope.coroutineContext + job)
        work.launch {
            try {
                if (device) {
                    val player = audio.deviceVoice(text, { completed(token, 0, work) }, { failed(token, IOException("Device voice failed.")) })
                    if (token != generation) { player.release(); return@launch }
                    current = Track(player, null)
                    player.start(); if (token == generation) update(NarrationPhase.Playing)
                } else load(0, token, work)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed(token, error) }
        }
        work.launch {
            while (isActive) {
                delay(250)
                current?.player?.let { player ->
                    mutableState.value = state.value.copy(positionMs = player.positionMs, durationMs = player.durationMs)
                }
            }
        }
    }

    private suspend fun load(index: Int, token: Long, work: CoroutineScope) {
        var part: NarrationPart? = null
        var player: NarrationPlayer? = null
        var owned = false
        try {
            part = api.narration(connection, message, index)
            if (part.index != index || part.total <= index || (index > 0 && part.total != state.value.total)) throw IOException("Invalid narration sequence.")
            player = audio.prepare(part.file, { completed(token, index, work) }, { failed(token, IOException("Narration playback failed.")) })
            currentCoroutineContext().ensureActive()
            if (token != generation) return
            val track = Track(player, part)
            if (current == null) {
                current = track; owned = true
                mutableState.value = state.value.copy(part = index, total = part.total, positionMs = 0, durationMs = player.durationMs)
                if (wanted) player.start()
                update(if (wanted) NarrationPhase.Playing else NarrationPhase.Paused)
                prefetch(index + 1, part.total, token, work)
            } else {
                current!!.player.link(player)
                next = track; linked = true; owned = true
            }
        } finally {
            if (!owned) { player?.release(); discard(part?.file) }
        }
    }

    private fun prefetch(index: Int, total: Int, token: Long, work: CoroutineScope) {
        if (index >= total) return
        work.launch {
            try { load(index, token, work) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed(token, error) }
        }
    }

    private fun completed(token: Long, index: Int, work: CoroutineScope) {
        if (token != generation || (current?.part?.index ?: 0) != index) return
        val finished = current ?: return
        current = null
        finished.player.release(); discard(finished.part?.file)
        if (state.value.device || index + 1 >= state.value.total) { stop(); return }
        val ready = next
        next = null
        val autoStarted = linked
        linked = false
        if (ready == null) {
            mutableState.value = state.value.copy(part = index + 1, positionMs = 0, durationMs = 0, phase = NarrationPhase.Loading)
            return // The existing prefetch will install/start this part when it becomes ready.
        }
        current = ready
        mutableState.value = state.value.copy(part = index + 1, positionMs = 0, durationMs = ready.player.durationMs)
        if (wanted && !autoStarted) ready.player.start()
        if (!wanted) ready.player.pause()
        update(if (wanted) NarrationPhase.Playing else NarrationPhase.Paused)
        prefetch(index + 2, state.value.total, token, work)
    }

    private fun failed(token: Long, error: Exception) {
        if (token != generation) return
        val device = state.value.device
        stop()
        if (!device && canUseDeviceSpeech(error)) {
            notice("Natural voice unavailable, using device voice")
            begin(device = true)
        } else notice(when {
            error is SpeechFailure && error.status == 404 -> "This letter can't be read aloud"
            error is SpeechFailure && error.status == 429 -> "Reading is busy. Try again in a moment."
            device -> "Reading aloud isn't available on this phone."
            else -> "Couldn't read this letter. Try again."
        })
    }

    fun stop() {
        generation++; session?.cancel(); session = null; noticeJob?.cancel()
        val old = current; val prefetched = next
        current = null; next = null; linked = false
        old?.player?.release(); prefetched?.player?.release()
        discard(old?.part?.file); discard(prefetched?.part?.file)
        audio.abandonFocus()
        mutableState.value = NarrationState(key = state.value.key)
    }
    private fun update(phase: NarrationPhase) { mutableState.value = state.value.copy(phase = phase) }
    private fun notice(value: String) {
        noticeJob?.cancel(); mutableState.value = state.value.copy(notice = value)
        noticeJob = scope.launch { delay(4_000); if (state.value.notice == value) mutableState.value = state.value.copy(notice = null) }
    }
    private fun discard(file: File?) {
        if (file != null) scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable + io) { file.delete() } }
    }
}
