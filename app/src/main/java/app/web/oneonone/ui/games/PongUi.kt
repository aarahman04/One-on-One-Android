package app.web.oneonone.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.data.games.PongClient
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.sqrt

private const val MarginX = .28f
private const val MarginTop = .5f
private const val MarginBottom = .9f
private val Arena = Color(0xFFFFCF70)
private val TableGreen = Color(0xFF76B536)
private val Ink = Color(0xFF10140D)
private val YourRacket = Color(0xFFFF6B55)
private val OtherRacket = Color(0xFF00B9ED)

private class PongHud {
    var mine by mutableIntStateOf(0)
    var theirs by mutableIntStateOf(0)
    var countdown by mutableIntStateOf(0)
    var serving by mutableStateOf(false)
    var myServe by mutableStateOf(false)
}

@Composable
internal fun PongContent(vm: GameViewModel, state: GameUi, modifier: Modifier, sounds: GameSounds?, footer: @Composable () -> Unit) {
    val game = vm.pong ?: return
    val match = state.match ?: return
    val hud = remember(game) { PongHud() }
    val touch = remember(game) { PongTouch() }
    var frame by remember(game) { mutableLongStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    val paused = match.paused || state.partner in match.away
    val currentPaused by rememberUpdatedState(paused)
    LaunchedEffect(game) {
        vm.paddle(game.myX, game.myY)
        var hits = game.hits
        var bounce = game.bounceAt
        var lastFrame = 0L
        var round = game.round
        while (isActive) {
            withFrameNanos {
                if (currentPaused || round != game.round) touch.cancel()
                round = game.round
                if (touch.held && !currentPaused) {
                    val dt = if (lastFrame == 0L) 1f / 60 else (it - lastFrame) / 1e9f
                    val follow = PongClient.racketFollow(dt)
                    vm.paddle(game.myX + (touch.x - game.myX) * follow, game.myY + (touch.y - game.myY) * follow, touch = touch.x to touch.y)
                }
                lastFrame = it
                game.advance(it); frame = it
            }
            if (game.hits != hits) { hits = game.hits; sounds?.play(GameSound.Hit); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            if (game.bounceAt != bounce) { bounce = game.bounceAt; sounds?.play(GameSound.Bounce) }
            if (game.myScore != hud.mine || game.theirScore != hud.theirs) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hud.mine = game.myScore; hud.theirs = game.theirScore
            hud.countdown = ceil(game.countdownMs / 1000f).toInt()
            hud.serving = game.waitingServe; hud.myServe = game.myServe
        }
    }
    Column(modifier.fillMaxWidth()) {
        PongScoreboard(hud.mine, hud.theirs, vm.nickname, match.points ?: 7)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Arena, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth / (1 + 2 * MarginX), maxHeight / (PongClient.CourtH + MarginTop + MarginBottom))
            Box(Modifier.size(unit * (1 + 2 * MarginX), unit * (PongClient.CourtH + MarginTop + MarginBottom))) {
                PongTable(game, touch, { frame }, enabled = !paused) { x, y -> vm.paddle(x, y, true) }
                when {
                    paused -> CourtNotice("Waiting for ${vm.nickname}…", Modifier.align(Alignment.Center))
                    hud.countdown > 0 -> Surface(Modifier.align(Alignment.Center).size(68.dp),
                        shape = RoundedCornerShape(100), color = OneTheme.colors.bgRaised.copy(alpha = .9f)) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(hud.countdown, label = "serve countdown") { count ->
                                Text("$count", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = OneTheme.colors.text,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                    }
                    hud.serving -> CourtNotice(if (hud.myServe) "Your serve · move your racket into the ball" else "${vm.nickname} is serving",
                        Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                }
            }
        }
        footer()
    }
}

@Composable
private fun CourtNotice(text: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(OneTheme.radii.md10), color = OneTheme.colors.bgRaised.copy(alpha = .94f)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelMedium, color = OneTheme.colors.text)
    }
}

@Composable
private fun PongScoreboard(mine: Int, theirs: Int, nickname: String, target: Int) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp),
        color = Ink, border = BorderStroke(1.dp, Ink)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) {
            contentDescription = "You $mine, $nickname $theirs, first to $target"
        }, verticalAlignment = Alignment.CenterVertically) {
            Text("You", color = YourRacket, style = MaterialTheme.typography.labelLarge)
            AnimatedContent(mine, Modifier.padding(start = 12.dp), label = "your score") { Text("$it", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text("first to $target", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .75f))
            AnimatedContent(theirs, Modifier.padding(end = 12.dp), label = "their score") { Text("$it", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(nickname, color = OtherRacket, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.widthIn(max = 92.dp))
        }
    }
}

@Composable
private fun PongTable(game: PongClient, touch: PongTouch, frame: () -> Long, enabled: Boolean, onRelease: (Float, Float) -> Unit) {
    val currentEnabled by rememberUpdatedState(enabled)
    Canvas(Modifier.fillMaxSize().pointerInput(game, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(); down.consume()
            if (!currentEnabled || !touch.begin(down.id.value, game.myX, game.myY)) return@awaitEachGesture
            val unit = size.width / (1 + 2 * MarginX)
            // Relative drag: the racket moves with the finger from wherever it touches, so it never jumps (a jump
            // would read as a violent stroke) and the finger can steer from below without hiding the racket.
            var anchorFinger = down.position
            var anchorX = game.myX; var anchorY = game.myY
            fun move(position: Offset) {
                if (!currentEnabled) return
                val x = anchorX + (position.x - anchorFinger.x) / unit
                val y = anchorY + (position.y - anchorFinger.y) / unit
                // Past a limit, slide the anchor so turning back responds at once.
                val cx = x.coerceIn(PongClient.RacketMin, PongClient.RacketMax)
                val cy = y.coerceIn(PongClient.RacketNear, PongClient.RacketFar)
                if (cx != x || cy != y) { anchorX = cx - (position.x - anchorFinger.x) / unit; anchorY = cy - (position.y - anchorFinger.y) / unit }
                touch.move(down.id.value, cx, cy)
            }
            var completed = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    change.consume()
                    move(change.position)
                    if (!change.pressed) { completed = true; break }
                }
            } finally {
                touch.end(down.id.value, !completed, game.myX, game.myY)?.let { onRelease(it.first, it.second) }
            }
        }
    }.semantics {
        contentDescription = "Ping Pong. Move your racket around your half and swipe through the ball. Swipe direction aims the shot."
        customActions = listOf(
            CustomAccessibilityAction("Move racket left") { if (currentEnabled) onRelease(game.myX - .1f, game.myY); currentEnabled },
            CustomAccessibilityAction("Move racket right") { if (currentEnabled) onRelease(game.myX + .1f, game.myY); currentEnabled },
            CustomAccessibilityAction("Move racket forward") { if (currentEnabled) onRelease(game.myX, game.myY - .1f); currentEnabled },
            CustomAccessibilityAction("Move racket back") { if (currentEnabled) onRelease(game.myX, game.myY + .1f); currentEnabled })
    }) {
        val now = frame()
        val u = size.width / (1 + 2 * MarginX)
        fun at(x: Float, y: Float) = Offset((x + MarginX) * u, (y + MarginTop) * u)
        val table = Rect(at(0f, 0f), at(1f, PongClient.CourtH))
        drawRect(TableGreen, table.topLeft, table.size)
        drawLine(Color.White, at(.5f, 0f), at(.5f, PongClient.CourtH), .035f * u)
        drawRect(Ink, table.topLeft, table.size, style = Stroke(.05f * u))
        drawLine(Ink, at(0f, PongClient.CourtH / 2), at(1f, PongClient.CourtH / 2), .055f * u)
        drawLine(Color.White, at(.025f, PongClient.CourtH / 2), at(.975f, PongClient.CourtH / 2), .014f * u)
        if (game.live) {
            val h = game.height.coerceAtLeast(0f)
            drawCircle(Ink.copy(alpha = .23f), PongClient.BallR * u * (1 - h * 1.216f).coerceAtLeast(.2f),
                at(game.ballX + .8f * h, game.ballY))
            val since = (now - game.bounceAt) / 1e6f
            if (game.bounceAt > 0 && since in 0f..160f) {
                val k = since / 160
                drawCircle(Color.White.copy(alpha = .5f * (1 - k)), PongClient.BallR * u * (1 + k),
                    at(game.bounceX, game.bounceY), style = Stroke(.008f * u))
            }
        }
        for (index in 1 until game.trail.size) {
            val previous = game.trail[index - 1]; val point = game.trail[index]
            val k = (1 - (now - point.atNanos) / 300_000_000f).coerceIn(0f, 1f)
            if (point.height >= 0 || point.x !in 0f..1f || point.y !in 0f..PongClient.CourtH)
                drawLine(Color.White.copy(alpha = .95f * k), at(previous.x, previous.y), at(point.x, point.y),
                    .09f * u * sqrt(k), cap = StrokeCap.Round)
        }
        if (game.live && (game.height >= 0 || game.ballX !in 0f..1f || game.ballY !in 0f..PongClient.CourtH)) {
            val position = game.visibleBallPosition()
            val ball = at(position.first, position.second)
            val r = PongClient.ballRadius(game.height) * u
            drawCircle(Ink, r * 1.35f, ball)
            drawCircle(Color.White, r, ball)
        }
        drawRacket(at(game.theirX, game.theirY), OtherRacket, u, up = true)
        drawRacket(at(game.myX, game.myY), YourRacket, u, up = false)
    }
}

private fun DrawScope.drawRacket(center: Offset, color: Color, u: Float, up: Boolean) {
    val r = PongClient.HeadR * u
    val handleWidth = .065f * u
    val handleY = if (up) center.y - r - .12f * u else center.y + r * .55f
    val handle = Rect(Offset(center.x - handleWidth / 2, handleY), Size(handleWidth, r * .45f + .12f * u))
    drawCircle(Ink.copy(alpha = .2f), r, center + Offset(.05f * u, .01f * u))
    drawRect(color, handle.topLeft, handle.size)
    drawRect(Ink, handle.topLeft, handle.size, style = Stroke(.023f * u))
    drawCircle(color, r, center)
    drawCircle(Ink, r, center, style = Stroke(PongClient.HeadOutline * u))
    val bandY = center.y + (if (up) -.65f else .65f) * r
    drawLine(Ink, Offset(center.x - .72f * r, bandY), Offset(center.x + .72f * r, bandY), .018f * u)
}
