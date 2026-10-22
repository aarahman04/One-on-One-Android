package app.web.oneonone.data

import app.web.oneonone.data.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

private class FakeSpeech : SpeechGateway {
    val requests = mutableListOf<Int>()
    val files = mutableListOf<File>()
    var total = 3
    var fetch: suspend (Int) -> Unit = {}
    var upload: suspend (File) -> Transcription = { Transcription("Hello there", "en", 2_000) }
    var uploads = 0
    override suspend fun narration(connection: String, message: String, part: Int): NarrationPart {
        assertEquals("connection", connection); assertEquals("letter", message)
        requests += part; fetch(part)
        return NarrationPart(File.createTempFile("narration-test", ".ogg").also { files += it }, part, total)
    }
    override suspend fun transcribe(file: File): Transcription { uploads++; return upload(file) }
}

private class FakePlayer(val completed: () -> Unit) : NarrationPlayer {
    override val positionMs = 500
    override val durationMs = 2_000
    var starts = 0
    var pauses = 0
    var released = false
    var playing = false
    var next: FakePlayer? = null
    var onStart: () -> Unit = {}
    override fun start() { check(!released); starts++; playing = true; onStart() }
    override fun pause() { pauses++; playing = false }
    override fun link(next: NarrationPlayer?) { this.next = next as? FakePlayer }
    override fun release() { released = true; playing = false }
    fun finish() { playing = false; next?.playing = true; completed() }
}
private class FakeAudio : NarrationAudio {
    val players = mutableListOf<FakePlayer>()
    var focusRequests = 0
    var focusLoss: () -> Unit = {}
    var deviceText: String? = null
    var failDeviceOnStart = false
    override fun focus(onLost: () -> Unit): Boolean { focusRequests++; focusLoss = onLost; return true }
    override fun abandonFocus() {}
    override suspend fun prepare(file: File, completed: () -> Unit, failed: () -> Unit) = FakePlayer(completed).also { players += it }
    override suspend fun deviceVoice(text: String, completed: () -> Unit, failed: () -> Unit): NarrationPlayer {
        deviceText = text; return FakePlayer(completed).also { if (failDeviceOnStart) it.onStart = failed; players += it }
    }
}
private class FakeRecorder : DictationRecorder {
    var starts = 0
    var stops = 0
    var cancels = 0
    var file: File? = null
    var starting: suspend () -> Unit = {}
    var limit: () -> Unit = {}
    var duration = 2.0
    override suspend fun startDictation(onLimit: () -> Unit) {
        starts++; limit = onLimit; starting()
        file = File.createTempFile("dictation-test", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }
    override suspend fun stopDictation(): VoiceClip? { stops++; return file?.let { VoiceClip(it, duration) } }
    override suspend fun cancelDictation() { cancels++; file?.delete(); file = null }
}
private class FakeDevice(override val available: Boolean = true) : DeviceSpeech {
    var starts = 0
    var stops = 0
    var result: (String) -> Unit = {}
    var partial: (String) -> Unit = {}
    override fun start(text: (String) -> Unit, partial: (String) -> Unit, failed: (String) -> Unit) {
        starts++; result = text; this.partial = partial
    }
    override fun stop() { stops++ }
    override fun cancel() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechTest {
    @Test fun narrationPrefetchesOnlyNextPartAndHandsOffWithoutExplicitRestart() = runTest {
        val api = FakeSpeech(); val audio = FakeAudio()
        val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler))
        narrator.toggle("connection", "letter", "local text"); runCurrent()
        assertEquals(listOf(0, 1), api.requests)
        assertEquals(NarrationPhase.Playing, narrator.state.value.phase)
        assertSame(audio.players[1], audio.players[0].next)
        repeat(3) { narrator.toggle("connection", "letter", "local text") }
        assertEquals(NarrationPhase.Paused, narrator.state.value.phase)
        assertEquals(listOf(0, 1), api.requests)
        narrator.toggle("connection", "letter", "local text")
        audio.players[0].finish(); runCurrent()
        assertEquals(listOf(0, 1, 2), api.requests)
        assertEquals(1, narrator.state.value.part)
        assertEquals(0, audio.players[1].starts)
        assertTrue(audio.players[1].playing)
        assertFalse(api.files[0].exists())
        audio.players[1].finish(); runCurrent()
        assertEquals(2, narrator.state.value.part)
        audio.players[2].finish(); runCurrent()
        assertEquals(NarrationPhase.Idle, narrator.state.value.phase)
        assertTrue(audio.players.all { it.released }); assertTrue(api.files.none { it.exists() })
    }

    @Test fun slowPrefetchStartsOnceWhenReadyAndRapidLoadingTapsDoNotOverlap() = runTest {
        val ready = CompletableDeferred<Unit>()
        val api = FakeSpeech().apply { total = 2; fetch = { if (it == 1) ready.await() } }
        val audio = FakeAudio(); val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler))
        narrator.toggle("connection", "letter", "text")
        repeat(10) { narrator.toggle("connection", "letter", "text") }
        runCurrent(); assertEquals(1, audio.focusRequests)
        audio.players[0].finish(); runCurrent()
        assertEquals(NarrationPhase.Loading, narrator.state.value.phase)
        ready.complete(Unit); runCurrent()
        assertEquals(NarrationPhase.Playing, narrator.state.value.phase)
        assertEquals(1, audio.players[1].starts)
        narrator.stop(); runCurrent(); assertTrue(api.files.none { it.exists() })
    }

    @Test fun pauseAtThePartBoundaryKeepsTheSuccessorPausedUntilResume() = runTest {
        val api = FakeSpeech(); val audio = FakeAudio()
        val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler))
        narrator.toggle("connection", "letter", "text"); runCurrent()
        narrator.toggle("connection", "letter", "text")
        audio.players[0].finish(); runCurrent()
        assertEquals(NarrationPhase.Paused, narrator.state.value.phase); assertFalse(audio.players[1].playing)
        narrator.toggle("connection", "letter", "text")
        assertEquals(1, audio.players[1].starts)
        narrator.stop(); runCurrent()
    }

    @Test fun stopCancelsPrefetchReleasesBothPlayersAndIgnoresLateCompletion() = runTest {
        val api = FakeSpeech(); val audio = FakeAudio()
        val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler))
        narrator.toggle("connection", "letter", "text"); runCurrent()
        audio.focusLoss(); runCurrent()
        assertEquals(NarrationPhase.Idle, narrator.state.value.phase)
        audio.players[0].completed(); runCurrent()
        assertEquals(listOf(0, 1), api.requests)
        assertTrue(audio.players.all { it.released }); assertTrue(api.files.none { it.exists() })
        val cancelled = CompletableDeferred<Unit>()
        api.fetch = { try { awaitCancellation() } finally { cancelled.complete(Unit) } }
        narrator.toggle("connection", "letter", "text"); runCurrent()
        narrator.stop(); runCurrent()
        assertTrue(cancelled.isCompleted); assertEquals(NarrationPhase.Idle, narrator.state.value.phase)
    }

    @Test fun narrationNetworkAndServiceFailuresUseDeviceVoiceButNotMissingLetter() = runTest {
        for (error in listOf(IOException("offline"), SpeechFailure(503, "speech_unavailable"), SpeechFailure(504, "speech_timeout"),
            SpeechFailure(429, "quota_exceeded"))) {
            val api = FakeSpeech().apply { fetch = { throw error } }; val audio = FakeAudio()
            val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler))
            narrator.toggle("connection", "letter", "private local text"); runCurrent()
            assertTrue(narrator.state.value.device)
            assertEquals("Natural voice unavailable, using device voice", narrator.state.value.notice)
            assertEquals("private local text", audio.deviceText)
            narrator.stop(); runCurrent()
        }
        val api = FakeSpeech().apply { fetch = { throw SpeechFailure(404, "not_found") } }; val audio = FakeAudio()
        val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler))
        narrator.toggle("connection", "letter", "text"); runCurrent()
        assertEquals(NarrationPhase.Idle, narrator.state.value.phase)
        assertEquals("This letter can't be read aloud", narrator.state.value.notice)
        assertNull(audio.deviceText); narrator.stop()
        // The per-minute limit is not the daily budget: no device fallback, just "busy".
        val busy = LetterNarrator(FakeSpeech().apply { fetch = { throw SpeechFailure(429, "rate_limited") } }, FakeAudio(), backgroundScope,
            StandardTestDispatcher(testScheduler))
        busy.toggle("connection", "letter", "text"); runCurrent()
        assertEquals("Reading is busy. Try again in a moment.", busy.state.value.notice)
        busy.stop()
    }

    @Test fun inconsistentPartTotalsRejectTheSequenceAndDeleteFiles() = runTest {
        val api = FakeSpeech().apply { fetch = { if (it == 1) total = 1 } }; val audio = FakeAudio()
        val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler))
        narrator.toggle("connection", "letter", "text"); runCurrent()
        assertTrue(narrator.state.value.device)
        assertTrue(api.files.none { it.exists() })
        narrator.stop(); runCurrent()
    }

    @Test fun transcriptionRecordsOnceUploadsAndDeliversTextWithoutSending() = runTest {
        val api = FakeSpeech(); val recorder = FakeRecorder(); val device = FakeDevice()
        val delivered = mutableListOf<String>(); val notices = mutableListOf<String>()
        val transcriber = SpeechTranscriber(api, recorder, device, backgroundScope, delivered::add, notices::add, StandardTestDispatcher(testScheduler))
        transcriber.start(); transcriber.start(); runCurrent()
        assertEquals(1, recorder.starts); assertEquals(DictationPhase.Recording, transcriber.state.value.phase)
        advanceTimeBy(2_000); runCurrent(); assertEquals(2, transcriber.state.value.seconds)
        val file = recorder.file!!
        transcriber.stop(); transcriber.stop(); assertEquals(DictationPhase.Transcribing, transcriber.state.value.phase)
        runCurrent()
        assertEquals(listOf("Hello there"), delivered); assertEquals(1, api.uploads)
        assertFalse("Recording must be deleted; cancels=${recorder.cancels}; state=${transcriber.state.value}", file.exists())
        assertFalse("Expected idle: ${transcriber.state.value}", transcriber.state.value.active)
        assertTrue(notices.isEmpty()); assertEquals(0, device.starts)
    }

    @Test fun transcriptionMapsNoSpeechTooLongAndRateLimitWithoutFallback() = runTest {
        for ((status, expected) in listOf(422 to "Didn't catch that. Try again.", 413 to "That's too long. Keep it under 2 minutes.",
            429 to "Speech recognition is busy. Try again in a moment.")) {
            val api = FakeSpeech().apply { upload = { throw SpeechFailure(status, "error") } }
            val recorder = FakeRecorder(); val device = FakeDevice(); val notices = mutableListOf<String>()
            val transcriber = SpeechTranscriber(api, recorder, device, backgroundScope, { fail("Must not deliver") }, notices::add, StandardTestDispatcher(testScheduler))
            transcriber.start(); runCurrent(); val file = recorder.file!!; transcriber.stop(); runCurrent()
            assertEquals(listOf(expected), notices); assertFalse("Recording must be deleted", file.exists())
            assertFalse("Expected idle: ${transcriber.state.value}", transcriber.state.value.active)
            assertEquals(0, device.starts)
        }
    }

    @Test fun transcriptionServiceAndNetworkFailuresCleanFileAndFallBackToDevice() = runTest {
        for (error in listOf(IOException("offline"), SpeechFailure(503, "speech_unavailable"), SpeechFailure(504, "speech_timeout"),
            SpeechFailure(429, "quota_exceeded"))) {
            val api = FakeSpeech().apply { upload = { throw error } }; val recorder = FakeRecorder(); val device = FakeDevice()
            val delivered = mutableListOf<String>(); val notices = mutableListOf<String>()
            val transcriber = SpeechTranscriber(api, recorder, device, backgroundScope, delivered::add, notices::add, StandardTestDispatcher(testScheduler))
            transcriber.start(); runCurrent(); val file = recorder.file!!; transcriber.stop(); runCurrent()
            assertFalse(file.exists()); assertEquals(DictationPhase.DeviceListening, transcriber.state.value.phase)
            assertEquals(listOf("Using device speech recognition"), notices)
            device.partial("Indian English"); assertEquals("Indian English", transcriber.state.value.partial)
            transcriber.stop(); assertEquals(1, device.stops)
            device.result("  Good morning  "); runCurrent()
            assertEquals(listOf("Good morning"), delivered); assertFalse(transcriber.state.value.active)
        }
    }

    @Test fun cancelAbortsUploadAndNeverDeliversLateTextOrLeavesTempFiles() = runTest {
        val began = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
        val api = FakeSpeech().apply { upload = { began.complete(Unit); try { awaitCancellation() } finally { cancelled.complete(Unit) } } }
        val recorder = FakeRecorder(); val device = FakeDevice()
        val transcriber = SpeechTranscriber(api, recorder, device, backgroundScope, { fail("Cancelled text") }, {}, StandardTestDispatcher(testScheduler))
        transcriber.start(); runCurrent(); val file = recorder.file!!
        transcriber.stop(); runCurrent(); assertTrue(began.isCompleted)
        transcriber.cancel(); runCurrent()
        assertTrue(cancelled.isCompleted); assertFalse(file.exists()); assertFalse(transcriber.state.value.active)
        transcriber.start(); runCurrent(); val second = recorder.file!!; transcriber.cancel(); runCurrent()
        assertFalse(second.exists()); assertEquals(2, recorder.starts)
    }

    @Test fun cancelledRecorderStartFinishesCleanupBeforeAnotherSession() = runTest {
        val ready = CompletableDeferred<Unit>()
        val recorder = FakeRecorder().apply { starting = { withContext(NonCancellable) { ready.await() } } }
        val transcriber = SpeechTranscriber(FakeSpeech(), recorder, FakeDevice(), backgroundScope, {}, {}, StandardTestDispatcher(testScheduler))
        transcriber.start(); runCurrent(); transcriber.cancel(); transcriber.start(); runCurrent()
        assertEquals(1, recorder.starts)
        ready.complete(Unit); runCurrent(); assertEquals(2, recorder.starts)
        assertEquals(DictationPhase.Recording, transcriber.state.value.phase)
        val file = recorder.file!!; transcriber.cancel(); runCurrent(); assertFalse(file.exists())
    }

    @Test fun cancelRejectsAnUploadResultThatArrivesAfterCancellation() = runTest {
        val ready = CompletableDeferred<Unit>()
        val api = FakeSpeech().apply { upload = { withContext(NonCancellable) { ready.await() }; Transcription("late", "en", 1_000) } }
        val recorder = FakeRecorder(); val delivered = mutableListOf<String>()
        val transcriber = SpeechTranscriber(api, recorder, FakeDevice(), backgroundScope, delivered::add, {}, StandardTestDispatcher(testScheduler))
        transcriber.start(); runCurrent(); val file = recorder.file!!; transcriber.stop(); runCurrent()
        transcriber.cancel(); ready.complete(Unit); runCurrent()
        assertTrue(delivered.isEmpty()); assertFalse(file.exists()); assertFalse(transcriber.state.value.active)
    }

    @Test fun overlongClipIsRejectedBeforeUploadAndStartFailureReturnsToIdle() = runTest {
        val recorder = FakeRecorder().apply { duration = 121.0 }; val api = FakeSpeech(); val notices = mutableListOf<String>()
        val transcriber = SpeechTranscriber(api, recorder, FakeDevice(), backgroundScope, {}, notices::add, StandardTestDispatcher(testScheduler))
        transcriber.start(); runCurrent(); val file = recorder.file!!; transcriber.stop(); runCurrent()
        assertEquals(0, api.uploads); assertEquals(listOf("That's too long. Keep it under 2 minutes."), notices)
        assertFalse(file.exists()); assertFalse(transcriber.state.value.active)
        recorder.starting = { error("Microphone is busy") }
        transcriber.start(); runCurrent()
        assertFalse(transcriber.state.value.active); assertEquals("Microphone is busy", notices.last())
    }

    @Test fun timerStopsAtTwoMinutesAndNativeLimitDoesNotUploadTwice() = runTest {
        val api = FakeSpeech(); val recorder = FakeRecorder()
        val transcriber = SpeechTranscriber(api, recorder, FakeDevice(), backgroundScope, {}, {}, StandardTestDispatcher(testScheduler))
        transcriber.start(); runCurrent(); advanceTimeBy(120_000); runCurrent(); recorder.limit(); runCurrent()
        assertEquals(1, recorder.stops); assertEquals(1, api.uploads)
        assertFalse("Expected idle: ${transcriber.state.value}; cancels=${recorder.cancels}", transcriber.state.value.active)
    }

    @Test fun externalDeviceDialogIsTokenScopedAndCancelRejectsItsLateResult() = runTest {
        val api = FakeSpeech().apply { upload = { throw IOException("offline") } }
        val delivered = mutableListOf<String>()
        val transcriber = SpeechTranscriber(api, FakeRecorder(), FakeDevice(false), backgroundScope, delivered::add, {}, StandardTestDispatcher(testScheduler))
        transcriber.start(); runCurrent(); transcriber.stop(); runCurrent()
        assertEquals(DictationPhase.DeviceDialog, transcriber.state.value.phase)
        val token = transcriber.state.value.token
        transcriber.cancel(); runCurrent(); transcriber.deviceResult(token, "late result")
        assertTrue(delivered.isEmpty())
        transcriber.start(); runCurrent(); transcriber.stop(); runCurrent()
        transcriber.deviceResult(transcriber.state.value.token, "new result"); runCurrent()
        assertEquals(listOf("new result"), delivered)
    }

    @Test fun draftAppendPreservesExistingTextAndWhitespaceWithoutDuplicatingSpaces() {
        assertEquals("Hello", appendDictationText("", " Hello "))
        assertEquals("Already here Good morning", appendDictationText("Already here", "Good morning"))
        assertEquals("Already here  Good morning", appendDictationText("Already here  ", "Good morning"))
        assertEquals("Line one\nGood morning", appendDictationText("Line one\n", "Good morning"))
        assertEquals("Already here", appendDictationText("Already here", " \n"))
    }

    @Test fun deviceOnlyBuildNeverUploadsAndUsesDeviceVoiceAndRecognitionQuietly() = runTest {
        val api = FakeSpeech().apply { upload = { throw AssertionError("Must not upload") } }; val recorder = FakeRecorder(); val device = FakeDevice()
        val notices = mutableListOf<String>()
        val transcriber = SpeechTranscriber(api, recorder, device, backgroundScope, { }, notices::add, StandardTestDispatcher(testScheduler), server = false)
        transcriber.start(); advanceUntilIdle()
        assertEquals(DictationPhase.DeviceListening, transcriber.state.value.phase)
        assertTrue(notices.isEmpty())
        val audio = FakeAudio()
        val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler), server = false)
        narrator.toggle("c", "m", "Dear you."); advanceUntilIdle()
        assertTrue(narrator.state.value.device)
    }

    @Test fun previewUsesDeviceVoiceEvenInServerBuildAndStopsOnRelease() = runTest {
        val api = FakeSpeech(); val audio = FakeAudio()
        val narrator = LetterNarrator(api, audio, backgroundScope, StandardTestDispatcher(testScheduler), server = true)
        narrator.toggle("connection", "preview", "Dear you. A comma, stays. With love, me.", device = true); runCurrent()
        assertTrue(api.requests.isEmpty())
        assertEquals("Dear you. A comma, stays. With love, me.", audio.deviceText)
        narrator.stop(); runCurrent()
        assertTrue(audio.players.single().released)
    }

    @Test fun synchronousDeviceVoiceFailureDoesNotLeaveAPlayingState() = runTest {
        val audio = FakeAudio().apply { failDeviceOnStart = true }
        val narrator = LetterNarrator(FakeSpeech(), audio, backgroundScope, StandardTestDispatcher(testScheduler))
        narrator.toggle("connection", "preview", "Dear you.", device = true); runCurrent()
        assertEquals(NarrationPhase.Idle, narrator.state.value.phase)
        assertTrue(audio.players.single().released)
        assertEquals("Reading aloud isn't available on this phone.", narrator.state.value.notice)
    }
}
