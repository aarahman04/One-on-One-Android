package app.web.oneonone.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.web.oneonone.data.games.*
import app.web.oneonone.ui.chat.FeatureModal
import app.web.oneonone.ui.components.PrimaryButton
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*
import kotlin.math.ceil

@Composable
internal fun GameBoard(vm: GameViewModel, state: GameUi) {
    val enabled = state.stage == GameStage.Playing && !state.paused && !state.busy && vm.board?.result == null && vm.board?.turn == state.player
    when (state.kind) {
        GameKind.Chess -> ChessBoard(vm.board as ChessGame, state.player, enabled, vm::move)
        GameKind.Connect4 -> ConnectFourBoard(vm.board as ConnectFourGame, state.player, enabled, vm::move)
        GameKind.TicTacToe -> TicTacToeBoard(vm.board as TicTacToeGame, state.player, enabled, vm::move)
        GameKind.Dots -> DotsBoard(vm.board as DotsGame, state.player, vm.nickname, enabled, vm::move)
        GameKind.Pong -> PongBoard(vm, state)
    }
}

@Composable
private fun CellGrid(rows: Int, columns: Int, description: (Int) -> String, enabled: (Int) -> Boolean,
                     click: (Int) -> Unit, content: @Composable BoxScope.(Int) -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        repeat(rows) { r -> Row(Modifier.weight(1f)) {
            repeat(columns) { col ->
                val index = r * columns + col
                Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = description(index) }
                    .clickable(enabled = enabled(index), role = Role.Button) { click(index) }, contentAlignment = Alignment.Center) { content(index) }
            }
        } }
    }
}

private fun squareAt(index: Int, player: Int): Int = if (player == 1) (7 - index / 8) * 8 + index % 8 else index / 8 * 8 + 7 - index % 8
private fun squareName(square: Int) = "${'a' + square % 8}${square / 8 + 1}"

@Composable
private fun ChessBoard(game: ChessGame, player: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val vectors = remember(c.accentYou, c.accentOther, player) { "PNBRQKpnbrqk".associateWith { chessVector(it, if (it.isUpperCase() == (player == 1)) c.accentYou else c.accentOther) } }
    var selected by remember(game) { mutableIntStateOf(-1) }
    var promotion by remember(game) { mutableStateOf<List<String>?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentMove by rememberUpdatedState(move)
    LaunchedEffect(game.nextSeq) { selected = -1; dragging = false; drag = Offset.Zero }
    fun destination(square: Int) {
        if (!currentEnabled || square !in 0..63) return
        val options = game.legal.filter { it.startsWith(squareName(selected) + squareName(square)) }
        if (selected >= 0 && options.isNotEmpty()) {
            if (options.size > 1) promotion = options else currentMove(buildJsonObject { put("uci", options.single()) })
            selected = -1
        } else if (game.pieces[square] != '.' && game.pieces[square].isUpperCase() == (player == 1)) selected = square
        else selected = -1
    }
    val currentDestination by rememberUpdatedState<(Int) -> Unit>(::destination)
    val legal = remember(selected, game.nextSeq) {
        BooleanArray(64).apply { if (selected >= 0) game.legal.filter { it.startsWith(squareName(selected)) }.forEach { this[(it[3] - '1') * 8 + (it[2] - 'a')] = true } }
    }
    val last = game.lastMove
    val lastFrom = last?.let { (it[1] - '1') * 8 + (it[0] - 'a') } ?: -1
    val lastTo = last?.let { (it[3] - '1') * 8 + (it[2] - 'a') } ?: -1
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.md10)).pointerInput(game, player) {
        fun at(position: Offset): Int {
            if (position.x !in 0f..size.width.toFloat() || position.y !in 0f..size.height.toFloat()) return -1
            val col = (position.x / size.width * 8).toInt().coerceIn(0, 7)
            val row = (position.y / size.height * 8).toInt().coerceIn(0, 7)
            return squareAt(row * 8 + col, player)
        }
        detectDragGestures(onDragStart = { pos ->
            val square = at(pos)
            if (currentEnabled && square >= 0 && game.pieces[square] != '.' && game.pieces[square].isUpperCase() == (player == 1)) {
                selected = square; dragging = true; dragStart = pos; drag = Offset.Zero
            }
        }, onDragCancel = { dragging = false; drag = Offset.Zero }, onDragEnd = {
            if (dragging) currentDestination(at(dragStart + drag))
            dragging = false; drag = Offset.Zero
        }) { change, amount -> if (dragging) { change.consume(); drag += amount } }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val tile = size.width / 8
            for (i in 0..63) {
                val sq = squareAt(i, player)
                val pos = Offset(i % 8 * tile, i / 8 * tile)
                drawRect(if ((i / 8 + i % 8) % 2 == 0) c.bgRaised else c.border, pos, Size(tile, tile))
                if (sq == lastFrom || sq == lastTo) drawRect(c.accentOther.copy(alpha = .18f), pos, Size(tile, tile))
                if (sq == selected) drawRect(c.accentYou.copy(alpha = .25f), pos, Size(tile, tile))
                if (sq == game.checkSquare) drawRect(c.danger.copy(alpha = .4f), pos, Size(tile, tile))
                if (legal[sq]) drawCircle(c.accentYou.copy(alpha = .65f), tile * .09f, pos + Offset(tile / 2, tile / 2))
            }
        }
        CellGrid(8, 8, description = { index ->
            val sq = squareAt(index, player); val piece = game.pieces[sq]
            "${squareName(sq)}, ${if (piece == '.') "empty" else "${if (piece.isUpperCase()) "white" else "black"} ${pieceName(piece)}"}${if (legal[sq]) ", legal destination" else ""}${if (sq == game.checkSquare) ", in check" else ""}"
        }, enabled = { enabled }, click = { destination(squareAt(it, player)) }) { index ->
            val sq = squareAt(index, player); val piece = game.pieces[sq]
            if (piece != '.') Icon(vectors.getValue(piece), null,
                Modifier.fillMaxSize().padding(OneTheme.spacing.xs4).graphicsLayer {
                    if (dragging && sq == selected) { translationX = drag.x; translationY = drag.y }
                }, tint = if (piece.isUpperCase() == (player == 1)) c.accentYou else c.accentOther)
        }
    }
    promotion?.let { options -> FeatureModal(onDismissRequest = { promotion = null }, title = { Text("Promote your pawn") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
            options.forEach { uci -> PrimaryButton(pieceName(uci.last()).replaceFirstChar { it.uppercase() }, {
                promotion = null; currentMove(buildJsonObject { put("uci", uci) })
            }, Modifier.fillMaxWidth()) }
        }
    }, confirmButton = {}, dismissButton = { SecondaryButton("Cancel", { promotion = null }) }) }
}

@Composable
private fun TicTacToeBoard(game: TicTacToeGame, player: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val motion = OneTheme.motion
    val progress = remember(game) { Animatable(1f) }
    val win = remember(game) { Animatable(0f) }
    LaunchedEffect(game.nextSeq) { progress.snapTo(0f); progress.animateTo(1f, tween(motion.base200, easing = motion.standard)) }
    LaunchedEffect(game.winning) { if (game.winning.isNotEmpty()) win.animateTo(1f, tween(motion.slow320, easing = motion.emphasized)) }
    val stroke = with(LocalDensity.current) { OneTheme.spacing.xs4.toPx() }
    val arcStroke = remember(stroke) { Stroke(stroke, cap = StrokeCap.Round) }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.bgRaised)) {
        Canvas(Modifier.fillMaxSize()) {
            val tile = size.width / 3
            for (i in 1..2) {
                drawLine(c.border, Offset(i * tile, tile * .1f), Offset(i * tile, size.height - tile * .1f), stroke / 2)
                drawLine(c.border, Offset(tile * .1f, i * tile), Offset(size.width - tile * .1f, i * tile), stroke / 2)
            }
            for (i in 0..8) if (game.cells[i] != 0) {
                val color = if (game.cells[i] == player) c.accentYou else c.accentOther
                val p = if (i == game.lastCell) progress.value else 1f
                val left = i % 3 * tile + tile * .27f; val top = i / 3 * tile + tile * .27f
                val length = tile * .46f
                if (game.cells[i] == 1) {
                    drawLine(color, Offset(left, top), Offset(left + length * p, top + length * p), stroke, StrokeCap.Round)
                    drawLine(color, Offset(left + length, top), Offset(left + length * (1 - p), top + length * p), stroke, StrokeCap.Round)
                } else drawArc(color, -90f, 360f * p, false, Offset(left, top), Size(length, length), style = arcStroke)
            }
            if (game.winning.isNotEmpty()) {
                val first = game.winning.first(); val last = game.winning.last()
                val start = Offset((first % 3 + .5f) * tile, (first / 3 + .5f) * tile)
                val end = Offset((last % 3 + .5f) * tile, (last / 3 + .5f) * tile)
                drawLine(if (game.result?.winner == player) c.accentYou else c.accentOther, start, start + (end - start) * win.value, stroke, StrokeCap.Round)
            }
        }
        CellGrid(3, 3, { "Row ${it / 3 + 1}, column ${it % 3 + 1}, ${when (game.cells[it]) { 1 -> "X"; 2 -> "O"; else -> "empty" }}" },
            { enabled && game.cells[it] == 0 }, { move(buildJsonObject { put("cell", it) }) })
    }
}

@Composable
private fun ConnectFourBoard(game: ConnectFourGame, player: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val motion = OneTheme.motion
    val drop = remember(game) { Animatable(1f) }
    LaunchedEffect(game.nextSeq) { drop.snapTo(0f); drop.animateTo(1f, spring(motion.springDamping, motion.springStiffness)) }
    val ring = remember { Stroke(4f) }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.border)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width / 7; val h = size.height / 6
            for (i in 0..41) {
                val x = (i % 7 + .5f) * w; val y = (i / 7 + .5f) * h
                drawCircle(c.bg, w * .37f, Offset(x, y))
                if (game.cells[i] != 0) {
                    val animatedY = if (i == game.lastCell) y - (i / 7 + 1) * h * (1f - drop.value) else y
                    drawCircle(if (game.cells[i] == player) c.accentYou else c.accentOther, w * .35f, Offset(x, animatedY))
                }
                if (game.winning.contains(i)) drawCircle(c.bg, w * .25f, Offset(x, y), style = ring)
            }
        }
        CellGrid(6, 7, { "Row ${it / 7 + 1}, column ${it % 7 + 1}, ${if (game.cells[it] == 0) "empty" else if (game.cells[it] == player) "your disc" else "partner's disc"}. Drop in column ${it % 7 + 1}" },
            { enabled && game.landing(it % 7) >= 0 }, { move(buildJsonObject { put("col", it % 7) }) })
    }
}

@Composable
private fun DotsBoard(game: DotsGame, player: Int, nickname: String, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val progress = remember(game) { Animatable(1f) }
    val motion = OneTheme.motion
    LaunchedEffect(game.nextSeq) { progress.snapTo(.4f); progress.animateTo(1f, tween(motion.base200, easing = motion.standard)) }
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.bgRaised)) {
        val step = maxWidth / 6
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 6
            for (i in 0..24) if (game.boxes[i] != 0) drawRect(
                (if (game.boxes[i] == player) c.accentYou else c.accentOther).copy(alpha = .15f * progress.value),
                Offset((i % 5 + .5f) * unit, (i / 5 + .5f) * unit), Size(unit, unit))
            for (i in 0..29) {
                val horizontal = game.horizontal[i]; val vertical = game.vertical[i]
                val hp = Offset((i % 5 + .5f) * unit, (i / 5 + .5f) * unit)
                val vp = Offset((i % 6 + .5f) * unit, (i / 6 + .5f) * unit)
                drawLine(when (horizontal) { player -> c.accentYou; 0 -> c.muted; else -> c.accentOther }, hp, hp + Offset(unit, 0f), if (horizontal == 0) unit * .025f else unit * .065f, StrokeCap.Round)
                drawLine(when (vertical) { player -> c.accentYou; 0 -> c.muted; else -> c.accentOther }, vp, vp + Offset(0f, unit), if (vertical == 0) unit * .025f else unit * .065f, StrokeCap.Round)
            }
            for (r in 0..5) for (col in 0..5) drawCircle(c.textDim, unit * .055f, Offset((col + .5f) * unit, (r + .5f) * unit))
        }
        for (i in 0..24) if (game.boxes[i] != 0) Box(Modifier.offset(step * (i % 5 + .5f), step * (i / 5 + .5f)).size(step), contentAlignment = Alignment.Center) {
            Text(if (game.boxes[i] == player) "Y" else nickname.take(1).uppercase(), color = if (game.boxes[i] == player) c.accentYou else c.accentOther,
                style = OneTextStyles.cardHeading, modifier = Modifier.semantics { contentDescription = "Box row ${i / 5 + 1}, column ${i % 5 + 1}, ${if (game.boxes[i] == player) "yours" else nickname}" })
        }
        for (horizontal in listOf(true, false)) repeat(30) { index ->
            val row = index / if (horizontal) 5 else 6
            val col = index % if (horizontal) 5 else 6
            val owner = if (horizontal) game.horizontal[index] else game.vertical[index]
            Box(Modifier.offset(step * (col + if (horizontal) .65f else .25f), step * (row + if (horizontal) .25f else .65f))
                .size(if (horizontal) step * .7f else step * .5f, if (horizontal) step * .5f else step * .7f)
                .semantics { contentDescription = "${if (horizontal) "Horizontal" else "Vertical"} line, row ${row + 1}, column ${col + 1}, ${if (owner == 0) "available" else if (owner == player) "yours" else nickname}" }
                .clickable(enabled = enabled && owner == 0, role = Role.Button) {
                    move(buildJsonObject { put("h", horizontal); put("r", row); put("c", col) })
                })
        }
    }
}

@Composable
private fun PongBoard(vm: GameViewModel, state: GameUi) {
    val game = vm.pong ?: return
    val c = OneTheme.colors
    val frames = vm.frame.collectAsState()
    var countdown by remember(game) { mutableIntStateOf(3) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(game, state.paused) {
        var hits = game.hits
        while (isActive && !state.paused) {
            withFrameNanos { vm.frame(it) }
            countdown = ceil(game.countdown).toInt()
            if (game.hits != hits) { hits = game.hits; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }
    val paused by rememberUpdatedState(state.paused)
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.bgRaised)) {
        Canvas(Modifier.fillMaxSize().pointerInput(game) {
            detectDragGestures(onDragStart = { if (!paused) vm.paddle(it.y / size.height) }) { change, _ ->
                if (!paused) { change.consume(); vm.paddle(change.position.y / size.height) }
            }
        }.semantics {
            contentDescription = "Table tennis court. Your paddle is on the ${if (state.player == 1) "left" else "right"}. Drag to move."
            role = Role.Button
            progressBarRangeInfo = ProgressBarRangeInfo(if (state.player == 1) game.hostY else game.guestY, PongGame.PaddleHalf..(1f - PongGame.PaddleHalf))
            setProgress { vm.paddle(it); true }
            customActions = listOf(CustomAccessibilityAction("Move paddle up") { vm.paddle((if (state.player == 1) game.hostY else game.guestY) - .07f); true },
                CustomAccessibilityAction("Move paddle down") { vm.paddle((if (state.player == 1) game.hostY else game.guestY) + .07f); true })
        }) {
            frames.value // Read in the draw phase: physics never recomposes the court every display frame.
            val w = size.width; val h = size.height
            val paddleH = PongGame.PaddleHalf * 2 * h; val paddleW = w * .018f
            for (i in 0..15) drawLine(c.border, Offset(w * .5f, i * h / 16), Offset(w * .5f, (i + .5f) * h / 16), w * .005f)
            drawRoundRect(if (state.player == 1) c.accentYou else c.accentOther, Offset(w * PongGame.PaddleX - paddleW / 2, (game.hostY - PongGame.PaddleHalf) * h), Size(paddleW, paddleH), CornerRadius(paddleW))
            drawRoundRect(if (state.player == 2) c.accentYou else c.accentOther, Offset(w * (1 - PongGame.PaddleX) - paddleW / 2, (game.guestY - PongGame.PaddleHalf) * h), Size(paddleW, paddleH), CornerRadius(paddleW))
            drawCircle(c.text, w * PongGame.BallRadius, Offset(game.x * w, game.y * h))
        }
        if (countdown > 0) Text(countdown.toString(), Modifier.align(Alignment.Center).semantics { liveRegion = LiveRegionMode.Polite }, color = c.text, style = OneTextStyles.screenTitle)
    }
}
