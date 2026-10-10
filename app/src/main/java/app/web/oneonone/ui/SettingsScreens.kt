package app.web.oneonone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.BuildConfig
import app.web.oneonone.R
import app.web.oneonone.data.api.BlockedUser
import app.web.oneonone.data.api.Me
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneOnOneTheme
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

/** Settings: [chat] shows the Appearance + Export rows; without a connection a plain Theme row stands in. */
@Composable
internal fun SettingsScreen(
    state: AppState,
    chat: Boolean,
    appearanceSummary: String,
    onAppearance: () -> Unit,
    onExport: () -> Unit,
    onNotifications: () -> Unit,
    onAccount: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold("Settings", onBack, busy = state.busy) {
        if (chat) SettingsGroup("Chat") {
            SettingsRow(R.drawable.ic_palette, "Appearance", subtitle = appearanceSummary, trailing = SettingsTrailing.Chevron, onClick = onAppearance)
            SettingsRow(R.drawable.ic_download, "Export conversation", subtitle = "Save as text, JSON or HTML", trailing = SettingsTrailing.Chevron, onClick = onExport)
        } else SettingsGroup("Appearance") {
            SettingsRow(R.drawable.ic_palette, "Theme", trailing = SettingsTrailing.Value(appearanceSummary), onClick = onAppearance)
        }
        SettingsGroup("General") {
            SettingsRow(R.drawable.ic_bell, "Notifications & background", subtitle = "Alerts, autostart and battery",
                trailing = SettingsTrailing.Chevron, onClick = onNotifications)
            SettingsRow(R.drawable.ic_user, "Account", subtitle = "Connection ID, blocked accounts, legal",
                trailing = SettingsTrailing.Chevron, onClick = onAccount)
        }
        SettingsGroup("About") {
            SettingsRow(R.drawable.ic_info, "App version", trailing = SettingsTrailing.Value(BuildConfig.VERSION_NAME))
        }
    }
}

@Composable
internal fun AccountScreen(
    state: AppState,
    onBlocks: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val code = state.me?.connectionCode.orEmpty()
    SettingsScaffold("Account", onBack, busy = state.busy, error = state.error) {
        ConnectionIdCard(code, onCopy = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Connection ID", code))
            Toast.makeText(context, "Connection ID copied", Toast.LENGTH_SHORT).show()
        })
        SettingsGroup("Privacy") {
            SettingsRow(R.drawable.ic_ban, "Blocked accounts",
                trailing = if (state.blocks.isEmpty()) SettingsTrailing.Chevron else SettingsTrailing.Value(state.blocks.size.toString()),
                onClick = onBlocks)
        }
        SettingsGroup("Legal") {
            SettingsRow(R.drawable.ic_shield, "Privacy Policy", trailing = SettingsTrailing.External, onClick = { uri.openUri("$LEGAL_ORIGIN/privacy") })
            SettingsRow(R.drawable.ic_file_text, "Terms", trailing = SettingsTrailing.External, onClick = { uri.openUri("$LEGAL_ORIGIN/terms") })
            SettingsRow(R.drawable.ic_heart, "Child Safety", trailing = SettingsTrailing.External, onClick = { uri.openUri("$LEGAL_ORIGIN/child-safety") })
        }
        SettingsGroup(null) {
            SettingsRow(R.drawable.ic_log_out, "Sign out", enabled = !state.busy, onClick = onSignOut)
        }
        SettingsGroup("Danger zone") {
            SettingsRow(R.drawable.ic_trash, "Delete account", subtitle = "Permanently removes your account and conversation",
                danger = true, enabled = !state.busy, onClick = { deleteOpen = true })
        }
    }
    if (deleteOpen) {
        BackHandler(enabled = state.busy) { }
        OneModal(onDismiss = { if (!state.busy) deleteOpen = false }) {
            DeleteAccountContent(state.busy, confirmation, onConfirmation = { confirmation = it }, onDelete, onCancel = { deleteOpen = false })
        }
    }
}

@Composable
private fun ConnectionIdCard(code: String, onCopy: () -> Unit) {
    val c = OneTheme.colors
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(OneTheme.radii.md10), color = c.bgRaised, border = BorderStroke(1.dp, c.border)) {
        Row(Modifier.padding(OneTheme.spacing.lg16), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.md12)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.accentYou.copy(alpha = .18f).compositeOver(c.bg)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_user), null, Modifier.size(24.dp), tint = c.accentYou)
            }
            Column(Modifier.weight(1f)) {
                Text("Your connection ID", style = OneTextStyles.cardHint, color = c.textDim)
                Text(code.ifEmpty { "—" }, style = OneTextStyles.connectionId.copy(fontSize = 22.sp, lineHeight = 30.sp), color = c.text, maxLines = 1)
            }
            OneIconButton(R.drawable.ic_copy, "Copy connection ID", onCopy, enabled = code.isNotEmpty())
        }
    }
}

@Composable
internal fun BlocksScreen(state: AppState, onUnblock: (String) -> Unit, onBack: () -> Unit) {
    SettingsScaffold("Blocked accounts", onBack, busy = state.busy, error = state.error) {
        SettingsCaption("Unblocking allows a future connection request. It does not restore a conversation.")
        if (state.blocks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = OneTheme.spacing.xxl32), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.md12)) {
                Icon(painterResource(R.drawable.ic_ban), null, Modifier.size(40.dp), tint = OneTheme.colors.muted)
                Text("No blocked accounts", style = OneTextStyles.subtitle, color = OneTheme.colors.textDim)
            }
        } else SettingsGroup("Accounts") {
            state.blocks.forEach { block ->
                SettingsRow(R.drawable.ic_ban, block.blockedUserId,
                    trailing = SettingsTrailing.Action("Unblock", enabled = !state.busy) { onUnblock(block.blockedUserId) })
            }
        }
    }
}

private val previewState = AppState(me = Me("me", "K7F29PQ2"), blocks = listOf(BlockedUser("blocked-account", ""), BlockedUser("another-blocked-account", "")))

@Preview(name = "Settings (chat)", widthDp = 390, heightDp = 844)
@Composable private fun SettingsPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { SettingsScreen(previewState, true, "Dark · Love wallpaper", {}, {}, {}, {}, {}) }
}

@Preview(name = "Settings (no connection)", widthDp = 360, heightDp = 640)
@Composable private fun SettingsNoChatPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { SettingsScreen(previewState, false, if (dark) "Dark" else "Light", {}, {}, {}, {}, {}) }
}

@Preview(name = "Account", widthDp = 390, heightDp = 844)
@Composable private fun AccountPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { AccountScreen(previewState, {}, {}, {}, {}) }
}

@Preview(name = "Account narrow", widthDp = 320, heightDp = 640)
@Composable private fun AccountNarrowPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { AccountScreen(AppState(me = Me("me", "K7F29PQ2")), {}, {}, {}, {}) }
}

@Preview(name = "Blocks", widthDp = 390, heightDp = 844)
@Composable private fun BlocksPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { BlocksScreen(previewState, {}, {}) }
}

@Preview(name = "Blocks empty", widthDp = 390, heightDp = 844)
@Composable private fun EmptyBlocksPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { BlocksScreen(AppState(), {}, {}) }
}
