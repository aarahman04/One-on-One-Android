package app.web.oneonone.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.data.games.*
import app.web.oneonone.ui.chat.FeatureModal
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Classic board and piece colours: the same on every theme, so "white" and "black" always mean what they say. */
internal object ChessColors {
    val light = Color(0xFFF0D9B5)
    val dark = Color(0xFFB58863)
    val lastMove = Color(0x66CDD26A)
    val whiteFill = Color(0xFFFBFAF7)
    val whiteOutline = Color(0xFF2C2A27)
    val blackFill = Color(0xFF2C2A27)
    val blackOutline = Color(0xFFE8E4DC)
}

@Composable
internal fun GameBoard(vm: GameViewModel, state: GameUi) {
    val match = state.match ?: return
    val enabled = state.myTurn && !match.paused
    when (match.game) {
        GameKind.Chess -> ChessView.from(match.board)?.let { ChessBoard(match.id, it, state.seat, enabled, vm::move) }
        GameKind.Connect4 -> GridView.from(match.board, 42)?.let { ConnectFourBoard(match.id, it, state.seat, enabled, vm::move) }
        GameKind.TicTacToe -> GridView.from(match.board, 9)?.let { TicTacToeBoard(match.id, it, state.seat, enabled, vm::move) }
        GameKind.Dots -> DotsView.from(match.board)?.let { DotsBoard(match.id, it, state.seat, vm.nickname, enabled, vm::move) }
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

/** Display cell (row-major from the top-left) -> board square, with the player's own colour at the bottom. */
private fun squareAt(cell: Int, white: Boolean): Int = if (white) (7 - cell / 8) * 8 + cell % 8 else cell / 8 * 8 + 7 - cell % 8
private fun cellOf(square: Int, white: Boolean): Int = squareAt(square, white) // the mapping is its own inverse

/** Piece identities between server snapshots (not Compose state: only the latest snapshot matters). */
private class SpriteTrack {
    private var previous: List<Sprite>? = null
    private var counter = 0
    var gone: List<Sprite> = emptyList(); private set
    fun next(view: ChessView): List<Sprite> {
        val next = advanceSprites(previous, view) { counter++ }
        val ids = next.mapTo(HashSet()) { it.id }
        // Only a real one-move step fades out a captured piece; a fresh board replaces everything.
        gone = if (previous != null && next.any { s -> previous!!.any { it.id == s.id } }) previous!!.filter { it.id !in ids } else emptyList()
        previous = next
        return next
    }
}

@Composable
private fun pieceVector(piece: Char) = remember(piece) {
    if (piece.isUpperCase()) chessVector(piece, ChessColors.whiteFill, ChessColors.whiteOutline)
    else chessVector(piece, ChessColors.blackFill, ChessColors.blackOutline)
}

@Composable
private fun ChessBoard(matchId: String, view: ChessView, seat: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val white = seat != 2
    val track = remember(matchId) { SpriteTrack() }
    val sprites = remember(matchId, view.fen) { track.next(view) }
    var selected by remember(matchId) { mutableIntStateOf(-1) }
    var promotion by remember(matchId) { mutableStateOf<List<String>?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentMove by rememberUpdatedState(move)
    val currentView by rememberUpdatedState(view)
    LaunchedEffect(view.fen) { selected = -1; dragging = false; drag = Offset.Zero; promotion = null }
    fun own(square: Int) = currentView.pieces[square] != '.' && currentView.pieces[square].isUpperCase() == white
    fun destination(square: Int) {
        if (!currentEnabled || square !in 0..63) return
        val options = if (selected >= 0) currentView.movesFrom(selected).filter { squareIndex(it.substring(2, 4)) == square } else emptyList()
        when {
            options.size > 1 -> { promotion = options; selected = -1 }
            options.size == 1 -> { currentMove(buildJsonObject { put("uci", options.single()) }); selected = -1 }
            own(square) -> selected = if (selected == square) -1 else square
            else -> selected = -1
        }
    }
    val currentDestination by rememberUpdatedState<(Int) -> Unit>(::destination)
    val targets = remember(selected, view.fen) {
        BooleanArray(64).apply { if (selected >= 0) view.movesFrom(selected).forEach { this[squareIndex(it.substring(2, 4))] = true } }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.md10)).pointerInput(matchId, white) {
        fun at(position: Offset): Int {
            if (position.x !in 0f..size.width.toFloat() || position.y !in 0f..size.height.toFloat()) return -1
            val col = (position.x / size.width * 8).toInt().coerceIn(0, 7)
            val row = (position.y / size.height * 8).toInt().coerceIn(0, 7)
            return squareAt(row * 8 + col, white)
        }
        detectDragGestures(onDragStart = { pos ->
            val square = at(pos)
            if (currentEnabled && square >= 0 && own(square)) { selected = square; dragging = true; dragStart = pos; drag = Offset.Zero }
        }, onDragCancel = { dragging = false; drag = Offset.Zero }, onDragEnd = {
            if (dragging) currentDestination(at(dragStart + drag))
            dragging = false; drag = Offset.Zero
        }) { change, amount -> if (dragging) { change.consume(); drag += amount } }
    }) {
        val tile = maxWidth / 8
        val tilePx = with(LocalDensity.current) { tile.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val t = size.width / 8
            for (cell in 0..63) {
                val sq = squareAt(cell, white)
                val pos = Offset(cell % 8 * t, cell / 8 * t)
                drawRect(if ((cell / 8 + cell % 8) % 2 == 0) ChessColors.light else ChessColors.dark, pos, Size(t, t))
                if (sq == view.lastFrom || sq == view.lastTo) drawRect(ChessColors.lastMove, pos, Size(t, t))
                if (sq == selected) drawRect(c.accentYou.copy(alpha = .35f), pos, Size(t, t))
                if (sq == view.checkSquare) drawRect(Brush.radialGradient(listOf(Color(0xE6E5484D), Color(0x00E5484D)),
                    center = pos + Offset(t / 2, t / 2), radius = t * .62f), pos, Size(t, t))
            }
            // Legal destinations: a dot on empty squares, a ring around a capture.
            for (sq in 0..63) if (targets[sq]) {
                val cell = cellOf(sq, white)
                val center = Offset(cell % 8 * t + t / 2, cell / 8 * t + t / 2)
                if (view.pieces[sq] == '.') drawCircle(Color(0x52000000), t * .16f, center)
                else drawCircle(Color(0x52000000), t * .44f, center, style = Stroke(t * .09f))
            }
        }
        // File and rank labels on the edge squares, in the opposite square colour.
        for (i in 0..7) {
            val fileSquare = squareAt(56 + i, white); val rankSquare = squareAt(i * 8, white)
            Text("${'a' + fileSquare % 8}", Modifier.offset(tile * i + tile - 11.dp, tile * 8 - 15.dp), fontSize = 10.sp,
                color = if ((7 + i) % 2 == 0) ChessColors.dark else ChessColors.light)
            Text("${rankSquare / 8 + 1}", Modifier.offset(3.dp, tile * i + 1.dp), fontSize = 10.sp,
                color = if (i % 2 == 0) ChessColors.dark else ChessColors.light)
        }
        for (sprite in track.gone) key("gone-${sprite.id}") {
            val fade = remember { Animatable(1f) }
            LaunchedEffect(Unit) { fade.animateTo(0f, tween(180)) }
            val cell = cellOf(sprite.square, white)
            Image(rememberVectorPainter(pieceVector(sprite.piece)), null, Modifier.size(tile)
                .offset { IntOffset((cell % 8 * tilePx).roundToInt(), (cell / 8 * tilePx).roundToInt()) }
                .graphicsLayer { alpha = fade.value; scaleX = .7f + .3f * fade.value; scaleY = .7f + .3f * fade.value }
                .padding(tile * .08f))
        }
        for (sprite in sprites) key(sprite.id) {
            val cell = cellOf(sprite.square, white)
            val target = IntOffset((cell % 8 * tilePx).roundToInt(), (cell / 8 * tilePx).roundToInt())
            val position by animateIntOffsetAsState(target, tween(OneTheme.motion.base200, easing = OneTheme.motion.standard), label = "piece")
            val lifted = dragging && sprite.square == selected
            Image(rememberVectorPainter(pieceVector(sprite.piece)), null, Modifier.size(tile)
                .offset { if (lifted) IntOffset(target.x + drag.x.roundToInt(), target.y + drag.y.roundToInt()) else position }
                .graphicsLayer { if (lifted) { scaleX = 1.12f; scaleY = 1.12f } }
                .padding(tile * .08f))
        }
        CellGrid(8, 8, description = { cell ->
            val sq = squareAt(cell, white); val piece = view.pieces[sq]
            "${squareName(sq)}, ${if (piece == '.') "empty" else "${if (piece.isUpperCase()) "white" else "black"} ${pieceName(piece)}"}" +
                (if (targets[sq]) ", legal move" else "") + (if (sq == view.checkSquare) ", in check" else "")
        }, enabled = { enabled }, click = { destination(squareAt(it, white)) })
    }
    promotion?.let { options -> FeatureModal(onDismissRequest = { promotion = null }, title = { Text("Promote your pawn") }, text = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf('q', 'r', 'b', 'n').forEach { letter ->
                val uci = options.firstOrNull { it.last() == letter } ?: return@forEach
                val piece = if (white) letter.uppercaseChar() else letter
                Column(Modifier.clip(RoundedCornerShape(OneTheme.radii.md10)).clickable(role = Role.Button) {
                    promotion = null; currentMove(buildJsonObject { put("uci", uci) })
                }.padding(OneTheme.spacing.sm8), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(OneTheme.radii.md10)).background(ChessColors.light), contentAlignment = Alignment.Center) {
                        Image(rememberVectorPainter(pieceVector(piece)), null, Modifier.size(44.dp))
                    }
                    Text(pieceName(piece).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }, confirmButton = {}, dismissButton = { SecondaryButton("Cancel", { promotion = null }) }) }
}

@Composable
private fun TicTacToeBoard(matchId: String, game: GridView, seat: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val motion = OneTheme.motion
    val progress = remember(matchId) { Animatable(1f) }
    val win = remember(matchId) { Animatable(0f) }
    LaunchedEffect(game.last) { if (game.last >= 0) { progress.snapTo(0f); progress.animateTo(1f, tween(motion.base200, easing = motion.standard)) } }
    LaunchedEffect(game.line.size) { if (game.line.isNotEmpty()) win.animateTo(1f, tween(motion.slow320, easing = motion.emphasized)) }
    val stroke = with(LocalDensity.current) { OneTheme.spacing.xs4.toPx() }
    val arcStroke = remember(stroke) { Stroke(stroke, cap = StrokeCap.Round) }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.bgRaised)) {
        Canvas(Modifier.fillMaxSize()) {
            val tile = size.width / 3
            for (i in 1..2) {
                drawLine(c.border, Offset(i * tile, tile * .1f), Offset(i * tile, size.height - tile * .1f), stroke / 2, StrokeCap.Round)
                drawLine(c.border, Offset(tile * .1f, i * tile), Offset(size.width - tile * .1f, i * tile), stroke / 2, StrokeCap.Round)
            }
            for (i in 0..8) if (game.cells[i] != 0) {
                val color = if (game.cells[i] == seat) c.accentYou else c.accentOther
                val p = if (i == game.last) progress.value else 1f
                val left = i % 3 * tile + tile * .27f; val top = i / 3 * tile + tile * .27f
                val length = tile * .46f
                if (game.cells[i] == 1) {
                    drawLine(color, Offset(left, top), Offset(left + length * p, top + length * p), stroke, StrokeCap.Round)
                    drawLine(color, Offset(left + length, top), Offset(left + length * (1 - p), top + length * p), stroke, StrokeCap.Round)
                } else drawArc(color, -90f, 360f * p, false, Offset(left, top), Size(length, length), style = arcStroke)
            }
            if (game.line.isNotEmpty()) {
                val first = game.line.first(); val last = game.line.last()
                val start = Offset((first % 3 + .5f) * tile, (first / 3 + .5f) * tile)
                val end = Offset((last % 3 + .5f) * tile, (last / 3 + .5f) * tile)
                drawLine(if (game.cells[first] == seat) c.accentYou else c.accentOther, start, start + (end - start) * win.value, stroke * 1.5f, StrokeCap.Round)
            }
        }
        CellGrid(3, 3, { "Row ${it / 3 + 1}, column ${it % 3 + 1}, ${when (game.cells[it]) { 1 -> "X"; 2 -> "O"; else -> "empty" }}" },
            { enabled && game.cells[it] == 0 }, { move(buildJsonObject { put("cell", it) }) })
    }
}

@Composable
private fun ConnectFourBoard(matchId: String, game: GridView, seat: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val motion = OneTheme.motion
    val drop = remember(matchId) { Animatable(1f) }
    LaunchedEffect(game.last) { if (game.last >= 0) { drop.snapTo(0f); drop.animateTo(1f, spring(motion.springDamping, motion.springStiffness)) } }
    val ring = remember { Stroke(5f) }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.border)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width / 7; val h = size.height / 6
            for (i in 0..41) {
                val x = (i % 7 + .5f) * w; val y = (i / 7 + .5f) * h
                drawCircle(c.bg, w * .38f, Offset(x, y))
                if (game.cells[i] != 0) {
                    val animatedY = if (i == game.last) y - (i / 7 + 1) * h * (1f - drop.value) else y
                    drawCircle(if (game.cells[i] == seat) c.accentYou else c.accentOther, w * .35f, Offset(x, animatedY))
                }
                if (game.line.contains(i)) drawCircle(c.bg, w * .22f, Offset(x, y), style = ring)
            }
        }
        CellGrid(6, 7, { "Column ${it % 7 + 1}, row ${it / 7 + 1}, ${if (game.cells[it] == 0) "empty" else if (game.cells[it] == seat) "your disc" else "partner's disc"}" },
            { enabled && game.landing(it % 7) >= 0 }, { move(buildJsonObject { put("col", it % 7) }) })
    }
}

@Composable
private fun DotsBoard(matchId: String, game: DotsView, seat: Int, nickname: String, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val progress = remember(matchId) { Animatable(1f) }
    val motion = OneTheme.motion
    LaunchedEffect(game.lastIndex, game.lastHorizontal) { progress.snapTo(.3f); progress.animateTo(1f, tween(motion.base200, easing = motion.standard)) }
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.bgRaised)) {
        val step = maxWidth / 6
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 6
            for (i in 0..24) if (game.boxes[i] != 0) drawRoundRect(
                (if (game.boxes[i] == seat) c.accentYou else c.accentOther).copy(alpha = .16f),
                Offset((i % 5 + .5f) * unit + unit * .08f, (i / 5 + .5f) * unit + unit * .08f), Size(unit * .84f, unit * .84f), CornerRadius(unit * .1f))
            for (horizontal in listOf(true, false)) for (i in 0..29) {
                val owner = if (horizontal) game.horizontal[i] else game.vertical[i]
                val start = if (horizontal) Offset((i % 5 + .5f) * unit, (i / 5 + .5f) * unit) else Offset((i % 6 + .5f) * unit, (i / 6 + .5f) * unit)
                val end = start + if (horizontal) Offset(unit, 0f) else Offset(0f, unit)
                val latest = game.lastHorizontal == horizontal && game.lastIndex == i
                val color = when (owner) { 0 -> c.border; seat -> c.accentYou; else -> c.accentOther }
                drawLine(color.copy(alpha = if (latest) progress.value else 1f), start, end, if (owner == 0) unit * .03f else unit * .07f, StrokeCap.Round)
            }
            for (r in 0..5) for (col in 0..5) drawCircle(c.text, unit * .06f, Offset((col + .5f) * unit, (r + .5f) * unit))
        }
        for (i in 0..24) if (game.boxes[i] != 0) Box(Modifier.offset(step * (i % 5 + .5f), step * (i / 5 + .5f)).size(step), contentAlignment = Alignment.Center) {
            Text(if (game.boxes[i] == seat) "You" else nickname.take(1).uppercase(), color = if (game.boxes[i] == seat) c.accentYou else c.accentOther,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { contentDescription = "Box row ${i / 5 + 1}, column ${i % 5 + 1}, ${if (game.boxes[i] == seat) "yours" else nickname}" })
        }
        // Generous invisible touch targets centred on each line.
        for (horizontal in listOf(true, false)) repeat(30) { index ->
            val row = index / if (horizontal) 5 else 6
            val col = index % if (horizontal) 5 else 6
            val owner = if (horizontal) game.horizontal[index] else game.vertical[index]
            Box(Modifier.offset(step * (col + if (horizontal) .7f else .2f), step * (row + if (horizontal) .2f else .7f))
                .size(step * .6f)
                .semantics { contentDescription = "${if (horizontal) "Horizontal" else "Vertical"} line, row ${row + 1}, column ${col + 1}, ${if (owner == 0) "available" else if (owner == seat) "yours" else nickname}" }
                .clickable(enabled = enabled && owner == 0, role = Role.Button) {
                    move(buildJsonObject { put("h", horizontal); put("r", row); put("c", col) })
                })
        }
    }
}

@Composable
internal fun PongBoard(vm: GameViewModel, state: GameUi) {
    val game = vm.pong ?: return
    val match = state.match ?: return
    val c = OneTheme.colors
    var frame by remember(game) { mutableLongStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(game) {
        var hits = game.hits
        while (isActive) {
            withFrameNanos { game.advance(it); frame = it }
            if (game.hits != hits) { hits = game.hits; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }
    val paused = match.paused
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight / PongClient.CourtH)
        Box(Modifier.size(width, width * PongClient.CourtH).clip(RoundedCornerShape(OneTheme.radii.lg16)).background(c.bgRaised)) {
            Canvas(Modifier.fillMaxSize()
                .pointerInput(game) { detectTapGestures { vm.paddle(it.x / size.width) } }
                .pointerInput(game) {
                    detectDragGestures(onDragStart = { vm.paddle(it.x / size.width) }) { change, _ ->
                        change.consume(); vm.paddle(change.position.x / size.width)
                    }
                }.semantics {
                    contentDescription = "Table tennis court. Your paddle is at the bottom. Drag left and right to move it."
                    customActions = listOf(
                        CustomAccessibilityAction("Move paddle left") { vm.paddle(game.myX - .1f); true },
                        CustomAccessibilityAction("Move paddle right") { vm.paddle(game.myX + .1f); true })
                }) {
                frame // Read in the draw phase: the court redraws every display frame.
                val w = size.width; val h = size.height
                val unit = h / PongClient.CourtH
                val dash = w / 24
                for (i in 0 until 12) drawLine(c.border, Offset(i * 2 * dash + dash / 2, h / 2), Offset(i * 2 * dash + dash * 1.5f, h / 2), unit * .006f, StrokeCap.Round)
                val paddleW = PongClient.PaddleHalf * 2 * w
                val paddleH = unit * .022f
                drawRoundRect(c.accentOther, Offset((game.theirX - PongClient.PaddleHalf) * w, PongClient.PaddleInset * unit - paddleH),
                    Size(paddleW, paddleH), CornerRadius(paddleH))
                drawRoundRect(c.accentYou, Offset((game.myX - PongClient.PaddleHalf) * w, h - PongClient.PaddleInset * unit),
                    Size(paddleW, paddleH), CornerRadius(paddleH))
                if (game.live) drawCircle(c.text, PongClient.BallR * w, Offset(game.ballX * w, game.ballY * unit))
            }
            Text("${game.theirScore}", Modifier.align(Alignment.TopCenter).padding(top = width * .32f).alpha(.18f), style = OneTextStyles.screenTitle.copy(fontSize = 56.sp), color = c.text)
            Text("${game.myScore}", Modifier.align(Alignment.BottomCenter).padding(bottom = width * .32f).alpha(.18f), style = OneTextStyles.screenTitle.copy(fontSize = 56.sp), color = c.text)
            val countdown = ceil(game.countdownMs / 1000f).toInt().also { frame }
            when {
                paused -> Text("Paused", Modifier.align(Alignment.Center).semantics { liveRegion = LiveRegionMode.Polite }, color = c.textDim, style = OneTextStyles.screenTitle)
                countdown > 0 -> Text("$countdown", Modifier.align(Alignment.Center).semantics { liveRegion = LiveRegionMode.Polite }, color = c.text, style = OneTextStyles.screenTitle)
            }
        }
    }
}
