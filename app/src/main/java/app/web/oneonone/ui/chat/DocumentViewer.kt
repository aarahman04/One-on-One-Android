package app.web.oneonone.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.createBitmap
import app.web.oneonone.R
import app.web.oneonone.ui.components.OneIconButton
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.theme.OneTextStyles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import kotlin.math.roundToInt

internal data class DocumentText(val text: String, val truncated: Boolean)

internal fun readDocumentText(input: InputStream): DocumentText {
    val maximum = 1_048_576
    val bytes = ByteArray(maximum + 1)
    var size = 0
    while (size < bytes.size) {
        val count = input.read(bytes, size, bytes.size - size)
        if (count == -1) break
        if (count == 0) {
            val next = input.read()
            if (next == -1) break
            bytes[size++] = next.toByte()
        } else size += count
    }
    return DocumentText(String(bytes, 0, size.coerceAtMost(maximum), Charsets.UTF_8).removePrefix("\uFEFF"), size > maximum)
}

internal fun documentLines(text: String): List<String> = text.lineSequence().flatMap {
    // Bound each row's layout work even when a file has one enormous line.
    if (it.isEmpty()) sequenceOf("") else it.chunked(4_096).asSequence()
}.toList()

internal fun pdfBitmapHeight(width: Int, pageWidth: Int, pageHeight: Int): Int {
    require(width > 0 && pageWidth > 0 && pageHeight > 0) { "Invalid PDF page size." }
    val height = width.toDouble() * pageHeight / pageWidth
    require(height.isFinite() && height >= 1 && width.toDouble() * height <= 16_000_000) {
        "This page is too large to preview. Use Open with…"
    }
    return height.roundToInt()
}

@Composable
internal fun AttachmentViewerFrame(name: String, busy: Boolean, save: () -> Unit, close: () -> Unit,
                                   openWith: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
                }
            }
        }
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OneIconButton(R.drawable.ic_x, "Close viewer", close, tint = Color.White)
                Text(name, Modifier.weight(1f).padding(horizontal = 8.dp), color = Color.White,
                    style = OneTextStyles.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OneIconButton(R.drawable.ic_download, "Save a copy", save, tint = Color.White, enabled = !busy)
                openWith?.let { OneIconButton(R.drawable.ic_open_with, "Open with…", it, tint = Color.White, enabled = !busy) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
internal fun DocumentViewer(uri: Uri, mime: String, name: String, busy: Boolean,
                            save: () -> Unit, openWith: () -> Unit, close: () -> Unit) {
    val nativePreview = mime in setOf("application/pdf", "text/plain", "text/csv")
    LaunchedEffect(uri, mime) { if (!nativePreview) openWith() }
    AttachmentViewerFrame(name, busy, save, close, openWith) {
        when (mime) {
            "application/pdf" -> PdfPages(uri)
            "text/plain", "text/csv" -> TextDocument(uri)
            else -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Open this ${attachmentType(mime)} file with another app.", color = Color.White, style = OneTextStyles.subtitle)
                Spacer(Modifier.height(16.dp))
                SecondaryButton("Open with…", openWith, enabled = !busy)
            }
        }
    }
}

@Composable
private fun TextDocument(uri: Uri) {
    val resolver = LocalContext.current.contentResolver
    var text by remember(uri) { mutableStateOf<DocumentText?>(null) }
    var lines by remember(uri) { mutableStateOf(emptyList<String>()) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        try {
            val preview = withContext(Dispatchers.IO) {
                val content = checkNotNull(resolver.openInputStream(uri)) { "Couldn't open this document." }.use(::readDocumentText)
                content to documentLines(content.text)
            }
            text = preview.first; lines = preview.second
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Couldn't read this document. Use Open with…" }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            error?.let { Text(it, color = Color.White, style = OneTextStyles.subtitle) }
            if (text == null && error == null) Text("Loading document…", color = Color.White)
            if (text?.truncated == true) Text("Preview truncated at 1 MB. Save or open the file to read the rest.",
                Modifier.padding(bottom = 16.dp), color = Color.White, style = OneTextStyles.cardHint)
        }
        items(lines.size) { index -> Text(lines[index], color = Color.White, style = OneTextStyles.subtitle.copy(fontFamily = FontFamily.Monospace)) }
    }
}

private class OpenPdf(private val descriptor: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    val pageCount = renderer.pageCount
    private val access = Mutex()
    private var closed = false

    suspend fun render(index: Int, width: Int): Bitmap = access.withLock {
        check(!closed) { "Document closed." }
        renderer.openPage(index).use { page ->
            val bitmap = createBitmap(width, pdfBitmapHeight(width, page.width, page.height))
            try {
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (failure: Throwable) { bitmap.recycle(); throw failure }
        }
    }

    suspend fun close() = access.withLock {
        if (!closed) {
            closed = true
            try { renderer.close() } finally { descriptor.close() }
        }
    }
}

private fun openPdf(resolver: ContentResolver, uri: Uri): OpenPdf {
    val descriptor = checkNotNull(resolver.openFileDescriptor(uri, "r")) { "Couldn't open this PDF." }
    try { return OpenPdf(descriptor, PdfRenderer(descriptor)) }
    catch (failure: Throwable) { descriptor.close(); throw failure }
}

@Composable
private fun PdfPages(uri: Uri) {
    val resolver = LocalContext.current.contentResolver
    var document by remember(uri) { mutableStateOf<OpenPdf?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        var opened: OpenPdf? = null
        try {
            withContext(Dispatchers.IO) { opened = openPdf(resolver, uri) }
            document = opened
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Couldn't preview this PDF. Use Open with…" }
        finally { withContext(NonCancellable + Dispatchers.IO) { opened?.close() } }
    }
    val pdf = document
    if (pdf == null) {
        Text(error ?: "Loading PDF…", Modifier.padding(16.dp), color = Color.White, style = OneTextStyles.subtitle)
        return
    }
    val list = rememberLazyListState()
    val currentPage by remember(list, pdf) { derivedStateOf { (list.firstVisibleItemIndex + 1).coerceAtMost(pdf.pageCount) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pdf.pageCount, key = { it }) { index ->
                    val visible by remember(list, index) { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.index == index } } }
                    PdfPage(pdf, index, width, visible)
                }
            }
            Text("$currentPage / ${pdf.pageCount}",
                Modifier.align(Alignment.CenterHorizontally).padding(8.dp), color = Color.White, style = OneTextStyles.cardHint)
        }
    }
}

@Composable
private fun PdfPage(document: OpenPdf, index: Int, width: Int, visible: Boolean) {
    var bitmap by remember(document, index, width) { mutableStateOf<Bitmap?>(null) }
    var aspect by rememberSaveable(index, width) { mutableFloatStateOf(1f / 1.4142f) }
    var error by remember(document, index, width) { mutableStateOf<String?>(null) }
    LaunchedEffect(document, index, width, visible) {
        if (!visible || width <= 0) return@LaunchedEffect
        var rendered: Bitmap? = null
        try {
            withContext(Dispatchers.IO) { rendered = document.render(index, width) }
            bitmap = rendered
            rendered?.let { aspect = it.width.toFloat() / it.height }
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Couldn't render page ${index + 1}. Use Open with…" }
        finally {
            withContext(NonCancellable) {
                if (bitmap === rendered) bitmap = null
                // Let Compose retire the image before recycling its backing pixels.
                try { withFrameNanos { } } finally { rendered?.recycle() }
            }
        }
    }
    val page = bitmap
    Box(Modifier.fillMaxWidth().aspectRatio(aspect)
        .background(Color.White), contentAlignment = Alignment.Center) {
        if (page != null) Image(page.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize())
        else Text(error ?: "Page ${index + 1}", Modifier.padding(16.dp), color = Color.Black, style = OneTextStyles.cardHint)
    }
}
