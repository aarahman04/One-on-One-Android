package app.web.oneonone.alarm

import app.web.oneonone.data.model.ChatMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlarmPolicyTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli()
    private fun at(agoMs: Long) = Instant.ofEpochMilli(now - agoMs).toString()

    private fun raise(id: String? = "r1", sender: String = "them", agoMs: Long = 10_000) =
        ChatMessage(id = id, senderId = sender, content = "", createdAt = at(agoMs), type = "alarm",
            payload = JsonObject(emptyMap()), tempId = if (id == null) "t1" else null)

    private fun ack(raiseId: String, id: String? = "a1", sender: String = "me", cancelled: Boolean = false,
                    state: String = "sent") = ChatMessage(id = id, senderId = sender, content = "", createdAt = at(5_000),
        type = "alarm", deliveryState = state, replyTo = raiseId,
        payload = JsonObject(buildMap {
            put("ack", JsonPrimitive(raiseId))
            if (cancelled) put("cancelled", JsonPrimitive(true))
        }))

    @Test fun `raise without server id is sending, never ackable`() {
        assertEquals(AlarmCardState.Sending, AlarmPolicy.cardState(raise(id = null), emptyList(), now))
    }

    @Test fun `confirmed raise inside the window is live`() {
        assertEquals(AlarmCardState.Live, AlarmPolicy.cardState(raise(), emptyList(), now))
    }

    @Test fun `queued ack is pending until the server echoes it`() {
        val r = raise()
        assertEquals(AlarmCardState.Pending, AlarmPolicy.cardState(r, listOf(r, ack("r1", id = null, state = "queued")), now))
    }

    @Test fun `failed ack makes the card tappable again`() {
        val r = raise()
        assertEquals(AlarmCardState.Failed, AlarmPolicy.cardState(r, listOf(r, ack("r1", id = null, state = "failed")), now))
    }

    @Test fun `server-confirmed cancel and ack are final`() {
        val r = raise()
        assertEquals(AlarmCardState.Cancelled, AlarmPolicy.cardState(r, listOf(r, ack("r1", cancelled = true)), now))
        assertEquals(AlarmCardState.Acknowledged, AlarmPolicy.cardState(r, listOf(r, ack("r1")), now))
        // Final even after the window (the old re-pop bug showed "tap to cancel" again).
        assertEquals(AlarmCardState.Cancelled,
            AlarmPolicy.cardState(r, listOf(r, ack("r1", cancelled = true)), now + AlarmPolicy.WINDOW_MS * 10))
    }

    @Test fun `unacked raise past the window is expired`() {
        assertEquals(AlarmCardState.Expired, AlarmPolicy.cardState(raise(agoMs = AlarmPolicy.WINDOW_MS + 1), emptyList(), now))
    }

    @Test fun `an ack for a different raise does not affect this one`() {
        val r = raise()
        assertEquals(AlarmCardState.Live, AlarmPolicy.cardState(r, listOf(r, ack("other")), now))
    }

    @Test fun `only live raises from the other member ring`() {
        val list = listOf(
            raise("mine", sender = "me"),
            raise("old", agoMs = AlarmPolicy.WINDOW_MS + 1),
            raise("acked"), ack("acked"),
            raise("pendingAck"), ack("pendingAck", id = null, state = "queued"),
            raise(id = null),
            raise("live"),
        )
        assertEquals(listOf("pendingAck", "live"), AlarmPolicy.raisesToRing(list, "me", now).map { it.first })
    }

    @Test fun `only server-confirmed acks stop a ring`() {
        val list = listOf(ack("a"), ack("b", id = null, state = "queued"), raise("c"))
        assertEquals(setOf("a"), AlarmPolicy.confirmedAcks(list))
    }

    @Test fun `handled or expired alarms never ring`() {
        assertTrue(AlarmPolicy.shouldRing(handled = false, raisedAtMs = now - 1_000, now = now))
        assertFalse(AlarmPolicy.shouldRing(handled = true, raisedAtMs = now - 1_000, now = now))
        assertFalse(AlarmPolicy.shouldRing(handled = false, raisedAtMs = now - AlarmPolicy.WINDOW_MS - 1, now = now))
    }
}
