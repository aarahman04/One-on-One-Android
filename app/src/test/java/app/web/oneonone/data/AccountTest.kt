package app.web.oneonone.data

import android.content.Context
import app.web.oneonone.data.api.*
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.auth.sha256Hex
import app.web.oneonone.data.auth.tokenSubject
import app.web.oneonone.ui.AppViewModel
import app.web.oneonone.ui.BootRoute
import app.web.oneonone.ui.isAdult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FakeSession : AccountSession {
    override val signedIn = MutableStateFlow(true)
    var token: String? = "old"
    var refreshes = 0
    var invalidations = 0
    val operations = mutableListOf<String>()
    override suspend fun accessToken() = token
    override suspend fun refreshToken(rejectedToken: String): String? {
        if (token != rejectedToken) return token
        refreshes++
        token = "fresh"
        return token
    }
    override suspend fun invalidate(rejectedToken: String) {
        if (token == rejectedToken) { token = null; invalidations++; signedIn.value = false }
    }
    override suspend fun signIn(activityContext: Context) { token = "old"; signedIn.value = true }
    override suspend fun signOut() { operations.add("signout"); token = null; signedIn.value = false }
}

class FakeDeviceStore : DeviceStore {
    var savedGates = Gates(true, true)
    var token: String? = "device-token"
    override suspend fun gates() = savedGates
    override suspend fun verifyAge() { savedGates = savedGates.copy(ageVerified = true) }
    override suspend fun acceptTerms() { savedGates = savedGates.copy(termsAccepted = true) }
    override suspend fun pushToken() = token
    override suspend fun savePushToken(token: String?) { this.token = token }
}

class FakeAccountApi(val session: FakeSession) : AccountApi {
    var current: CurrentConnection? = null
    var unregisterFails = false
    var requestedCode: String? = null
    var failCurrent = false
    var deleteFails = false
    override suspend fun me() = Me("me", "ABCDEFGH")
    override suspend fun regenerate() = CodeResult("JKLMNPQR")
    override suspend fun deleteAccount() { check(!deleteFails); session.operations.add("delete") }
    override suspend fun blocks() = BlocksResult(emptyList())
    override suspend fun unblock(id: String) { }
    override suspend fun current(): CurrentResult {
        check(!failCurrent) { "Server unavailable" }
        return CurrentResult(current)
    }
    override suspend fun request(body: ConnectionRequest): JsonObject {
        requestedCode = body.connectionCode
        current = connection("pending", true)
        return JsonObject(emptyMap())
    }
    override suspend fun accept(id: String): JsonObject {
        current = connection("active", false)
        return JsonObject(emptyMap())
    }
    override suspend fun decline(id: String): JsonObject { current = null; return JsonObject(emptyMap()) }
    override suspend fun cancel(id: String): JsonObject { current = null; return JsonObject(emptyMap()) }
    override suspend fun unregister(body: TokenBody) {
        check(!unregisterFails) { "offline" }
        session.operations.add("unregister")
    }
}

fun connection(status: String, requester: Boolean) = CurrentConnection(
    "connection", status, "me", requester, null, "JKLMNPQR", 0, 0, null,
    false, false, null, null, "off", "bubbles",
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountTest {
    @Test fun nonceAndInputValidation() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
        assertEquals("user", tokenSubject("header.eyJzdWIiOiJ1c2VyIn0.signature"))
        assertNull(tokenSubject("invalid"))
        assertEquals("ABCDEFGH", normalizeCode(" abcdefgh "))
        listOf("", "ABCDEFGI", "ABCDEFG0", "ABCDEFGH/").forEach {
            assertTrue(runCatching { normalizeCode(it) }.isFailure)
        }
        val today = LocalDate.of(2026, 10, 7)
        assertTrue(isAdult(LocalDate.of(2008, 10, 7), today))
        assertFalse(isAdult(LocalDate.of(2008, 10, 8), today))
        assertFalse(isAdult(today.plusDays(1), today))
    }

    @Test fun unregisterPrecedesSignOutAndFailurePreservesSession() = runTest {
        val session = FakeSession()
        val api = FakeAccountApi(session)
        val preferences = FakeDeviceStore()
        val repository = AccountRepository(api, session, preferences)
        api.unregisterFails = true
        assertTrue(runCatching { repository.signOut() }.isFailure)
        assertEquals("old", session.token)
        assertEquals("device-token", preferences.token)
        api.unregisterFails = false
        repository.signOut()
        assertEquals(listOf("unregister", "signout"), session.operations)
        assertNull(preferences.token)
    }

    @Test fun deletionOnlyClearsSessionAfterServerSuccess() = runTest {
        val session = FakeSession()
        val preferences = FakeDeviceStore()
        val api = FakeAccountApi(session).apply { deleteFails = true }
        val repository = AccountRepository(api, session, preferences)
        assertTrue(runCatching { repository.deleteAccount() }.isFailure)
        assertEquals("old", session.token)
        assertEquals("device-token", preferences.token)
        api.deleteFails = false
        repository.deleteAccount()
        assertEquals(listOf("delete", "signout"), session.operations)
        assertNull(preferences.token)
    }

    @Test fun viewModelRoutesGatesPendingActiveAndSignOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val api = FakeAccountApi(session)
            val preferences = FakeDeviceStore().apply { savedGates = Gates() }
            val vm = AppViewModel(session, AccountRepository(api, session, preferences), preferences)
            runCurrent()
            assertEquals(BootRoute.Age, vm.state.value.route)
            vm.verifyAge(LocalDate.now().minusYears(18)); runCurrent()
            assertEquals(BootRoute.Consent, vm.state.value.route)
            vm.acceptTerms(); runCurrent()
            assertEquals(BootRoute.Connect, vm.state.value.route)
            vm.request(" jklmnpqr "); runCurrent()
            assertEquals("JKLMNPQR", api.requestedCode)
            assertEquals(BootRoute.Waiting, vm.state.value.route)
            vm.connectionAction("cancel"); runCurrent()
            assertEquals(BootRoute.Connect, vm.state.value.route)
            api.current = connection("pending", false)
            vm.refresh(); runCurrent()
            assertEquals(BootRoute.Request, vm.state.value.route)
            vm.connectionAction("accept"); runCurrent()
            assertEquals(BootRoute.Chat, vm.state.value.route)
            api.failCurrent = true
            vm.refresh(); runCurrent()
            assertEquals(BootRoute.Chat, vm.state.value.route)
            assertNotNull(vm.state.value.error)
            vm.signOut(); runCurrent()
            assertEquals(BootRoute.SignIn, vm.state.value.route)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun underAgeCannotBypassGateWithPoll() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val api = FakeAccountApi(session)
            val preferences = FakeDeviceStore().apply { savedGates = Gates() }
            val vm = AppViewModel(session, AccountRepository(api, session, preferences), preferences)
            runCurrent()
            vm.verifyAge(LocalDate.now().minusYears(17)); runCurrent()
            vm.refresh(); runCurrent()
            assertEquals(BootRoute.UnderAge, vm.state.value.route)
            assertFalse(preferences.savedGates.ageVerified)
        } finally { Dispatchers.resetMain() }
    }
}
