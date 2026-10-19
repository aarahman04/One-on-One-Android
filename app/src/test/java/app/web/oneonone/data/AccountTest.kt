package app.web.oneonone.data

import android.content.Context
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.api.*
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.auth.sha256Hex
import app.web.oneonone.data.auth.tokenSubject
import app.web.oneonone.ui.AppViewModel
import app.web.oneonone.ui.BootRoute
import app.web.oneonone.ui.isAdult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import app.web.oneonone.data.stickers.StickerLibrary
import java.io.File
import java.util.UUID
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
    var gatesOwner: String? = null
    var token: String? = "device-token"
    var seenOnboarding = true
    // Same owner rules as DevicePreferences: legacy gates adopt the first owner; another owner's gates never count.
    override suspend fun gates(owner: String): Gates {
        if (gatesOwner == null && (savedGates.ageVerified || savedGates.termsAccepted)) gatesOwner = owner
        return if (gatesOwner == owner) savedGates else Gates()
    }
    override suspend fun verifyAge(owner: String) {
        if (gatesOwner != owner) savedGates = savedGates.copy(termsAccepted = false)
        gatesOwner = owner; savedGates = savedGates.copy(ageVerified = true)
    }
    override suspend fun acceptTerms(owner: String) {
        if (gatesOwner != owner) savedGates = savedGates.copy(ageVerified = false)
        gatesOwner = owner; savedGates = savedGates.copy(termsAccepted = true)
    }
    override suspend fun resetGates() { savedGates = Gates(); gatesOwner = null }
    override suspend fun pushToken() = token
    override suspend fun savePushToken(token: String?) { this.token = token }
    override suspend fun onboardingSeen() = seenOnboarding
    override suspend fun markOnboardingSeen() { seenOnboarding = true }
    override suspend fun claimNotification(messageId: String) = true
    override val theme = MutableStateFlow("dark")
    override suspend fun setTheme(value: String) { theme.value = value }
    override suspend fun letterSignature() = ""
    override suspend fun saveLetterSignature(value: String) { }
}

open class FakeAccountApi(val session: FakeSession) : AccountApi {
    var current: CurrentConnection? = null
    var unregisterFails = false
    var requestedCode: String? = null
    var failCurrent = false
    var deleteFails = false
    /** Server gate record (contract §10). */
    var serverGates = GatesResult()
    val gatePosts = mutableListOf<JsonObject>()
    var gatesFail = false
    override suspend fun me() = Me("me", "ABCDEFGH", serverGates.ageVerifiedAt, serverGates.termsAcceptedAt)
    override suspend fun gates(body: JsonObject): GatesResult {
        gatePosts += body
        check(!gatesFail) { "404" }
        serverGates = GatesResult(serverGates.ageVerifiedAt ?: body["ageVerified"]?.let { "2026-10-19T00:00:00Z" },
            serverGates.termsAcceptedAt ?: body["termsAccepted"]?.let { "2026-10-19T00:00:00Z" })
        return serverGates
    }
    val acknowledged = mutableListOf<String>()
    override suspend fun acknowledgeEnd(id: String) { acknowledged += id; current = null }
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
    override suspend fun nickname(id: String, body: NicknameBody) { }
    override suspend fun register(body: PushTokenBody) { session.operations.add("register:${body.platform}") }
    override suspend fun leave(id: String) { current = null }
    override suspend fun wallpaper(id: String, body: WallpaperBody) { }
    override suspend fun block(id: String) { }
    override suspend fun reportConnection(id: String, body: ReportBody) { }
    override suspend fun reportMessage(id: String, body: ReportBody) { }
}

fun connection(status: String, requester: Boolean) = CurrentConnection(
    "connection", status, "me", requester, null, "JKLMNPQR", null, null, "off", "bubbles",
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountTest {
    @Test fun stickerCleanupFollowsSuccessfulSignOutAndAccountDeletionOnly() = runTest {
        val owner = "00000000-0000-4000-8000-000000000001"
        val session = FakeSession()
        val api = FakeAccountApi(session)
        val library = testStickerLibrary()
        val repository = AccountRepository(api, session, FakeDeviceStore(), stickers = library)
        val sticker = library.save(owner, byteArrayOf(1, 2), 1).single()
        val file = library.file(owner, sticker.id)
        api.unregisterFails = true
        assertTrue(runCatching { repository.signOut() }.isFailure)
        assertTrue(file.exists())
        api.unregisterFails = false
        repository.signOut()
        assertFalse(file.exists())
        session.signedIn.value = true; session.token = "old"
        val replacement = library.save(owner, byteArrayOf(3), 2).single()
        api.deleteFails = true
        assertTrue(runCatching { repository.deleteAccount() }.isFailure)
        assertTrue(library.file(owner, replacement.id).exists())
        api.deleteFails = false
        repository.deleteAccount()
        assertFalse(library.file(owner, replacement.id).exists())
    }

    @Test fun onboardingAppearsAfterConsentAndOnlyOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val preferences = FakeDeviceStore().apply { savedGates = Gates(); seenOnboarding = false }
            val vm = AppViewModel(session, AccountRepository(FakeAccountApi(session), session, preferences, stickers = testStickerLibrary()), preferences)
            runCurrent()
            assertEquals(BootRoute.Age, vm.state.value.route)
            assertFalse(vm.state.value.needsNotificationOnboarding)
            vm.verifyAge(LocalDate.now().minusYears(18)); runCurrent()
            vm.acceptTerms(); runCurrent()
            assertTrue(vm.state.value.needsNotificationOnboarding)
            vm.onboardingShown(); runCurrent()
            vm.refresh(); runCurrent()
            assertTrue(preferences.seenOnboarding)
            assertFalse(vm.state.value.needsNotificationOnboarding)
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

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
        val repository = AccountRepository(api, session, preferences, stickers = testStickerLibrary())
        api.unregisterFails = true
        assertTrue(runCatching { repository.signOut() }.isFailure)
        assertEquals("old", session.token)
        assertEquals("device-token", preferences.token)
        api.unregisterFails = false
        repository.signOut()
        assertEquals(listOf("unregister", "signout"), session.operations)
        assertNull(preferences.token)
        // Signing back into the same account skips Age and Terms.
        assertEquals(Gates(true, true), preferences.savedGates)
    }

    @Test fun deletionOnlyClearsSessionAfterServerSuccess() = runTest {
        val session = FakeSession()
        val preferences = FakeDeviceStore()
        val api = FakeAccountApi(session).apply { deleteFails = true }
        val repository = AccountRepository(api, session, preferences, stickers = testStickerLibrary())
        assertTrue(runCatching { repository.deleteAccount() }.isFailure)
        assertEquals("old", session.token)
        assertEquals("device-token", preferences.token)
        api.deleteFails = false
        repository.deleteAccount()
        assertEquals(listOf("delete", "signout"), session.operations)
        assertNull(preferences.token)
        assertEquals(Gates(), preferences.savedGates)
    }

    @Test fun viewModelRoutesGatesPendingActiveAndSignOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val api = FakeAccountApi(session)
            val preferences = FakeDeviceStore().apply { savedGates = Gates() }
            val vm = AppViewModel(session, AccountRepository(api, session, preferences, stickers = testStickerLibrary()), preferences)
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
            vm.signOut().join(); runCurrent()
            assertEquals(BootRoute.SignIn, vm.state.value.route)
            api.failCurrent = false
            session.token = "old"; session.signedIn.value = true; runCurrent()
            assertEquals(BootRoute.Chat, vm.state.value.route)
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun underAgeCannotBypassGateWithPoll() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val api = FakeAccountApi(session)
            val preferences = FakeDeviceStore().apply { savedGates = Gates() }
            val vm = AppViewModel(session, AccountRepository(api, session, preferences, stickers = testStickerLibrary()), preferences)
            runCurrent()
            vm.verifyAge(LocalDate.now().minusYears(17)); runCurrent()
            vm.refresh(); runCurrent()
            assertEquals(BootRoute.UnderAge, vm.state.value.route)
            assertFalse(preferences.savedGates.ageVerified)
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GatesTest {
    private fun jwt(sub: String) = "h." + java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"sub\":\"$sub\"}".toByteArray()) + ".s"

    private fun TestScope.boot(session: FakeSession, api: FakeAccountApi, preferences: FakeDeviceStore): AppViewModel {
        val vm = AppViewModel(session, AccountRepository(api, session, preferences, stickers = testStickerLibrary()), preferences)
        runCurrent(); return vm
    }

    @Test fun serverGatesSkipDobAndRulesAfterReinstallOrSignOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply { token = jwt("user-a") }
            val api = FakeAccountApi(session).apply { serverGates = GatesResult("2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z") }
            // Reinstall: no device record at all.
            val preferences = FakeDeviceStore().apply { savedGates = Gates() }
            val vm = boot(session, api, preferences)
            assertEquals(BootRoute.Connect, vm.state.value.route)
            assertTrue(api.gatePosts.isEmpty())
            // The server answer is cached for this account only.
            assertEquals(Gates(true, true), preferences.gates("user-a"))
            assertEquals(Gates(), preferences.gates("user-b"))
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun sameAccountDeviceGatesArePushedUpOnceThenServerIsTruth() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply { token = jwt("user-a") }
            val api = FakeAccountApi(session)
            val preferences = FakeDeviceStore().apply { savedGates = Gates(true, true); gatesOwner = "user-a" }
            val vm = boot(session, api, preferences)
            assertEquals(BootRoute.Connect, vm.state.value.route)
            assertEquals(1, api.gatePosts.size)
            assertEquals(setOf("ageVerified", "termsAccepted"), api.gatePosts.single().keys)
            vm.refresh(); runCurrent(); vm.refresh(); runCurrent()
            assertEquals(1, api.gatePosts.size)
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun olderBackendKeepsTheCachedAnswerAndRetriesOnlyOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply { token = jwt("user-a") }
            val api = FakeAccountApi(session).apply { gatesFail = true }
            val preferences = FakeDeviceStore().apply { savedGates = Gates(true, true); gatesOwner = "user-a" }
            val vm = boot(session, api, preferences)
            assertEquals(BootRoute.Connect, vm.state.value.route)
            vm.refresh(); runCurrent()
            assertEquals(1, api.gatePosts.size)
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun anotherAccountsDeviceGatesNeverCountOrUpload() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession().apply { token = jwt("user-b") }
            val api = FakeAccountApi(session)
            val preferences = FakeDeviceStore().apply { savedGates = Gates(true, true); gatesOwner = "user-a" }
            val vm = boot(session, api, preferences)
            assertEquals(BootRoute.Age, vm.state.value.route)
            assertTrue(api.gatePosts.isEmpty())
            vm.verifyAge(LocalDate.now().minusYears(20)); runCurrent()
            assertEquals(BootRoute.Consent, vm.state.value.route)
            assertEquals(setOf("ageVerified"), api.gatePosts.single().keys)
            // Switching owner on the device dropped user-a's Terms answer.
            assertFalse(preferences.gates("user-b").termsAccepted)
            vm.acceptTerms(); runCurrent()
            assertEquals(BootRoute.Connect, vm.state.value.route)
            assertNotNull(api.serverGates.termsAcceptedAt)
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun endedConnectionRoutesToSaveScreenAndAcknowledgeGoesHome() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = FakeSession()
            val api = FakeAccountApi(session).apply { current = connection("ended", false).copy(endedAt = "2026-10-19T00:00:00Z", purgeAt = "2026-10-26T00:00:00Z") }
            val vm = boot(session, api, FakeDeviceStore())
            assertEquals(BootRoute.PartnerLeft, vm.state.value.route)
            assertEquals("connection", vm.state.value.ended?.id)
            // A pending request outranks the ended one; it is remembered so Accept can warn.
            api.current = connection("pending", false)
            vm.refresh(); runCurrent()
            assertEquals(BootRoute.Request, vm.state.value.route)
            assertNotNull(vm.state.value.ended)
            api.current = connection("ended", false)
            vm.refresh(); runCurrent()
            vm.acknowledgeEnd(); runCurrent()
            assertEquals(listOf("connection"), api.acknowledged)
            assertEquals(BootRoute.Connect, vm.state.value.route)
            assertNull(vm.state.value.ended)
            vm.viewModelScope.cancel(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }
}

internal fun testStickerLibrary() = StickerLibrary(File(System.getProperty("java.io.tmpdir"), "stickers-test-" + UUID.randomUUID()), Json)
