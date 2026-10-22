package app.web.oneonone.ui.games

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    var chosen by rememberSaveable(state.stage == GameStage.Hub) { mutableStateOf<GameKind?>(null) }
    if (state.stage == GameStage.Hub) ModalBottomSheet(onDismissRequest = vm::dismissHub, containerColor = c.bgRaised,
        shape = RoundedCornerShape(topStart = OneTheme.radii.lg16, topEnd = OneTheme.radii.lg16)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = s.xl24).padding(bottom = s.xl24),
            verticalArrangement = Arrangement.spacedBy(s.md12)) {
            chosen?.let { kind ->
                InviteOptions(kind, vm.nickname, onBack = { chosen = null }, onInvite = { options -> chosen = null; vm.invite(kind, options) })
                return@Column
            }
            Text("A little time together", style = OneTextStyles.screenTitle, color = c.text)
            Text("Choose a game to play with ${vm.nickname}.", style = OneTextStyles.subtitle, color = c.textDim)
            Spacer(Modifier.height(s.xs4))
            GameKind.entries.filter { it != GameKind.Dots }.forEach { kind ->
                val vector = remember(kind, c.accentYou) { gameVector(kind.wire, c.accentYou) }
                Surface(onClick = { chosen = kind }, shape = RoundedCornerShape(OneTheme.radii.lg16),
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
        title = { Text(state.match?.game?.title.orEmpty()) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(s.lg16)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accentYou, trackColor = c.border)
                Text("Waiting for ${vm.nickname}…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                state.match?.let(::optionsLabel)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textDim) }
                Text("The invitation expires in 30 seconds.", style = MaterialTheme.typography.bodySmall, color = c.textDim)
            }
        }, confirmButton = { SecondaryButton("Cancel", vm::cancelInvite) })
    if (state.stage == GameStage.Playing || state.stage == GameStage.Result) {
        var confirm by rememberSaveable(state.match?.id) { mutableStateOf<String?>(null) }
        GameSurface(vm, state, photo, confirm) { confirm = it }
    }
}

@Composable
fun GameInviteBanner(vm: GameViewModel) {
    val state by vm.ui.collectAsState()
    if (state.stage != GameStage.Incoming) return
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val match = state.match ?: return
    val vector = remember(match.game, c.accentYou) { gameVector(match.game.wire, c.accentYou) }
    var expanded by rememberSaveable(match.id) { mutableStateOf(true) }
    if (!expanded) {
        Surface(onClick = { expanded = true }, color = c.bgRaised, contentColor = c.text) {
            Row(Modifier.fillMaxWidth().padding(horizontal = s.md12, vertical = s.sm8),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
                Text("${vm.nickname} invites you to play ${match.game.title}", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2)
                TextLink("Play", vm::accept)
                TextLink("Not now", vm::decline)
            }
        }
        return
    }
    OneModal(onDismiss = { expanded = false }) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.md12)) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(c.accentYou.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(vector, null, Modifier.size(52.dp), tint = c.accentYou)
            }
            Text("${vm.nickname} invites you to play", color = c.textDim, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(match.game.title, color = c.text, style = OneTextStyles.screenTitle, textAlign = TextAlign.Center)
            optionsLabel(match)?.let { Text(it, color = c.textDim, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(s.xs4))
            PrimaryButton("Play", vm::accept, Modifier.fillMaxWidth())
            SecondaryButton("Not now", vm::decline, Modifier.fillMaxWidth())
        }
    }
}

/** "You 2 · Alex 1" across rematches, or null before the first finished game. */
private fun seriesText(match: Match, me: String, partner: String, nickname: String): String? {
    val mine = match.series[me] ?: 0
    val theirs = match.series[partner] ?: 0
    return if (mine + theirs == 0) null else "You $mine · $nickname $theirs"
}

internal val DrawReasons = setOf("stalemate", "insufficient_material", "fivefold_repetition", "seventy_five_moves",
    "threefold_repetition", "fifty_moves", "agreement", "board_full", "equal_discs")

internal fun outcomeTitle(match: Match, me: String, nickname: String): String = when (val winner = match.outcome?.winner) {
    me -> "You won"
    null -> if (match.outcome?.reason in DrawReasons) "A draw" else "Game ended"
    else -> if (winner.isNotEmpty()) "$nickname won" else "Game ended"
}

internal fun outcomeDetail(match: Match, me: String, nickname: String): String {
    val outcome = match.outcome ?: return ""
    val won = outcome.winner == me
    return when (outcome.reason) {
        "checkmate" -> "Checkmate"
        "stalemate" -> "Stalemate: no legal move and not in check"
        "insufficient_material" -> "Neither side can checkmate"
        "fivefold_repetition" -> "The same position five times"
        "seventy_five_moves" -> "75 moves without a capture or pawn move"
        "threefold_repetition" -> "Draw claimed by threefold repetition"
        "fifty_moves" -> "Draw claimed by the fifty-move rule"
        "agreement" -> "Draw by agreement"
        "three_in_a_row" -> "Three in a row"
        "four_connected" -> "Four connected"
        "board_full" -> "The board is full"
        "most_boxes" -> DotsView.from(match.board)?.let { "${it.score(match.seat(me))}–${it.score(3 - match.seat(me))} boxes" } ?: "Most boxes"
        "most_discs" -> ReversiView.from(match.board)?.let { "${it.count(match.seat(me))}–${it.count(3 - match.seat(me))} discs" } ?: "Most discs"
        "equal_discs" -> "Equal discs"
        "first_to_7", "first_to_points" -> (match.board?.get("score") as? kotlinx.serialization.json.JsonArray)?.let { score ->
            val seat = match.seat(me)
            "${score[seat - 1]}–${score[2 - seat]} · first to ${match.points ?: 7}"
        } ?: "First to ${match.points ?: 7}"
        "timeout" -> if (won) "$nickname ran out of time" else "You ran out of time"
        "resigned" -> if (won) "$nickname resigned" else "You resigned"
        "left" -> if (won) "$nickname left the game" else "You left the game"
        "abandoned" -> when { won -> "$nickname didn't come back in time"; outcome.winner == null -> "Both players were away"; else -> "You were away too long" }
        "inactive" -> "No moves for 30 minutes"
        "connection_ended" -> "The connection has ended"
        else -> "The game has ended"
    }
}

@Composable
private fun GameSurface(vm: GameViewModel, state: GameUi, photo: Pair<Any, String>?, confirm: String?, onConfirm: (String?) -> Unit) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val match = state.match ?: return
    val window = LocalActivity.current?.window
    DisposableEffect(window, state.stage) {
        val alreadyAwake = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if (state.stage == GameStage.Playing) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (!alreadyAwake) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { if (state.stage == GameStage.Playing) onConfirm("leave") else vm.done() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(match.id, match.seq) { if (match.seq > 0 && match.game != GameKind.Pong) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val sounds = rememberGameSounds()
    LaunchedEffect(state.stage) {
        if (state.stage != GameStage.Result) return@LaunchedEffect
        if (match.outcome?.winner == state.me) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        sounds.play(GameSound.End)
    }
    val series = seriesText(match, state.me, state.partner, vm.nickname)
    val reactions by vm.reactions.collectAsState()
    var tray by rememberSaveable(match.id) { mutableStateOf(false) }
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
                    // The partner's reaction appears under their name for a moment (ours too once the controls are gone).
                    val shown = reactions.lastOrNull { it.from != state.me || state.stage == GameStage.Result }
                    ReactionSlot(shown, shown?.from == state.me, vm.nickname) {
                        Text(listOfNotNull(match.game.title, optionsLabel(match), series).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1)
                    }
                }
                OneIconButton(R.drawable.ic_smile, if (tray) "Close reactions" else "React or comment", { tray = !tray },
                    Modifier.then(if (tray) Modifier.background(c.accentYou.copy(alpha = .12f), CircleShape) else Modifier),
                    tint = if (tray) c.accentYou else c.textDim)
            }
            HorizontalDivider(color = c.border)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.stage == GameStage.Result) GameResultContent(vm, state, photo?.first, sounds, Modifier.fillMaxSize())
                else PlayingContent(vm, state, photo?.first, sounds, Modifier.fillMaxSize(), onConfirm)
                ReactionTray(tray, vm::react, { tray = false }, Modifier.align(Alignment.TopCenter))
            }
            if (state.stage == GameStage.Playing) GameActionBar(vm, state, reactions.lastOrNull { it.from == state.me }, onConfirm)
        }
    }
    confirm?.let { action -> FeatureModal(onDismissRequest = { onConfirm(null) }, title = { Text(if (action == "resign") "Resign this game?" else "Leave this game?") },
        text = { Text(if (action == "resign") "${vm.nickname} wins this game." else "Leaving ends the game and ${vm.nickname} wins.") },
        confirmButton = { DangerButton(if (action == "resign") "Resign" else "Leave game", { onConfirm(null); vm.leave(action == "resign") }) },
        dismissButton = { SecondaryButton("Keep playing", { onConfirm(null) }) }) }
}

@Composable
private fun PlayingContent(vm: GameViewModel, state: GameUi, photo: Any?, sounds: GameSounds, modifier: Modifier, onConfirm: (String?) -> Unit) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val match = state.match ?: return
    when (match.game) {
        // Draw offers and the game's actions live in GameActionBar.
        GameKind.Chess -> ChessContent(vm, state, photo, modifier, sounds) {}
        GameKind.Pong -> PongContent(vm, state, modifier, sounds) {}
        else -> Column(modifier.fillMaxWidth()) {
            val status = when {
                match.paused || state.partner in match.away -> "Waiting for ${vm.nickname} to come back…"
                state.busy -> "Sending your move…"
                // Reversi skips a player with no move; say so, or the repeat turn looks like a bug.
                match.game == GameKind.Reversi && ReversiView.from(match.board)?.passed == true ->
                    if (match.turn == state.me) "${vm.nickname} has no move · your move again" else "You have no move · ${vm.nickname} goes again"
                match.turn == state.me -> "Your move"
                else -> "${vm.nickname} is thinking…"
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = s.lg16, vertical = s.md12), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(s.sm8)) {
                when (match.game) {
                    GameKind.TicTacToe -> Text(if (state.seat == 1) "You are X · X moves first" else "You are O · X moves first", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                    GameKind.Connect4 -> Text("${if (state.seat == 1) "You drop first" else "${vm.nickname} drops first"} · connect four in any direction", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                    GameKind.Dots -> DotsView.from(match.board)?.let { Text("You ${it.score(state.seat)} · ${vm.nickname} ${it.score(3 - state.seat)} boxes", color = c.textDim, style = MaterialTheme.typography.bodySmall) }
                    GameKind.Reversi -> ReversiView.from(match.board)?.let { Text("You are ${if (state.seat == 1) "Black" else "White"} · You ${it.count(state.seat)} · ${vm.nickname} ${it.count(3 - state.seat)} discs", color = c.textDim, style = MaterialTheme.typography.bodySmall) }
                    else -> Unit
                }
                Text(status, style = MaterialTheme.typography.titleLarge, color = if (match.turn == state.me) c.text else c.textDim,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                rememberClockLeft(match)?.let { left ->
                    Row(horizontalArrangement = Arrangement.spacedBy(s.md12), verticalAlignment = Alignment.CenterVertically) {
                        Text("You", color = c.textDim, style = MaterialTheme.typography.labelLarge)
                        ClockChip(left[state.seat - 1], match.turn == state.me, "Your")
                        ClockChip(left[2 - state.seat], match.turn == state.partner, "${vm.nickname}'s")
                        Text(vm.nickname, color = c.textDim, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = s.md12), contentAlignment = Alignment.Center) {
                Box(Modifier.size(minOf(maxWidth, maxHeight))) { GameBoard(vm, state) }
            }
        }
    }
}

/** One bar for every game: a short how-to line, then Draw (chess), Resign and Leave as equal buttons. */
@Composable
private fun GameActionBar(vm: GameViewModel, state: GameUi, mine: GameReaction?, onConfirm: (String?) -> Unit) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val match = state.match ?: return
    Column(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = s.md12).padding(top = s.xs4, bottom = s.sm8),
        verticalArrangement = Arrangement.spacedBy(s.sm8)) {
        HorizontalDivider(color = c.border)
        val notice by vm.error.collectAsState()
        // The partner's draw offer takes the info line until it is answered (any game).
        if (match.drawOffer == state.partner) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(OneTheme.radii.md10))
            .background(c.accentOther.copy(alpha = .12f)).padding(start = s.md12, end = s.xs4, top = s.xs4, bottom = s.xs4),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
            Text("${vm.nickname} offers a draw", Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            TextLink("Decline", { vm.draw("decline") })
            PrimaryButton("Accept", { vm.draw("accept") })
        }
        // Otherwise: our own reaction for a moment, then any game notice (e.g. a declined draw), then the how-to line.
        else ReactionSlot(mine, true, vm.nickname, Modifier.fillMaxWidth(), Alignment.Center) { notice?.let {
            Text(it, color = c.accentOther, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } ?: Text(when (match.game) {
            GameKind.Chess -> "Tap or drag a piece"
            GameKind.Pong -> "Swipe through the ball"
            GameKind.Connect4 -> "Tap a column"
            GameKind.TicTacToe -> "Tap an empty square"
            GameKind.Dots -> "Close a box to go again"
            GameKind.Reversi -> "Tap a dotted square to outflank"
        }, color = c.textDim, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
            // Every game can end in an agreed draw; only chess has claimable draws (repetition / fifty moves).
            val claim = remember(match.board) { if (match.game == GameKind.Chess) ChessView.from(match.board)?.claim else null }
            when {
                claim != null && state.myTurn -> GameAction(R.drawable.ic_handshake, "Claim draw", Modifier.weight(1f), highlight = true) { vm.draw("claim") }
                match.drawOffer == state.me -> GameAction(R.drawable.ic_handshake, "Draw offered", Modifier.weight(1f), enabled = false) {}
                else -> GameAction(R.drawable.ic_handshake, "Offer draw", Modifier.weight(1f), enabled = match.drawOffer == null) { vm.draw("offer") }
            }
            GameAction(R.drawable.ic_flag, "Resign", Modifier.weight(1f)) { onConfirm("resign") }
            GameAction(R.drawable.ic_exit, "Leave", Modifier.weight(1f)) { onConfirm("leave") }
        }
    }
}

@Composable
private fun GameAction(icon: Int, label: String, modifier: Modifier, enabled: Boolean = true, highlight: Boolean = false, onClick: () -> Unit) {
    val c = OneTheme.colors
    val tint = when { !enabled -> c.muted; highlight -> c.accentYou; else -> c.text }
    Row(modifier.heightIn(min = OneTheme.sizes.send48).clip(RoundedCornerShape(OneTheme.radii.md10))
        .background(if (highlight) c.accentYou.copy(alpha = .12f) else c.bgRaised)
        .border(1.dp, if (highlight) c.accentYou.copy(alpha = .5f) else c.border, RoundedCornerShape(OneTheme.radii.md10))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(OneTheme.spacing.sm8))
        Text(label, Modifier.weight(1f, fill = false).padding(vertical = OneTheme.spacing.xs4), color = tint,
            style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** Title, reason, series and the rematch choices. */
@Composable
private fun ResultActions(vm: GameViewModel, state: GameUi, compact: Boolean) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val match = state.match ?: return
    val series = seriesText(match, state.me, state.partner, vm.nickname)
    val iAsked = state.me in match.rematch
    val theyAsked = state.partner in match.rematch
    // They closed the results or their phone went offline: a rematch can't be answered, so don't offer one.
    val theyLeft = state.partner in match.away
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) s.sm8 else s.lg16)) {
        Text(outcomeTitle(match, state.me, vm.nickname), style = if (compact) MaterialTheme.typography.titleLarge else OneTextStyles.screenTitle,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(listOfNotNull(outcomeDetail(match, state.me, vm.nickname), series.takeIf { compact }).joinToString(" · "),
            color = c.textDim, style = OneTextStyles.subtitle, textAlign = TextAlign.Center)
        if (series != null && !compact) Text(series, style = MaterialTheme.typography.titleMedium)
        if (theyLeft) Text("${vm.nickname} has left the game", color = c.textDim, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        else if (theyAsked) Text("${vm.nickname} wants a rematch", color = c.accentOther, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (match.outcome?.reason != "connection_ended") {
            if (theyLeft) Unit
            else if (iAsked) SecondaryButton("Waiting for ${vm.nickname}…", {}, Modifier.fillMaxWidth(), enabled = false)
            else PrimaryButton(if (theyAsked) "Accept rematch" else "Rematch", vm::rematch, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
                SecondaryButton("New game", vm::hub, Modifier.weight(1f))
                SecondaryButton("Done", vm::done, Modifier.weight(1f))
            }
        } else SecondaryButton("Done", vm::done, Modifier.fillMaxWidth())
    }
}

@Composable
private fun GameResultContent(vm: GameViewModel, state: GameUi, photo: Any?, sounds: GameSounds, modifier: Modifier) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val match = state.match ?: return
    // Chess keeps the final position on screen, as a finished game should.
    if (match.game == GameKind.Chess) {
        ChessContent(vm, state, photo, modifier, sounds) {
            Surface(Modifier.fillMaxWidth().padding(s.md12), shape = RoundedCornerShape(OneTheme.radii.lg16), color = c.bgRaised,
                border = BorderStroke(1.dp, c.border)) {
                Box(Modifier.padding(s.lg16)) { ResultActions(vm, state, compact = true) }
            }
        }
        return
    }
    val vector = remember(match.game, c.accentYou) { gameVector(match.game.wire, c.accentYou) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(s.xl24),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.lg16, Alignment.CenterVertically)) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(c.accentYou.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            Icon(vector, null, Modifier.size(56.dp), tint = c.accentYou)
        }
        ResultActions(vm, state, compact = false)
    }
    }
}
