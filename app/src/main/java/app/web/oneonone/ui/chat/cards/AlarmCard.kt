package app.web.oneonone.ui.chat.cards

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.web.oneonone.data.model.ChatMessage
import kotlinx.serialization.json.JsonObject

/** Frozen signature; Claude owns this file after A2. No alarm behavior in this placeholder. */
@Composable
fun AlarmCard(message: ChatMessage, isMine: Boolean, onSend: (type: String, payload: JsonObject, replyTo: String?) -> Unit) {
    Text(if (message.id == null) "Emergency alarm — sending…" else "Emergency alarm")
}
