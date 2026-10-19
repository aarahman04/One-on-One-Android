package app.web.oneonone.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.chat.archiveFileName
import app.web.oneonone.push.MessageNotifications
import app.web.oneonone.ui.AppState
import app.web.oneonone.ui.ScreenFrame
import app.web.oneonone.ui.components.DangerButton
import app.web.oneonone.ui.components.OneModal
import app.web.oneonone.ui.components.PrimaryButton
import app.web.oneonone.ui.components.ScreenTitle
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.components.Subtitle
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Saving your conversation… 40%" with a bar, or the failure copy. Announced politely to TalkBack. */
@Composable
private fun ArchiveStatus(archive: ArchiveState, modifier: Modifier = Modifier) {
    val c = OneTheme.colors
    Column(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (archive.error != null) {
            Text("Couldn't save your conversation. ${archive.error}", color = c.danger, style = OneTextStyles.cardHint)
        } else {
            Text("Saving your conversation… ${archive.progress}%", color = c.text, style = OneTextStyles.cardHint)
            LinearProgressIndicator(progress = { archive.progress / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp),
                color = c.accentYou, trackColor = c.border)
        }
    }
}

/** SAF "save as" for the zip; the save itself runs in [features] so rotation and the picker's return keep it. */
@Composable
private fun rememberArchiveSaver(connection: CurrentConnection, features: FeatureViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) features.saveArchive(uri, connection)
    }
    return { launcher.launch(archiveFileName(connection.otherNickname ?: "Conversation")) }
}

/** Instant leave (contract §10): one decision, optionally after saving a zip of everything first. */
@Composable
internal fun LeaveDialog(connection: CurrentConnection, features: FeatureViewModel, busy: Boolean, error: String?,
                         onDismiss: () -> Unit, onLeave: () -> Unit) {
    val c = OneTheme.colors
    val archive by features.archive.collectAsState()
    val save = rememberArchiveSaver(connection, features)
    // Leave only once the whole zip is written.
    LaunchedEffect(archive?.saved) { if (archive?.saved == true) { features.cancelArchive(); onLeave() } }
    val saving = archive != null && archive?.error == null
    OneModal(onDismiss = { features.cancelArchive(); onDismiss() }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Leave connection?", style = OneTextStyles.subtitle.copy(fontWeight = FontWeight.Bold), color = c.text)
            Text("Your chat, photos, voice notes, letters and files will be deleted for both of you. " +
                "${connection.otherNickname ?: "They"} can save a copy for 7 days.", style = OneTextStyles.cardHint, color = c.textDim)
            archive?.let { ArchiveStatus(it) }
            error?.takeIf { !saving }?.let { Text(it, color = c.danger, style = OneTextStyles.cardHint,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (saving) SecondaryButton("Cancel", onClick = features::cancelArchive, modifier = Modifier.fillMaxWidth())
                else {
                    PrimaryButton(if (archive?.error != null) "Try again" else "Save a copy, then leave", onClick = save,
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    DangerButton("Leave without saving", onClick = { features.cancelArchive(); onLeave() },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    SecondaryButton("Cancel", onClick = { features.cancelArchive(); onDismiss() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

internal fun purgeDate(connection: CurrentConnection, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? =
    runCatching {
        val at = connection.purgeAt?.let(Instant::parse) ?: Instant.parse(connection.endedAt).plusSeconds(7 * 24 * 3_600)
        DateTimeFormatter.ofPattern("MMMM d, yyyy", locale).format(at.atZone(zone))
    }.getOrNull()

/** The partner left: the conversation stays readable (read-only) until it's saved, skipped, or purged after 7 days. */
@Composable
internal fun PartnerLeftScreen(state: AppState, connection: CurrentConnection, features: FeatureViewModel, onAcknowledge: () -> Unit) {
    val context = LocalContext.current
    val archive by features.archive.collectAsState()
    val save = rememberArchiveSaver(connection, features)
    LaunchedEffect(connection.id) { MessageNotifications.clear(context, connection.id) }
    LaunchedEffect(archive?.saved) { if (archive?.saved == true) { features.cancelArchive(); onAcknowledge() } }
    val saving = archive != null && archive?.error == null
    val blockWidth = Modifier.widthIn(max = 340.dp).fillMaxWidth()
    // Acknowledging hard-deletes the conversation for good, so skipping the save asks once more.
    var confirmSkip by rememberSaveable { mutableStateOf(false) }
    if (confirmSkip) FeatureModal(onDismissRequest = { confirmSkip = false }, title = { Text("Delete without saving?") },
        text = { Text("Your chat, photos and letters will be gone for good.") },
        confirmButton = { DangerButton("Delete", onClick = { confirmSkip = false; onAcknowledge() }, enabled = !state.busy) },
        dismissButton = { SecondaryButton("Go back", onClick = { confirmSkip = false }) })
    ScreenFrame(state) { _ ->
        ScreenTitle("${connection.otherNickname ?: "Your connection"} left the connection", Modifier.widthIn(max = 340.dp))
        Subtitle(purgeDate(connection)?.let { "Save your conversation before it's deleted on $it." }
            ?: "Save your conversation before it's deleted in 7 days.", Modifier.widthIn(max = 320.dp))
        archive?.let { ArchiveStatus(it, blockWidth) }
        Column(blockWidth, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (saving) SecondaryButton("Cancel", onClick = features::cancelArchive, modifier = Modifier.fillMaxWidth())
            else {
                PrimaryButton(if (archive?.error != null) "Try again" else "Save conversation", onClick = save,
                    modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                SecondaryButton("Continue without saving", onClick = { features.cancelArchive(); confirmSkip = true },
                    modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
            }
        }
    }
}
