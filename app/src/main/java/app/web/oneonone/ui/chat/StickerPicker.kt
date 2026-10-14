package app.web.oneonone.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.web.oneonone.data.stickers.*
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
internal fun StickerGrid(owner: String, vm: StickerViewModel, sending: Boolean, onCreate: () -> Unit, onSend: (StickerEntry) -> Unit) {
    val entries by vm.entries.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var deleting by remember { mutableStateOf<StickerEntry?>(null) }
    val sections = remember(entries) { stickerSections(entries) }
    val c = OneTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Updating sticker library" })
        error?.let { Text(it, color = c.danger, style = OneTextStyles.cardHint) }
        LazyVerticalGrid(GridCells.Adaptive(72.dp), Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "create") {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).heightIn(min = 48.dp)
                    .border(1.dp, c.border, RoundedCornerShape(OneTheme.radii.sm6))
                    .combinedClickable(enabled = !busy && !sending, role = Role.Button, onClick = onCreate)
                    .semantics { contentDescription = "Create a sticker from a photo" }, contentAlignment = Alignment.Center) {
                    Text("+", color = c.accentYou, style = OneTextStyles.screenTitle)
                }
            }
            sections.forEach { section ->
                item(key = section.title, span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, Modifier.semantics { heading() }, color = c.textDim, style = OneTextStyles.menuItem)
                        if (section.entries.isEmpty() && section.title != "My stickers")
                            Text("None yet.", color = c.muted, style = OneTextStyles.cardHint)
                    }
                }
                if (section.entries.isEmpty() && section.title == "My stickers") item(key = section.title + "-empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Tap + to make your first sticker.",
                        color = c.muted, style = OneTextStyles.cardHint)
                }
                items(section.entries, key = { section.title + "/" + it.id }) { sticker ->
                    var menu by remember { mutableStateOf(false) }
                    val favourite = if (sticker.favourite) "Unfavourite" else "Favourite"
                    Box {
                        AsyncImage(vm.file(owner, sticker.id), null,
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(OneTheme.radii.sm6))
                                .combinedClickable(enabled = !busy && !sending, role = Role.Button,
                                    onClickLabel = "Send sticker", onLongClickLabel = "Sticker options",
                                    onClick = { onSend(sticker) }, onLongClick = { menu = true })
                                .semantics {
                                    contentDescription = "Send " + sticker.title + if (sticker.favourite) ", favourite sticker" else ", sticker"
                                    customActions = listOf(
                                        CustomAccessibilityAction(favourite) { if (!busy && !sending) { vm.favourite(sticker.id); true } else false },
                                        CustomAccessibilityAction("Delete sticker") { if (!busy && !sending) { deleting = sticker; true } else false },
                                    )
                                }.padding(6.dp))
                        OneMenu(menu, { menu = false }) {
                            OneMenuItem(favourite, { menu = false; vm.favourite(sticker.id) }, Modifier.heightIn(min = 48.dp))
                            OneMenuItem("Delete", { menu = false; deleting = sticker }, Modifier.heightIn(min = 48.dp), danger = true)
                        }
                    }
                }
            }
        }
    }
    deleting?.let { sticker ->
        FeatureModal(onDismissRequest = { deleting = null }, title = { Text("Delete sticker?") },
            text = { Text("This removes it from your library. Messages you've already sent stay in the conversation.") },
            confirmButton = { DangerButton("Delete", { vm.delete(sticker.id); deleting = null }, enabled = !busy && !sending) },
            dismissButton = { SecondaryButton("Cancel", { deleting = null }) })
    }
}

@Composable
internal fun StickerMaker(vm: StickerViewModel) {
    val draft by vm.draft.collectAsState()
    val cutOut by vm.cutOut.collectAsState()
    val crop by vm.crop.collectAsState()
    val trim by vm.trim.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val source = if (cutOut) draft.cutOut ?: draft.original else draft.original
    var trimming by rememberSaveable { mutableStateOf(false) }
    val c = OneTheme.colors
    OneModal(onDismiss = { if (!busy) vm.closeMaker() }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Make a sticker", color = c.text, style = OneTextStyles.screenTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val originalModifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = !cutOut; contentDescription = "Keep original photo" }
                val cutModifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = cutOut; contentDescription = "Remove photo background" }
                if (!cutOut) PrimaryButton("Original", { vm.original(true) }, originalModifier, enabled = !busy)
                else SecondaryButton("Original", { vm.original(true) }, originalModifier, enabled = !busy)
                if (cutOut) PrimaryButton("Cut-out", { vm.original(false) }, cutModifier, enabled = !busy && draft.cutOut != null)
                else SecondaryButton("Cut-out", { vm.original(false) }, cutModifier, enabled = !busy && draft.cutOut != null)
            }
            if (draft.processing) {
                Text(if (draft.original == null) "Opening photo…" else "Removing background…", color = c.textDim, style = OneTextStyles.cardHint)
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Processing photo on this device" })
            }
            source?.let {
                if (trimming) {
                    Text("Drag the corners to crop, or drag inside to move.", color = c.textDim, style = OneTextStyles.cardHint)
                    StickerTrimEditor(it, trim, enabled = !busy, onTrim = vm::trim)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Reset", { vm.trim(StickerTrim()) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy)
                        PrimaryButton("Done", { trimming = false }, Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                } else {
                    val trimmed = remember(it, trim) { trimSticker(it, trim) }
                    Text("Pinch to zoom, drag to place.", color = c.textDim, style = OneTextStyles.cardHint)
                    StickerCropPreview(trimmed, crop, enabled = !busy, onCrop = vm::crop)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Crop", { trimming = true }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy)
                        SecondaryButton("Reset", { vm.trim(StickerTrim()) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy)
                    }
                }
            }
            draft.notice?.let { Text(it, color = c.textDim, style = OneTextStyles.cardHint) }
            error?.let { Text(it, color = c.danger, style = OneTextStyles.cardHint) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cancel", vm::closeMaker, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy)
                PrimaryButton("Save", vm::save, Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = source != null && !draft.processing && !busy && !trimming)
            }
        }
    }
}

@Composable
private fun Modifier.stickerCheckerboard(): Modifier {
    val c = OneTheme.colors
    return drawBehind {
        drawRect(c.bg)
        val square = 12.dp.toPx()
        for (y in 0..(size.height / square).toInt()) for (x in 0..(size.width / square).toInt()) {
            if ((x + y) % 2 == 0) drawRect(c.border, Offset(x * square, y * square), Size(square, square))
        }
    }
}

/** The whole photo with a crop box: corner handles resize it, dragging inside moves it. Dimmed outside the box. */
@Composable
internal fun StickerTrimEditor(source: Bitmap, trim: StickerTrim, enabled: Boolean, onTrim: (StickerTrim) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    val current by rememberUpdatedState(trim)
    val accent = OneTheme.colors.accentYou
    var frame by remember { mutableFloatStateOf(1f) }
    // Where the photo sits inside the square frame (fit, centred), as frame fractions.
    // Inset 4% so the corner handles are never clipped by the frame edge.
    val area = remember(source) {
        stickerPlacement(source.width, source.height, StickerCrop()).run { copy(left = .04f + left * .92f, top = .04f + top * .92f, width = width * .92f, height = height * .92f) }
    }
    val reach = with(LocalDensity.current) { 36.dp.toPx() }
    fun nudge(handle: Int, du: Float, dv: Float) = onTrim(dragStickerTrim(current, handle, du, dv))
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds().stickerCheckerboard()
        .onSizeChanged { frame = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(source, enabled) {
            if (!enabled) return@pointerInput
            var handle = -1
            detectDragGestures(onDragStart = { at ->
                val t = current
                val corners = listOf(t.left to t.top, t.right to t.top, t.left to t.bottom, t.right to t.bottom).map { (u, v) ->
                    Offset((area.left + u * area.width) * frame, (area.top + v * area.height) * frame)
                }
                handle = corners.indices.minBy { (corners[it] - at).getDistance() }.takeIf { (corners[it] - at).getDistance() <= reach }
                    ?: 4.takeIf {
                        val u = (at.x / frame - area.left) / area.width; val v = (at.y / frame - area.top) / area.height
                        u in t.left..t.right && v in t.top..t.bottom
                    } ?: -1
            }) { change, drag ->
                if (handle < 0) return@detectDragGestures
                change.consume()
                nudge(handle, drag.x / frame / area.width, drag.y / frame / area.height)
            }
        }.semantics {
            contentDescription = "Crop box"
            customActions = if (!enabled) emptyList() else listOf(
                CustomAccessibilityAction("Crop in") { onTrim(StickerTrim(current.left + .05f, current.top + .05f, current.right - .05f, current.bottom - .05f)); true },
                CustomAccessibilityAction("Move left") { nudge(4, -.05f, 0f); true },
                CustomAccessibilityAction("Move right") { nudge(4, .05f, 0f); true },
                CustomAccessibilityAction("Move up") { nudge(4, 0f, -.05f); true },
                CustomAccessibilityAction("Move down") { nudge(4, 0f, .05f); true },
                CustomAccessibilityAction("Reset crop") { onTrim(StickerTrim()); true },
            )
        }) {
        val left = area.left * size.width; val top = area.top * size.height
        val w = area.width * size.width; val h = area.height * size.height
        drawImage(image, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.High)
        val box = androidx.compose.ui.geometry.Rect(left + trim.left * w, top + trim.top * h, left + trim.right * w, top + trim.bottom * h)
        val shade = androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f)
        drawRect(shade, Offset.Zero, Size(size.width, box.top))
        drawRect(shade, Offset(0f, box.bottom), Size(size.width, size.height - box.bottom))
        drawRect(shade, Offset(0f, box.top), Size(box.left, box.height))
        drawRect(shade, Offset(box.right, box.top), Size(size.width - box.right, box.height))
        drawRect(accent, box.topLeft, box.size, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        val knob = 7.dp.toPx()
        listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight).forEach { drawCircle(accent, knob, it) }
    }
}

@Composable
internal fun StickerCropPreview(source: Bitmap, crop: StickerCrop, enabled: Boolean, onCrop: (StickerCrop) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    val current by rememberUpdatedState(crop)
    var frame by remember { mutableFloatStateOf(1f) }
    fun transform(zoom: Float, x: Float = 0f, y: Float = 0f, focus: Offset = Offset.Zero) {
        onCrop(transformStickerCrop(source.width, source.height, current, zoom, x, y, focus.x, focus.y))
    }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds().stickerCheckerboard()
        .border(1.dp, OneTheme.colors.accentYou).onSizeChanged { frame = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(source, enabled) {
            if (enabled) detectTransformGestures { centroid, pan, zoom, _ ->
                transform(zoom, pan.x / frame, pan.y / frame, Offset(centroid.x / frame - .5f, centroid.y / frame - .5f))
            }
        }.semantics {
            contentDescription = "Sticker crop preview"
            customActions = if (!enabled) emptyList() else listOf(
                CustomAccessibilityAction("Zoom in") { transform(1.25f); true },
                CustomAccessibilityAction("Zoom out") { transform(.8f); true },
                CustomAccessibilityAction("Move left") { transform(1f, -.1f); true },
                CustomAccessibilityAction("Move right") { transform(1f, .1f); true },
                CustomAccessibilityAction("Move up") { transform(1f, y = -.1f); true },
                CustomAccessibilityAction("Move down") { transform(1f, y = .1f); true },
                CustomAccessibilityAction("Reset crop") { onCrop(StickerCrop()); true },
            )
        }) {
        val placement = stickerPlacement(source.width, source.height, crop)
        drawImage(image, dstOffset = IntOffset((placement.left * size.width).roundToInt(), (placement.top * size.height).roundToInt()),
            dstSize = IntSize((placement.width * size.width).roundToInt().coerceAtLeast(1), (placement.height * size.height).roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High)
    }
}
