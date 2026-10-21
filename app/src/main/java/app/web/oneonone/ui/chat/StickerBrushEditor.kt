package app.web.oneonone.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.web.oneonone.data.stickers.*
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

@Composable
internal fun StickerBrushEditor(brush: StickerBrushSession, enabled: Boolean,
    onStroke: (StickerBrushSession, StickerBrushStroke) -> Unit, onSize: (Float) -> Unit, onDistance: (Float) -> Unit) {
    val density = LocalDensity.current
    val previewHeight = with(density) { minOf(320.dp, LocalWindowInfo.current.containerSize.height.toDp() * .36f) }
    val radius = with(density) { brush.size.dp.toPx() / 2 }
    val distance = with(density) { brush.distance.dp.toPx() }
    val base = remember(brush.base) { brush.base.asImageBitmap() }
    val original = remember(brush.original) { ShaderBrush(ImageShader(brush.original.asImageBitmap())) }
    val paths = remember(brush.strokes) { brush.strokes.map { stickerBrushPath(it).asComposePath() to it.restore } }
    var active by remember(brush.token) { mutableStateOf<StickerBrushStroke?>(null) }
    val activePath = remember(active) { active?.let { stickerBrushPath(it).asComposePath() } }
    var cursor by remember(brush.token) { mutableStateOf<Offset?>(null) }
    var frame by remember { mutableStateOf(IntSize(1, 1)) }
    val currentBrush by rememberUpdatedState(brush)
    val currentStroke by rememberUpdatedState(onStroke)
    val accent = OneTheme.colors.accentYou
    val canDraw = enabled && brush.strokes.size < STICKER_BRUSH_STROKES
    fun point(at: Offset) = stickerBrushPoint(brush.base.width, brush.base.height, frame.width.toFloat(), frame.height.toFloat(), at.x, at.y, distance)
    fun strokeAt(at: Offset) = StickerBrushStroke(listOf(point(at)), radius / stickerBrushScale(brush.base.width, brush.base.height,
        frame.width.toFloat(), frame.height.toFloat()), brush.restore)
    fun move(x: Float, y: Float): Boolean {
        val at = cursor ?: Offset(frame.width / 2f, frame.height / 2f + distance)
        cursor = Offset((at.x + x * frame.width).coerceIn(0f, frame.width.toFloat()), (at.y + y * frame.height).coerceIn(0f, frame.height.toFloat()))
        return true
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Touch below the circle. The circle ${if (brush.restore) "restores" else "erases"} the photo.",
            color = OneTheme.colors.textDim, style = OneTextStyles.cardHint)
        Box(Modifier.fillMaxWidth().height(previewHeight).clipToBounds().stickerCheckerboard().onSizeChanged { frame = it }) {
            Canvas(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                val fit = stickerBrushScale(brush.base.width, brush.base.height, size.width, size.height)
                withTransform({ translate((size.width - brush.base.width * fit) / 2, (size.height - brush.base.height * fit) / 2)
                    scale(fit, fit, Offset.Zero) }) {
                    clipRect(0f, 0f, brush.base.width.toFloat(), brush.base.height.toFloat()) {
                        drawImage(base)
                        for ((path, restore) in paths) {
                            if (restore) drawPath(path, original, blendMode = BlendMode.Src)
                            else drawPath(path, Color.Transparent, blendMode = BlendMode.Clear)
                        }
                        activePath?.let { path ->
                            if (active?.restore == true) drawPath(path, original, blendMode = BlendMode.Src)
                            else drawPath(path, Color.Transparent, blendMode = BlendMode.Clear)
                        }
                    }
                }
            }
            Canvas(Modifier.matchParentSize().pointerInput(brush.token, enabled, brush.restore, radius, distance) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (currentBrush.strokes.size >= STICKER_BRUSH_STROKES) return@awaitEachGesture
                    cursor = down.position
                    var stroke = strokeAt(down.position)
                    active = stroke
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume(); cursor = change.position
                            stroke = stroke.copy(points = appendStickerBrushPoint(stroke.points, point(change.position)))
                            active = stroke
                            if (!change.pressed) break
                        }
                    } finally {
                        currentStroke(brush, stroke)
                        active = null; cursor = null
                    }
                }
            }.semantics {
                contentDescription = if (brush.restore) "Restore original photo with offset brush" else "Erase photo with offset brush"
                customActions = if (!canDraw) emptyList() else listOf(
                    CustomAccessibilityAction("Move brush left") { move(-.05f, 0f) },
                    CustomAccessibilityAction("Move brush right") { move(.05f, 0f) },
                    CustomAccessibilityAction("Move brush up") { move(0f, -.05f) },
                    CustomAccessibilityAction("Move brush down") { move(0f, .05f) },
                    CustomAccessibilityAction(if (brush.restore) "Restore here" else "Erase here") {
                        currentStroke(brush, strokeAt(cursor ?: Offset(frame.width / 2f, frame.height / 2f + distance))); true
                    },
                )
            }) {
                cursor?.let { touch ->
                    val circle = touch - Offset(0f, distance)
                    drawLine(accent.copy(alpha = .5f), touch, circle, 1.dp.toPx())
                    drawCircle(Color.Black.copy(alpha = .7f), radius, circle, style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, radius, circle, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.Black, 6.dp.toPx(), touch)
                    drawCircle(accent, 4.dp.toPx(), touch)
                }
            }
        }
        if (!canDraw && enabled) Text("Tap Done to keep these edits, then open Erase or Restore to continue.",
            color = OneTheme.colors.textDim, style = OneTextStyles.cardHint)
        Text("Circle size", color = OneTheme.colors.textDim, style = OneTextStyles.cardHint)
        Slider(brush.size, onSize, enabled = enabled, valueRange = 8f..80f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Circle size" })
        Text("Distance above touch", color = OneTheme.colors.textDim, style = OneTextStyles.cardHint)
        Slider(brush.distance, onDistance, enabled = enabled, valueRange = 0f..120f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Distance above touch" })
    }
}
