package app.web.oneonone.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.web.oneonone.R
import app.web.oneonone.data.model.LinkPreview
import app.web.oneonone.ui.components.OneIconButton
import app.web.oneonone.ui.theme.BubblePalette
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class LoadedPreview(val preview: LinkPreview, val image: ImageBitmap?)

/**
 * WhatsApp-style link card at the top of a text bubble: cover art (Spotify, YouTube…) full width, or the site's
 * icon beside the title. Fetched by the server (contract: GET /api/link-preview), so the site never sees this phone.
 * Draws nothing until a preview exists, then grows in.
 */
@Composable
internal fun LinkPreviewCard(url: String, load: suspend (String) -> LinkPreview?) {
    val loaded by produceState<LoadedPreview?>(null, url) {
        val preview = load(url) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val image = preview.image?.let { encoded -> runCatching { decodeBounded(Base64.decode(encoded, Base64.DEFAULT)) }.getOrNull() }
            LoadedPreview(preview, image)
        }
    }
    AnimatedVisibility(loaded != null, enter = fadeIn(tween(220)) + expandVertically(tween(260))) {
        val item = loaded ?: return@AnimatedVisibility
        val p = item.preview
        val colors = LocalBubbleColors.current
        val uriHandler = LocalUriHandler.current
        // Wide art (YouTube, Spotify banners) goes full width; square-ish art and site icons sit beside the title.
        val wide = item.image?.let { it.width >= it.height * 1.4f } == true
        val cover = item.image.takeIf { p.imageKind == "cover" && wide }
        val thumb = item.image.takeIf { cover == null }
        Column(
            Modifier.padding(bottom = 4.dp).width(264.dp).clip(RoundedCornerShape(OneTheme.radii.sm6))
                .background(colors.text.copy(alpha = .08f))
                // The server's final URL (after redirects) is untrusted: only ever open http(s), else the link as sent.
                .clickable(role = Role.Button, onClickLabel = "Open link") { runCatching { uriHandler.openUri(safeWebUrl(p.url) ?: url) } }
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(p.siteName, p.title).joinToString(", ") },
        ) {
            if (cover != null) Image(cover, null, Modifier.fillMaxWidth().aspectRatio((cover.width.toFloat() / cover.height).coerceIn(1.4f, 2f)),
                contentScale = ContentScale.Crop)
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (thumb != null) Image(thumb, null, Modifier.size(if (p.imageKind == "cover") 64.dp else 36.dp).clip(RoundedCornerShape(OneTheme.radii.sm6)),
                    contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(p.siteName, color = colors.meta, style = OneTextStyles.bubbleMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    p.title?.let { Text(it, color = colors.text, style = OneTextStyles.cardHint.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    p.description?.let { Text(it, color = colors.meta, style = OneTextStyles.bubbleMeta.copy(lineHeight = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** Shows/hides a strip (banner, bar, search) by fading and folding its height, never popping. */
@Composable
internal fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    val motion = OneTheme.motion
    AnimatedVisibility(visible,
        enter = fadeIn(tween(motion.base200)) + expandVertically(tween(240, easing = motion.standard)),
        exit = fadeOut(tween(motion.enter160)) + shrinkVertically(tween(motion.base200, easing = motion.standard))) { content() }
}

/** Untrusted server data: a small file can still claim huge dimensions, so sample down to at most ~720 px first. */
private fun decodeBounded(bytes: ByteArray, maxSide: Int = 720): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

internal fun safeWebUrl(value: String): String? =
    runCatching { java.net.URI(value) }.getOrNull()?.takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() }?.toString()

/** WhatsApp's "+": every emoji, by category, with recents (androidx EmojiPickerView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = OneTheme.colors
    // The view's listener is set once in factory: read the latest callback, not the first composition's.
    val pick by rememberUpdatedState(onPick)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.bgRaised, contentColor = c.text) {
        AndroidView(
            factory = { context -> EmojiPickerView(context).apply { emojiGridColumns = 8; setOnEmojiPickedListener { pick(it.emoji) } } },
            modifier = Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 8.dp),
        )
    }
}

/** "They're typing": a small incoming bubble with three dots rising one after another. */
@Composable
internal fun TypingBubble(visible: Boolean, palette: BubblePalette) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)) + expandVertically(tween(220)), exit = fadeOut(tween(160)) + shrinkVertically(tween(200))) {
        val colors = palette.other
        val transition = rememberInfiniteTransition(label = "typing")
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).semantics { contentDescription = "Typing" }) {
            Row(
                Modifier.bubbleTail(false, colors.backgroundStart).clip(RoundedCornerShape(OneTheme.radii.bubble8)).background(colors.backgroundStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    val lift by transition.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
                        durationMillis = 1_200
                        0f at 0; 1f at 300; 0f at 600; 0f at 1_200
                    }, RepeatMode.Restart, StartOffset(index * 160)), label = "dot$index")
                    Box(Modifier.size(7.dp).graphicsLayer { translationY = -5.dp.toPx() * lift; alpha = .45f + .55f * lift }
                        .clip(CircleShape).background(colors.text))
                }
            }
        }
    }
}

/** Own voice note / sticker / GIF still uploading: shown at once, replaced by the real message when the send lands. */
@Composable
internal fun PendingMediaBubble(item: FeatureViewModel.PendingMedia, palette: BubblePalette, modifier: Modifier = Modifier) {
    val colors = palette.mine
    Box(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), contentAlignment = Alignment.CenterEnd) {
        val bare = item.kind == "sticker" // Stickers have no bubble once sent, so none while sending either.
        Row(
            (if (bare) Modifier else Modifier.bubbleTail(true, colors.backgroundStart).clip(RoundedCornerShape(OneTheme.radii.bubble8))
                .background(colors.backgroundStart).padding(if (item.kind == "voice") 12.dp else 4.dp))
                .semantics(mergeDescendants = true) { contentDescription = "Sending ${if (item.kind == "gif") "GIF" else item.kind}" },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.kind == "voice") {
                Icon(painterResource(R.drawable.ic_mic), null, tint = colors.text, modifier = Modifier.size(20.dp))
                val seconds = kotlin.math.round(item.duration ?: 0.0).toInt()
                Text("%d:%02d".format(seconds / 60, seconds % 60), color = colors.text, style = OneTextStyles.menuItem)
                Text("Sending…", color = colors.meta, style = OneTextStyles.cardHint)
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = colors.meta, strokeWidth = 2.dp)
            } else Box(Modifier.size(if (bare) StickerSize else 140.dp), contentAlignment = Alignment.Center) {
                item.file?.let { coil3.compose.AsyncImage(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = .6f }) }
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = colors.text, strokeWidth = 2.5.dp)
            }
        }
    }
}

/** The connection's one pinned message, under the header. Tap jumps to it; × unpins (for both). */
@Composable
internal fun PinnedBanner(author: String, snippet: String, onOpen: () -> Unit, onUnpin: () -> Unit) {
    val c = OneTheme.colors
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(c.bgRaised)
            .clickable(role = Role.Button, onClickLabel = "Go to pinned message", onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.accentOther))
        Icon(painterResource(R.drawable.ic_pin), null, tint = c.accentOther, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Pinned by $author", color = c.accentOther, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(snippet, color = c.textDim, style = OneTextStyles.cardHint.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OneIconButton(R.drawable.ic_x, "Unpin", onUnpin)
    }
    HorizontalDivider(color = c.border)
}
