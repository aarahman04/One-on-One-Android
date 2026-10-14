package app.web.oneonone.ui.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.AccountMutationLock
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.stickers.*
import app.web.oneonone.ui.userError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class StickerDraft(
    val original: Bitmap? = null,
    val cutOut: Bitmap? = null,
    val processing: Boolean = false,
    val notice: String? = null,
)

@HiltViewModel
class StickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: MessageService,
    private val auth: AccountSession,
    private val mutations: AccountMutationLock,
    private val library: StickerLibrary,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableEntries = MutableStateFlow(emptyList<StickerEntry>())
    val entries = mutableEntries.asStateFlow()
    private val mutableDraft = MutableStateFlow(StickerDraft())
    val draft = mutableDraft.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val makerOpen = saved.getStateFlow("stickerMaker", false)
    val cutOut = saved.getStateFlow("stickerCutOut", true)
    val crop = saved.getStateFlow("stickerCrop", StickerCrop())
    val trim = saved.getStateFlow("stickerTrim", StickerTrim())
    private var processing: Job? = null

    init {
        viewModelScope.launch {
            var hadSession = false
            service.active.collectLatest { session ->
                if (session == null && !hadSession) return@collectLatest
                if (session != null) hadSession = true
                val owner = session?.ownerId
                mutableEntries.value = emptyList()
                if (saved.get<String>("stickerOwner") != owner) { closeMaker(); saved["stickerOwner"] = owner }
                if (owner == null) return@collectLatest
                try {
                    mutations.mutex.withLock {
                        checkOwner(owner)
                        mutableEntries.value = library.load(owner)
                    }
                    saved.get<String>("stickerSource")?.let { process(it.toUri(), owner) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableError.value = "Couldn't open your sticker library. Try again." }
            }
        }
    }

    private suspend fun checkOwner(owner: String) {
        check(auth.signedIn.first() && service.active.value?.ownerId == owner) { "Account changed." }
    }

    fun file(owner: String, id: String) = library.file(owner, id)
    fun clearError() { mutableError.value = null }

    fun pick(uri: Uri) {
        val owner = service.active.value?.ownerId ?: return
        closeMaker()
        saved["stickerOwner"] = owner; saved["stickerSource"] = uri.toString(); saved["stickerMaker"] = true
        process(uri, owner)
    }

    private fun process(uri: Uri, owner: String) {
        processing?.cancel()
        mutableDraft.value = StickerDraft(processing = true)
        processing = viewModelScope.launch {
            try {
                val original = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    decodeStickerPhoto(context, uri)
                }
                checkOwner(owner)
                mutableDraft.value = StickerDraft(original = original, processing = true)
                val cut = try { withContext(Dispatchers.Default) { cutOutSticker(context, original) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        checkOwner(owner)
                        saved["stickerCutOut"] = false
                        mutableDraft.value = StickerDraft(original, notice = "Couldn't make a cut-out. You can keep Original.")
                        return@launch
                    }
                checkOwner(owner)
                if (cut == null) saved["stickerCutOut"] = false
                mutableDraft.value = StickerDraft(original, cut, notice = if (cut == null) "No subject found. You can keep Original." else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableDraft.value = StickerDraft(notice = "Couldn't open that photo. Choose another from your gallery.") }
        }
    }

    fun original(keepOriginal: Boolean) { saved["stickerCutOut"] = !keepOriginal }
    fun crop(value: StickerCrop) { saved["stickerCrop"] = value }
    // The cropped part has new proportions, so its placement in the square starts over.
    fun trim(value: StickerTrim) { saved["stickerTrim"] = boundedStickerTrim(value); saved["stickerCrop"] = StickerCrop() }

    fun closeMaker() {
        processing?.cancel(); processing = null
        saved["stickerMaker"] = false; saved["stickerSource"] = null
        saved["stickerCrop"] = StickerCrop(); saved["stickerTrim"] = StickerTrim(); saved["stickerCutOut"] = true
        mutableDraft.value = StickerDraft(); mutableError.value = null
    }

    private fun edit(block: suspend (String) -> List<StickerEntry>) = viewModelScope.launch {
        if (mutableBusy.value) return@launch
        val owner = service.active.value?.ownerId ?: return@launch
        mutableBusy.value = true; mutableError.value = null
        try {
            mutations.mutex.withLock {
                checkOwner(owner)
                mutableEntries.value = block(owner)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableError.value = userError(failure) }
        finally { mutableBusy.value = false }
    }

    fun save() {
        val draft = mutableDraft.value
        if (draft.processing || mutableBusy.value) return
        val source = (if (cutOut.value) draft.cutOut else draft.original) ?: return
        val crop = crop.value
        val trim = trim.value
        edit { owner ->
            val bytes = withContext(Dispatchers.Default) {
                val trimmed = trimSticker(source, trim)
                try {
                    val rendered = renderSticker(trimmed, crop)
                    try { stickerWebP(rendered) } finally { rendered.recycle() }
                } finally { if (trimmed !== source) trimmed.recycle() }
            }
            library.save(owner, bytes).also { closeMaker() }
        }
    }

    fun favourite(id: String) = edit { library.favourite(it, id) }
    fun delete(id: String) = edit { library.delete(it, id) }
    fun used(id: String) = edit { library.used(it, id) }
}
