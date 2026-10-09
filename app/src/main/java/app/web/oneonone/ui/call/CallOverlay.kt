package app.web.oneonone.ui.call

import android.Manifest
import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.web.oneonone.ui.ScreenThemePreviews
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.web.oneonone.call.AudioRoute
import app.web.oneonone.call.CallKind
import app.web.oneonone.call.CallManager
import app.web.oneonone.call.CallProtocol
import app.web.oneonone.call.CallUi
import app.web.oneonone.call.MediaState
import app.web.oneonone.call.RtcEngine
import app.web.oneonone.R
import app.web.oneonone.ui.components.pressScale
import app.web.oneonone.ui.theme.OneTheme
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneOnOneTheme
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

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

    CallContent(current, route, statusOf(current), animationsEnabled(), remote != null,
        onDecline = { calls.decline() }, onAccept = { calls.accept() }, onHangup = { calls.hangup() },
        onMute = { calls.toggleMute() }, onSpeaker = { calls.toggleSpeaker() },
        onCamera = { calls.toggleCamera() }, onFlip = { calls.switchCamera() },
        remote = { remote?.let { VideoView(it, Modifier.fillMaxSize(), mirror = false) } },
        local = { modifier -> if (local != null) VideoView(checkNotNull(local), modifier, mirror = true, overlay = true) })
}

@Composable
private fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
private fun CallContent(current: CallUi, route: AudioRoute, status: String, animate: Boolean, remoteLive: Boolean,
    onDecline: () -> Unit, onAccept: () -> Unit, onHangup: () -> Unit, onMute: () -> Unit,
    onSpeaker: () -> Unit, onCamera: () -> Unit, onFlip: () -> Unit,
    remote: @Composable () -> Unit, local: @Composable (Modifier) -> Unit) {
    val c = OneTheme.colors
    val motion = OneTheme.motion
    val enabled = animate && !LocalInspectionMode.current
    val enter = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(enabled) {
        if (enabled) enter.animateTo(1f, tween(motion.slow320, easing = motion.emphasized)) else enter.snapTo(1f)
    }
    val active = current as? CallUi.Active
    val video = active?.kind == CallKind.Video
    val reconnecting = active?.media == MediaState.Reconnecting
    val ringing = current is CallUi.Incoming || current is CallUi.Outgoing
    val shadow = with(LocalDensity.current) { Shadow(c.scrim, Offset(0f, 1.dp.toPx()), 6.dp.toPx()) }
    val pulse = if (enabled && (ringing || reconnecting)) {
        val transition = rememberInfiniteTransition(label = "call pulse")
        val value by transition.animateFloat(0f, 1f,
            infiniteRepeatable(keyframes {
                durationMillis = if (reconnecting) 1100 else 2000
                0f at 0 using motion.standard
                1f at (durationMillis * .7f).toInt()
                1f at durationMillis
            }), label = "ring spread")
        value
    } else 0f
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer {
        alpha = enter.value
        translationY = 8.dp.toPx() * (1f - enter.value)
    }.background(c.bg)) {
        val avatarSize = minOf(168.dp, maxWidth * .44f)
        val avatarFont = (maxWidth.value * .18f).coerceIn(48f, 64f).sp
        val previewWidth = minOf(112.dp, maxWidth * .28f)
        if (video) {
            remote()
            if (active.cameraOn) local(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(OneTheme.spacing.lg16)
                .width(previewWidth).aspectRatio(3f / 4f).clip(RoundedCornerShape(OneTheme.radii.md10))
                .border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(OneTheme.radii.md10)))
        }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
            .padding(horizontal = OneTheme.spacing.xl24, vertical = OneTheme.spacing.xxl32),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
                Text(peerOf(current), style = if (video && remoteLive) OneTextStyles.callName.copy(shadow = shadow) else OneTextStyles.callName, color = if (video && remoteLive) Color.White else c.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(status, style = if (video && remoteLive) OneTextStyles.callStatus.copy(shadow = shadow) else OneTextStyles.callStatus, color = if (video && remoteLive) Color.White.copy(alpha = .85f) else c.textDim)
            }
            if (!video || !remoteLive) Box(Modifier.padding(vertical = 24.dp).defaultMinSize(minHeight = avatarSize + 52.dp), contentAlignment = Alignment.Center) {
                val connected = active?.media == MediaState.Connected
                val ringColor = when {
                    !enabled && (ringing || reconnecting) -> c.accentOther
                    reconnecting -> c.muted
                    connected -> c.accentOther
                    else -> c.border
                }
                Box(Modifier.size(avatarSize).drawBehind {
                    if (enabled && (ringing || reconnecting)) drawCircle(c.accentOther.copy(alpha = .3f * (1f - pulse)), radius = size.minDimension / 2 + 26.dp.toPx() * pulse)
                }.clip(CircleShape).background(c.bgRaised).border(1.dp, ringColor, CircleShape), contentAlignment = Alignment.Center) {
                    Text(peerOf(current).take(1).uppercase(), style = OneTextStyles.callName.copy(fontSize = avatarFont, lineHeight = 70.sp),
                        color = if (connected) c.accentOther else c.textDim)
                }
            } else Spacer(Modifier.height(24.dp))
            if (current !is CallUi.Ended) Surface(Modifier.widthIn(max = 400.dp).fillMaxWidth(), color = c.bgRaised,
                shape = RoundedCornerShape(OneTheme.radii.lg16), border = BorderStroke(1.dp, c.border), shadowElevation = OneTheme.elevation.e3) {
                Row(Modifier.padding(start = 12.dp, top = 32.dp, end = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (current) {
                        is CallUi.Incoming -> {
                            CallControl("Decline", R.drawable.ic_phone_off, c.danger, Color.White, onDecline, Modifier.weight(1f))
                            CallControl("Answer", if (current.kind == CallKind.Video) R.drawable.ic_video else R.drawable.ic_phone, c.accentYou, c.onPrimary, onAccept, Modifier.weight(1f))
                        }
                        is CallUi.Outgoing -> CallControl("Cancel", R.drawable.ic_phone_off, c.danger, Color.White, onHangup, Modifier.weight(1f))
                        is CallUi.Active -> {
                            CallControl(if (current.muted) "Unmute" else "Mute", if (current.muted) R.drawable.ic_mic_off else R.drawable.ic_mic,
                                if (current.muted) c.text else c.bg, if (current.muted) c.bg else c.text, onMute, Modifier.weight(1f))
                            CallControl("Speaker", R.drawable.ic_volume, if (route == AudioRoute.Speaker) c.text else c.bg,
                                if (route == AudioRoute.Speaker) c.bg else c.text, onSpeaker, Modifier.weight(1f))
                            if (current.kind == CallKind.Video) {
                                CallControl(if (current.cameraOn) "Camera off" else "Camera on", if (current.cameraOn) R.drawable.ic_video else R.drawable.ic_video_off,
                                    if (!current.cameraOn) c.text else c.bg, if (!current.cameraOn) c.bg else c.text, onCamera, Modifier.weight(1f))
                                CallControl("Flip", R.drawable.ic_camera_flip, c.bg, c.text, onFlip, Modifier.weight(1f))
                            }
                            CallControl("End", R.drawable.ic_phone_off, c.danger, Color.White, onHangup, Modifier.weight(1f))
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun CallControl(label: String, @DrawableRes icon: Int, background: Color, foreground: Color, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = onClick, modifier = Modifier.widthIn(max = 62.dp).fillMaxWidth().aspectRatio(1f).pressScale(source), shape = CircleShape,
            color = background, contentColor = foreground, border = BorderStroke(1.dp, if (background == OneTheme.colors.bg) OneTheme.colors.border else background), interactionSource = source) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), label, Modifier.size(26.dp), tint = foreground) }
        }
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = OneTheme.colors.textDim,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
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

@Composable
private fun CallPreview(dark: Boolean, state: CallUi, status: String, video: Boolean = false) {
    OneOnOneTheme(darkTheme = dark) {
        CallContent(state, AudioRoute.Speaker, status, false, video, {}, {}, {}, {}, {}, {}, {},
            remote = { Box(Modifier.fillMaxSize().background(OneTheme.colors.scrim), contentAlignment = Alignment.Center) { Text("Remote video preview", color = Color.White) } },
            local = { modifier -> Box(modifier.background(OneTheme.colors.bgRaised), contentAlignment = Alignment.Center) { Text("Your camera", color = OneTheme.colors.text) } })
    }
}

@Preview(name = "Call ringing", widthDp = 390, heightDp = 844)
@Composable private fun RingingPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    CallPreview(dark, CallUi.Incoming("preview", CallKind.Audio, "Alex"), "Incoming voice call")

@Preview(name = "Call connected", widthDp = 390, heightDp = 844)
@Composable private fun ConnectedPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    CallPreview(dark, CallUi.Active("preview", CallKind.Audio, "Alex", MediaState.Connected, null, false, true), "2:25")

@Preview(name = "Call reconnecting", widthDp = 390, heightDp = 844)
@Composable private fun ReconnectingPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    CallPreview(dark, CallUi.Active("preview", CallKind.Audio, "Alex", MediaState.Reconnecting, null, true, true), "Reconnecting…")

@Preview(name = "Call video", widthDp = 390, heightDp = 844)
@Preview(name = "Call video landscape", widthDp = 844, heightDp = 390)
@Composable private fun VideoPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    CallPreview(dark, CallUi.Active("preview", CallKind.Video, "Alex", MediaState.Connected, null, false, true), "2:25", video = true)

@Preview(name = "Ringing motion (interactive)", widthDp = 390, heightDp = 844)
@Composable private fun RingingMotionPreview() {
    OneOnOneTheme(darkTheme = true) {
        CallContent(CallUi.Incoming("preview", CallKind.Audio, "Alex"), AudioRoute.Earpiece, "Incoming voice call", animationsEnabled(), false,
            {}, {}, {}, {}, {}, {}, {}, remote = {}, local = {})
    }
}
