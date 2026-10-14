package app.web.oneonone

import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import app.web.oneonone.ui.chat.PartnerPhoto
import android.content.Intent
import android.os.Build
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.web.oneonone.ui.chat.SharedContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.web.oneonone.alarm.AlarmService
import app.web.oneonone.call.CallManager
import app.web.oneonone.call.CallNotifications
import app.web.oneonone.call.CallUi
import app.web.oneonone.ui.call.CallOverlay
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import app.web.oneonone.ui.AppNavigation
import app.web.oneonone.ui.ProfileViewModel
import app.web.oneonone.ui.AppViewModel
import app.web.oneonone.ui.BootRoute
import app.web.oneonone.ui.chat.ChatViewModel
import app.web.oneonone.ui.chat.FeatureViewModel
import app.web.oneonone.ui.theme.OneColors
import app.web.oneonone.ui.theme.OneOnOneTheme
import dagger.hilt.android.AndroidEntryPoint
import app.web.oneonone.push.PushRegistration
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private val featureViewModel: FeatureViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    @Inject lateinit var pushRegistration: PushRegistration
    @Inject lateinit var calls: CallManager
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val splashStartedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition {
            viewModel.state.value.route == BootRoute.Loading && SystemClock.uptimeMillis() - splashStartedAt < 3_000
        }
        enableEdgeToEdge()
        handleAlarmIntent(intent)
        handleCallIntent(intent)
        if (savedInstanceState == null) handleShareIntent(intent) // a recreate must not paste it twice
        lifecycleScope.launch {
            // Alarm over and no call: stop forcing the screen on/over the lock.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(AlarmService.ringing, calls.state) { alarm, call -> alarm != null || call !is CallUi.Idle }
                    .collect { if (!it) showOverLock(false) }
            }
        }
        lifecycleScope.launch {
            // In a call the volume keys set the call volume, not media (which is silent then).
            calls.state.collect { volumeControlStream = if (it is CallUi.Active || it is CallUi.Outgoing) AudioManager.STREAM_VOICE_CALL
                else AudioManager.USE_DEFAULT_STREAM_TYPE }
        }
        setContent {
            val dark by viewModel.darkTheme.collectAsState()
            val state by viewModel.state.collectAsState()
            val launchDark = dark || state.route == BootRoute.Loading
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !launchDark
                    isAppearanceLightNavigationBars = !launchDark
                }
                // Keep the window behind Compose in the resolved theme so light mode never flashes dark.
                window.setBackgroundDrawable((if (launchDark) OneColors.Dark else OneColors.Light).bg.toArgb().toDrawable())
            }
            OneOnOneTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box {
                        AppNavigation(viewModel, chatViewModel, featureViewModel, profileViewModel, pushRegistration)
                        val connection = state.connection
                        val avatars by profileViewModel.urls.collectAsState()
                        val inCall by remember { calls.state.map { it !is CallUi.Idle } }.collectAsState(false)
                        // Read when a call starts, so a photo changed in the chat is picked up. Off the main thread.
                        val ownPhoto by produceState<File?>(null, connection?.id, inCall) {
                            value = if (connection == null || !inCall) null else withContext(Dispatchers.IO) {
                                PartnerPhoto.file(this@MainActivity, connection.myUserId, connection.id).takeIf { it.isFile && it.length() > 0 }
                            }
                        }
                        CallOverlay(calls, avatar = ownPhoto?.let { it to "${it.path}@${it.lastModified()}" }
                            ?: connection?.otherAvatarId?.let { id -> avatars.other?.let { it to "avatar:$id" } })
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); CallNotifications.appVisible = true }
    override fun onStop() { CallNotifications.appVisible = false; super.onStop() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlarmIntent(intent)
        handleCallIntent(intent)
        handleShareIntent(intent)
    }

    // From the Android share sheet. Untrusted input: only content:// streams (never file://, which could
    // point at this app's private files), text capped at the message limit. Nothing sends until the user does.
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.takeIf { it.scheme == "content" }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()?.take(4_000)?.takeIf { it.isNotEmpty() }
        when {
            stream != null -> chatViewModel.share(SharedContent(null, stream.toString(), if (type.startsWith("image/")) "image" else "file"))
            text != null -> chatViewModel.share(SharedContent(text, null, null))
        }
    }

    // From the incoming-call notification: Answer, or the full-screen intent. Token-checked:
    // this activity is exported and an unchecked "answer" intent would pick up a call silently.
    private fun handleCallIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != CallNotifications.ACTION_ANSWER && action != CallNotifications.ACTION_SHOW) return
        val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID)
        val token = intent.getStringExtra(CallNotifications.EXTRA_TOKEN)
        intent.removeExtra(CallNotifications.EXTRA_TOKEN)
        intent.removeExtra(CallNotifications.EXTRA_CALL_ID)
        if (callId != null && CallNotifications.tokenValid(token, callId)) {
            showOverLock(true)
            if (action == CallNotifications.ACTION_ANSWER) calls.answerFromNotification(callId)
        }
    }

    // From the alarm notification. This activity is exported, so every extra is untrusted:
    // nothing happens unless the per-ring token our own service put in its PendingIntents
    // matches. Extras are stripped so a recreate can't replay them. It doesn't navigate or
    // silence: the normal boot routing opens the chat, where the alarm is acknowledged.
    private fun handleAlarmIntent(intent: Intent?) {
        if (intent == null) return
        val token = intent.getStringExtra(AlarmService.EXTRA_TOKEN)
        val show = intent.getBooleanExtra(AlarmService.EXTRA_SHOW, false)
        val alarmId = intent.getStringExtra(AlarmService.EXTRA_ALARM_ID)?.takeIf { it.length <= 64 }
        val raisedAt = intent.getLongExtra(AlarmService.EXTRA_RAISED_AT, 0L)
        listOf(AlarmService.EXTRA_TOKEN, AlarmService.EXTRA_SHOW, AlarmService.EXTRA_ALARM_ID, AlarmService.EXTRA_RAISED_AT).forEach(intent::removeExtra)
        if (!show || !AlarmService.tokenValid(token) || alarmId == null) return
        showOverLock(true)
        // Backup notification only (service start was refused in the background): now in the foreground, ring for real.
        if (AlarmService.fallbackId == alarmId && raisedAt > 0) runCatching {
            ContextCompat.startForegroundService(this, AlarmService.ringIntent(this, alarmId, raisedAt))
        }
    }

    private fun showOverLock(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        }
    }
}
