package app.web.oneonone.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.web.oneonone.call.CallKind
import app.web.oneonone.call.CallLauncher
import app.web.oneonone.data.api.AccountApi
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.api.NicknameBody
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.ui.userError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** From the Android share sheet: text goes to the draft, an image or file ([kind] image|file) to the attachment preview. */
data class SharedContent(val text: String?, val uri: String?, val kind: String?)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val service: MessageService,
    private val account: AccountApi,
    private val calls: CallLauncher,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val messages = service.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val connectionState = service.connectionState
    val receipts = service.receipts
    val presence = service.presence
    val ended = service.ended
    val draft = saved.getStateFlow("draft", "")
    val replyTo = saved.getStateFlow<String?>("replyTo", null)
    /** Id of my message being edited in the composer, or null. */
    val editing = saved.getStateFlow<String?>("editing", null)
    val otherTyping = service.otherTyping
    val pin = service.pin
    private val mutableError = MutableStateFlow<String?>(null)
    val error = combine(mutableError, service.error) { local, network -> local ?: network }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    /** Content another app shared into One on One, waiting for the chat to pick it up. */
    private val mutableShared = MutableStateFlow<SharedContent?>(null)
    val shared = mutableShared.asStateFlow()
    fun share(content: SharedContent) { mutableShared.value = content }
    fun consumeShared() { mutableShared.value = null }
    private val mutableHasOlder = MutableStateFlow(true)
    val hasOlder = mutableHasOlder.asStateFlow()

    fun open(connection: CurrentConnection) = action(showBusy = false) {
        val draftOwner = "${connection.myUserId}/${connection.id}"
        if (saved.get<String>("draftOwner") != draftOwner) {
            saved["draft"] = ""
            saved["replyTo"] = null
            saved["editing"] = null
            saved["draftOwner"] = draftOwner
            mutableHasOlder.value = true
        }
        service.activate(connection)
    }
    fun close(clear: Boolean) = action(showBusy = false) { service.deactivate(clear) }
    fun visible(resumed: Boolean) = service.visible(resumed)
    private var typingSentAt = 0L
    fun draft(value: String) {
        saved["draft"] = value.take(4_000)
        // Typing signal: at most every 3 s while there's text; "stopped" once when it empties.
        val now = System.currentTimeMillis()
        if (value.isNotBlank() && editing.value == null) {
            if (now - typingSentAt > 3_000) { typingSentAt = now; service.typing(true) }
        } else if (typingSentAt != 0L) { typingSentAt = 0L; service.typing(false) }
    }
    fun startEdit(id: String, content: String) { saved["replyTo"] = null; saved["editing"] = id; draft(content) }
    fun cancelEdit() { saved["editing"] = null; draft("") }
    fun delete(ids: List<String>, unsent: List<String> = emptyList()) = action {
        unsent.forEach { service.discardUnsent(it) }
        ids.forEach { service.delete(it) }
    }
    fun pin(id: String?) = action { service.pin(id) }
    fun syncPin(connection: CurrentConnection) = service.syncPin(connection)
    suspend fun linkPreview(url: String) = service.linkPreview(url)
    fun reply(id: String?) { saved["replyTo"] = id }
    fun send() = action {
        val content = draft.value
        editing.value?.let { id ->
            service.edit(id, content)
            saved["editing"] = null
            if (draft.value == content) saved["draft"] = ""
            return@action
        }
        if (typingSentAt != 0L) { typingSentAt = 0L; service.typing(false) }
        service.send(content, replyTo = replyTo.value)
        if (draft.value == content) saved["draft"] = ""
        saved["replyTo"] = null
    }
    // Not busy-gated: an alarm raise/ack must never be dropped because a text send is still in flight.
    // The outbox serialises sends and the server dedupes acks, so overlapping calls are safe.
    fun sendCard(type: String, payload: JsonObject, replyTo: String?) = action(showBusy = false) {
        service.send("", type, payload, replyTo)
    }
    fun retry(tempId: String, confirmed: Boolean = false) = action { service.retry(tempId, confirmed) }
    fun resync() = action { service.resync() }
    fun older() = action { mutableHasOlder.value = service.older() }
    fun react(id: String, emoji: String, remove: Boolean) = action { service.react(id, emoji, remove) }
    fun call(kind: CallKind) = calls.start(kind)
    fun nickname(id: String, nickname: String, onSaved: () -> Unit) = action {
        val value = nickname.trim()
        require(value.length in 1..40) { "Nickname must be 1–40 characters." }
        account.nickname(id, NicknameBody(value)); onSaved()
    }
    fun leave(id: String, leaveAction: String, onDone: () -> Unit) = action {
        when (leaveAction) {
            "advance" -> account.leave(id)
            "cancel" -> account.cancelLeave(id)
            "end" -> account.confirmEnd(id)
            else -> error("Unsupported leave action")
        }
        onDone()
    }
    private fun action(showBusy: Boolean = true, block: suspend () -> Unit) = viewModelScope.launch {
        if (showBusy && mutableBusy.value) return@launch
        if (showBusy) mutableBusy.value = true
        mutableError.value = null
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutableError.value = userError(error) }
        finally { if (showBusy) mutableBusy.value = false }
    }
}
