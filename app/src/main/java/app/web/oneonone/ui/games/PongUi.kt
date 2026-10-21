package app.web.oneonone.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
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

private const val MarginX = .28f
private const val MarginTop = .42f
private const val MarginBottom = .7f
private val OwnRubber = Color(0xFFEE7468)
private val OtherRubber = Color(0xFF55B9E8)

private class PongHud {
    var mine by mutableIntStateOf(0)
    var theirs by mutableIntStateOf(0)
    var countdown by mutableIntStateOf(0)
    var serving by mutableStateOf(false)
    var myServe by mutableStateOf(false)
}

@Composable
internal fun PongContent(vm: GameViewModel, state: GameUi, modifier: Modifier, footer: @Composable () -> Unit) {
    val game = vm.pong ?: return
    val match = state.match ?: return
    val hud = remember(game) { PongHud() }
    var frame by remember(game) { mutableLongStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(game) {
        vm.paddle(game.myX, game.myY)
        var hits = game.hits
        while (isActive) {
            withFrameNanos { game.advance(it); frame = it }
            if (game.hits != hits) { hits = game.hits; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            if (game.myScore != hud.mine || game.theirScore != hud.theirs) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hud.mine = game.myScore; hud.theirs = game.theirScore
            hud.countdown = ceil(game.countdownMs / 1000f).toInt()
            hud.serving = game.waitingServe; hud.myServe = game.myServe
        }
    }
    val paused = match.paused || state.partner in match.away
    Column(modifier.fillMaxWidth()) {
        PongScoreboard(hud.mine, hud.theirs, vm.nickname)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth / (1 + 2 * MarginX), maxHeight / (PongClient.CourtH + MarginTop + MarginBottom))
            Box(Modifier.size(unit * (1 + 2 * MarginX), unit * (PongClient.CourtH + MarginTop + MarginBottom))) {
                PongTable(game, { frame }, enabled = !paused, onPaddle = vm::paddle)
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
private fun PongScoreboard(mine: Int, theirs: Int, nickname: String) {
    val c = OneTheme.colors
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp),
        color = c.bgRaised, border = BorderStroke(1.dp, c.border)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) {
            contentDescription = "You $mine, $nickname $theirs, first to 7"
        }, verticalAlignment = Alignment.CenterVertically) {
            Text("You", color = OwnRubber, style = MaterialTheme.typography.labelLarge)
            AnimatedContent(mine, Modifier.padding(start = 12.dp), label = "your score") { Text("$it", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text("first to 7", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = c.muted)
            AnimatedContent(theirs, Modifier.padding(end = 12.dp), label = "their score") { Text("$it", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(nickname, color = OtherRubber, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.widthIn(max = 92.dp))
        }
    }
}

@Composable
private fun PongTable(game: PongClient, frame: () -> Long, enabled: Boolean, onPaddle: (Float, Float, Boolean) -> Unit) {
    val currentEnabled by rememberUpdatedState(enabled)
    Canvas(Modifier.fillMaxSize().pointerInput(game) {
        awaitEachGesture {
            val down = awaitFirstDown(); down.consume()
            val unit = size.width / (1 + 2 * MarginX)
            fun move(position: Offset) {
                if (currentEnabled) onPaddle(position.x / unit - MarginX, position.y / unit - MarginTop, false)
            }
            move(down.position)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    move(change.position)
                }
            } finally {
                if (currentEnabled) onPaddle(game.myX, game.myY, true)
            }
        }
    }.semantics {
        contentDescription = "Ping Pong. Move your racket around your half and swipe through the ball. A sideways brush adds spin."
        customActions = listOf(
            CustomAccessibilityAction("Move racket left") { if (currentEnabled) onPaddle(game.myX - .1f, game.myY, true); currentEnabled },
            CustomAccessibilityAction("Move racket right") { if (currentEnabled) onPaddle(game.myX + .1f, game.myY, true); currentEnabled },
            CustomAccessibilityAction("Move racket forward") { if (currentEnabled) onPaddle(game.myX, game.myY - .1f, true); currentEnabled },
            CustomAccessibilityAction("Move racket back") { if (currentEnabled) onPaddle(game.myX, game.myY + .1f, true); currentEnabled })
    }) {
        val now = frame()
        val u = size.width / (1 + 2 * MarginX)
        fun at(x: Float, y: Float) = Offset((x + MarginX) * u, (y + MarginTop) * u)
        val table = Rect(at(0f, 0f), at(1f, PongClient.CourtH))
        drawRoundRect(Color.Black.copy(alpha = .18f), table.topLeft + Offset(.025f * u, .035f * u), table.size, CornerRadius(.012f * u))
        drawRect(Color(0xFF548E43), table.topLeft, table.size)
        drawRect(Color(0xFF284930), table.topLeft, table.size, style = Stroke(.025f * u))
        val inset = .025f * u
        drawRect(Color.White.copy(alpha = .85f), table.topLeft + Offset(inset, inset), Size(table.width - 2 * inset, table.height - 2 * inset), style = Stroke(.009f * u))
        drawLine(Color.White.copy(alpha = .75f), at(.5f, .025f), at(.5f, PongClient.CourtH - .025f), .009f * u)
        drawLine(Color(0xFF20372B), at(-.035f, PongClient.CourtH / 2), at(1.035f, PongClient.CourtH / 2), .035f * u)
        drawLine(Color.White.copy(alpha = .9f), at(-.035f, PongClient.CourtH / 2), at(1.035f, PongClient.CourtH / 2), .009f * u)

        for (index in 1 until game.trail.size) {
            val previous = game.trail[index - 1]; val point = game.trail[index]
            val k = index.toFloat() / game.trail.size
            drawLine(Color.White.copy(alpha = .25f * k), at(previous.x, previous.y), at(point.x, point.y),
                PongClient.BallR * u * k, cap = StrokeCap.Round)
        }
        if (game.live) {
            val h = (game.height / PongClient.ArcH).coerceIn(0f, 1f)
            drawCircle(Color.Black.copy(alpha = .19f - .05f * h), PongClient.BallR * u * (1 - .15f * h),
                at(game.ballX + .12f * h, game.ballY + .025f * h))
            val since = (now - game.bounceAt) / 1e6f
            if (game.bounceAt > 0 && since in 0f..300f) {
                val k = since / 300
                drawCircle(Color.White.copy(alpha = .25f * (1 - k)), PongClient.BallR * u, at(game.bounceX, game.bounceY))
                drawCircle(Color.White.copy(alpha = .65f * (1 - k)), PongClient.BallR * u * (1 + 1.2f * k), at(game.bounceX, game.bounceY), style = Stroke(.009f * u))
            }
        }
        rotate(game.theirAngle, at(game.theirX, game.theirY)) { drawRacket(at(game.theirX, game.theirY), OtherRubber, u, up = true) }
        rotate(game.myAngle, at(game.myX, game.myY)) { drawRacket(at(game.myX, game.myY), OwnRubber, u, up = false) }
        if (game.live && (game.height >= 0 || game.ballX !in 0f..1f || game.ballY !in 0f..PongClient.CourtH)) {
            val h = (game.height / PongClient.ArcH).coerceIn(-1f, 1f)
            val ball = at(game.ballX, game.ballY)
            val r = PongClient.BallR * u * (1 + .35f * h)
            drawCircle(Color(0xFF253A2C), r * 1.22f, ball)
            drawCircle(Color.White, r, ball)
            if (game.spin != 0f) drawArc(Color(0xFF738277), game.spinAngle, 120f, false, ball - Offset(r * .65f, r * .65f),
                Size(r * 1.3f, r * 1.3f), style = Stroke(.005f * u))
        }
    }
}

private fun DrawScope.drawRacket(center: Offset, color: Color, u: Float, up: Boolean) {
    val r = PongClient.HeadR * u
    val edge = Color(0xFF263B31)
    val handleWidth = .065f * u
    val handleY = if (up) center.y - r - .12f * u else center.y + r * .55f
    val handle = Rect(Offset(center.x - handleWidth / 2, handleY), Size(handleWidth, r * .45f + .12f * u))
    drawCircle(Color.Black.copy(alpha = .18f), r, center + Offset(.025f * u, .025f * u))
    drawRoundRect(lerp(color, Color.White, .2f), handle.topLeft, handle.size, CornerRadius(.015f * u))
    drawRoundRect(edge, handle.topLeft, handle.size, CornerRadius(.015f * u), style = Stroke(.014f * u))
    drawCircle(color, r, center)
    drawCircle(edge, r, center, style = Stroke(.015f * u))
    val bandY = center.y + (if (up) -.65f else .65f) * r
    drawLine(edge, Offset(center.x - .72f * r, bandY), Offset(center.x + .72f * r, bandY), .012f * u)
}
