package app.web.oneonone.ui.chat.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.call.CallProtocol
import app.web.oneonone.data.games.GameKind
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.ui.chat.LocalBubbleColors
import app.web.oneonone.ui.games.DrawReasons
import app.web.oneonone.ui.games.gameVector
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** "Chess" and "You won · 4 min 12 sec" for a server-authored `game` row (`{game, inviter, winner, reason, durationSec}`). */
internal fun gameLogText(message: ChatMessage, me: String, nickname: String): Pair<String, String> {
    val payload = message.payload
    fun text(key: String) = (payload?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val game = GameKind.entries.firstOrNull { it.wire == text("game") }?.title ?: "Game"
    val seconds = (payload?.get("durationSec") as? JsonPrimitive)?.intOrNull ?: 0
    val result = when (text("winner")) {
        me -> "You won"
        null -> if (text("reason") in DrawReasons) "A draw" else "Game ended"
        else -> "$nickname won"
    }
    return game to "$result · ${CallProtocol.spokenDuration(seconds)}"
}

@Composable
fun GameLogCard(message: ChatMessage, me: String, nickname: String) {
    val (heading, result) = gameLogText(message, me, nickname)
    val colors = LocalBubbleColors.current
    val wire = (message.payload?.get("game") as? JsonPrimitive)?.contentOrNull.orEmpty()
    val icon = remember(wire, colors.text) { gameVector(wire, colors.text) }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(OneTheme.colors.discBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = colors.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(heading, style = OneTextStyles.cardHeading, color = colors.text)
            Text(result, style = OneTextStyles.cardHint.copy(fontSize = 12.sp), color = colors.text.copy(alpha = .7f))
        }
    }
}
