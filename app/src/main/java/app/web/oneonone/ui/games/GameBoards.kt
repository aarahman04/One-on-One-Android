package app.web.oneonone.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import app.web.oneonone.data.games.*
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.serialization.json.*

@Composable
internal fun GameBoard(vm: GameViewModel, state: GameUi) {
    val match = state.match ?: return
    val enabled = state.myTurn && !match.paused
    when (match.game) {
        GameKind.Connect4 -> GridView.from(match.board, 42)?.let { ConnectFourBoard(match.id, it, state.seat, enabled, vm::move) }
        GameKind.TicTacToe -> GridView.from(match.board, 9)?.let { TicTacToeBoard(match.id, it, state.seat, enabled, vm::move) }
        GameKind.Dots -> DotsView.from(match.board)?.let { DotsBoard(match.id, it, state.seat, vm.nickname, enabled, vm::move) }
        GameKind.Reversi -> ReversiView.from(match.board)?.let { ReversiBoard(match.id, it, state.seat, enabled, vm::move) }
        GameKind.Chess, GameKind.Pong -> Unit // ChessUi.kt, PongUi.kt
    }
}

@Composable
internal fun CellGrid(rows: Int, columns: Int, description: (Int) -> String, enabled: (Int) -> Boolean,
                     click: (Int) -> Unit, content: @Composable BoxScope.(Int) -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        repeat(rows) { r -> Row(Modifier.weight(1f)) {
            repeat(columns) { col ->
                val index = r * columns + col
                Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = description(index) }
                    
                    // No ripple: boards draw their own feedback, and a per-cell flash reads as a stray white box.
                    .clickable(interactionSource = null, indication = null, enabled = enabled(index), role = Role.Button) { click(index) }, contentAlignment = Alignment.Center) { content(index) }
            }
        } }
    }
}

@Composable
private fun TicTacToeBoard(matchId: String, server: GridView, seat: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val pending = rememberPending(matchId, server)
    val game = pending.value?.let { server.played(it, seat) } ?: server
    val motion = OneTheme.motion
    // Animation state is created per move, so its first frame already starts from zero (no flash of the final state).
    val progress = remember(matchId, game.last) { Animatable(if (game.last >= 0) 0f else 1f) }
    val win = remember(matchId) { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(motion.base200, easing = motion.standard)) }
    // The winning line strikes through once the last mark is drawn.
    LaunchedEffect(game.line.size) { if (game.line.isNotEmpty()) win.animateTo(1f, tween(motion.slow320, delayMillis = motion.base200, easing = motion.emphasized)) }
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
            { enabled && pending.value == null && game.cells[it] == 0 }, { pending.value = it; move(buildJsonObject { put("cell", it) }) })
    }
}

@Composable
private fun ConnectFourBoard(matchId: String, server: GridView, seat: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val pending = rememberPending(matchId, server)
    val game = pending.value?.let { server.played(it, seat) } ?: server
    val motion = OneTheme.motion
    val drop = remember(matchId, game.last) { Animatable(if (game.last >= 0) 0f else 1f) }
    val glow = remember(matchId) { Animatable(0f) }
    LaunchedEffect(drop) {
        if (game.last < 0) return@LaunchedEffect
        // Falls like a disc: accelerating, longer from higher up, then a small settle bounce on landing.
        val rows = game.last / 7 + 1
        drop.animateTo(1f, tween(dropMillis(rows), easing = Gravity))
        drop.animateTo(1f, spring(.45f, 1400f), initialVelocity = -6f / rows) // same ~0.15-row hop from any height
    }
    LaunchedEffect(game.line.size) { if (game.line.isNotEmpty()) { glow.snapTo(0f); glow.animateTo(1f, tween(motion.slow320, delayMillis = 350, easing = motion.emphasized)) } }
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
                if (game.line.contains(i)) drawCircle(c.bg.copy(alpha = glow.value), w * (.12f + .1f * glow.value), Offset(x, y), style = ring)
            }
        }
        CellGrid(6, 7, { "Column ${it % 7 + 1}, row ${it / 7 + 1}, ${if (game.cells[it] == 0) "empty" else if (game.cells[it] == seat) "your disc" else "partner's disc"}" },
            { enabled && pending.value == null && game.landing(it % 7) >= 0 }, { pending.value = game.landing(it % 7); move(buildJsonObject { put("col", it % 7) }) })
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

/**
 * The player's own mark/disc, drawn at once while the server confirms it. Cleared by the server's next board,
 * or after a short wait if the move was rejected (the board then simply shows the server's state).
 */
@Composable
private fun rememberPending(matchId: String, server: GridView): MutableState<Int?> {
    val pending = remember(matchId, server.last, server.cells.count { it != 0 }) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pending.value) { if (pending.value != null) { kotlinx.coroutines.delay(2500); pending.value = null } }
    return pending
}

/** Ease-in like free fall. */
private val Gravity = androidx.compose.animation.core.CubicBezierEasing(.5f, 0f, .9f, .5f)

/** Drop time grows with the square root of the height fallen, as in free fall (1 row ≈ 200 ms, 6 rows ≈ 360 ms). */
internal fun dropMillis(rows: Int): Int = (90 + 110 * kotlin.math.sqrt(rows.coerceIn(1, 6).toFloat())).toInt()

private const val FlipMs = 260
private const val FlipStaggerMs = 70

/**
 * Reversi on green felt, black and white discs. The new disc drops in, then the
 * outflanked discs turn over edge-on (narrowing, swapping colour, widening) in a wave from it.
 */
@Composable
private fun ReversiBoard(matchId: String, game: ReversiView, seat: Int, enabled: Boolean, move: (JsonObject) -> Unit) {
    val c = OneTheme.colors
    val clock = remember(matchId, game.last, game.flipped.size) { Animatable(if (game.last >= 0) 0f else Float.MAX_VALUE) }
    val waves = (game.flipped.maxOfOrNull { game.flipRank(it) } ?: 0) + 1
    val total = 120 + waves * FlipStaggerMs + FlipMs
    LaunchedEffect(clock) { if (game.last >= 0) clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing)) }
    val flipped = remember(game) { game.flipped.toHashSet() }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(OneTheme.radii.sm6)).background(ReversiFelt)) {
        Canvas(Modifier.fillMaxSize()) {
            val t = size.width / 8
            val ms = clock.value
            for (i in 1..7) {
                drawLine(ReversiLine.copy(alpha = .35f), Offset(i * t, 0f), Offset(i * t, size.height), 1.5f)
                drawLine(ReversiLine.copy(alpha = .35f), Offset(0f, i * t), Offset(size.width, i * t), 1.5f)
            }
            if (game.last >= 0) drawRect(ReversiLast.copy(alpha = .55f), Offset(game.last % 8 * t, game.last / 8 * t), Size(t, t))
            for (i in 0..63) {
                val owner = game.cells[i]
                if (owner == 0) continue
                val center = Offset((i % 8 + .5f) * t, (i / 8 + .5f) * t)
                var shown = owner
                var widthScale = 1f
                var scale = 1f
                if (i == game.last) scale = (ms / 120f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
                else if (i in flipped) {
                    val k = ((ms - 120 - game.flipRank(i) * FlipStaggerMs) / FlipMs).coerceIn(0f, 1f)
                    widthScale = kotlin.math.abs(kotlin.math.cos(Math.PI.toFloat() * k)).coerceAtLeast(.06f)
                    if (k < .5f) shown = 3 - owner
                }
                if (scale <= 0f) continue
                val r = t * .41f * scale
                val size2 = Size(r * 2 * widthScale, r * 2)
                val topLeft = Offset(center.x - size2.width / 2, center.y - r)
                drawOval(Color.Black.copy(alpha = .25f), topLeft + Offset(0f, t * .04f), size2)
                drawOval(if (shown == 1) ChessColors.blackFill else ChessColors.whiteFill, topLeft, size2)
                if (shown == 2) drawOval(ChessColors.whiteOutline.copy(alpha = .35f), topLeft, size2, style = Stroke(1.5f))
            }
            // Where you may play: a soft dot in your colour.
            if (enabled) for (i in game.legal) drawCircle((if (seat == 1) ChessColors.blackFill else ChessColors.whiteFill).copy(alpha = .45f),
                t * .12f, Offset((i % 8 + .5f) * t, (i / 8 + .5f) * t))
        }
        val legal = remember(game) { game.legal.toHashSet() }
        CellGrid(8, 8, { "${'a' + it % 8}${8 - it / 8}, ${when (game.cells[it]) { 1 -> "black"; 2 -> "white"; else -> if (it in legal) "empty, you can play here" else "empty" }}" },
            { enabled && it in legal }, { move(buildJsonObject { put("cell", it) }) })
    }
}

// Reversi keeps its traditional green felt (the chess board is black and white).
private val ReversiFelt = Color(0xFF2F7A4B)
private val ReversiLine = Color(0xFFCFE8D5)
private val ReversiLast = Color(0xFF8FCB5A)
