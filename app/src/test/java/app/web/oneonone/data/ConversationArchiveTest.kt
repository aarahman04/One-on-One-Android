package app.web.oneonone.data

import app.web.oneonone.data.chat.*
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.data.model.ReactionSummary
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

class ConversationArchiveTest {
    private val utc = ZoneOffset.UTC
    private fun media(id: String, type: String, mime: String, extra: Map<String, Any> = emptyMap(), deleted: Boolean = false,
                      at: String = "2026-10-19T10:00:00Z") = ChatMessage(id, "other", "", at, type,
        buildJsonObject {
            put("path", "c/$id"); put("mime", mime); put("size", 10)
            extra.forEach { (k, v) -> if (v is Number) put(k, v) else put(k, v.toString()) }
        }, deletedAt = if (deleted) "2026-10-19T11:00:00Z" else null)

    private val letter = ChatMessage("l1", "me", "Dear you", "2026-10-19T09:00:00Z", "letter",
        buildJsonObject { put("appearance", "dawn"); put("from", "Me"); put("to", "Alex") })

    @Test fun deletedSystemAndUnsentMessagesNeverReachTheArchive() {
        val messages = listOf(ChatMessage("t1", "me", "hi", "2026-10-19T08:00:00Z"),
            ChatMessage("t2", "me", "", "2026-10-19T08:01:00Z", deletedAt = "2026-10-19T08:02:00Z"),
            ChatMessage("s1", "me", "", "2026-10-19T08:03:00Z", "system"),
            ChatMessage(null, "me", "queued", "2026-10-19T08:04:00Z", tempId = "x"),
            ChatMessage("c1", "me", "", "2026-10-19T08:05:00Z", "call", buildJsonObject { put("kind", "audio"); put("outcome", "completed"); put("durationSec", 65) }))
        assertEquals(listOf("t1", "c1"), archivable(messages).map { it.id })
        // Calls stay in the transcript but never get a file.
        assertTrue(archiveFiles(messages, utc).isEmpty())
        assertTrue(conversationText(archivable(messages), "me", "Alex", emptyMap(), utc).contains("Voice call"))
    }

    @Test fun foldersFollowTypeAndMime() {
        assertEquals("images", archiveFolder(media("a", "image", "image/jpeg", mapOf("width" to 10, "height" to 10))))
        assertEquals("images/stickers", archiveFolder(media("b", "image", "image/webp", mapOf("width" to 512, "height" to 512))))
        assertEquals("voice", archiveFolder(media("c", "voice", "audio/mp4")))
        assertEquals("files", archiveFolder(media("d", "file", "application/pdf", mapOf("name" to "x.pdf"))))
        assertNull(archiveFolder(media("e", "file", "application/x-sh", mapOf("name" to "x.sh"))))
        assertNull(archiveFolder(media("f", "voice", "image/png")))
        assertNull(archiveFolder(ChatMessage("g", "me", "hi", "2026-10-19T08:00:00Z")))
    }

    @Test fun entryNamesAreSanitizedNumberedAndUnique() {
        assertEquals("_.._etc_passwd", sanitizeEntryName("../../etc/passwd"))
        assertEquals("file", sanitizeEntryName("  ..  "))
        assertEquals("a_b_c", sanitizeEntryName("a\\b\u0000c"))
        val files = archiveFiles(listOf(letter, letter.copy(id = "l2"),
            media("p1", "image", "image/jpeg", mapOf("width" to 1, "height" to 1)),
            media("p2", "image", "image/png", mapOf("width" to 1, "height" to 1)),
            media("v1", "voice", "audio/mp4"),
            media("f1", "file", "application/pdf", mapOf("name" to "../Report.PDF")),
            media("f2", "file", "application/pdf", mapOf("name" to "../report.pdf")),
            media("f3", "file", "text/plain", mapOf("name" to "notes.exe")),
            media("gone", "image", "image/jpeg", mapOf("width" to 1, "height" to 1), deleted = true)), utc).map { it.path }
        assertEquals(listOf("letters/2026-10-19-1.pdf", "letters/2026-10-19-2.pdf", "images/2026-10-19-1.jpg",
            "images/2026-10-19-2.png", "voice/2026-10-19-1.m4a", "files/_Report.pdf", "files/_report (2).pdf", "files/notes.txt"), files)
        assertTrue(files.none { it.contains("..") || it.startsWith("/") })
        assertEquals("One on One - Alex_B - 2026-10-19.zip", archiveFileName("Alex/B", java.time.LocalDate.of(2026, 10, 19)))
    }

    @Test fun zipStreamsKeptFilesAndNeverFetchesDeletedOnes() = runTest {
        val opened = mutableListOf<String?>()
        val photo = media("p1", "image", "image/jpeg", mapOf("width" to 1, "height" to 1)).copy(content = "sunset",
            reactions = listOf(ReactionSummary("❤️", listOf("me"))))
        val reply = ChatMessage("t1", "me", "lovely", "2026-10-19T10:01:00Z", replyTo = "p1")
        val missing = media("v1", "voice", "audio/mp4")
        val gone = media("gone", "image", "image/jpeg", mapOf("width" to 1, "height" to 1), deleted = true)
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        writeConversationArchive(listOf(letter, photo, reply, missing, gone), "me", "Alex", output,
            letter = { _, out -> out.write("%PDF".toByteArray()) },
            open = { opened += it.id; if (it.id == "p1") ByteArrayInputStream(byteArrayOf(1, 2, 3)) else null },
            progress = { progress += it }, zone = utc)
        assertEquals(listOf("p1", "v1"), opened)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
        }
        assertEquals(setOf("letters/2026-10-19-1.pdf", "images/2026-10-19-1.jpg", "conversation.html", "conversation.txt"), entries.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), entries["images/2026-10-19-1.jpg"])
        val text = String(entries.getValue("conversation.txt"))
        assertTrue(text.contains("[Photo: images/2026-10-19-1.jpg] sunset"))
        assertTrue(text.contains("[Voice note: not available]"))
        assertTrue(text.contains("> Alex: [Photo"))
        assertTrue(text.contains("Reactions: ❤️ You"))
        assertFalse(text.contains("gone"))
        val html = String(entries.getValue("conversation.html"))
        assertTrue(html.contains("<img src=\"images/2026-10-19-1.jpg\""))
        assertTrue(html.contains("letters/2026-10-19-1.pdf"))
        assertEquals(100, progress.last())
    }

    @Test fun oversizedAttachmentFailsTheArchive() = runTest {
        val photo = media("p1", "image", "image/jpeg", mapOf("width" to 1, "height" to 1))
        val result = runCatching {
            writeConversationArchive(listOf(photo), "me", "Alex", ByteArrayOutputStream(), letter = { _, _ -> },
                open = { ByteArrayInputStream(ByteArray(10 * 1024 * 1024 + 1)) }, zone = utc)
        }
        assertTrue(result.isFailure)
    }
}
