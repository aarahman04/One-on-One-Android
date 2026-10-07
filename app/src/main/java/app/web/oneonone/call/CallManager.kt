package app.web.oneonone.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.web.oneonone.data.api.AccountApi
import app.web.oneonone.push.handlers.CallPushHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import app.web.oneonone.data.realtime.ConnectionState
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CallUi {
    data object Idle : CallUi
    /** Waiting for the user to grant mic (and camera for video) before inviting/answering. */
    data class NeedsPermission(val kind: CallKind, val answerCallId: String?) : CallUi
    data class Outgoing(val callId: String?, val kind: CallKind, val peer: String) : CallUi
    data class Incoming(val callId: String, val kind: CallKind, val peer: String) : CallUi
    data class Active(
        val callId: String, val kind: CallKind, val peer: String, val media: MediaState,
        val connectedAt: Long?, val muted: Boolean, val cameraOn: Boolean,
    ) : CallUi
    data class Ended(val message: String) : CallUi
}

/**
 * The one call state machine. Binds the frozen [CallLauncher] (chat header buttons) and
 * [CallPushHandler] (FCM `call` / `call_end`) seams. Signaling follows docs/API-CONTRACT.md;
 * the server owns call state (busy, ring timeout, missed rows), this only mirrors it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signaling: CallSignaling,
    private val audio: AudioRouter,
    private val account: AccountApi,
) : CallLauncher, CallPushHandler {
    // Single-threaded: every state change and WebRTC call is serialised here.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val mutableState = MutableStateFlow<CallUi>(CallUi.Idle)
    val state: StateFlow<CallUi> = mutableState.asStateFlow()
    private val mutableSession = MutableStateFlow<WebRtcSession?>(null)
    val session: StateFlow<WebRtcSession?> = mutableSession.asStateFlow()
    val route = audio.route

    private var pendingSignals = mutableListOf<Pair<String, Signal>>()
    private var answerOnArrival: String? = null
    private var mediaJob: Job? = null
    private var endedJob: Job? = null

    /** Called once from Application.onCreate: listen for call events for the process lifetime. */
    fun listen() {
        scope.launch { signaling.incoming.collect { onIncoming(it.callId, it.kind, null) } }
        scope.launch { signaling.accepted.collect { onAccepted(it) } }
        scope.launch { signaling.ended.collect { (callId, reason) -> onEnded(callId, reason) } }
        scope.launch {
            signaling.signals.collect { (callId, signal) ->
                signal ?: return@collect
                val session = mutableSession.value
                if (session != null && currentCallId() == callId) guarded { session.handle(signal) }
                else if (currentCallId() == callId) pendingSignals += callId to signal // offer can beat our accept ack
            }
        }
    }

    // --- Seams ------------------------------------------------------------------------------

    override fun start(kind: CallKind) { scope.launch { beginOutgoing(kind) } }

    override fun onCallPush(data: Map<String, String>) {
        val callId = data["callId"]?.takeIf { it.length in 1..64 } ?: return
        scope.launch { onIncoming(callId, CallProtocol.kindOf(data["kind"]), data["callerName"]?.take(40)?.ifBlank { null }) }
    }

    override fun onCallEndPush(data: Map<String, String>) {
        val callId = data["callId"] ?: return
        scope.launch { onEnded(callId, "ended") }
    }

    // --- UI actions -------------------------------------------------------------------------

    fun permissionResult(granted: Boolean) = scope.launch {
        val waiting = mutableState.value as? CallUi.NeedsPermission ?: return@launch
        if (!granted) {
            waiting.answerCallId?.let { id -> runCatching { signaling.decline(id) } }
            finish(if (waiting.kind == CallKind.Video) "Camera and microphone are needed for video calls" else "Microphone is needed for calls")
            return@launch
        }
        mutableState.value = CallUi.Idle
        if (waiting.answerCallId != null) {
            mutableState.value = CallUi.Incoming(waiting.answerCallId, waiting.kind, peerName(null))
            answer()
        } else beginOutgoing(waiting.kind)
    }

    fun accept() { scope.launch { answer() } }

    /** From the incoming-call notification's Answer (token-checked in MainActivity). */
    fun answerFromNotification(callId: String) = scope.launch {
        val now = mutableState.value
        if (now is CallUi.Incoming && now.callId == callId) { answer(); return@launch }
        answerOnArrival = callId // the socket replays call:incoming once it connects
        // Normally the chat screen opens the socket (MessageService restarts it on activate, so
        // starting it here first would race that). Fallback if the app didn't route to the chat.
        delay(8_000)
        if (answerOnArrival == callId && signaling.connection.value != ConnectionState.Connected) {
            runCatching { signaling.ensureConnected() }
        }
    }

    fun decline() = scope.launch {
        val incoming = mutableState.value as? CallUi.Incoming ?: return@launch
        CallNotifications.cancelIncoming(context)
        mutableState.value = CallUi.Idle
        runCatching { connected(); signaling.decline(incoming.callId) }
    }

    /** Decline from the notification while the app may be closed: connect just long enough to tell the server. */
    fun declineFromNotification(callId: String) = scope.launch {
        CallNotifications.cancelIncoming(context)
        if (currentCallId() == callId) mutableState.value = CallUi.Idle
        runCatching { connected(); signaling.decline(callId) } // else the server's 45 s ring timeout ends it
    }

    fun hangup() = scope.launch {
        val id = currentCallId()
        finish(null) // an invite still in flight sees the state change and ends its call itself
        if (id != null) runCatching { signaling.end(id) }
    }

    fun toggleMute() = scope.launch {
        val active = mutableState.value as? CallUi.Active ?: return@launch
        mutableSession.value?.setMuted(!active.muted)
        mutableState.value = active.copy(muted = !active.muted)
    }

    fun toggleCamera() = scope.launch {
        val active = mutableState.value as? CallUi.Active ?: return@launch
        mutableSession.value?.setCameraEnabled(!active.cameraOn)
        mutableState.value = active.copy(cameraOn = !active.cameraOn)
    }

    fun switchCamera() = scope.launch { mutableSession.value?.switchCamera() }
    fun toggleSpeaker() = audio.toggleSpeaker()

    // --- Flow -------------------------------------------------------------------------------

    private suspend fun beginOutgoing(kind: CallKind) {
        if (mutableState.value !is CallUi.Idle && mutableState.value !is CallUi.Ended) return
        if (!hasPermissions(kind)) { mutableState.value = CallUi.NeedsPermission(kind, null); return }
        endedJob?.cancel()
        val peer = peerName(null)
        mutableState.value = CallUi.Outgoing(null, kind, peer)
        CallService.start(context, kind, peer)
        try {
            connected()
            val (callId, ice) = signaling.invite(kind)
            outgoingIce = ice
            if (mutableState.value is CallUi.Outgoing) mutableState.value = CallUi.Outgoing(callId, kind, peer)
            else runCatching { signaling.end(callId) } // hung up while inviting
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { finish(error.message ?: "Call failed") }
    }
    private var outgoingIce: List<IceServerSpec> = emptyList()

    private suspend fun onAccepted(callId: String) {
        when (val now = mutableState.value) {
            is CallUi.Outgoing -> if (now.callId == callId) beginMedia(callId, now.kind, now.peer, outgoingIce, caller = true)
            // call:accepted goes to the whole room: if another of our devices answered, drop this prompt.
            is CallUi.Incoming -> if (now.callId == callId) { CallNotifications.cancelIncoming(context); mutableState.value = CallUi.Idle }
            else -> Unit
        }
    }

    private suspend fun onIncoming(callId: String, kind: CallKind, pushName: String?) {
        if (callId.isBlank()) return
        when (val now = mutableState.value) {
            is CallUi.Incoming -> if (now.callId == callId) { if (answerOnArrival == callId) answer(); return } else return
            is CallUi.Idle, is CallUi.Ended -> Unit
            else -> return // already in a call: the server reports busy to the caller
        }
        endedJob?.cancel()
        val peer = peerName(pushName)
        mutableState.value = CallUi.Incoming(callId, kind, peer)
        CallNotifications.showIncoming(context, callId, kind, peer)
        if (answerOnArrival == callId) answer()
    }

    private suspend fun answer() {
        val incoming = mutableState.value as? CallUi.Incoming ?: return
        answerOnArrival = null
        CallNotifications.cancelIncoming(context)
        if (!hasPermissions(incoming.kind)) {
            mutableState.value = CallUi.NeedsPermission(incoming.kind, incoming.callId); return
        }
        CallService.start(context, incoming.kind, incoming.peer)
        try {
            connected()
            val ice = signaling.accept(incoming.callId)
            beginMedia(incoming.callId, incoming.kind, incoming.peer, ice, caller = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { finish(error.message ?: "Call failed") }
    }

    private suspend fun beginMedia(callId: String, kind: CallKind, peer: String, ice: List<IceServerSpec>, caller: Boolean) {
        val session = WebRtcSession(context, scope, kind, ice) { signaling.signal(callId, it) }
        mutableSession.value = session
        audio.start(kind)
        mutableState.value = CallUi.Active(callId, kind, peer, MediaState.Connecting, null, muted = false, cameraOn = kind == CallKind.Video)
        val queued = pendingSignals.filter { it.first == callId }.map { it.second }
        pendingSignals = mutableListOf()
        guarded { if (caller) session.startAsCaller() }
        queued.forEach { guarded { session.handle(it) } }
        mediaJob = scope.launch {
            session.state.collect { media ->
                val active = mutableState.value as? CallUi.Active ?: return@collect
                if (active.callId != callId) return@collect
                if (media == MediaState.Failed) { finish("Call dropped"); runCatching { signaling.end(callId) }; return@collect }
                mutableState.value = active.copy(media = media,
                    connectedAt = active.connectedAt ?: System.currentTimeMillis().takeIf { media == MediaState.Connected })
            }
        }
    }

    private fun onEnded(callId: String, reason: String) {
        if (currentCallId() != callId) {
            if (answerOnArrival == callId) answerOnArrival = null
            CallNotifications.cancelIncoming(context, callId)
            return
        }
        val wasIncoming = mutableState.value is CallUi.Incoming
        finish(when (reason) {
            "declined" -> "Declined"
            "missed" -> if (wasIncoming) null else "No answer"
            "cancelled" -> null
            "unreachable" -> "They're not reachable right now"
            "failed" -> "Call failed"
            else -> "Call ended"
        })
    }

    private fun finish(message: String?) {
        mediaJob?.cancel(); mediaJob = null
        mutableSession.value?.close()
        mutableSession.value = null
        pendingSignals = mutableListOf()
        audio.stop()
        CallService.stop(context)
        CallNotifications.cancelIncoming(context)
        if (message == null) { mutableState.value = CallUi.Idle; return }
        mutableState.value = CallUi.Ended(message)
        endedJob = scope.launch { delay(2_500); if (mutableState.value is CallUi.Ended) mutableState.value = CallUi.Idle }
    }

    private fun currentCallId(): String? = when (val s = mutableState.value) {
        is CallUi.Outgoing -> s.callId
        is CallUi.Incoming -> s.callId
        is CallUi.Active -> s.callId
        is CallUi.NeedsPermission -> s.answerCallId
        else -> null
    }

    private suspend fun connected() {
        signaling.ensureConnected()
        withTimeout(15_000) { signaling.connection.first { it == ConnectionState.Connected } }
    }

    private fun hasPermissions(kind: CallKind): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.RECORD_AUDIO) && (kind == CallKind.Audio || granted(Manifest.permission.CAMERA))
    }

    private suspend fun peerName(pushName: String?): String = pushName
        ?: runCatching { account.current().connection?.otherNickname }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "Your One on One"

    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* a bad SDP/candidate must not kill the call loop; the link timer handles failure */ }
    }
}
