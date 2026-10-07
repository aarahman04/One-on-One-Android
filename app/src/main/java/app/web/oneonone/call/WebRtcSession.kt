package app.web.oneonone.call

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One factory + EGL context for the process; renderers in the call UI share [eglContext]. */
object RtcEngine {
    private var factory: PeerConnectionFactory? = null
    val egl: EglBase by lazy { EglBase.create() }

    @Synchronized fun factory(context: Context): PeerConnectionFactory = factory ?: run {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .createInitializationOptions())
        // JavaAudioDeviceModule plays through USAGE_VOICE_COMMUNICATION: with MODE_IN_COMMUNICATION
        // (AudioRouter) that is the full voice-call stream, routable to the earpiece.
        val adm = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory().also { factory = it }
    }
}

enum class MediaState { Connecting, Connected, Reconnecting, Failed }

/**
 * Mirrors the web CallSession protocol: the caller offers once the callee accepts, the callee
 * answers; ICE candidates trickle as `{candidate}`; the offerer restarts ICE when the link drops;
 * a link that stays down for 20 s fails the call. All methods run on [scope] (single-threaded).
 */
class WebRtcSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val kind: CallKind,
    iceServers: List<IceServerSpec>,
    private val send: (Signal) -> Unit,
) {
    private val factory = RtcEngine.factory(context)
    private val mutableState = MutableStateFlow(MediaState.Connecting)
    val state: StateFlow<MediaState> = mutableState.asStateFlow()
    private val mutableRemoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = mutableRemoteVideo.asStateFlow()
    private val mutableLocalVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = mutableLocalVideo.asStateFlow()

    private var offerer = false
    private var closed = false
    private var restarting = false
    private var reconnectJob: Job? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var audioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var textures: SurfaceTextureHelper? = null
    private var frontCamera = true

    private val pc: PeerConnection = checkNotNull(factory.createPeerConnection(
        PeerConnection.RTCConfiguration(iceServers.map { spec ->
            PeerConnection.IceServer.builder(spec.urls).apply {
                spec.username?.let(::setUsername)
                spec.credential?.let(::setPassword)
            }.createIceServer()
        }).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        },
        object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch { if (!closed) send(Signal.Candidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)) }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                scope.launch { onLink(newState) }
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) scope.launch { mutableRemoteVideo.value = track }
            }
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        },
    )) { "Couldn't start the call connection." }

    init {
        // Local tracks are attached synchronously, before any offer can arrive, so the answer
        // always carries our audio (the web callee attaches after an async getUserMedia).
        val stream = listOf(STREAM_ID)
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("audio0", audioSource).also {
            pc.addTrack(it, stream)
        }
        if (kind == CallKind.Video) startCamera()?.let { pc.addTrack(it, stream) }
    }

    private fun startCamera(): VideoTrack? {
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        val name = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull() ?: return null
        frontCamera = enumerator.isFrontFacing(name)
        val camera = enumerator.createCapturer(name, null) ?: return null
        val source = factory.createVideoSource(false).also { videoSource = it }
        val helper = SurfaceTextureHelper.create("call-camera", RtcEngine.egl.eglBaseContext)
        camera.initialize(helper, context, source.capturerObserver)
        camera.startCapture(1280, 720, 30)
        capturer = camera
        textures = helper
        return factory.createVideoTrack("video0", source).also { mutableLocalVideo.value = it }
    }

    suspend fun startAsCaller() {
        offerer = true
        val offer = create(offer = true, iceRestart = false)
        setLocal(offer)
        send(Signal.Sdp("offer", offer.description))
    }

    suspend fun handle(signal: Signal) {
        if (closed) return
        when (signal) {
            is Signal.Sdp -> {
                val type = if (signal.type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
                setRemote(SessionDescription(type, signal.sdp))
                pendingCandidates.forEach(pc::addIceCandidate)
                pendingCandidates.clear()
                if (type == SessionDescription.Type.OFFER) {
                    val answer = create(offer = false, iceRestart = false)
                    setLocal(answer)
                    send(Signal.Sdp("answer", answer.description))
                } else restarting = false
            }
            is Signal.Candidate -> {
                val candidate = IceCandidate(signal.sdpMid, signal.sdpMLineIndex, signal.candidate)
                // Candidates can beat the SDP across the relay; hold them until it lands.
                if (pc.remoteDescription == null) pendingCandidates += candidate else pc.addIceCandidate(candidate)
            }
        }
    }

    fun setMuted(muted: Boolean) { audioTrack?.setEnabled(!muted) }
    fun setCameraEnabled(enabled: Boolean) { mutableLocalVideo.value?.setEnabled(enabled) }
    fun switchCamera() { capturer?.switchCamera(null) }

    fun close() {
        if (closed) return
        closed = true
        reconnectJob?.cancel()
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        textures?.dispose()
        mutableRemoteVideo.value = null
        mutableLocalVideo.value = null
        pc.dispose() // disposes its senders' tracks; sources are ours to free
        audioSource?.dispose()
        videoSource?.dispose()
    }

    private suspend fun onLink(link: PeerConnection.PeerConnectionState) {
        if (closed) return
        when (link) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                reconnectJob?.cancel(); reconnectJob = null
                restarting = false
                mutableState.value = MediaState.Connected
            }
            PeerConnection.PeerConnectionState.DISCONNECTED, PeerConnection.PeerConnectionState.FAILED -> {
                mutableState.value = MediaState.Reconnecting
                if (reconnectJob == null) reconnectJob = scope.launch {
                    delay(RECONNECT_GRACE_MS)
                    if (!closed && mutableState.value != MediaState.Connected) mutableState.value = MediaState.Failed
                }
                if (offerer && !restarting) {
                    restarting = true
                    runCatching {
                        val offer = create(offer = true, iceRestart = true)
                        setLocal(offer)
                        send(Signal.Sdp("offer", offer.description))
                    }.onFailure { restarting = false }
                }
            }
            else -> Unit
        }
    }

    private suspend fun create(offer: Boolean, iceRestart: Boolean): SessionDescription = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            if (iceRestart) mandatory += MediaConstraints.KeyValuePair("IceRestart", "true")
        }
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = cont.resume(description)
            override fun onCreateFailure(error: String?) = cont.resumeWithException(IllegalStateException(error ?: "SDP create failed"))
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }
        if (offer) pc.createOffer(observer, constraints) else pc.createAnswer(observer, constraints)
    }

    private suspend fun setLocal(description: SessionDescription) = set { pc.setLocalDescription(it, description) }
    private suspend fun setRemote(description: SessionDescription) = set { pc.setRemoteDescription(it, description) }

    private suspend fun set(block: (SdpObserver) -> Unit): Unit = suspendCancellableCoroutine { cont ->
        block(object : SdpObserver {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) = cont.resumeWithException(IllegalStateException(error ?: "SDP set failed"))
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String?) = Unit
        })
    }

    private companion object {
        const val STREAM_ID = "oneonone"
        const val RECONNECT_GRACE_MS = 20_000L // same as web RECONNECT_GRACE_MS
    }
}
