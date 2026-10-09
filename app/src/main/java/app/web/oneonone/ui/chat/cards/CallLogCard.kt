package app.web.oneonone.ui.chat.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import app.web.oneonone.R
import app.web.oneonone.ui.chat.LocalBubbleColors
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
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
    val text = CallProtocol.logText(kind, outcome, seconds, isMine)
    val icon = if (kind == "video") {
        if (isMine) R.drawable.ic_call_log_video_out else R.drawable.ic_call_log_video_in
    } else if (isMine) R.drawable.ic_call_log_phone_out else R.drawable.ic_call_log_phone_in
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(OneTheme.colors.discBg), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), if (kind == "video") "📹" else "📞", Modifier.size(19.dp),
                tint = if (missed) OneTheme.colors.danger else LocalBubbleColors.current.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text.substringBefore(" · "), style = OneTextStyles.cardHeading, color = LocalBubbleColors.current.text)
            val subtitle = text.substringAfter(" · ", "")
            if (subtitle.isNotBlank()) Text(subtitle, style = OneTextStyles.cardHint.copy(fontSize = 12.sp), color = LocalBubbleColors.current.text.copy(alpha = .7f))
        }
    }
}
