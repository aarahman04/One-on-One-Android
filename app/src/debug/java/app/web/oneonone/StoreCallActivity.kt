package app.web.oneonone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.web.oneonone.call.AudioRoute
import app.web.oneonone.call.CallKind
import app.web.oneonone.call.CallUi
import app.web.oneonone.call.MediaState
import app.web.oneonone.ui.call.CallContent
import app.web.oneonone.ui.theme.OneOnOneTheme

/** The real call layout with fictional video frames; no camera, microphone or network session. */
class StoreCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneOnOneTheme(darkTheme = true) {
                CallContent(
                    CallUi.Active("store", CallKind.Video, "Arjun", MediaState.Connected, null, false, true),
                    AudioRoute.Speaker, "3:42", false, true, {}, {}, {}, {}, {}, {}, {},
                    remote = { Image(painterResource(R.drawable.store_arjun), "Arjun's video",
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                    local = { modifier -> Image(painterResource(R.drawable.store_maya), "Maya's camera",
                        modifier, contentScale = ContentScale.Crop) },
                )
            }
        }
    }
}
