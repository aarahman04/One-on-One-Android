package app.web.oneonone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.web.oneonone.R
import app.web.oneonone.ui.theme.OneOnOneTheme
import app.web.oneonone.ui.theme.OneTheme

@Composable
private fun PreviewHost(dark: Boolean, content: @Composable () -> Unit) {
    OneOnOneTheme(darkTheme = dark) {
        Surface(color = OneTheme.colors.bg) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun ButtonsSample() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Continue", onClick = {})
        SecondaryButton("Cancel", onClick = {})
        DangerButton("Leave", onClick = {})
    }
    TextLink("Use a different account", onClick = {})
    Row {
        OneIconButton(R.drawable.ic_phone, "Call", onClick = {})
        OneIconButton(R.drawable.ic_video, "Video call", onClick = {})
        OneIconButton(R.drawable.ic_more_vertical, "More options", onClick = {})
        OneIconButton(R.drawable.ic_paperclip, "Attach", onClick = {})
    }
}

@Composable
private fun TextSample() {
    Eyebrow("YOUR CONNECTION ID")
    ScreenTitle("Connect with someone")
    Subtitle("Share your ID or enter theirs.")
    OneTextField(value = "", onValueChange = {}, placeholder = "Connection ID")
    OneTextField(value = "ABC123", onValueChange = {})
}

@Composable
private fun MenuSample() {
    OneMenu(expanded = true, onDismissRequest = {}) {
        OneMenuItem("Search", onClick = {})
        OneMenuItem("Appearance", onClick = {}, enabled = false)
        OneMenuItem("Leave connection", onClick = {}, danger = true)
    }
}

@Preview(name = "Buttons dark", showBackground = true) @Composable
private fun ButtonsDark() = PreviewHost(true) { ButtonsSample() }

@Preview(name = "Buttons light", showBackground = true) @Composable
private fun ButtonsLight() = PreviewHost(false) { ButtonsSample() }

@Preview(name = "Text dark", showBackground = true) @Composable
private fun TextDark() = PreviewHost(true) { TextSample() }

@Preview(name = "Text light", showBackground = true) @Composable
private fun TextLight() = PreviewHost(false) { TextSample() }

@Preview(name = "Menu dark", showBackground = true) @Composable
private fun MenuDark() = PreviewHost(true) { MenuSample() }

@Preview(name = "Menu light", showBackground = true) @Composable
private fun MenuLight() = PreviewHost(false) { MenuSample() }

@Preview(name = "Modal dark", showBackground = true) @Composable
private fun ModalDark() = PreviewHost(true) { ModalSample() }

@Preview(name = "Modal light", showBackground = true) @Composable
private fun ModalLight() = PreviewHost(false) { ModalSample() }

@Composable
private fun ModalSample() {
    OneModal(onDismiss = {}) {
        ScreenTitle("Leave connection?")
        Subtitle("This starts a countdown.")
        DangerButton("Leave", onClick = {})
    }
}

@Preview(name = "Modal bottom dark", showBackground = true) @Composable
private fun ModalBottomDark() = PreviewHost(true) { ModalBottomSample() }

@Preview(name = "Modal bottom light", showBackground = true) @Composable
private fun ModalBottomLight() = PreviewHost(false) { ModalBottomSample() }

@Composable
private fun ModalBottomSample() {
    OneModal(onDismiss = {}, placement = ModalPlacement.Bottom) {
        ScreenTitle("Send image?")
        Subtitle("Up to 10 MiB.")
        PrimaryButton("Send", onClick = {})
    }
}
