package app.web.oneonone.ui.chat.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.web.oneonone.call.CallProtocol
import app.web.oneonone.data.model.ChatMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Server-authored call log row (`{kind, outcome, durationSec}`); `isMine` = I was the caller. */
@Composable
fun CallLogCard(message: ChatMessage, isMine: Boolean, onSend: (type: String, payload: JsonObject, replyTo: String?) -> Unit) {
    val payload = message.payload
    val kind = (payload?.get("kind") as? JsonPrimitive)?.contentOrNull
    val outcome = (payload?.get("outcome") as? JsonPrimitive)?.contentOrNull
    val seconds = (payload?.get("durationSec") as? JsonPrimitive)?.intOrNull ?: 0
    val missed = !isMine && outcome in setOf("missed", "cancelled", "unreachable")
    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (kind == "video") "📹" else "📞")
        Spacer(Modifier.width(8.dp))
        Text(CallProtocol.logText(kind, outcome, seconds, isMine), style = MaterialTheme.typography.bodyMedium,
            color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}
