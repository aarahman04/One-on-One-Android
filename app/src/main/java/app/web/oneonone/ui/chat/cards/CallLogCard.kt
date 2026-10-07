package app.web.oneonone.ui.chat.cards

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.web.oneonone.data.model.ChatMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Frozen signature; Claude owns this file after A2. No calling behavior in this placeholder. */
@Composable
fun CallLogCard(message: ChatMessage, isMine: Boolean, onSend: (type: String, payload: JsonObject, replyTo: String?) -> Unit) {
    Text("${if (isMine) "Outgoing" else "Incoming"} ${message.payload?.get("kind")?.jsonPrimitive?.content ?: "audio"} call")
}
