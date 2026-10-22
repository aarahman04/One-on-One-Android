package app.web.oneonone.data

import app.web.oneonone.data.model.*
import app.web.oneonone.data.transport.decodeEvents
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SocketEventsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun malformedMessagesDoNotDisconnectLaterMessages() = runTest {
        val valid = JSONObject("""{"id":"m1","senderId":"other","content":"Hello","createdAt":"2026-10-22T00:00:00Z"}""")
        val received = flowOf(valid, JSONObject(), JSONObject(valid.toString()).put("createdAt", "bad-date"),
            JSONObject(valid.toString()).put("id", JSONObject.NULL), JSONObject(valid.toString()).put("id", "m2"))
            .decodeEvents<ChatMessage>(json).toList()
        assertEquals(listOf("m1", "m2"), received.map { it.id })
    }

    @Test fun malformedStatusEventsDoNotDisconnectLaterUpdates() = runTest {
        val receipts = flowOf(JSONObject(), JSONObject("""{"userId":"other","lastReadAt":"bad-date"}"""),
            JSONObject("""{"userId":"other","lastReadAt":"2026-10-22T00:00:00Z"}"""))
            .decodeEvents<ReceiptUpdate>(json).toList()
        assertEquals("2026-10-22T00:00:00Z", receipts.single().lastReadAt)
        val presence = flowOf(JSONObject("""{"userId":"other","online":false,"lastSeenAt":"bad-date"}"""),
            JSONObject("""{"userId":"other","online":true}""")).decodeEvents<PresenceUpdate>(json).toList()
        assertTrue(presence.single().online)
        val typing = flowOf(JSONObject(), JSONObject("""{"userId":"other","typing":true}"""))
            .decodeEvents<TypingUpdate>(json).toList()
        assertTrue(typing.single().typing)
    }
}
