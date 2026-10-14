package app.web.oneonone.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import app.web.oneonone.data.media.GifPage
import app.web.oneonone.data.media.GifResult
import app.web.oneonone.ui.components.OneTextField
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Composer sheet: Emoji inserts into the draft; GIFs and stickers send ordinary images. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerEmojiSheet(
    onEmoji: (String) -> Unit,
    search: suspend (String, Int) -> GifPage,
    onGif: (GifResult) -> Unit,
    sending: Boolean,
    stickers: @Composable () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = OneTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pick by rememberUpdatedState(onEmoji)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.bgRaised, contentColor = c.text) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Emoji", "GIFs", "Stickers").forEachIndexed { index, label ->
                val on = tab == index
                Text(label,
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(OneTheme.radii.pill22))
                        .background(if (on) c.accentYou.copy(alpha = .18f) else c.bg)
                        .clickable(role = Role.Tab) { tab = index }
                        .semantics { selected = on; contentDescription = label }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    color = if (on) c.accentYou else c.textDim, style = OneTextStyles.menuItem,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            when (tab) {
            2 -> stickers()
            1 -> GifGrid(search, onGif, sending)
            else -> AndroidView(
                factory = { context -> EmojiPickerView(context).apply { emojiGridColumns = 8; setOnEmojiPickedListener { pick(it.emoji) } } },
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            )
            }
        }
    }
}

@Composable
private fun GifGrid(search: suspend (String, Int) -> GifPage, onGif: (GifResult) -> Unit, sending: Boolean) {
    val c = OneTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<GifResult>()) }
    var next by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val load by rememberUpdatedState(search)
    suspend fun fetch(offset: Int) {
        loading = true; error = null
        try {
            val page = load(query, offset)
            results = if (offset == 0) page.results else (results + page.results).distinctBy { it.id }
            next = page.next
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = "Couldn't load GIFs. Check your connection and try again." }
        finally { loading = false }
    }
    LaunchedEffect(query) { delay(if (query.isBlank()) 0 else 350); fetch(0) }
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= results.size - 6 } == true } }
    LaunchedEffect(grid) {
        snapshotFlow { nearEnd }.collect { if (it && !loading) next?.let { offset -> fetch(offset) } }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OneTextField(query, { query = it.take(50) }, Modifier.fillMaxWidth().semantics { contentDescription = "Search GIFs" }, placeholder = "Search GIPHY")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                error != null && results.isEmpty() -> Text(error.orEmpty(), Modifier.align(Alignment.Center).padding(24.dp), color = c.textDim, style = OneTextStyles.cardHint)
                !loading && results.isEmpty() -> Text("No GIFs found.", Modifier.align(Alignment.Center), color = c.textDim, style = OneTextStyles.cardHint)
                else -> LazyVerticalGrid(GridCells.Fixed(2), state = grid, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results, key = { it.id }) { gif ->
                        AsyncImage(gif.previewUrl, gif.title.ifBlank { "GIF" },
                            Modifier.fillMaxWidth().aspectRatio((gif.width.toFloat() / gif.height).coerceIn(.5f, 2f))
                                .clip(RoundedCornerShape(OneTheme.radii.sm6))
                                .clickable(enabled = !sending, role = Role.Button) { onGif(gif) },
                            contentScale = ContentScale.Crop, placeholder = ColorPainter(c.bg))
                    }
                }
            }
        }
        // GIPHY's terms require visible attribution in the picker.
        Text("Powered by GIPHY", Modifier.align(Alignment.End).padding(bottom = 8.dp), color = c.muted, style = OneTextStyles.bubbleMeta)
    }
}
