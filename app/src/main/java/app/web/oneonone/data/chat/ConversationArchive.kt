package app.web.oneonone.data.chat

import app.web.oneonone.call.CallProtocol
import app.web.oneonone.data.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One file inside the zip: a letter PDF or an attachment, with its sanitized, unique path. */
internal data class ArchiveFile(val message: ChatMessage, val path: String)

/** Deleted-for-everyone messages never reach the archive, nor are their attachments fetched; system rows are noise. */
internal fun archivable(messages: List<ChatMessage>): List<ChatMessage> =
    messages.filter { it.id != null && it.deletedAt == null && it.type != "system" }

private val Extensions = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif",
    "audio/webm" to "webm", "audio/mp4" to "m4a", "audio/ogg" to "ogg", "audio/mpeg" to "mp3",
    "application/pdf" to "pdf", "text/plain" to "txt", "text/csv" to "csv", "application/msword" to "doc",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx", "application/vnd.ms-excel" to "xls",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx", "application/vnd.ms-powerpoint" to "ppt",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx")

/** Zip folder for a message's attachment, or null when it has none we can keep (calls, cards, unknown MIME types). */
internal fun archiveFolder(message: ChatMessage): String? {
    val mime = message.payload.text("mime")
    return when {
        message.type == "image" && mime in ImageMimes -> if (archiveSticker(message)) "images/stickers" else "images"
        message.type == "voice" && mime in VoiceMimes -> "voice"
        message.type == "file" && mime in FileMimes -> "files"
        else -> null
    }
}

// Mirrors ui.chat.isSticker: the maker's 512×512 WebP.
private fun archiveSticker(message: ChatMessage) = message.payload.text("mime") == "image/webp" &&
    message.payload.number("width") == 512.0 && message.payload.number("height") == 512.0

/** A single safe path segment: no separators, traversal, control or reserved characters; never empty. */
internal fun sanitizeEntryName(name: String): String {
    val cleaned = name.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().trimStart('.').trim()
    return cleaned.take(100).ifBlank { "file" }
}

private fun day(message: ChatMessage, zone: ZoneId) = runCatching {
    DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.parse(message.createdAt).atZone(zone))
}.getOrDefault("undated")

/** Letters become `letters/<date>-<n>.pdf`; attachments go to their folder; every path is unique (case-insensitive). */
internal fun archiveFiles(messages: List<ChatMessage>, zone: ZoneId = ZoneId.systemDefault()): List<ArchiveFile> {
    val taken = mutableSetOf<String>()
    val counters = mutableMapOf<String, Int>()
    fun unique(folder: String, base: String, extension: String): String {
        var n = 1
        while (true) {
            val path = "$folder/" + (if (n == 1) base else "$base ($n)") + if (extension.isEmpty()) "" else ".$extension"
            if (taken.add(path.lowercase(Locale.ROOT))) return path
            n++
        }
    }
    fun numbered(folder: String, message: ChatMessage, extension: String): String {
        val date = day(message, zone)
        val key = "$folder/$date"
        while (true) {
            val n = (counters[key] ?: 0) + 1
            counters[key] = n
            val path = "$folder/$date-$n.$extension"
            if (taken.add(path.lowercase(Locale.ROOT))) return path
        }
    }
    return archivable(messages).mapNotNull { message ->
        if (message.type == "letter") return@mapNotNull ArchiveFile(message, numbered("letters", message, "pdf"))
        val folder = archiveFolder(message) ?: return@mapNotNull null
        val extension = checkNotNull(Extensions[message.payload.text("mime")])
        if (folder != "files") return@mapNotNull ArchiveFile(message, numbered(folder, message, extension))
        // Files keep their own name (sanitized); the extension follows the server-checked MIME type.
        val name = sanitizeEntryName(message.payload.text("name"))
        val base = name.substringBeforeLast('.', name).let(::sanitizeEntryName)
        ArchiveFile(message, unique(folder, base, extension))
    }
}

/**
 * Streams the whole conversation into [output] as a zip: letters and attachments first (each straight from its source,
 * never buffered whole), then `conversation.html` and `conversation.txt`, which link to what was saved.
 * [open] returns an attachment's bytes, or null when it is no longer available. Cancellable between every chunk.
 */
internal suspend fun writeConversationArchive(
    messages: List<ChatMessage>, owner: String, other: String, output: OutputStream,
    letter: (ChatMessage, OutputStream) -> Unit,
    open: suspend (ChatMessage) -> InputStream?,
    progress: (Int) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val kept = archivable(messages)
    val files = archiveFiles(kept, zone)
    val saved = mutableMapOf<String, String>() // message id -> path that made it into the zip
    val total = files.size + 1
    var done = 0
    progress(0)
    val zip = ZipOutputStream(output.buffered())
    for (file in files) {
        currentCoroutineContext().ensureActive()
        val id = checkNotNull(file.message.id)
        if (file.message.type == "letter") {
            zip.putNextEntry(ZipEntry(file.path))
            // writeLetterPdf never closes the stream it is given.
            letter(file.message, zip)
            zip.closeEntry()
            saved[id] = file.path
        } else open(file.message)?.use { input ->
            zip.putNextEntry(ZipEntry(file.path))
            copyBounded(input, zip, attachmentLimit(file.message.type))
            zip.closeEntry()
            saved[id] = file.path
        }
        progress(++done * 100 / total)
    }
    zip.putNextEntry(ZipEntry("conversation.html"))
    zip.write(conversationHtml(kept, owner, other, saved, zone).toByteArray(Charsets.UTF_8))
    zip.closeEntry()
    zip.putNextEntry(ZipEntry("conversation.txt"))
    zip.write(conversationText(kept, owner, other, saved, zone).toByteArray(Charsets.UTF_8))
    zip.closeEntry()
    zip.finish()
    zip.flush()
    progress(100)
}

private suspend fun copyBounded(input: InputStream, output: OutputStream, maximum: Int) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count == -1) break
        total += count
        require(total <= maximum) { "An attachment is larger than allowed." }
        output.write(buffer, 0, count)
    }
}

private fun stamp(message: ChatMessage, zone: ZoneId) = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).format(Instant.parse(message.createdAt).atZone(zone))
}.getOrDefault(message.createdAt)

/** What a row says in the transcript; attachments point at their file in the zip. */
internal fun archiveLine(message: ChatMessage, owner: String, saved: Map<String, String>): String {
    val path = saved[message.id]
    val content = message.content.trim()
    return when (message.type) {
        "call" -> CallProtocol.logText(message.payload.text("kind"), message.payload.text("outcome"),
            message.payload.number("durationSec")?.toInt() ?: 0, message.senderId == owner)
        "image", "voice", "file", "letter" -> {
            val label = if (message.type == "image" && path?.startsWith("images/stickers/") == true) "Sticker" else messageSummary(message)
            "[$label: ${path ?: "not available"}]" + if (content.isNotEmpty() && message.type != "letter") " $content" else ""
        }
        "location" -> "[Location] $content"
        "text" -> content
        else -> content.ifEmpty { messageSummary(message) }
    }
}

private fun authorName(message: ChatMessage, owner: String, other: String) = if (message.senderId == owner) "You" else other

private fun reactionsLine(message: ChatMessage, owner: String, other: String) = message.reactions.joinToString(", ") { reaction ->
    reaction.emoji + " " + reaction.userIds.joinToString(" & ") { if (it == owner) "You" else other }
}

internal fun conversationText(messages: List<ChatMessage>, owner: String, other: String, saved: Map<String, String>,
                              zone: ZoneId = ZoneId.systemDefault()): String = buildString {
    val byId = messages.associateBy { it.id }
    appendLine("One on One conversation with $other")
    appendLine()
    for (message in messages) {
        message.replyTo?.let { byId[it] }?.let { quoted ->
            appendLine("    > ${authorName(quoted, owner, other)}: ${archiveLine(quoted, owner, saved).take(80)}")
        }
        appendLine("[${stamp(message, zone)}] ${authorName(message, owner, other)}: ${archiveLine(message, owner, saved)}" +
            if (message.editedAt != null) " (edited)" else "")
        if (message.type == "letter") message.content.lines().forEach { appendLine("    $it") }
        if (message.reactions.isNotEmpty()) appendLine("    Reactions: ${reactionsLine(message, owner, other)}")
    }
}

internal fun conversationHtml(messages: List<ChatMessage>, owner: String, other: String, saved: Map<String, String>,
                              zone: ZoneId = ZoneId.systemDefault()): String = buildString {
    val byId = messages.associateBy { it.id }
    val title = htmlEscape("One on One · $other")
    append("""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title><style>
:root{--bg:#f6f4f1;--panel:#fff;--text:#1d1b19;--dim:#6d6862;--me:#2f6f5e;--me-text:#fff;--them:#ebe7e2;--line:#ddd7d0}
@media (prefers-color-scheme:dark){:root{--bg:#121212;--panel:#1c1c1c;--text:#ece8e3;--dim:#9b958e;--me:#3c8a75;--me-text:#fff;--them:#262626;--line:#333}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:720px;margin:0 auto;padding:24px 16px 48px}h1{font-size:20px;margin:0 0 4px}header p{margin:0 0 24px;color:var(--dim);font-size:13px}
.day{text-align:center;color:var(--dim);font-size:12px;margin:20px 0 8px}
.msg{display:flex;flex-direction:column;margin:6px 0;max-width:80%}.me{margin-left:auto;align-items:flex-end}.them{align-items:flex-start}
.bubble{padding:8px 12px;border-radius:14px;background:var(--them);white-space:pre-wrap;overflow-wrap:anywhere}
.me .bubble{background:var(--me);color:var(--me-text)}.meta{font-size:11px;color:var(--dim);margin-top:2px}
.quote{font-size:12px;opacity:.85;border-left:3px solid currentColor;padding-left:8px;margin-bottom:6px}
.reactions{font-size:12px;margin-top:2px}.event{text-align:center;color:var(--dim);font-size:12px;margin:10px 0}
img{max-width:100%;border-radius:10px;display:block}.sticker img{max-width:160px}audio{max-width:100%}a{color:inherit}
.letter{background:var(--panel);color:var(--text);border:1px solid var(--line);font-family:Georgia,serif}
</style></head><body><main><header><h1>$title</h1><p>Saved ${htmlEscape(DateTimeFormatter.ISO_LOCAL_DATE.format(java.time.LocalDate.now(zone)))} · ${messages.size} messages</p></header>
""")
    var lastDay: String? = null
    for (message in messages) {
        val date = day(message, zone)
        if (date != lastDay) { append("<div class=\"day\">${htmlEscape(date)}</div>\n"); lastDay = date }
        val time = htmlEscape(stamp(message, zone).substringAfter(' '))
        if (message.type == "call") {
            append("<div class=\"event\">${htmlEscape(archiveLine(message, owner, saved))} · $time</div>\n"); continue
        }
        val mine = message.senderId == owner
        val path = saved[message.id]?.let(::htmlEscape)
        append("<div class=\"msg ${if (mine) "me" else "them"}\"><div class=\"bubble${if (message.type == "letter") " letter" else ""}${if (path?.startsWith("images/stickers/") == true) " sticker" else ""}\">")
        message.replyTo?.let { byId[it] }?.let { quoted ->
            append("<div class=\"quote\">${htmlEscape(authorName(quoted, owner, other))}: ${htmlEscape(archiveLine(quoted, owner, saved).take(80))}</div>")
        }
        val caption = htmlEscape(message.content.trim())
        when {
            message.type == "image" && path != null -> append("<img src=\"$path\" alt=\"Photo\">" + if (caption.isNotEmpty()) "<div>$caption</div>" else "")
            message.type == "voice" && path != null -> append("<audio controls src=\"$path\"></audio>" + if (caption.isNotEmpty()) "<div>$caption</div>" else "")
            message.type == "letter" -> append("$caption" + if (path != null) "<div><a href=\"$path\">Open the letter (PDF)</a></div>" else "")
            message.type == "file" && path != null -> append("<a href=\"$path\">${htmlEscape(message.payload.text("name").ifBlank { "File" })}</a>" + if (caption.isNotEmpty()) "<div>$caption</div>" else "")
            else -> append(htmlEscape(archiveLine(message, owner, saved)))
        }
        append("</div><div class=\"meta\">${htmlEscape(authorName(message, owner, other))} · $time${if (message.editedAt != null) " · edited" else ""}</div>")
        if (message.reactions.isNotEmpty()) append("<div class=\"reactions\">${htmlEscape(reactionsLine(message, owner, other))}</div>")
        append("</div>\n")
    }
    append("</main></body></html>")
}

/** "One on One - Alex - 2026-10-19.zip", safe as a document name. */
fun archiveFileName(partner: String, today: java.time.LocalDate = java.time.LocalDate.now()): String =
    "One on One - ${sanitizeEntryName(partner).take(40)} - $today.zip"
