package app.web.oneonone.call

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallTerminationTest {
    @Test fun teardownFailureStillReconnectsBeforeEnding() = runTest {
        val events = mutableListOf<String>()
        endCallAfterCleanup({ events += "cleanup"; error("camera teardown failed") }) {
            events += "connected"
            events += "call:end"
        }
        assertEquals(listOf("cleanup", "connected", "call:end"), events)
    }

    @Test fun droppedCollectorCanBeCancelledWhileSiblingWaitsForConnection() = runTest {
        val connected = CompletableDeferred<Unit>()
        var ended = false
        lateinit var collector: Job
        collector = launch { awaitCancellation() }
        val resolution = launch {
            endCallAfterCleanup({ collector.cancel() }) { connected.await(); ended = true }
        }
        runCurrent()
        assertTrue(collector.isCancelled)
        assertFalse(ended)
        connected.complete(Unit)
        resolution.join()
        assertTrue(ended)
    }

    @Test fun acceptanceBeforeInviteAckStartsTheMatchingCall() {
        val pending = CallAttempt()
        pending.acceptedBeforeAck("call")
        assertEquals(InviteResolution.Accepted, pending.bind("call"))
        assertTrue(pending.canStartMedia("call"))
    }

    @Test fun endingBeforeInviteAckWinsOverAcceptance() {
        val pending = CallAttempt()
        pending.acceptedBeforeAck("call")
        pending.endedBeforeAck("call", "completed")
        assertEquals(InviteResolution.Ended("completed"), pending.bind("call"))
    }

    @Test fun previousCallEventsCannotAcceptOrEndTheNewInvite() {
        val pending = CallAttempt()
        pending.acceptedBeforeAck("old")
        pending.endedBeforeAck("old", "completed")
        assertEquals(InviteResolution.Ringing, pending.bind("new"))
        assertFalse(pending.canStartMedia("old"))
    }

    @Test fun hangupWhileInviteAckIsPendingEndsOnlyTheOldCall() = runTest {
        val old = CallAttempt()
        val ack = CompletableDeferred<String>()
        val ended = mutableListOf<String>()
        val completion = launch {
            val id = ack.await()
            if (old.bind(id) == null) ended += id
        }
        runCurrent()
        old.cancel()
        val next = CallAttempt("new")
        ack.complete("old")
        completion.join()
        assertEquals(listOf("old"), ended)
        assertTrue(next.canStartMedia("new"))
        assertFalse(old.canStartMedia("old"))
    }

    @Test fun ownAcceptanceKeepsTheAnswerPendingAndDuplicateTapsDoNothing() {
        val pending = CallAttempt("call")
        assertTrue(pending.shouldDismissIncoming("call")) // another device answered
        assertTrue(pending.beginAnswer())
        assertFalse(pending.shouldDismissIncoming("call")) // this device is awaiting its accept ack
        assertFalse(pending.beginAnswer())
    }

    @Test fun endingWhileAcceptAckIsPendingCannotRestartMedia() = runTest {
        val pending = CallAttempt("call")
        val ack = CompletableDeferred<Unit>()
        var mediaStarted = false
        assertTrue(pending.beginAnswer())
        val answer = launch {
            ack.await()
            mediaStarted = pending.canStartMedia("call")
        }
        runCurrent()
        pending.cancel()
        ack.complete(Unit)
        answer.join()
        assertFalse(mediaStarted)
        assertFalse(pending.beginAnswer())
    }
}
