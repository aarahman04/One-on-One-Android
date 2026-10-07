package app.web.oneonone.data

import app.web.oneonone.data.api.PushTokenBody
import app.web.oneonone.data.auth.tokenSubject
import app.web.oneonone.data.chat.*
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.data.model.ReactionSummary
import app.web.oneonone.push.*
import app.web.oneonone.push.handlers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushTest {
    private val connectionId = "11111111-1111-4111-8111-111111111111"
    private val messageId = "22222222-2222-4222-8222-222222222222"
    private fun data(type: String = "text") = mapOf("type" to type, "connectionId" to connectionId,
        "messageId" to messageId, "senderName" to "Friend", "preview" to "Hello")

    @Test fun routesAlarmCallAndCallEndOnlyToFrozenHandlers() {
        val seen = mutableListOf<String>()
        val router = PushRouter(object : AlarmPushHandler {
            override fun onAlarmPush(data: Map<String, String>) { seen += "alarm:${data["marker"]}" }
        }, object : CallPushHandler {
            override fun onCallPush(data: Map<String, String>) { seen += "call:${data["marker"]}" }
            override fun onCallEndPush(data: Map<String, String>) { seen += "end:${data["marker"]}" }
        })
        listOf("alarm", "call", "call_end").forEach { router.route(data(it) + ("marker" to "unchanged")) { seen += "message" } }
        router.route(data()) { seen += "message:${it.preview}" }
        router.route(data("system")) { seen += "unexpected" }
        assertEquals(listOf("alarm:unchanged", "call:unchanged", "end:unchanged", "message:Hello"), seen)
    }

    @Test fun normalPushRequiresContractTypeAndCanonicalIds() {
        assertNotNull(decodeMessagePush(data()))
        assertNull(decodeMessagePush(data() - "messageId"))
        assertNull(decodeMessagePush(data() + ("connectionId" to "1-1-1-1-1")))
        assertNull(decodeMessagePush(data() - "preview"))
        listOf("alarm", "call", "call_end", "system", "unknown").forEach { assertNull(decodeMessagePush(data(it))) }
        val truncated = decodeMessagePush(data() + mapOf("senderName" to "x".repeat(60), "preview" to "y".repeat(150)))!!
        assertEquals(40, truncated.senderName.length)
        assertEquals(120, truncated.preview.length)
    }

    @Test fun notificationAndActionsStayInCurrentAccountAndLiveConversation() {
        val push = decodeMessagePush(data())!!
        val current = connection("active", true).copy(id = connectionId)
        assertTrue(shouldNotify(push, current, false))
        assertFalse(shouldNotify(push, current, true))
        assertFalse(shouldNotify(push, current.copy(id = "another"), false))
        assertFalse(shouldNotify(push, current.copy(status = "pending"), false))
        assertFalse(shouldNotify(push, null, false))
        assertTrue(acceptsNotificationAction("owner", "owner", current, connectionId))
        assertFalse(acceptsNotificationAction("new-owner", "owner", current, connectionId))
        assertFalse(acceptsNotificationAction("owner", "owner", current, "another"))
        assertFalse(acceptsNotificationAction("owner", "owner", current.copy(status = "terminated"), connectionId))
    }

    @Test fun platformIsAlwaysEncodedAsAndroidNative() {
        assertTrue(Json.encodeToString(PushTokenBody("device", "android-native")).contains("\"platform\":\"android-native\""))
    }

    @Test fun registrationRotationAndSignOutAreSerializedAndCandidateSurvivesFailure() = runTest {
        val session = FakeSession().apply { token = "header.eyJzdWIiOiJvd25lciJ9.signature" }
        val preferences = FakeDeviceStore()
        val mutations = AccountMutationLock()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = object : FakeAccountApi(session) {
            override suspend fun register(body: PushTokenBody) {
                session.operations += "register:${body.platform}"
                entered.complete(Unit)
                release.await()
            }
        }
        val registration = launch { registerPushToken("rotated-token", "owner", api, session, preferences, mutations) }
        entered.await()
        assertEquals("rotated-token", preferences.token)
        val signOut = launch { AccountRepository(api, session, preferences, mutations).signOut() }
        runCurrent()
        assertEquals(listOf("unregister", "register:android-native"), session.operations)
        release.complete(Unit)
        registration.join(); signOut.join()
        assertEquals(listOf("unregister", "register:android-native", "unregister", "signout"), session.operations)
        assertNull(preferences.token)

        val otherSession = FakeSession().apply { token = "header.eyJzdWIiOiJvd25lciJ9.signature" }
        val failedPreferences = FakeDeviceStore().apply { token = null }
        val failing = object : FakeAccountApi(otherSession) {
            override suspend fun register(body: PushTokenBody) { throw java.io.IOException("lost response") }
        }
        assertTrue(runCatching { registerPushToken("candidate", "owner", failing, otherSession, failedPreferences, mutations) }.isFailure)
        assertEquals("candidate", failedPreferences.token)
        assertFalse(registerPushToken("wrong", "different-owner", failing, otherSession, failedPreferences, mutations))
        assertEquals("candidate", failedPreferences.token)
    }

    @Test fun replyWorkerRetryKeepsOneTempIdAndHistoryKeepsItsAlias() = runTest {
        val transport = FakeTransport().apply { loseAckOnce = true }
        val store = MemoryMessageStore()
        val service = MessageService(transport, store, FakeSession(), Json { ignoreUnknownKeys = true }, backgroundScope)
        service.activate(connection("active", true)); runCurrent()
        service.send("reply", clientTempId = "worker-id"); runCurrent()
        service.send("reply", clientTempId = "worker-id"); runCurrent()
        assertEquals(1, store.rows.value.size)
        assertEquals(1, transport.sends.size)
        service.flush()
        val canonical = checkNotNull(service.findSend("worker-id"))
        val history = canonical.copy(tempId = null, reactions = listOf(ReactionSummary("👍", listOf("other"))))
        store.reconcile("me", "connection", history)
        assertEquals("worker-id", service.findSend("worker-id")?.tempId)
        service.send("reply", clientTempId = "worker-id")
        assertEquals(2, transport.sends.size) // First uncertain attempt + one same-ID retry, no third send.
        val delayedAck = reconcileStored(history, canonical)
        assertEquals(history.reactions, delayedAck.reactions)
        service.deactivate()
    }
}
