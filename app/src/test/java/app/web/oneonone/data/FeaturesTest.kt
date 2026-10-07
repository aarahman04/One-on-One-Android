package app.web.oneonone.data

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.api.*
import app.web.oneonone.data.chat.*
import app.web.oneonone.data.media.*
import app.web.oneonone.data.model.*
import app.web.oneonone.ui.chat.FeatureViewModel
import app.web.oneonone.ui.chat.locationTile
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

private fun obj(vararg pairs: Pair<String, Any>) = buildJsonObject { pairs.forEach { (k, v) ->
    when (v) { is Number -> put(k, v); is Boolean -> put(k, v); else -> put(k, v.toString()) }
} }
private class FakeMedia : MediaGateway {
    var savedText = ""
    var discarded = 0
    override suspend fun upload(connection: String, kind: String, uri: Uri, duration: Double?) = error("offline")
    override suspend fun signedUrl(connection: String, path: String) = error("offline")
    override suspend fun download(connection: String, payload: JsonObject): Uri = error("offline")
    override suspend fun location() = obj("lat" to 20.0, "lng" to 70.0)
    override suspend fun startRecording(onLimit: () -> Unit) { }
    override suspend fun stopRecording(): VoiceClip? = null
    override fun releaseRecording() { }
    override suspend fun discardVoice(path: String?) { if (path != null) discarded++ }
    override suspend fun save(uri: Uri, text: String) { savedText = text }
    override suspend fun saveAttachment(connection: String, payload: JsonObject, destination: Uri) { }
    override fun playbackFocus(onLost: () -> Unit) = true
    override fun abandonPlaybackFocus() { }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeaturesTest {
    @Test fun lateEnqueueCompletesBeforeTeardownPurgesOutbox() = runTest {
        val fake = FakeSession(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val auth = object : app.web.oneonone.data.auth.AccountSession by fake {
            override suspend fun accessToken(): String? { started.complete(Unit); release.await(); return fake.token }
        }
        val store = MemoryMessageStore(); val service = MessageService(FakeTransport(), store, auth, Json, backgroundScope)
        service.activate(connection("active", true)); runCurrent()
        val send = async { service.send("body") }; started.await()
        val end = async { service.deactivate(clear = true) }; runCurrent()
        assertFalse(end.isCompleted)
        release.complete(Unit); send.await(); end.await()
        assertTrue(store.rows.value.isEmpty())
    }

    @Test fun structuredPayloadsMatchContractAndRejectBoundaryViolations() {
        val valid = mapOf(
            "letter" to obj("appearance" to "dawn", "from" to "me", "to" to "you"),
            "ask" to obj("question" to "q", "answerA" to "a", "answerB" to "b"),
            "countdown" to obj("label" to "day", "targetIso" to "2026-10-07T00:00:00Z"),
            "checkin" to obj("mood" to "good", "note" to "hi"),
            "thisorthat" to obj("optionA" to "a", "optionB" to "b", "pickSender" to "a", "pickRecipient" to "b"),
            "location" to obj("lat" to 90, "lng" to -180, "accuracy" to 0),
            "image" to obj("path" to "connection/id.jpg", "mime" to "image/jpeg", "size" to 10 * MiB, "width" to 20_000, "height" to 1),
            "voice" to obj("path" to "connection/id.m4a", "mime" to "audio/mp4", "size" to 16 * MiB, "duration" to 3_600),
            "file" to obj("path" to "connection/id.pdf", "mime" to "application/pdf", "size" to 25 * MiB, "name" to "file.pdf"),
        )
        valid.forEach { (type, payload) -> validateFeaturePayload("connection", type, payload) }
        fun rejects(type: String, key: String, value: JsonElement) {
            assertTrue("$type/$key", runCatching { validateFeaturePayload("connection", type, JsonObject(valid.getValue(type) + (key to value))) }.isFailure)
        }
        rejects("letter", "appearance", JsonPrimitive("anything")); rejects("letter", "from", JsonPrimitive("x".repeat(41)))
        rejects("ask", "question", JsonPrimitive("x".repeat(301))); rejects("ask", "answerB", JsonPrimitive(""))
        rejects("countdown", "targetIso", JsonPrimitive("tomorrow")); rejects("checkin", "mood", JsonPrimitive("angry"))
        rejects("thisorthat", "pickRecipient", JsonPrimitive("c")); rejects("location", "lat", JsonPrimitive(91))
        rejects("location", "accuracy", JsonPrimitive(-1)); rejects("location", "lng", JsonPrimitive("NaN"))
        rejects("image", "path", JsonPrimitive("other/file.jpg")); rejects("image", "width", JsonPrimitive(1.5))
        rejects("voice", "duration", JsonPrimitive(0)); rejects("voice", "size", JsonPrimitive(16 * MiB + 1))
        rejects("file", "mime", JsonPrimitive("application/x-executable")); rejects("file", "name", JsonPrimitive("x".repeat(256)))
    }

    @Test fun boundedReadsCountdownMapsAndEscapedExports() {
        assertArrayEquals(byteArrayOf(1, 2), readBounded(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        assertTrue(runCatching { readBounded(ByteArrayInputStream(byteArrayOf()), 2) }.isFailure)
        assertTrue(runCatching { readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2) }.isFailure)
        val now = Instant.parse("2026-10-07T00:00:00Z")
        assertEquals("Now", countdownText(now.toString(), now))
        assertEquals("1d 1h 1m", countdownText(now.plusSeconds(90_060).toString(), now))
        assertEquals("https://tile.openstreetmap.org/15/32767/0.png", locationTile(90.0, 180.0))
        assertTrue(runCatching { locationTile(Double.NaN, 0.0) }.isFailure)
        val message = ChatMessage(id = "id", senderId = "me", createdAt = now.toString(), content = "<script>alert('x')</script>",
            type = "letter", payload = obj("appearance" to "dawn", "from" to "<me>", "to" to "&you"))
        assertFalse(letterHtml(message).contains("<script>")); assertTrue(letterHtml(message).contains("&lt;me&gt;"))
        assertFalse(exportChat(listOf(message), "me", "<them>", "html").contains("<script>"))
        assertEquals(message.content, Json.decodeFromString<List<ChatMessage>>(exportChat(listOf(message), "me", "Them", "json")).single().content)
    }

    @Test fun repliesUseOutboxAndInvalidDraftIsRetainedUntilSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession(); val transport = FakeTransport(); val store = MemoryMessageStore()
            val service = MessageService(transport, store, session, Json, backgroundScope)
            service.activate(connection("active", true)); runCurrent()
            val vm = FeatureViewModel(service, FakeAccountApi(session), FakeMedia(), FakeDeviceStore(), SavedStateHandle())
            runCurrent(); var closed = false
            vm.send("ask", "", obj("question" to "Q", "answerA" to "A", "answerB" to ""), "original") { closed = true }
            runCurrent(); assertFalse(closed); assertNotNull(vm.error.value); assertTrue(transport.sends.isEmpty())
            vm.send("ask", "", obj("question" to "Q", "answerA" to "A", "answerB" to "B"), "original") { closed = true }
            runCurrent(); assertTrue(closed)
            assertEquals("original", transport.sends.single().replyTo); assertEquals("Q", transport.sends.single().content)
            vm.viewModelScope.cancel(); runCurrent(); service.deactivate()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun fullExportPagesBackwardAndRejectsChangingConversation() = runTest {
        val session = FakeSession(); val transport = FakeTransport(); val service = MessageService(transport, MemoryMessageStore(), session, Json, backgroundScope)
        service.activate(connection("active", true)); runCurrent()
        val start = Instant.parse("2026-10-07T00:00:00Z")
        val history = (0..119).map { ChatMessage(id = "id$it", senderId = "me", content = "$it", createdAt = start.plusSeconds(it.toLong()).toString()) }
        transport.history = { before, _ -> history.filter { before == null || Instant.parse(it.createdAt).isBefore(Instant.parse(before)) }.takeLast(50) }
        assertEquals(history, service.exportHistory())
        assertTrue(transport.historyCalls.any { it.first != null })
        transport.history = { _, _ -> service.deactivate(); history.takeLast(50) }
        assertTrue(runCatching { service.exportHistory() }.isFailure)
    }

    @Test fun restoredVoiceWaitsForBootButIsDiscardedOnDifferentConnection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession(); val service = MessageService(FakeTransport(), MemoryMessageStore(), session, Json, backgroundScope)
            val media = FakeMedia(); val saved = SavedStateHandle(mapOf("voicePath" to "private-clip", "voiceDuration" to 2.0, "mediaOwner" to "me/connection"))
            val vm = FeatureViewModel(service, FakeAccountApi(session), media, FakeDeviceStore(), saved)
            runCurrent(); assertEquals("private-clip", vm.voicePath.value)
            service.activate(connection("active", true)); runCurrent(); assertEquals("private-clip", vm.voicePath.value)
            service.activate(connection("active", true).copy(id = "different")); runCurrent(); assertNull(vm.voicePath.value)
            assertEquals(1, media.discarded); vm.viewModelScope.cancel(); runCurrent(); service.deactivate()
        } finally { Dispatchers.resetMain() }
    }
}
