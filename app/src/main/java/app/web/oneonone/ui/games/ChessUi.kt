package app.web.oneonone.ui.games

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.web.oneonone.R
import app.web.oneonone.data.games.*
import app.web.oneonone.ui.components.PrimaryButton
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.theme.OneTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import kotlin.math.roundToInt

/** Familiar green board colours and highlights; the same in light and dark mode, like a real board. */
internal object ChessColors {
    val light = Color(0xFFEBECD0)
    val dark = Color(0xFF739552)
    val lastLight = Color(0xFFF5F682)
    val lastDark = Color(0xFFB9CA43)
    val hint = Color(0x24000000)
    val hover = Color(0xA6FFFFFF)
    val check = Color(0xFFE5484D)
    val whiteFill = Color(0xFFFBFAF7)
    val whiteOutline = Color(0xFF2C2A27)
    val blackFill = Color(0xFF2C2A27)
}

@DrawableRes
internal fun pieceDrawable(piece: Char): Int = when (piece) {
    'K' -> R.drawable.chess_wk; 'Q' -> R.drawable.chess_wq; 'R' -> R.drawable.chess_wr
    'B' -> R.drawable.chess_wb; 'N' -> R.drawable.chess_wn; 'P' -> R.drawable.chess_wp
    'k' -> R.drawable.chess_bk; 'q' -> R.drawable.chess_bq; 'r' -> R.drawable.chess_br
    'b' -> R.drawable.chess_bb; 'n' -> R.drawable.chess_bn; else -> R.drawable.chess_bp
}

/** Display cell (row-major from the top-left) -> board square, with the player's own colour at the bottom. */
private fun squareAt(cell: Int, white: Boolean): Int = if (white) (7 - cell / 8) * 8 + cell % 8 else cell / 8 * 8 + 7 - cell % 8
private fun cellOf(square: Int, white: Boolean): Int = squareAt(square, white) // the mapping is its own inverse
private fun isLight(square: Int) = (square / 8 + square % 8) % 2 == 1

/** Piece identities between positions (not Compose state: only the latest position matters). */
private class SpriteTrack {
    private var previous: List<Sprite>? = null
    private var position = ""
    private var counter = 0
    var gone: List<Sprite> = emptyList(); private set
    fun next(view: ChessView): List<Sprite> {
        // The server confirming a move shown in advance: same pieces, nothing to animate.
        if (view.position == position) return previous!!
        val next = advanceSprites(previous, view) { counter++ }
        val ids = next.mapTo(HashSet()) { it.id }
        // Only a real one-move step fades out a captured piece; a fresh board replaces everything.
        gone = if (previous != null && next.any { s -> previous!!.any { it.id == s.id } }) previous!!.filter { it.id !in ids } else emptyList()
        previous = next; position = view.position
        return next
    }
}

/** Slide time grows with distance (a one-square step is quick, a long diagonal still reads as one glide). */
internal fun slideMillis(fromCell: Int, toCell: Int): Int {
    val dx = (fromCell % 8 - toCell % 8).toFloat(); val dy = (fromCell / 8 - toCell / 8).toFloat()
    return (150 + 45 * kotlin.math.sqrt(kotlin.math.sqrt(dx * dx + dy * dy))).roundToInt().coerceAtMost(280)
}

/** Where a released piece was under the finger, so it settles from there instead of jumping. */
private class Release(val id: Int, val at: Offset)

/** Top-left of a dragged piece: centred on the finger and raised so the finger doesn't hide it. */
private fun liftedAt(finger: Offset, tilePx: Float) = Offset(finger.x - tilePx / 2, finger.y - tilePx * .85f)

@Composable
internal fun ChessBoard(matchId: String, view: ChessView, seat: Int, enabled: Boolean, busy: Boolean,
                        sounds: GameSounds?, move: (JsonObject) -> Unit) {
    val white = seat != 2
    // The player's own move is shown at once; the server's answer replaces it (or a rejection reverts it).
    var pending by remember(matchId) { mutableStateOf<String?>(null) }
    val currentBusy by rememberUpdatedState(busy)
    LaunchedEffect(view.position, busy) { if (!busy) pending = null }
    LaunchedEffect(pending) { if (pending != null) { delay(1500); if (!currentBusy) pending = null } }
    val shown = remember(view, pending) { pending?.let { view.played(it) } ?: view }
    val track = remember(matchId) { SpriteTrack() }
    val sprites = remember(matchId, shown.position) { track.next(shown) }
    var selected by remember(matchId) { mutableIntStateOf(-1) }
    var promotion by remember(matchId) { mutableStateOf<List<String>?>(null) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var release by remember { mutableStateOf<Release?>(null) }
    val currentEnabled by rememberUpdatedState(enabled && pending == null)
    val currentShown by rememberUpdatedState(shown)
    val currentSprites by rememberUpdatedState(sprites)

    // Sounds follow what the board shows: once per new position, never again for the server's echo.
    val heard = remember(matchId) { intArrayOf(-1) }
    LaunchedEffect(shown.position) {
        val count = shown.pieces.count { it != '.' }
        if (heard[0] >= 0 && shown.lastTo >= 0) sounds?.play(when {
            shown.checkSquare >= 0 -> GameSound.Check
            count < heard[0] -> GameSound.Capture
            else -> GameSound.Move
        })
        heard[0] = count
        selected = -1; promotion = null
    }

    fun own(square: Int) = currentShown.pieces[square] != '.' && currentShown.pieces[square].isUpperCase() == white
    fun play(uci: String) {
        pending = uci
        move(buildJsonObject { put("uci", uci) })
    }
    fun destination(square: Int, dropped: Boolean) {
        if (!currentEnabled || square !in 0..63) { selected = -1; return }
        val options = if (selected >= 0) currentShown.movesFrom(selected).filter { squareIndex(it.substring(2, 4)) == square } else emptyList()
        when {
            options.size > 1 -> promotion = options
            options.size == 1 -> { play(options.single()); selected = -1 }
            own(square) && !dropped -> selected = if (selected == square) -1 else square
            else -> selected = -1
        }
    }
    val currentDestination by rememberUpdatedState<(Int, Boolean) -> Unit>(::destination)
    val targets = remember(selected, shown.position) {
        BooleanArray(64).apply { if (selected >= 0) shown.movesFrom(selected).forEach { this[squareIndex(it.substring(2, 4))] = true } }
    }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(matchId, white) {
        fun at(position: Offset): Int {
            if (position.x !in 0f..size.width.toFloat() || position.y !in 0f..size.height.toFloat()) return -1
            val col = (position.x / size.width * 8).toInt().coerceIn(0, 7)
            val row = (position.y / size.height * 8).toInt().coerceIn(0, 7)
            return squareAt(row * 8 + col, white)
        }
        detectDragGestures(onDragStart = { pos ->
            val square = at(pos)
            if (currentEnabled && square >= 0 && own(square)) { selected = square; dragFrom = square; dragPos = pos }
        }, onDragCancel = {
            dragPos?.let { p -> currentSprites.firstOrNull { it.square == dragFrom }?.let { release = Release(it.id, liftedAt(p, size.width / 8f)) } }
            dragPos = null; dragFrom = -1
        }, onDragEnd = {
            val end = dragPos
            if (end != null && dragFrom >= 0) {
                currentSprites.firstOrNull { it.square == dragFrom }?.let { release = Release(it.id, liftedAt(end, size.width / 8f)) }
                currentDestination(at(end), true)
            }
            dragPos = null; dragFrom = -1
        }) { change, amount -> dragPos?.let { change.consume(); dragPos = it + amount } }
    }) {
        val tile = maxWidth / 8
        val tilePx = with(LocalDensity.current) { tile.toPx() }
        val hover = dragPos?.let { p ->
            val col = (p.x / tilePx).toInt(); val row = (p.y / tilePx).toInt()
            if (col in 0..7 && row in 0..7) squareAt(row * 8 + col, white) else -1
        } ?: -1
        Canvas(Modifier.fillMaxSize()) {
            val t = size.width / 8
            for (cell in 0..63) {
                val sq = squareAt(cell, white)
                val pos = Offset(cell % 8 * t, cell / 8 * t)
                val highlighted = sq == shown.lastFrom || sq == shown.lastTo || sq == selected
                drawRect(when {
                    highlighted -> if (isLight(sq)) ChessColors.lastLight else ChessColors.lastDark
                    isLight(sq) -> ChessColors.light
                    else -> ChessColors.dark
                }, pos, Size(t, t))
                if (sq == shown.checkSquare) drawRect(Brush.radialGradient(listOf(ChessColors.check, ChessColors.check.copy(alpha = 0f)),
                    center = pos + Offset(t / 2, t / 2), radius = t * .7f), pos, Size(t, t))
                if (sq == hover && dragFrom >= 0) drawRect(ChessColors.hover, pos + Offset(t * .03f, t * .03f), Size(t * .94f, t * .94f), style = Stroke(t * .06f))
            }
            // Legal destinations: a dot on an empty square, a ring around a piece that can be taken.
            for (sq in 0..63) if (targets[sq]) {
                val cell = cellOf(sq, white)
                val center = Offset(cell % 8 * t + t / 2, cell / 8 * t + t / 2)
                if (shown.pieces[sq] == '.') drawCircle(ChessColors.hint, t * .16f, center)
                else drawCircle(ChessColors.hint, t * .45f, center, style = Stroke(t * .09f))
            }
        }
        // Coordinates inside the edge squares (files bottom-right, ranks top-left), in the other square colour.
        for (i in 0..7) {
            val fileSquare = squareAt(56 + i, white); val rankSquare = squareAt(i * 8, white)
            Box(Modifier.offset(tile * i, tile * 7).size(tile).padding(horizontal = 3.dp, vertical = 1.dp), contentAlignment = Alignment.BottomEnd) {
                Text("${'a' + fileSquare % 8}", fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isLight(fileSquare)) ChessColors.dark else ChessColors.light)
            }
            Box(Modifier.offset(0.dp, tile * i).size(tile).padding(horizontal = 3.dp, vertical = 1.dp), contentAlignment = Alignment.TopStart) {
                Text("${rankSquare / 8 + 1}", fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isLight(rankSquare)) ChessColors.dark else ChessColors.light)
            }
        }
        // The capture fades while the attacker travels, finishing as it lands.
        val captureMs = if (shown.lastFrom >= 0) slideMillis(cellOf(shown.lastFrom, white), cellOf(shown.lastTo, white)) else 200
        for (sprite in track.gone) key("gone-${sprite.id}") {
            val fade = remember { Animatable(1f) }
            LaunchedEffect(Unit) { fade.animateTo(0f, tween(captureMs, easing = LinearOutSlowInEasing)) }
            val cell = cellOf(sprite.square, white)
            Image(painterResource(pieceDrawable(sprite.piece)), null, Modifier.size(tile)
                .offset { IntOffset((cell % 8 * tilePx).roundToInt(), (cell / 8 * tilePx).roundToInt()) }
                .graphicsLayer { alpha = fade.value; scaleX = .85f + .15f * fade.value; scaleY = .85f + .15f * fade.value }
                .padding(tile * .04f))
        }
        val easing = OneTheme.motion.standard
        for (sprite in sprites) key(sprite.id) {
            val cell = cellOf(sprite.square, white)
            val target = Offset(cell % 8 * tilePx, cell / 8 * tilePx)
            val position = remember { Animatable(target, Offset.VectorConverter) }
            val lifted = dragFrom == sprite.square && dragPos != null
            val lift by animateFloatAsState(if (lifted) 1.2f else 1f, spring(dampingRatio = .8f, stiffness = Spring.StiffnessMedium), label = "lift")
            LaunchedEffect(target, lifted) {
                if (lifted) return@LaunchedEffect
                val r = release
                if (r?.id == sprite.id) {
                    // Released: glide from under the finger onto its square (or back home), no jump.
                    position.snapTo(r.at)
                    release = null
                    position.animateTo(target, spring(dampingRatio = .85f, stiffness = 900f))
                } else if (position.value != target) {
                    val from = position.value
                    val fromCell = (from.y / tilePx).roundToInt().coerceIn(0, 7) * 8 + (from.x / tilePx).roundToInt().coerceIn(0, 7)
                    position.animateTo(target, tween(slideMillis(fromCell, cell), easing = easing))
                }
            }
            Image(painterResource(pieceDrawable(sprite.piece)), null, Modifier.size(tile)
                .zIndex(if (lifted || release?.id == sprite.id) 2f else if (position.isRunning) 1f else 0f)
                .offset {
                    // Lifted above the finger so it stays visible while dragging; placement only, no recomposition per frame.
                    // Until the effect has snapped it, a just-released piece is drawn where the finger let go.
                    val at = dragPos?.takeIf { lifted }?.let { liftedAt(it, tilePx) }
                        ?: release?.takeIf { it.id == sprite.id }?.at ?: position.value
                    IntOffset(at.x.roundToInt(), at.y.roundToInt())
                }
                .graphicsLayer { scaleX = lift; scaleY = lift }
                .padding(tile * .04f))
        }
        CellGrid(8, 8, description = { cell ->
            val sq = squareAt(cell, white); val piece = shown.pieces[sq]
            "${squareName(sq)}, ${if (piece == '.') "empty" else "${if (piece.isUpperCase()) "white" else "black"} ${pieceName(piece)}"}" +
                (if (targets[sq]) ", legal move" else "") + (if (sq == shown.checkSquare) ", in check" else "")
        }, enabled = { enabled }, click = { destination(squareAt(it, white), false) })
        promotion?.let { options -> PromotionPicker(options, white, tile, cellOf(squareIndex(options.first().substring(2, 4)), white) % 8,
            onPick = { uci -> promotion = null; play(uci) }, onCancel = { promotion = null; selected = -1 }) }
    }
}

/** A column of four pieces over the promotion square, like the big chess sites; tap outside to cancel. */
@Composable
private fun PromotionPicker(options: List<String>, white: Boolean, tile: androidx.compose.ui.unit.Dp, column: Int,
                            onPick: (String) -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().zIndex(3f).background(Color(0x66000000)).clickable(onClickLabel = "Cancel promotion", onClick = onCancel)) {
        Column(Modifier.offset(x = tile * column).width(tile).shadow(8.dp, RoundedCornerShape(OneTheme.radii.sm6))
            .background(Color.White, RoundedCornerShape(OneTheme.radii.sm6))) {
            listOf('q', 'n', 'r', 'b').forEach { letter ->
                val uci = options.firstOrNull { it.last() == letter } ?: return@forEach
                val piece = if (white) letter.uppercaseChar() else letter
                Image(painterResource(pieceDrawable(piece)), "Promote to ${pieceName(piece)}", Modifier.size(tile)
                    .clickable(role = Role.Button) { onPick(uci) }.padding(tile * .06f))
            }
        }
    }
}

/** Name, avatar, captured pieces with the material lead, and whose move it is. */
@Composable
internal fun ChessPlayerBar(name: String, photo: Any?, white: Boolean, view: ChessView?, active: Boolean, status: String?) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val taken = view?.captured(!white).orEmpty() // pieces of the other colour this player has taken
    val lead = (view?.material() ?: 0).let { if (white) it else -it }
    Row(Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = s.xs4), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(OneTheme.radii.sm6))
            .background(if (white) ChessColors.whiteFill else ChessColors.blackFill)
            .border(1.dp, c.border, RoundedCornerShape(OneTheme.radii.sm6)), contentAlignment = Alignment.Center) {
            if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(1).uppercase(), color = if (white) ChessColors.blackFill else ChessColors.whiteFill, style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                taken.forEach { piece -> Image(painterResource(pieceDrawable(piece)), null, Modifier.size(15.dp)) }
                if (lead > 0) Text("  +$lead", style = MaterialTheme.typography.labelSmall, color = c.textDim)
            }
        }
        if (status != null) Surface(shape = CircleShape, color = if (active) c.accentYou.copy(alpha = .14f) else c.bgRaised,
            border = BorderStroke(1.dp, if (active) c.accentYou.copy(alpha = .5f) else c.border)) {
            Text(status, Modifier.padding(horizontal = s.sm8, vertical = s.xxs2).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium, color = if (active) c.accentYou else c.textDim)
        }
    }
}

/** "1. e4 e5 2. Nf3 …", scrolled to the latest move. */
@Composable
internal fun ChessMoveList(history: List<String>) {
    val c = OneTheme.colors
    val list = rememberLazyListState()
    LaunchedEffect(history.size) { if (history.isNotEmpty()) list.animateScrollToItem(history.size - 1) }
    LazyRow(Modifier.fillMaxWidth().height(32.dp).background(c.bgRaised), state = list,
        contentPadding = PaddingValues(horizontal = OneTheme.spacing.md12), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.xxs2)) {
        if (history.isEmpty()) item { Text("Moves appear here", style = MaterialTheme.typography.labelMedium, color = c.muted) }
        itemsIndexed(history) { i, san ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i % 2 == 0) Text("${i / 2 + 1}.", Modifier.padding(start = OneTheme.spacing.xs4, end = OneTheme.spacing.xxs2),
                    style = MaterialTheme.typography.labelMedium, color = c.muted)
                val last = i == history.lastIndex
                Text(san, Modifier.clip(RoundedCornerShape(OneTheme.radii.xs4))
                    .background(if (last) c.border else Color.Transparent).padding(horizontal = OneTheme.spacing.xs4, vertical = OneTheme.spacing.xxs2),
                    style = MaterialTheme.typography.labelMedium, fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal, color = c.text)
            }
        }
    }
}

/** Move list, opponent bar, board, own bar, draw controls: the playing and the finished chess screen. */
@Composable
internal fun ChessContent(vm: GameViewModel, state: GameUi, photo: Any?, modifier: Modifier, sounds: GameSounds?, footer: @Composable () -> Unit) {
    val match = state.match ?: return
    val view = ChessView.from(match.board)
    val white = state.seat == 1
    val playing = match.status == MatchStatus.Playing
    val partnerAway = state.partner in match.away
    val myTurn = playing && match.turn == state.me
    Column(modifier.fillMaxWidth()) {
        ChessMoveList(view?.history.orEmpty())
        // Bar, board, bar stay together as one block, centred in the space left over.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight - BarHeight * 2)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(side)) {
                    ChessPlayerBar(vm.nickname, photo, !white, view, active = playing && !myTurn, status = when {
                        !playing -> null
                        partnerAway -> "Reconnecting…"
                        !myTurn -> "Thinking…"
                        else -> null
                    })
                }
                Box(Modifier.size(side)) {
                    if (view != null) ChessBoard(match.id, view, state.seat, enabled = state.myTurn && !match.paused, busy = state.busy, sounds = sounds, move = vm::move)
                }
                Box(Modifier.width(side)) {
                    ChessPlayerBar("You", null, white, view, active = myTurn, status = when {
                        !playing -> null
                        myTurn && view?.checkSquare?.let { it >= 0 } == true -> "Check · your move"
                        myTurn -> "Your move"
                        else -> null
                    })
                }
            }
        }
        footer()
    }
}

private val BarHeight = 44.dp

/** Draw offer / claim / answer, under the board. */
@Composable
internal fun ChessDrawControls(vm: GameViewModel, state: GameUi) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    val match = state.match ?: return
    val view = ChessView.from(match.board) ?: return
    if (match.drawOffer == state.partner) Surface(color = c.bgRaised, border = BorderStroke(1.dp, c.accentOther.copy(alpha = .4f)),
        shape = RoundedCornerShape(OneTheme.radii.lg16), modifier = Modifier.padding(horizontal = s.md12, vertical = s.xs4)) {
        Row(Modifier.fillMaxWidth().padding(s.md12), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
            Text("${vm.nickname} offers a draw", Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Accept", { vm.draw("accept") })
            SecondaryButton("Decline", { vm.draw("decline") })
        }
    } else Row(Modifier.fillMaxWidth().padding(horizontal = s.md12, vertical = s.xs4), horizontalArrangement = Arrangement.spacedBy(s.sm8)) {
        if (view.claim != null && state.myTurn) PrimaryButton(if (view.claim == "fifty_moves") "Claim draw (50 moves)" else "Claim draw (repetition)",
            { vm.draw("claim") }, Modifier.weight(1f))
        SecondaryButton(if (match.drawOffer == state.me) "Draw offered" else "Offer draw", { vm.draw("offer") }, Modifier.weight(1f),
            enabled = match.drawOffer == null)
    }
}
