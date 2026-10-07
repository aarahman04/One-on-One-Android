package app.web.oneonone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.web.oneonone.ui.AppNavigation
import app.web.oneonone.ui.AppViewModel
import app.web.oneonone.ui.chat.ChatViewModel
import app.web.oneonone.ui.theme.OneOnOneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneOnOneTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppNavigation(viewModel, chatViewModel) }
            }
        }
    }
}
