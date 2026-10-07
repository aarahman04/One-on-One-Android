package app.web.oneonone.ui.chat.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.web.oneonone.alarm.AlarmCardState
import app.web.oneonone.alarm.AlarmPolicy
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.model.ChatMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@EntryPoint @InstallIn(SingletonComponent::class)
interface AlarmCardEntryPoint { fun messages(): MessageService }

private val AlarmRed = Color(0xFFE5484D)

/**
 * Raise: tappable only once the server has given it an id (never acks a temp id).
 * Raiser cancels, the other member acknowledges; the server enforces both. The card
 * state is derived from the conversation, so it flips only on the server's echo.
 * Ack/cancel rows render as a small confirmation line.
 */
@Composable
fun AlarmCard(message: ChatMessage, isMine: Boolean, onSend: (type: String, payload: JsonObject, replyTo: String?) -> Unit) {
    if (AlarmPolicy.ackOf(message) != null) {
        val who = if (isMine) "You" else "They"
        Text(if (AlarmPolicy.isCancel(message)) "⛔ $who cancelled the alarm" else "✅ $who acknowledged the alarm",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
        return
    }
    val context = LocalContext.current.applicationContext
    val service = remember { EntryPointAccessors.fromApplication(context, AlarmCardEntryPoint::class.java).messages() }
    val all by service.messages.collectAsState(initial = emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val state = AlarmPolicy.cardState(message, all, now)
    LaunchedEffect(message.id, state) {
        // Re-evaluate once the window closes so a live card turns "expired" on its own.
        if (state == AlarmCardState.Live || state == AlarmCardState.Failed) {
            delay((AlarmPolicy.createdAtMs(message) + AlarmPolicy.WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0) + 500)
            now = System.currentTimeMillis()
        }
    }
    val tappable = state == AlarmCardState.Live || state == AlarmCardState.Failed
    val hint = when (state) {
        AlarmCardState.Sending -> "sending…"
        AlarmCardState.Live -> if (isMine) "tap to cancel" else "tap to acknowledge"
        AlarmCardState.Pending -> if (isMine) "cancelling…" else "acknowledging…"
        AlarmCardState.Failed -> "didn't send — tap to try again"
        AlarmCardState.Acknowledged -> "acknowledged"
        AlarmCardState.Cancelled -> "cancelled"
        AlarmCardState.Expired -> "expired"
    }
    val live = state == AlarmCardState.Live || state == AlarmCardState.Pending || state == AlarmCardState.Failed
    OutlinedCard(
        onClick = {
            val raiseId = message.id ?: return@OutlinedCard
            val payload = buildMap {
                put("ack", JsonPrimitive(raiseId))
                if (isMine) put("cancelled", JsonPrimitive(true))
            }
            onSend("alarm", JsonObject(payload), raiseId)
        },
        enabled = tappable,
        border = BorderStroke(if (live) 2.dp else 1.dp, if (live) AlarmRed else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🚨", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Emergency alarm", fontWeight = FontWeight.SemiBold)
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
