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
    private var attempt: CallAttempt? = null

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
            answerOnArrival = waiting.answerCallId
            onIncoming(waiting.answerCallId, waiting.kind, null)
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
        finish(null)
        runCatching { connected(); signaling.decline(incoming.callId) }
    }

    /** Decline from the notification while the app may be closed: connect just long enough to tell the server. */
    fun declineFromNotification(callId: String) = scope.launch {
        if (currentCallId() == callId) finish(null) else CallNotifications.cancelIncoming(context, callId)
        runCatching { connected(); signaling.decline(callId) } // else the server's 45 s ring timeout ends it
    }

    fun hangup() = scope.launch {
        val id = currentCallId()
        // An invite still in flight sees the state change and ends its call itself. The server only writes the
        // call card on `call:end`, so wait for the socket instead of dropping it, and send it even if teardown throws.
        endCallAfterCleanup({ finish(null) }) { if (id != null) endOnServer(id) }
    }

    private suspend fun endOnServer(callId: String) {
        try { connected(); signaling.end(callId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The server's disconnect grace resolves an unreachable call. */ }
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
        val pending = newAttempt()
        mutableState.value = CallUi.Outgoing(null, kind, "Your One on One")
        try {
            val peer = peerName(null)
            if (!pending.active) return
            mutableState.value = CallUi.Outgoing(null, kind, peer)
            CallService.start(context, kind, peer)
            connected()
            if (!pending.active) return
            val (callId, ice) = signaling.invite(kind)
            val resolution = pending.bind(callId)
            if (resolution == null) { endOnServer(callId); return }
            outgoingIce = ice
            mutableState.value = CallUi.Outgoing(callId, kind, peer)
            when (resolution) {
                InviteResolution.Ringing -> Unit
                InviteResolution.Accepted -> onAccepted(callId)
                is InviteResolution.Ended -> onEnded(callId, resolution.reason)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (pending.active) finish(callError(error)) }
    }
    private var outgoingIce: List<IceServerSpec> = emptyList()

    private suspend fun onAccepted(callId: String) {
        val pending = attempt ?: return
        when (val now = mutableState.value) {
            is CallUi.Outgoing -> if (now.callId == null) pending.acceptedBeforeAck(callId)
            else if (now.callId == callId) {
                try { beginMedia(callId, now.kind, now.peer, outgoingIce, caller = true) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    endCallAfterCleanup({ if (pending.active) finish(callError(error)) }) { endOnServer(callId) }
                }
            }
            // call:accepted goes to the whole room: if another of our devices answered, drop this prompt.
            is CallUi.Incoming -> if (pending.shouldDismissIncoming(callId)) finish(null)
            is CallUi.NeedsPermission -> if (pending.shouldDismissIncoming(callId)) finish(null)
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
        val pending = newAttempt(callId)
        mutableState.value = CallUi.Incoming(callId, kind, pushName ?: "Your One on One")
        val peer = peerName(pushName)
        if (!pending.shouldDismissIncoming(callId) || mutableState.value !is CallUi.Incoming) return
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
        val pending = attempt ?: return
        if (!pending.beginAnswer()) return
        CallService.start(context, incoming.kind, incoming.peer)
        try {
            connected()
            if (!pending.active) return
            val ice = signaling.accept(incoming.callId)
            if (!pending.active) { endOnServer(incoming.callId); return }
            beginMedia(incoming.callId, incoming.kind, incoming.peer, ice, caller = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            endCallAfterCleanup({ if (pending.active) finish(callError(error)) }) { endOnServer(incoming.callId) }
        }
    }

    private suspend fun beginMedia(callId: String, kind: CallKind, peer: String, ice: List<IceServerSpec>, caller: Boolean) {
        val pending = attempt ?: return
        if (!pending.canStartMedia(callId)) return
        val session = WebRtcSession(context, scope, kind, ice) { signaling.signal(callId, it) }
        mutableSession.value = session
        audio.start(kind)
        mutableState.value = CallUi.Active(callId, kind, peer, MediaState.Connecting, null, muted = false, cameraOn = kind == CallKind.Video)
        val queued = pendingSignals.filter { it.first == callId }.map { it.second }
        pendingSignals = mutableListOf()
        guarded { if (caller) session.startAsCaller() }
        if (!pending.active) return
        queued.forEach { guarded { session.handle(it) } }
        if (!pending.active) return
        mediaJob = scope.launch {
            session.state.collect { media ->
                val active = mutableState.value as? CallUi.Active ?: return@collect
                if (active.callId != callId) return@collect
                if (media == MediaState.Failed) {
                    // finish cancels this collector; resolve the call from a sibling job.
                    scope.launch {
                        endCallAfterCleanup({ if (pending.active) finish("Call dropped") }) { endOnServer(callId) }
                    }
                    return@collect
                }
                mutableState.value = active.copy(media = media,
                    connectedAt = active.connectedAt ?: System.currentTimeMillis().takeIf { media == MediaState.Connected })
            }
        }
    }

    private fun onEnded(callId: String, reason: String) {
        val outgoing = mutableState.value as? CallUi.Outgoing
        if (outgoing != null && outgoing.callId == null) {
            attempt?.endedBeforeAck(callId, reason)
            return
        }
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
        attempt?.cancel()
        attempt = null
        mediaJob?.cancel(); mediaJob = null
        runCatching { mutableSession.value?.close() } // native teardown must never skip the rest (video has more to free)
        mutableSession.value = null
        pendingSignals = mutableListOf()
        runCatching { audio.stop() }
        CallService.stop(context)
        CallNotifications.cancelIncoming(context)
        if (message == null) { mutableState.value = CallUi.Idle; return }
        mutableState.value = CallUi.Ended(message)
        endedJob = scope.launch { delay(2_500); if (mutableState.value is CallUi.Ended) mutableState.value = CallUi.Idle }
    }

    private fun newAttempt(callId: String? = null): CallAttempt {
        attempt?.cancel()
        return CallAttempt(callId).also { attempt = it }
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

/** Signaling errors carry the server's sentence; network internals never reach the call screen. */
private fun callError(error: Exception): String =
    if (error is java.io.IOException) "Call failed. Check your connection." else error.message?.replaceFirstChar { it.uppercase() } ?: "Call failed"

/** Local device teardown must never prevent the server from recording the call. */
internal suspend fun endCallAfterCleanup(cleanup: () -> Unit, end: suspend () -> Unit) {
    runCatching { cleanup() }
    end()
}

internal sealed interface InviteResolution {
    data object Ringing : InviteResolution
    data object Accepted : InviteResolution
    data class Ended(val reason: String) : InviteResolution
}

/** Owns async call setup so a late acknowledgement cannot revive an ended call. */
internal class CallAttempt(private var callId: String? = null) {
    var active = true
        private set
    private var answering = false
    private val accepted = mutableSetOf<String>()
    private val ended = mutableMapOf<String, String>()

    fun cancel() { active = false; accepted.clear(); ended.clear() }
    fun canStartMedia(id: String): Boolean = active && callId == id
    fun beginAnswer(): Boolean {
        if (!active || answering) return false
        answering = true
        return true
    }
    fun shouldDismissIncoming(id: String): Boolean = canStartMedia(id) && !answering
    fun acceptedBeforeAck(id: String) { if (active && callId == null) accepted += id }
    fun endedBeforeAck(id: String, reason: String) { if (active && callId == null) ended[id] = reason }
    fun bind(id: String): InviteResolution? {
        if (!active) return null
        callId = id
        val resolution = ended[id]?.let { InviteResolution.Ended(it) }
            ?: if (id in accepted) InviteResolution.Accepted else InviteResolution.Ringing
        accepted.clear(); ended.clear()
        return resolution
    }
}
