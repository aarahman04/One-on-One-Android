package app.web.oneonone.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.data.model.ReactionSummary
import app.web.oneonone.ui.theme.BubblePalette
import app.web.oneonone.ui.theme.BubbleTokens
import app.web.oneonone.ui.theme.OneOnOneTheme
import app.web.oneonone.ui.theme.OneTheme

// Previews stand in for screenshots (no device capture in CI). Open this file in Android Studio's preview pane.

private fun msg(sender: String, text: String, at: String, id: String? = at + sender, replyTo: String? = null,
                reactions: List<ReactionSummary> = emptyList(), state: String = "sent") =
    ChatMessage(id = id, senderId = sender, content = text, createdAt = at, replyTo = replyTo, reactions = reactions, tempId = id ?: at, deliveryState = state)

@Composable
private fun Host(dark: Boolean, content: @Composable () -> Unit) {
    OneOnOneTheme(darkTheme = dark) {
        Surface(color = OneTheme.colors.bg) { Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalArrangement = Arrangement.Top) { content() } }
    }
}

@Composable
private fun PreviewBubble(m: ChatMessage, mine: Boolean, palette: BubblePalette, groupStart: Boolean = true, receipt: String = "Read", quote: BubbleQuote? = null) =
    MessageBubble(m, mine, palette, receipt, groupStart = groupStart, animateIn = false, quote = quote,
        onReply = {}, onReact = {}, onRetry = {}, onQuote = {}, onReport = {}, card = { Text("card") })

@Composable
private fun Conversation(palette: BubblePalette) {
    val a = "2026-10-10T10:00:00Z"
    PreviewBubble(msg("them", "Morning! Did you see the link I sent?", a), false, palette)
    PreviewBubble(msg("them", "https://example.com/very/long/path/that/should/wrap/nicely", "2026-10-10T10:00:20Z"), false, palette, groupStart = false)
    PreviewBubble(msg("me", "Yes, reading it now", "2026-10-10T10:02:00Z"), true, palette, receipt = "Delivered",
        quote = BubbleQuote("Them", "Morning! Did you see the link I sent?"))
    PreviewBubble(msg("me", "Love it", "2026-10-10T10:02:30Z", reactions = listOf(ReactionSummary("❤️", listOf("them")), ReactionSummary("👍", listOf("me", "them")))), true, palette, groupStart = false, receipt = "Sent")
    PreviewBubble(msg("me", "Still sending…", "2026-10-10T10:03:00Z", id = null, state = "queued"), true, palette, receipt = "queued")
    PreviewBubble(msg("me", "This one failed to go out", "2026-10-10T10:04:00Z", id = null, state = "failed"), true, palette, receipt = "failed")
}

@Preview(name = "Bubbles dark", showBackground = true, widthDp = 380)
@Composable
private fun BubblesDark() = Host(true) { Conversation(BubbleTokens.Dark) }

@Preview(name = "Bubbles light", showBackground = true, widthDp = 380)
@Composable
private fun BubblesLight() = Host(false) { Conversation(BubbleTokens.Light) }

@Preview(name = "Bubbles love", showBackground = true, widthDp = 380)
@Composable
private fun BubblesLove() = Host(true) { Conversation(BubbleTokens.Love) }

@Preview(name = "Bubbles samurai", showBackground = true, widthDp = 380)
@Composable
private fun BubblesSamurai() = Host(true) { Conversation(BubbleTokens.Samurai) }

@Preview(name = "Header", showBackground = true, widthDp = 380)
@Composable
private fun HeaderPreview() = Host(true) {
    ChatHeader("Alex", ChatPresence("Online", online = true), true, {}, {}, {}, {})
    ChatHeader("A very long nickname that has to be truncated with an ellipsis", ChatPresence("Connecting…"), true, {}, {}, {}, {})
}

@Preview(name = "Menu dark", showBackground = true, widthDp = 380, heightDp = 360)
@Composable
private fun MenuDarkPreview() = Host(true) { ChatMenu(true, {}, {}, {}, {}, {}, {}, {}, {}) }

@Preview(name = "Menu light", showBackground = true, widthDp = 380, heightDp = 360)
@Composable
private fun MenuLightPreview() = Host(false) { ChatMenu(true, {}, {}, {}, {}, {}, {}, {}, {}) }

@Preview(name = "Composer empty", showBackground = true, widthDp = 380)
@Composable
private fun ComposerEmpty() = Host(true) { ChatComposer("", {}, true, false, {}, {}, {}) }

@Preview(name = "Composer with text + reply", showBackground = true, widthDp = 380)
@Composable
private fun ComposerText() = Host(true) {
    ReplyBar("Them", "Morning! Did you see the link I sent?") {}
    ChatComposer("Yes, reading it now", {}, true, false, {}, {}, {})
}

@Preview(name = "Slash menu open", showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun SlashOpen() = Host(false) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { SlashMenu(slashMatches("/"), 220.dp, {}) }
    ChatComposer("/", {}, true, false, {}, {}, {})
}

@Preview(name = "Composer recording", showBackground = true, widthDp = 380)
@Composable
private fun ComposerRecording() = Host(true) { ChatComposer("", {}, true, true, {}, {}, {}, elapsedSeconds = 75) }

@Preview(name = "Attach sheet", showBackground = true, widthDp = 380)
@Composable
private fun AttachSheetPreview() = Host(true) {
    AttachSheet(true, Modifier, {}, {})
    ChatComposer("", {}, true, false, {}, {}, {})
}
