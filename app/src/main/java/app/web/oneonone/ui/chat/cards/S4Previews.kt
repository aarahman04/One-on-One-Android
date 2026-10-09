package app.web.oneonone.ui.chat.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.ui.ScreenThemePreviews
import app.web.oneonone.ui.chat.*
import app.web.oneonone.ui.components.PrimaryButton
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.theme.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CardPalettePreviews : PreviewParameterProvider<BubblePalette> {
    override val values = sequenceOf(BubbleTokens.Dark, BubbleTokens.Light, BubbleTokens.Love, BubbleTokens.Samurai)
    override fun getDisplayName(index: Int) = listOf("Dark", "Light", "Love", "Samurai")[index]
}

private val PreviewConnection = CurrentConnection("preview", "active", "me", false, "Alex", "K7F29PQ2", 0, 0, null, false, false, null, null, "off", "bubbles")

private fun sample(type: String, payload: JsonObject = buildJsonObject {}, content: String = "") =
    ChatMessage("preview", "them", content, "2026-10-10T10:00:00Z", type, payload)

@Composable
private fun CardPreview(palette: BubblePalette, message: ChatMessage, content: @Composable (Boolean) -> Unit) {
    OneOnOneTheme(darkTheme = palette != BubbleTokens.Light) {
        Column(Modifier.fillMaxSize().background(OneTheme.colors.bg).safeDrawingPadding().padding(6.dp)) {
            listOf(false, true).forEach { mine ->
                val colors = if (mine) palette.mine else palette.other
                CompositionLocalProvider(LocalBubbleColors provides colors, LocalContentColor provides colors.text, LocalInspectionMode provides true) {
                    MessageBubble(message, mine, palette, "Read", true, false, null, {}, {}, {}, {}, {}, card = { content(mine) })
                }
            }
        }
    }
}

@Composable
private fun FeaturePreview(palette: BubblePalette, message: ChatMessage) {
    CardPreview(palette, message) { mine ->
        FeatureCardContent(message, mine, null, PreviewConnection, false, null, if (message.type == "image") "preview" else null, null, { _, _, _, _, _ -> }, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Letter card", widthDp = 390)
@Composable private fun LetterCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("letter", buildJsonObject { put("to", "Alex"); put("from", "Sam"); put("appearance", "dawn") }, "Thinking of you."))

@Preview(name = "Countdown card", widthDp = 390)
@Composable private fun CountdownCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("countdown", buildJsonObject { put("label", "Our next adventure"); put("targetIso", "2099-10-11T18:00:00Z") }))

@Preview(name = "Check-in card", widthDp = 390)
@Composable private fun CheckInCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("checkin", buildJsonObject { put("mood", "good"); put("note", "A quiet day and a long walk.") }))

@Preview(name = "Ask sealed", widthDp = 390)
@Composable private fun AskCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("ask", buildJsonObject { put("question", "What made you smile today?"); put("answerA", "Your message.") }))

@Preview(name = "Ask revealed", widthDp = 390)
@Composable private fun AskRevealedPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("ask", buildJsonObject { put("question", "What made you smile today?"); put("answerA", "Your message."); put("answerB", "Our plans.") }))

@Preview(name = "This or that sealed", widthDp = 390)
@Composable private fun ThisOrThatPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("thisorthat", buildJsonObject { put("optionA", "Mountains"); put("optionB", "Ocean"); put("pickSender", "a") }))

@Preview(name = "This or that revealed", widthDp = 390)
@Composable private fun ThisOrThatRevealedPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("thisorthat", buildJsonObject { put("optionA", "Mountains"); put("optionB", "Ocean"); put("pickSender", "a"); put("pickRecipient", "b") }))

@Preview(name = "Location card", widthDp = 390)
@Composable private fun LocationCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("location", buildJsonObject { put("lat", 12.9716); put("lng", 77.5946); put("accuracy", 24) }))

@Preview(name = "Photo card (media placeholder)", widthDp = 390)
@Composable private fun ImageCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("image", buildJsonObject { put("path", "preview/photo.jpg"); put("name", "photo.jpg"); put("mime", "image/jpeg") }))

@Preview(name = "Voice card", widthDp = 390)
@Composable private fun VoiceCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("voice", buildJsonObject { put("path", "preview/voice.m4a"); put("duration", 18) }))

@Preview(name = "File card", widthDp = 390)
@Composable private fun FileCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) =
    FeaturePreview(palette, sample("file", buildJsonObject { put("path", "preview/plans.pdf"); put("name", "Weekend plans.pdf"); put("size", 204800); put("mime", "application/pdf") }))

@Preview(name = "Alarm card", widthDp = 390)
@Composable private fun AlarmCardPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) {
    CardPreview(palette, sample("alarm")) { mine -> AlarmContent(if (mine) "tap to cancel" else "tap to acknowledge", true, {}) }
}

@Preview(name = "Alarm acknowledgment", widthDp = 390)
@Composable private fun AlarmAckPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) {
    val message = sample("alarm", buildJsonObject { put("ack", "raised") })
    CardPreview(palette, message) { mine -> AlarmCard(message, mine, { _, _, _ -> }) }
}

@Preview(name = "Voice call log", widthDp = 390)
@Composable private fun CallLogPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) {
    val message = sample("call", buildJsonObject { put("kind", "audio"); put("outcome", "completed"); put("durationSec", 145) })
    CardPreview(palette, message) { mine -> CallLogCard(message, mine, { _, _, _ -> }) }
}

@Preview(name = "Missed video call log", widthDp = 390)
@Composable private fun MissedVideoLogPreview(@PreviewParameter(CardPalettePreviews::class) palette: BubblePalette) {
    val message = sample("call", buildJsonObject { put("kind", "video"); put("outcome", "missed") })
    CardPreview(palette, message) { mine -> CallLogCard(message, mine, { _, _, _ -> }) }
}

@Composable private fun DialogPreview(dark: Boolean, content: @Composable () -> Unit) {
    OneOnOneTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            Surface(color = OneTheme.colors.bg, modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

@Preview(name = "Compose letter", widthDp = 390, heightDp = 844)
@Composable private fun ComposeLetterPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    DialogPreview(dark) { FeatureComposerContent("letter", PreviewConnection, "Sam", false, null, null, { _, _, _, _, _ -> }, {}) }

@Preview(name = "Compose countdown", widthDp = 390, heightDp = 844)
@Composable private fun ComposeCountdownPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    DialogPreview(dark) { FeatureComposerContent("countdown", PreviewConnection, "Sam", false, null, null, { _, _, _, _, _ -> }, {}) }

@Preview(name = "Compose check-in", widthDp = 390, heightDp = 844)
@Composable private fun ComposeCheckInPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    DialogPreview(dark) { FeatureComposerContent("checkin", PreviewConnection, "Sam", false, null, null, { _, _, _, _, _ -> }, {}) }

@Preview(name = "Compose ask", widthDp = 390, heightDp = 844)
@Composable private fun ComposeAskPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    DialogPreview(dark) { FeatureComposerContent("ask", PreviewConnection, "Sam", false, null, null, { _, _, _, _, _ -> }, {}) }

@Preview(name = "Compose this or that", widthDp = 390, heightDp = 844)
@Composable private fun ComposeThisOrThatPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    DialogPreview(dark) { FeatureComposerContent("thisorthat", PreviewConnection, "Sam", false, null, null, { _, _, _, _, _ -> }, {}) }

private fun letter(appearance: String) = sample("letter", buildJsonObject { put("appearance", appearance); put("to", "Alex"); put("from", "Sam") }, "Thank you for making ordinary days special.")

@Preview(name = "Dawn letter viewer", widthDp = 390, heightDp = 844)
@Composable private fun DawnViewerPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { LetterViewer(letter("dawn"), {}, {}) }

@Preview(name = "Botanical letter viewer", widthDp = 390, heightDp = 844)
@Composable private fun BotanicalViewerPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { LetterViewer(letter("botanical"), {}, {}) }

@Preview(name = "Letter preview step", widthDp = 390, heightDp = 844)
@Composable private fun LetterSendPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) {
    FeatureModal({}, title = { Text("Preview letter") }, text = { LetterBody(letter("dawn")) },
        confirmButton = { PrimaryButton("Send", {}) }, dismissButton = { SecondaryButton("Edit", {}) })
}

@Preview(name = "Location confirmation", widthDp = 390, heightDp = 844)
@Composable private fun LocationDialogPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { LocationContent(false, null, {}, {}) }

@Preview(name = "Appearance", widthDp = 390, heightDp = 844)
@Composable private fun AppearancePreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { AppearanceContent("love", false, null, {}, {}, {}) }

@Preview(name = "Report person", widthDp = 390, heightDp = 844)
@Composable private fun ReportPersonPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { ReportContent(null, false, null, { _, _, _ -> }, {}) }

@Preview(name = "Report message", widthDp = 390, heightDp = 844)
@Composable private fun ReportMessagePreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { ReportContent("message", false, null, { _, _, _ -> }, {}) }

@Preview(name = "Block", widthDp = 390, heightDp = 844)
@Composable private fun BlockPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { BlockContent(false, null, {}, {}) }

@Preview(name = "Export", widthDp = 390, heightDp = 844)
@Composable private fun ExportPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { ExportContent("html", {}, {}, {}) }

@Preview(name = "Upload photo", widthDp = 390, heightDp = 844)
@Composable private fun UploadPhotoPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { UploadContent("image", null, false, null, {}, {}) }

@Preview(name = "Upload file", widthDp = 390, heightDp = 844)
@Composable private fun UploadFilePreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { UploadContent("file", null, false, null, {}, {}) }

@Preview(name = "Photo viewer", widthDp = 390, heightDp = 844)
@Composable private fun PhotoViewerPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = DialogPreview(dark) { PhotoViewer(null, false, {}, {}) }
