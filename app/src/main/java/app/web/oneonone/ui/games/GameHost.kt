package app.web.oneonone.ui.games

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.web.oneonone.R
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.games.*
import app.web.oneonone.ui.chat.FeatureModal
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHost(vm: GameViewModel, connection: CurrentConnection, photo: Pair<Any, String>?) {
    val state by vm.ui.collectAsState()
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(connection.id, connection.myUserId, connection.otherNickname) { vm.open(connection) }
    DisposableEffect(vm, lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> vm.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        lifecycle.addObserver(observer)
        vm.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); vm.foreground(false) }
    }
    if (state.stage == GameStage.Hub) ModalBottomSheet(onDismissRequest = vm::dismissHub, containerColor = c.bgRaised,
        shape = RoundedCornerShape(topStart = OneTheme.radii.lg16, topEnd = OneTheme.radii.lg16)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = s.xl24).padding(bottom = s.xl24),
            verticalArrangement = Arrangement.spacedBy(s.md12)) {
            Text("A little time together", style = OneTextStyles.screenTitle, color = c.text)
            Text("Choose a game to play with ${vm.nickname}.", style = OneTextStyles.subtitle, color = c.textDim)
            Spacer(Modifier.height(s.xs4))
            GameKind.entries.forEach { kind ->
                val vector = remember(kind, c.accentYou) { gameVector(kind.wire, c.accentYou) }
                Surface(onClick = { vm.invite(kind) }, shape = RoundedCornerShape(OneTheme.radii.lg16),
                    color = c.bg, border = BorderStroke(1.dp, c.border), modifier = Modifier.semantics { role = Role.Button }) {
                    Row(Modifier.fillMaxWidth().padding(s.lg16), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(s.lg16)) {
                        Box(Modifier.size(OneTheme.sizes.send48).clip(RoundedCornerShape(OneTheme.radii.md10)).background(c.accentYou.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                            Icon(vector, null, Modifier.size(32.dp), tint = c.accentYou)
                        }
                        Column(Modifier.weight(1f)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
                                Text(kind.title, color = c.text, style = MaterialTheme.typography.titleMedium)
                                if (kind == GameKind.Pong) Text("Real-time", Modifier.clip(CircleShape).background(c.accentOther.copy(alpha = .12f)).padding(horizontal = s.sm8, vertical = s.xxs2),
                                    color = c.accentOther, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(kind.description, color = c.textDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = c.muted)
                    }
                }
            }
        }
    }
    if (state.stage == GameStage.Outgoing) FeatureModal(onDismissRequest = vm::cancelInvite,
        title = { Text(state.kind.title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(s.lg16)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accentYou, trackColor = c.border)
                Text("Waiting for ${vm.nickname}…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text("The invitation expires in 30 seconds.", style = MaterialTheme.typography.bodySmall, color = c.textDim)
            }
        }, confirmButton = { SecondaryButton("Cancel", vm::cancelInvite) })
    if (state.stage == GameStage.Playing || state.stage == GameStage.Result) {
        var confirm by rememberSaveable(state.id) { mutableStateOf<String?>(null) }
        GameSurface(vm, state, photo, confirm) { confirm = it }
    }
}

@Composable
fun GameInviteBanner(vm: GameViewModel) {
    val state by vm.ui.collectAsState()
    if (state.stage != GameStage.Incoming) return
    val c = OneTheme.colors
    val s = OneTheme.spacing
    Surface(color = c.bgRaised, border = BorderStroke(1.dp, c.accentOther.copy(alpha = .4f)),
        shape = RoundedCornerShape(OneTheme.radii.lg16), modifier = Modifier.padding(s.md12)) {
        Column(Modifier.fillMaxWidth().padding(s.lg16), verticalArrangement = Arrangement.spacedBy(s.md12)) {
            Text("${vm.nickname} wants to play ${state.kind.title}", color = c.text, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Row(horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
                PrimaryButton("Accept", vm::accept, Modifier.weight(1f))
                SecondaryButton("Decline", vm::decline, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GameSurface(vm: GameViewModel, state: GameUi, photo: Pair<Any, String>?, confirm: String?, onConfirm: (String?) -> Unit) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val window = LocalActivity.current?.window
    DisposableEffect(window, state.stage) {
        val alreadyAwake = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if (state.stage == GameStage.Playing) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (!alreadyAwake) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { if (state.stage == GameStage.Playing) onConfirm("leave") else vm.done() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.id, state.revision) { if (state.revision > 0 && state.kind != GameKind.Pong) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    LaunchedEffect(state.stage) { if (state.stage == GameStage.Result && state.result?.winner == state.player) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    Surface(Modifier.fillMaxSize(), color = c.bg, contentColor = c.text) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = s.md12, vertical = s.sm8), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
                OneIconButton(R.drawable.ic_arrow_left, "Leave game", { if (state.stage == GameStage.Playing) onConfirm("leave") else vm.done() })
                if (photo != null) AsyncImage(photo.first, "${vm.nickname}'s avatar", Modifier.size(OneTheme.sizes.touch40).clip(CircleShape), contentScale = ContentScale.Crop)
                else Box(Modifier.size(OneTheme.sizes.touch40).clip(CircleShape).background(c.accentOther.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Text(vm.nickname.take(1).uppercase(), color = c.accentOther, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(vm.nickname, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(state.kind.title, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
                if (state.stage == GameStage.Playing) TextLink("Resign", { onConfirm("resign") })
            }
            HorizontalDivider(color = c.border)
            if (state.stage == GameStage.Result) GameResultContent(vm, state, Modifier.weight(1f))
            else {
                val engine = vm.board
                val status = when {
                    state.paused -> "Waiting for ${vm.nickname}…"
                    engine?.result != null -> engine.result!!.detail
                    state.kind == GameKind.Pong -> "${vm.pong?.hostScore ?: 0}  :  ${vm.pong?.guestScore ?: 0}"
                    engine?.turn == state.player -> "Your turn"
                    else -> "${vm.nickname}'s turn"
                }
                Column(Modifier.fillMaxWidth().padding(s.lg16), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(s.xs4)) {
                    Text(status, style = MaterialTheme.typography.headlineSmall, color = if (state.paused) c.textDim else c.text,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(when {
                        state.paused -> "We'll wait up to 15 seconds."
                        state.kind == GameKind.Chess -> if (state.player == 1) "You play white" else "You play black"
                        state.kind == GameKind.Dots -> "You ${ (engine as? DotsGame)?.score(state.player) ?: 0 } · ${vm.nickname} ${(engine as? DotsGame)?.score(3 - state.player) ?: 0}"
                        state.kind == GameKind.Pong -> "${if (state.player == 1) "You" else vm.nickname} · first to 7 · ${if (state.player == 2) "You" else vm.nickname}"
                        state.kind == GameKind.TicTacToe -> if (state.player == 1) "You play X" else "You play O"
                        else -> "Connect four in any direction"
                    }, color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = s.lg16), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    Box(Modifier.size(side)) { GameBoard(vm, state) }
                }
                if (state.kind == GameKind.Chess) CapturedPieces(vm.board as ChessGame, state.player)
                Text(when (state.kind) {
                    GameKind.Chess -> "Tap a piece or drag it to a legal square"
                    GameKind.Pong -> "Drag up and down to move your paddle"
                    GameKind.Connect4 -> "Tap a column to drop your disc"
                    GameKind.TicTacToe -> "Tap an empty square"
                    GameKind.Dots -> "Tap a line · close a box to play again"
                }, Modifier.align(Alignment.CenterHorizontally).padding(s.lg16), color = c.textDim, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(horizontal = s.xl24).padding(bottom = s.md12), horizontalArrangement = Arrangement.Center) {
                    TextLink("Leave game", { onConfirm("leave") })
                }
            }
        }
    }
    confirm?.let { action -> FeatureModal(onDismissRequest = { onConfirm(null) }, title = { Text(if (action == "resign") "Resign this game?" else "Leave this game?") },
        text = { Text("The game will end for both of you.") },
        confirmButton = { DangerButton(if (action == "resign") "Resign" else "Leave game", { onConfirm(null); vm.leave(action == "resign") }) },
        dismissButton = { SecondaryButton("Keep playing", { onConfirm(null) }) }) }
}

@Composable
private fun GameResultContent(vm: GameViewModel, state: GameUi, modifier: Modifier) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val result = state.result ?: return
    val title = when (result.winner) {
        state.player -> "You won"
        0 -> if (result.detail.contains("draw", true) || result.detail in listOf("Stalemate", "Insufficient material", "The board is full")) "A draw" else "Game ended"
        else -> "${vm.nickname} won"
    }
    val vector = remember(state.kind, c.accentYou) { gameVector(state.kind.wire, c.accentYou) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(s.xl24),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.lg16, Alignment.CenterVertically)) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(c.accentYou.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            Icon(vector, null, Modifier.size(56.dp), tint = c.accentYou)
        }
        Text(title, style = OneTextStyles.screenTitle, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(result.detail, color = c.textDim, style = OneTextStyles.subtitle, textAlign = TextAlign.Center)
        if (state.kind == GameKind.TicTacToe && (state.myWins + state.theirWins > 0)) {
            Text("You ${state.myWins} · ${vm.nickname} ${state.theirWins}", style = MaterialTheme.typography.titleMedium)
            if (state.bestOfThree) Text(if (state.myWins >= 2 || state.theirWins >= 2) "Best of 3 complete" else "Best of 3", color = c.accentOther, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton("Rematch", { vm.invite(state.kind) }, Modifier.fillMaxWidth())
        if (state.kind == GameKind.TicTacToe && !state.bestOfThree) SecondaryButton("Play best of 3", { vm.invite(state.kind, bestOfThree = true) }, Modifier.fillMaxWidth())
        SecondaryButton("Done", vm::done, Modifier.fillMaxWidth())
    }
    }
}

@Composable
private fun CapturedPieces(game: ChessGame, player: Int) {
    val c = OneTheme.colors
    val vectors = remember(c.accentYou, c.accentOther) { "PNBRQKpnbrqk".associateWith { chessVector(it, if (it.isUpperCase() == (player == 1)) c.accentYou else c.accentOther) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = OneTheme.spacing.xl24), verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.xxs2)) {
        for (white in listOf(true, false)) FlowRow(horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.xxs2)) {
            Text("Captured ${if (white) "white" else "black"}", color = c.textDim, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(OneTheme.spacing.sm8))
            game.captured.filter { it.isUpperCase() == white }.forEach { piece -> Icon(vectors.getValue(piece), "Captured ${if (white) "white" else "black"} ${pieceName(piece)}", Modifier.size(OneTheme.spacing.lg16),
                tint = if (white == (player == 1)) c.accentYou else c.accentOther) }
        }
    }
}
