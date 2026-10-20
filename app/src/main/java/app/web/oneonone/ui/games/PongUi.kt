package app.web.oneonone.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

// Table geometry around the server's court (contract §12), in table widths. The table is 1 x CourtH; each
// racket head is centred on an end line and its handle sticks out past the table.
private const val HandleLength = .15f
private const val HandleWidth = .075f
private const val Margin = PongClient.HeadR + HandleLength + .03f

/** Scores, countdown and pause read as Compose state only when they change; the table itself redraws per frame. */
private class PongHud {
    var mine by mutableIntStateOf(0)
    var theirs by mutableIntStateOf(0)
    var countdown by mutableIntStateOf(0)
}

/** Scoreboard over the table, then the table; drag anywhere on it to move your racket. */
@Composable
internal fun PongContent(vm: GameViewModel, state: GameUi, modifier: Modifier, footer: @Composable () -> Unit) {
    val game = vm.pong
    val match = state.match ?: return
    val hud = remember(game) { PongHud() }
    var frame by remember(game) { mutableLongStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(game) {
        val client = game ?: return@LaunchedEffect
        var hits = client.hits
        while (isActive) {
            withFrameNanos { client.advance(it); frame = it }
            if (client.hits != hits) { hits = client.hits; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            if (client.myScore != hud.mine) { hud.mine = client.myScore; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            hud.theirs = client.theirScore
            hud.countdown = ceil(client.countdownMs / 1000f).toInt()
        }
    }
    val paused = match.paused || state.partner in match.away
    Column(modifier.fillMaxWidth()) {
        PongScoreboard(hud.mine, hud.theirs, vm.nickname)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = OneTheme.spacing.lg16), contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth, maxHeight / (PongClient.CourtH + 2 * Margin))
            Box(Modifier.size(unit, unit * (PongClient.CourtH + 2 * Margin)), contentAlignment = Alignment.Center) {
                if (game != null) PongTable(game, { frame }, enabled = !paused, onPaddle = vm::paddle)
                when {
                    paused -> Surface(shape = RoundedCornerShape(OneTheme.radii.lg16), color = OneTheme.colors.bgRaised,
                        border = BorderStroke(1.dp, OneTheme.colors.border)) {
                        Text("Waiting for ${vm.nickname}…", Modifier.padding(horizontal = OneTheme.spacing.lg16, vertical = OneTheme.spacing.sm8)
                            .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleSmall, color = OneTheme.colors.text)
                    }
                    hud.countdown > 0 -> AnimatedContent(hud.countdown, transitionSpec = {
                        (scaleIn(tween(220), initialScale = .6f) + fadeIn(tween(220))) togetherWith fadeOut(tween(120))
                    }, label = "countdown") { n ->
                        Surface(Modifier.size(88.dp), shape = RoundedCornerShape(OneTheme.radii.full), color = OneTheme.colors.bgRaised.copy(alpha = .92f),
                            border = BorderStroke(1.dp, OneTheme.colors.border)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("$n", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                                    color = OneTheme.colors.text)
                            }
                        }
                    }
                }
            }
        }
        footer()
    }
}

/** "You 3  ·  first to 7  ·  2 Alex", scores sliding up as they change. */
@Composable
private fun PongScoreboard(mine: Int, theirs: Int, nickname: String) {
    val c = OneTheme.colors
    val s = OneTheme.spacing
    Surface(Modifier.fillMaxWidth().padding(horizontal = s.lg16, vertical = s.sm8), shape = RoundedCornerShape(OneTheme.radii.lg16),
        color = c.bgRaised, border = BorderStroke(1.dp, c.border)) {
        Row(Modifier.padding(horizontal = s.lg16, vertical = s.sm8).semantics(mergeDescendants = true) {
            contentDescription = "You $mine, $nickname $theirs, first to 7"
        }, verticalAlignment = Alignment.CenterVertically) {
            ScoreSide("You", mine, c.accentYou, Alignment.Start, Modifier.weight(1f))
            Text("first to 7", style = MaterialTheme.typography.labelMedium, color = c.muted)
            ScoreSide(nickname, theirs, c.accentOther, Alignment.End, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreSide(name: String, score: Int, color: Color, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier, horizontalAlignment = align) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        AnimatedContent(score, transitionSpec = {
            (slideInVertically(tween(ScoreSlideMs)) { it } + fadeIn(tween(ScoreSlideMs))) togetherWith
                (slideOutVertically(tween(ScoreSlideMs)) { -it } + fadeOut(tween(ScoreSlideMs)))
        }, label = "score") { value ->
            Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OneTheme.colors.text)
        }
    }
}

private const val ScoreSlideMs = 200

@Composable
private fun PongTable(game: PongClient, frame: () -> Long, enabled: Boolean, onPaddle: (Float) -> Unit) {
    val c = OneTheme.colors
    val currentEnabled by rememberUpdatedState(enabled)
    val table = lerp(c.bgRaised, c.accentYou, .12f)
    val line = c.text.copy(alpha = .28f)
    val net = c.text.copy(alpha = .7f)
    Canvas(Modifier.fillMaxSize()
        // The whole area is the control: the finger's x is the racket's x. Every change is consumed so the
        // screen never scrolls under a rally.
        .pointerInput(game) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val w = size.width.toFloat()
                if (currentEnabled) onPaddle(down.position.x / w)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    if (currentEnabled) onPaddle(change.position.x / w)
                }
            }
        }
        .semantics {
            contentDescription = "Table tennis table. Your racket is at the bottom. Drag left and right to move it."
            customActions = listOf(
                CustomAccessibilityAction("Move racket left") { onPaddle(game.myX - .1f); true },
                CustomAccessibilityAction("Move racket right") { onPaddle(game.myX + .1f); true })
        }) {
        val now = frame() // Read in the draw phase: only the canvas redraws each display frame.
        val u = size.width
        val top = Margin * u
        fun at(x: Float, y: Float) = Offset(x * u, top + y * u)
        val tableRect = Rect(0f, top, u, top + PongClient.CourtH * u)
        val corner = CornerRadius(.035f * u)
        // Table, lines and net.
        drawRoundRect(table, tableRect.topLeft, tableRect.size, corner)
        drawRoundRect(c.border, tableRect.topLeft, tableRect.size, corner, style = Stroke(.012f * u))
        drawLine(line, at(.5f, .03f), at(.5f, PongClient.CourtH - .03f), .012f * u)
        val netY = top + PongClient.CourtH / 2 * u
        drawLine(net, Offset(-.02f * u, netY), Offset(1.02f * u, netY), .022f * u)
        drawCircle(net, .02f * u, Offset(-.02f * u, netY)); drawCircle(net, .02f * u, Offset(1.02f * u, netY))

        clipPath(Path().apply { addRoundRect(RoundRect(tableRect, corner)) }) {
            // Shadows fall on the table only.
            val shadow = Color.Black.copy(alpha = .14f)
            drawCircle(shadow, PongClient.HeadR * u, at(game.theirX + .025f, .03f))
            drawCircle(shadow, PongClient.HeadR * u, at(game.myX + .025f, PongClient.CourtH + .03f))
            if (game.live) {
                val h = game.height
                drawCircle(Color.Black.copy(alpha = .2f - .06f * h), PongClient.BallR * u * (1f - .15f * h),
                    at(game.ballX + .04f * h, game.ballY + .05f * h))
                val since = (now - game.bounceAt) / 1e6f
                if (game.bounceAt > 0 && since in 0f..260f) {
                    val k = since / 260f
                    drawCircle(c.text.copy(alpha = .35f * (1 - k)), PongClient.BallR * u * (1 + 1.4f * k), at(game.bounceX, game.bounceY),
                        style = Stroke(.006f * u))
                }
            }
        }
        // Rackets: the opponent's handle points away from you, yours towards you.
        drawRacket(at(game.theirX, 0f), up = true, color = c.accentOther, u = u)
        drawRacket(at(game.myX, PongClient.CourtH), up = false, color = c.accentYou, u = u)
        if (game.live) {
            val r = PongClient.BallR * u * (1f + .28f * game.height)
            val ball = at(game.ballX, game.ballY)
            drawCircle(Color.White, r, ball)
            drawCircle(c.text.copy(alpha = .45f), r, ball, style = Stroke(.006f * u))
            // A seam that rolls with the sidespin, so a curving shot visibly spins.
            if (game.spin != 0f) drawArc(c.text.copy(alpha = .4f), game.spinAngle, 110f, false, ball - Offset(r * .6f, r * .6f),
                Size(r * 1.2f, r * 1.2f), style = Stroke(.005f * u, cap = StrokeCap.Round))
        }
    }
}

/** A table tennis racket seen from above: round head with a lighter rubber edge, short handle. */
private fun DrawScope.drawRacket(center: Offset, up: Boolean, color: Color, u: Float) {
    val r = PongClient.HeadR * u
    val dir = if (up) -1f else 1f // handle direction on screen
    val edge = lerp(color, Color.Black, .35f)
    val handleColor = lerp(color, Color.Black, .25f)
    val hw = HandleWidth * u
    val handleTop = if (up) center.y - r - HandleLength * u else center.y + r * .6f
    val handleHeight = HandleLength * u + r * .4f
    drawRoundRect(handleColor, Offset(center.x - hw / 2, handleTop), Size(hw, handleHeight), CornerRadius(hw * .35f))
    drawRoundRect(edge, Offset(center.x - hw / 2, handleTop), Size(hw, handleHeight), CornerRadius(hw * .35f), style = Stroke(.01f * u))
    drawCircle(color, r, center)
    // The throat band where the blade meets the handle, as on a real racket.
    val head = Path().apply { addOval(Rect(center, r)) }
    clipPath(head) {
        val bandTop = if (up) center.y - r else center.y + r * .55f
        drawRect(lerp(color, Color.White, .3f), Offset(center.x - r, bandTop), Size(2 * r, r * .45f))
    }
    drawCircle(edge, r, center, style = Stroke(.014f * u))
    drawLine(edge, Offset(center.x - r * .84f, center.y + dir * r * .55f), Offset(center.x + r * .84f, center.y + dir * r * .55f), .01f * u)
}
