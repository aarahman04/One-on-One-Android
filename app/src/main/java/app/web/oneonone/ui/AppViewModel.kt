package app.web.oneonone.ui

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.AccountRepository
import app.web.oneonone.data.DeviceStore
import app.web.oneonone.data.Gates
import app.web.oneonone.data.api.BlockedUser
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.api.Me
import app.web.oneonone.data.auth.AccountSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

enum class BootRoute { Loading, SignIn, Age, UnderAge, Consent, Connect, Waiting, Request, Chat }
data class AppState(
    val route: BootRoute = BootRoute.Loading,
    val me: Me? = null,
    val connection: CurrentConnection? = null,
    val blocks: List<BlockedUser> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val needsNotificationOnboarding: Boolean = false,
)

internal fun bootRoute(signedIn: Boolean, gates: Gates, connection: CurrentConnection?): BootRoute = when {
    !signedIn -> BootRoute.SignIn
    !gates.ageVerified -> BootRoute.Age
    !gates.termsAccepted -> BootRoute.Consent
    connection == null -> BootRoute.Connect
    connection.status == "pending" -> if (connection.isRequester) BootRoute.Waiting else BootRoute.Request
    connection.status in setOf("active", "leave_pending") -> BootRoute.Chat
    else -> BootRoute.Connect
}

internal fun isAdult(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
    !date.isAfter(today) && Period.between(date, today).years >= 18

@HiltViewModel
class AppViewModel @Inject constructor(
    private val auth: AccountSession,
    private val account: AccountRepository,
    private val preferences: DeviceStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    val darkTheme = preferences.theme.map { it == "dark" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    private val operation = Mutex()
    private var poll: Job? = null

    init {
        viewModelScope.launch {
            // Await persisted-session restoration before treating initial state as signed out.
            try { auth.accessToken() } catch (error: Exception) {
                mutable.update { it.copy(error = userError(error)) }
            }
            auth.signedIn.distinctUntilChanged().collect { signedIn ->
                if (!signedIn) mutable.value = AppState(route = BootRoute.SignIn)
                else refresh()
            }
        }
    }

    fun foreground(resumed: Boolean) {
        poll?.cancel()
        poll = if (resumed) viewModelScope.launch {
            while (true) {
                refresh()
                delay(3_000)
            }
        } else null
    }

    fun refresh() = action(showBusy = false) { load() }
    private suspend fun load() {
        if (auth.accessToken() == null) {
            mutable.value = AppState(route = BootRoute.SignIn)
            return
        }
        val gates = preferences.gates()
        if (!gates.ageVerified || !gates.termsAccepted) {
            if (state.value.route != BootRoute.UnderAge) mutable.update {
                it.copy(route = bootRoute(true, gates, null))
            }
            return
        }
        val me = account.api.me()
        val current = account.api.current().connection
        val onboarding = !preferences.onboardingSeen()
        mutable.update { it.copy(route = bootRoute(true, gates, current), me = me, connection = current, error = null,
            needsNotificationOnboarding = onboarding) }
    }

    fun signIn(context: Context) = action { auth.signIn(context); load() }
    fun verifyAge(date: LocalDate) = action {
        if (isAdult(date)) { preferences.verifyAge(); load() }
        else mutable.update { it.copy(route = BootRoute.UnderAge) }
    }
    fun acceptTerms() = action { preferences.acceptTerms(); load() }
    fun request(code: String) = action { account.request(code); load() }
    fun regenerate() = action {
        val code = account.api.regenerate().connectionCode
        mutable.update { it.copy(me = it.me?.copy(connectionCode = code)) }
    }
    fun connectionAction(name: String) = action {
        val current = state.value.connection ?: return@action
        when (name) {
            "accept" -> account.api.accept(current.id)
            "decline" -> account.api.decline(current.id)
            "cancel" -> account.api.cancel(current.id)
            else -> error("Unsupported connection action")
        }
        load()
    }
    fun loadBlocks() = action { mutable.update { it.copy(blocks = account.api.blocks().blocks) } }
    fun unblock(id: String) = action {
        account.api.unblock(id)
        mutable.update { it.copy(blocks = account.api.blocks().blocks) }
    }
    fun signOut() = action { account.signOut() }
    fun deleteAccount() = action { account.deleteAccount() }
    fun onboardingShown() = action {
        preferences.markOnboardingSeen()
        mutable.update { it.copy(needsNotificationOnboarding = false) }
    }

    private fun action(showBusy: Boolean = true, block: suspend () -> Unit) = viewModelScope.launch {
        // Polls never pile up behind a user mutation or another poll.
        if (showBusy) operation.lock() else if (!operation.tryLock()) return@launch
        try {
            if (showBusy) mutable.update { it.copy(busy = true, error = null) }
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: GetCredentialCancellationException) {
            // Dismissing Google's picker is normal navigation.
        } catch (error: Exception) {
            mutable.update { it.copy(error = userError(error)) }
        } finally {
            mutable.update { it.copy(busy = false) }
            operation.unlock()
        }
    }
}

internal fun userError(error: Exception): String {
    if (error is HttpException) {
        val serverMessage = try {
            error.response()?.errorBody()?.string()?.let {
                Json.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content
            }
        } catch (_: Exception) { null }
        return serverMessage ?: "Request failed (${error.code()}). Try again."
    }
    return when (error) {
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Couldn't complete that action."
        else -> "Couldn't connect. Check your internet connection and try again."
    }
}
