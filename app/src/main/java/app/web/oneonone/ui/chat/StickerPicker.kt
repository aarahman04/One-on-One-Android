package app.web.oneonone.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(OneTheme.radii.sm6)).stickerCheckerboard()
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
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val source = if (cutOut) draft.cutOut ?: draft.original else draft.original
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
                Text("Pinch to zoom, drag to crop.", color = c.textDim, style = OneTextStyles.cardHint)
                StickerCropPreview(it, crop, enabled = !busy, onCrop = vm::crop)
                SecondaryButton("Reset crop", { vm.crop(StickerCrop()) }, Modifier.heightIn(min = 48.dp), enabled = !busy)
            }
            draft.notice?.let { Text(it, color = c.textDim, style = OneTextStyles.cardHint) }
            error?.let { Text(it, color = c.danger, style = OneTextStyles.cardHint) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cancel", vm::closeMaker, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy)
                PrimaryButton("Save", vm::save, Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = source != null && !draft.processing && !busy)
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
