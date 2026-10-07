package app.web.oneonone.ui.call

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.web.oneonone.call.AudioRoute
import app.web.oneonone.call.CallKind
import app.web.oneonone.call.CallManager
import app.web.oneonone.call.CallProtocol
import app.web.oneonone.call.CallUi
import app.web.oneonone.call.MediaState
import app.web.oneonone.call.RtcEngine
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val Danger = Color(0xFFE5484D)
private val Accept = Color(0xFF2EA043)
private val CallBackground = Color(0xFF0D1117)

/** Full-screen call UI drawn over the whole app whenever a call is not idle. */
@Composable
fun CallOverlay(calls: CallManager) {
    val state by calls.state.collectAsState()
    val session by calls.session.collectAsState()
    val route by calls.route.collectAsState()

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        calls.permissionResult(result.values.all { it })
    }
    (state as? CallUi.NeedsPermission)?.let { need ->
        LaunchedEffect(need) {
            permissions.launch(buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (need.kind == CallKind.Video) add(Manifest.permission.CAMERA)
            }.toTypedArray())
        }
    }

    val current = state
    if (current is CallUi.Idle || current is CallUi.NeedsPermission) return
    val remote by (session?.remoteVideo ?: remember { kotlinx.coroutines.flow.MutableStateFlow<VideoTrack?>(null) }).collectAsState()
    val local by (session?.localVideo ?: remember { kotlinx.coroutines.flow.MutableStateFlow<VideoTrack?>(null) }).collectAsState()

    Box(Modifier.fillMaxSize().background(CallBackground)) {
        val active = current as? CallUi.Active
        if (active?.kind == CallKind.Video) {
            remote?.let { VideoView(it, Modifier.fillMaxSize(), mirror = false) }
            if (local != null && active.cameraOn) VideoView(checkNotNull(local),
                Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp).size(110.dp, 160.dp).clip(RoundedCornerShape(12.dp)),
                mirror = true, overlay = true)
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(peerOf(current), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(statusOf(current), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            when (current) {
                is CallUi.Incoming -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton("Decline", Danger) { calls.decline() }
                    RoundButton("Answer", Accept) { calls.accept() }
                }
                is CallUi.Outgoing -> RoundButton("Cancel", Danger) { calls.hangup() }
                is CallUi.Active -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Toggle(if (current.muted) "Unmute" else "Mute", current.muted) { calls.toggleMute() }
                        Toggle("Speaker", route == AudioRoute.Speaker) { calls.toggleSpeaker() }
                        if (current.kind == CallKind.Video) {
                            Toggle(if (current.cameraOn) "Camera off" else "Camera on", !current.cameraOn) { calls.toggleCamera() }
                            Toggle("Flip", false) { calls.switchCamera() }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    RoundButton("End", Danger) { calls.hangup() }
                }
                else -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun statusOf(state: CallUi): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val connectedAt = (state as? CallUi.Active)?.connectedAt
    LaunchedEffect(connectedAt) { while (connectedAt != null) { now = System.currentTimeMillis(); delay(1_000) } }
    return when (state) {
        is CallUi.Incoming -> if (state.kind == CallKind.Video) "Incoming video call" else "Incoming voice call"
        is CallUi.Outgoing -> if (state.callId == null) "Calling…" else "Ringing…"
        is CallUi.Active -> when (state.media) {
            MediaState.Connecting -> "Connecting…"
            MediaState.Reconnecting -> "Reconnecting…"
            MediaState.Failed -> "Call dropped"
            MediaState.Connected -> CallProtocol.duration(((now - (connectedAt ?: now)) / 1000).toInt())
        }
        is CallUi.Ended -> state.message
        else -> ""
    }
}

private fun peerOf(state: CallUi): String = when (state) {
    is CallUi.Incoming -> state.peer
    is CallUi.Outgoing -> state.peer
    is CallUi.Active -> state.peer
    else -> ""
}

@Composable
private fun RoundButton(label: String, color: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        modifier = Modifier.height(56.dp)) { Text(label) }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, colors = if (on) ButtonDefaults.filledTonalButtonColors(
        containerColor = Color.White, contentColor = Color.Black) else ButtonDefaults.filledTonalButtonColors()) { Text(label) }
}

@Composable
private fun VideoView(track: VideoTrack, modifier: Modifier, mirror: Boolean, overlay: Boolean = false) {
    val holder = remember { arrayOfNulls<SurfaceViewRenderer>(1) }
    AndroidView(modifier = modifier, factory = { context ->
        SurfaceViewRenderer(context).apply {
            init(RtcEngine.egl.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(mirror)
            setZOrderMediaOverlay(overlay)
            holder[0] = this
        }
    })
    DisposableEffect(track) {
        val view = holder[0]
        view?.let(track::addSink)
        onDispose { view?.let { runCatching { track.removeSink(it) } } }
    }
    DisposableEffect(Unit) { onDispose { holder[0]?.release() } }
}
