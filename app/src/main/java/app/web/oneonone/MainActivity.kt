package app.web.oneonone

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
