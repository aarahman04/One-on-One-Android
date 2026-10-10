package app.web.oneonone

import android.content.Intent
import android.os.Build
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.web.oneonone.alarm.AlarmCoordinator
import app.web.oneonone.alarm.AlarmService
import app.web.oneonone.call.CallManager
import app.web.oneonone.call.CallNotifications
import app.web.oneonone.call.CallUi
import app.web.oneonone.ui.call.CallOverlay
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import app.web.oneonone.ui.AppNavigation
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
    @Inject lateinit var pushRegistration: PushRegistration
    @Inject lateinit var alarms: AlarmCoordinator
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
        lifecycleScope.launch {
            // Alarm over and no call: stop forcing the screen on/over the lock.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(AlarmService.ringing, calls.state) { alarm, call -> alarm != null || call !is CallUi.Idle }
                    .collect { if (!it) showOverLock(false) }
            }
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
                        AppNavigation(viewModel, chatViewModel, featureViewModel, pushRegistration)
                        CallOverlay(calls)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlarmIntent(intent)
        handleCallIntent(intent)
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
    // matches. Extras are stripped so a recreate can't replay them. Neither path navigates;
    // the normal boot routing opens the chat.
    private fun handleAlarmIntent(intent: Intent?) {
        if (intent == null) return
        val token = intent.getStringExtra(AlarmService.EXTRA_TOKEN)
        val silence = intent.getBooleanExtra(AlarmService.EXTRA_SILENCE, false)
        val show = intent.getBooleanExtra(AlarmService.EXTRA_SHOW, false)
        val alarmId = intent.getStringExtra(AlarmService.EXTRA_ALARM_ID)?.takeIf { it.length <= 64 }
        listOf(AlarmService.EXTRA_TOKEN, AlarmService.EXTRA_SILENCE, AlarmService.EXTRA_SHOW, AlarmService.EXTRA_ALARM_ID)
            .forEach(intent::removeExtra)
        if (!(silence || show) || !AlarmService.tokenValid(token) || alarmId == null) return
        if (silence) {
            alarms.stop(alarmId)
            showOverLock(false)
        } else showOverLock(true)
    }

    private fun showOverLock(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        }
    }
}
