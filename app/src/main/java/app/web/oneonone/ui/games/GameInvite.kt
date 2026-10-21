package app.web.oneonone.ui.games

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.web.oneonone.data.games.*
import app.web.oneonone.ui.components.PrimaryButton
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.coroutines.delay

/** Time presets offered for every turn game; null = no time limit. */
private val Presets: List<Pair<String, Long?>> = listOf("No limit" to null, "1 min" to 60_000L, "2 min" to 120_000L,
    "5 min" to 300_000L, "10 min" to 600_000L, "1 hour" to 3_600_000L)
private val CustomBases = listOf(10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600, 5400, 7200, 10800).map { it * 1000L }
private val CustomIncs = listOf(0, 1, 2, 3, 5, 10, 15, 20, 30, 60).map { it * 1000L }
private val PointPresets = listOf(7, 11, 21)

/** Someone shouldn't wait forever: each turn game starts with a sensible clock (No limit is one tap away). */
internal fun defaultClock(kind: GameKind): Long? = when (kind) {
    GameKind.Chess -> 600_000L
    GameKind.Reversi -> 300_000L
    GameKind.Connect4 -> 120_000L
    GameKind.TicTacToe -> 60_000L
    else -> null
}

/** The second step of the games sheet: time control (turn games) or target score (Ping Pong), then invite. */
@Composable
internal fun InviteOptions(kind: GameKind, nickname: String, onBack: () -> Unit, onInvite: (MatchOptions) -> Unit) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    var preset by rememberSaveable(kind) { mutableStateOf(Presets.indexOfFirst { it.second == defaultClock(kind) }.coerceAtLeast(0)) }
    var custom by rememberSaveable(kind) { mutableStateOf(false) }
    var baseIndex by rememberSaveable(kind) { mutableIntStateOf(CustomBases.indexOf(180_000L)) }
    var incIndex by rememberSaveable(kind) { mutableIntStateOf(CustomIncs.indexOf(2_000L)) }
    var points by rememberSaveable(kind) { mutableIntStateOf(7) }
    var customPoints by rememberSaveable(kind) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(s.md12)) {
        Text(kind.title, style = OneTextStyles.screenTitle, color = c.text)
        if (kind == GameKind.Pong) {
            Text("Play to how many points?", style = OneTextStyles.subtitle, color = c.textDim)
            ChoiceRow(PointPresets.map { "$it" } + "Custom", if (customPoints) PointPresets.size else PointPresets.indexOf(points).coerceAtLeast(0)) { i ->
                customPoints = i == PointPresets.size
                if (!customPoints) points = PointPresets[i]
            }
            if (customPoints) Stepper("First to", "$points", { points = (points - 1).coerceAtLeast(1) }, { points = (points + 1).coerceAtMost(99) })
        } else {
            Text("How much time each?", style = OneTextStyles.subtitle, color = c.textDim)
            ChoiceRow(Presets.map { it.first } + "Custom", if (custom) Presets.size else preset) { i ->
                custom = i == Presets.size
                if (!custom) preset = i
            }
            if (custom) {
                Stepper("Time each", clockLabel(CustomBases[baseIndex], 0), { baseIndex = (baseIndex - 1).coerceAtLeast(0) },
                    { baseIndex = (baseIndex + 1).coerceAtMost(CustomBases.lastIndex) })
                Stepper("Added per move", if (CustomIncs[incIndex] == 0L) "None" else "+${CustomIncs[incIndex] / 1000} s",
                    { incIndex = (incIndex - 1).coerceAtLeast(0) }, { incIndex = (incIndex + 1).coerceAtMost(CustomIncs.lastIndex) })
            }
            Text("Run out of time and you lose.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Spacer(Modifier.height(s.xs4))
        PrimaryButton("Invite $nickname", {
            onInvite(when {
                kind == GameKind.Pong -> MatchOptions(points = points)
                custom -> MatchOptions(clock = CustomBases[baseIndex] to CustomIncs[incIndex])
                else -> MatchOptions(clock = Presets[preset].second?.let { it to 0L })
            })
        }, Modifier.fillMaxWidth())
        SecondaryButton("Back", onBack, Modifier.fillMaxWidth())
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = OneTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8), verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Surface(onClick = { onSelect(i) }, shape = CircleShape, color = if (on) c.accentYou.copy(alpha = .16f) else c.bg,
                border = BorderStroke(1.dp, if (on) c.accentYou else c.border),
                modifier = Modifier.heightIn(min = 40.dp).semantics { role = Role.RadioButton; this.selected = on }) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(label, Modifier.padding(vertical = 10.dp), color = if (on) c.accentYou else c.text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    val c = OneTheme.colors
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = false) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyLarge)
        StepButton("−", "Less $label", minus)
        Text(value, Modifier.widthIn(min = 96.dp), color = c.text, style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+", "More $label", plus)
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    val c = OneTheme.colors
    Surface(onClick = onClick, shape = CircleShape, color = c.bg, border = BorderStroke(1.dp, c.border),
        modifier = Modifier.size(44.dp).semantics { contentDescription = description }) {
        Box(contentAlignment = Alignment.Center) { Text(text, color = c.text, style = MaterialTheme.typography.titleLarge) }
    }
}

/** The match's options in a few words, for invites and the game header. */
internal fun optionsLabel(match: Match): String? = when {
    match.game == GameKind.Pong -> "First to ${match.points ?: 7}"
    match.clock != null -> clockLabel(match.clock.base, match.clock.inc)
    match.game != GameKind.Dots -> "No time limit"
    else -> null
}

/**
 * Each seat's remaining time, counting down locally from the server's snapshot (which already charged the side
 * to move). The server decides time-outs; this only displays.
 */
@Composable
internal fun rememberClockLeft(match: Match): List<Long>? {
    val clock = match.clock ?: return null
    val received = remember(match.id, match.version) { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(received) }
    val running = match.status == MatchStatus.Playing
    LaunchedEffect(match.id, match.version, running) {
        while (running) { now = SystemClock.elapsedRealtime(); delay(100) }
    }
    val turnSeat = if (running) match.turn?.let { match.players.indexOf(it) } ?: -1 else -1
    return clock.left.mapIndexed { i, ms -> if (i == turnSeat) (ms - (now - received)).coerceAtLeast(0) else ms }
}

/** A chess-style clock face; highlighted while running, red under ten seconds. */
@Composable
internal fun ClockChip(ms: Long, running: Boolean, whose: String) {
    val c = OneTheme.colors
    val low = ms < 10_000
    val tint = when { low && running -> c.danger; running -> c.accentYou; else -> c.textDim }
    Surface(shape = RoundedCornerShape(OneTheme.radii.sm6), color = if (running) tint.copy(alpha = .14f) else c.bgRaised,
        border = BorderStroke(1.dp, if (running) tint.copy(alpha = .6f) else c.border),
        modifier = Modifier.semantics { contentDescription = "$whose clock ${clockFace(ms)}" }) {
        Text(clockFace(ms), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = tint,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}
