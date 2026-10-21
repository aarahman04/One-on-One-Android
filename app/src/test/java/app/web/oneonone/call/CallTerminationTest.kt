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
}
