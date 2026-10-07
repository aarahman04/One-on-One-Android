package app.web.oneonone

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.web.oneonone.alarm.AlarmCoordinator
import app.web.oneonone.alarm.AlarmService
import kotlinx.coroutines.launch
import app.web.oneonone.ui.AppNavigation
import app.web.oneonone.ui.AppViewModel
import app.web.oneonone.ui.chat.ChatViewModel
import app.web.oneonone.ui.chat.FeatureViewModel
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAlarmIntent(intent)
        lifecycleScope.launch {
            // Alarm over (acked, cancelled, silenced, auto-cleared): stop forcing the screen on/over the lock.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AlarmService.ringing.collect { if (it == null) showOverLock(false) }
            }
        }
        setContent {
            val dark by viewModel.darkTheme.collectAsState()
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            OneOnOneTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) { AppNavigation(viewModel, chatViewModel, featureViewModel, pushRegistration) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlarmIntent(intent)
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
