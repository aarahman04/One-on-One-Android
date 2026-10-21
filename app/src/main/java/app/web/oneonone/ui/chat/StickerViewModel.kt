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
    val progress: String? = null,
    val manual: Bitmap? = null,
)

internal data class StickerBrushSession(val base: Bitmap, val original: Bitmap, val restore: Boolean,
    val strokes: List<StickerBrushStroke> = emptyList(), val redo: List<StickerBrushStroke> = emptyList(),
    val size: Float = 32f, val distance: Float = 64f, val token: Any = Any())

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
    private var brushJob: Job? = null
    private var generation = 0
    private val mutableBrush = MutableStateFlow<StickerBrushSession?>(null)
    internal val brush = mutableBrush.asStateFlow()

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
        val ticket = ++generation
        mutableDraft.value = StickerDraft(processing = true)
        processing = viewModelScope.launch {
            try {
                val original = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    decodeStickerPhoto(context, uri)
                }
                checkOwner(owner)
                if (ticket == generation) mutableDraft.value = StickerDraft(original = original)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == generation) mutableDraft.value = StickerDraft(notice = "Couldn't open that photo. Choose another from your gallery.") }
        }
    }

    fun removeBackground() {
        val original = mutableDraft.value.original ?: return
        val owner = service.active.value?.ownerId ?: return
        if (mutableDraft.value.processing || mutableBusy.value || mutableBrush.value != null) return
        val ticket = ++generation
        saved["stickerCutOut"] = true
        mutableDraft.value = mutableDraft.value.copy(processing = true, notice = null, progress = null)
        processing = viewModelScope.launch {
            try {
                checkOwner(owner)
                val cut = try { cutOutSticker(context, original) { progress ->
                    withContext(Dispatchers.Main.immediate) {
                        if (ticket == generation) mutableDraft.value = mutableDraft.value.copy(progress = progress)
                    }
                } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        android.util.Log.w("StickerCutout", "Couldn't remove photo background.", failure)
                        checkOwner(owner)
                        if (ticket == generation) mutableDraft.value = mutableDraft.value.copy(processing = false, progress = null,
                            notice = (if (failure is IllegalStateException) failure.message else null)
                                ?: "Automatic removal failed. You can use Erase and Restore instead.")
                        return@launch
                    }
                checkOwner(owner)
                if (ticket == generation) mutableDraft.value = StickerDraft(original, cut,
                    notice = if (cut == null) "No subject found. You can use Erase and Restore instead." else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == generation) mutableDraft.value = mutableDraft.value.copy(processing = false, progress = null,
                notice = "Automatic removal failed. You can use Erase and Restore instead.") }
        }
    }

    fun original(keepOriginal: Boolean) { saved["stickerCutOut"] = !keepOriginal }
    fun crop(value: StickerCrop) { saved["stickerCrop"] = value }
    // The cropped part has new proportions, so its placement in the square starts over.
    fun trim(value: StickerTrim) { saved["stickerTrim"] = boundedStickerTrim(value); saved["stickerCrop"] = StickerCrop() }

    internal fun beginBrush(restore: Boolean) {
        val draft = mutableDraft.value
        if (draft.processing || mutableBusy.value) return
        val original = draft.original ?: return
        mutableBrush.value = mutableBrush.value?.copy(restore = restore)
            ?: StickerBrushSession(draft.manual ?: draft.cutOut ?: original, original, restore)
    }
    internal fun brushSize(size: Float) { mutableBrush.value = mutableBrush.value?.copy(size = size.coerceIn(8f, 80f)) }
    internal fun brushDistance(distance: Float) { mutableBrush.value = mutableBrush.value?.copy(distance = distance.coerceIn(0f, 120f)) }
    internal fun stroke(session: StickerBrushSession, stroke: StickerBrushStroke) {
        val brush = mutableBrush.value ?: return
        if (brush.token === session.token && !mutableBusy.value && brush.strokes.size < STICKER_BRUSH_STROKES)
            mutableBrush.value = brush.copy(strokes = brush.strokes + stroke, redo = emptyList())
    }
    internal fun undoBrush() { mutableBrush.value?.let { if (it.strokes.isNotEmpty())
        mutableBrush.value = it.copy(strokes = it.strokes.dropLast(1), redo = it.redo + it.strokes.last()) } }
    internal fun redoBrush() { mutableBrush.value?.let { if (it.redo.isNotEmpty())
        mutableBrush.value = it.copy(strokes = it.strokes + it.redo.last(), redo = it.redo.dropLast(1)) } }
    internal fun cancelBrush() { if (!mutableBusy.value) mutableBrush.value = null }
    internal fun resetEdits() {
        if (mutableBusy.value || mutableDraft.value.processing) return
        mutableBrush.value?.let { brush ->
            mutableBrush.value = brush.copy(base = mutableDraft.value.cutOut ?: brush.original,
                strokes = emptyList(), redo = emptyList(), token = Any())
            return
        }
        mutableDraft.value = mutableDraft.value.copy(manual = null)
        saved["stickerCrop"] = StickerCrop(); saved["stickerTrim"] = StickerTrim()
    }
    internal fun finishBrush() {
        val brush = mutableBrush.value ?: return
        val owner = service.active.value?.ownerId ?: return
        if (mutableBusy.value) return
        if (brush.strokes.isEmpty()) {
            mutableDraft.value = mutableDraft.value.copy(manual = brush.base.takeIf { it !== mutableDraft.value.cutOut && it !== brush.original })
            mutableBrush.value = null; return
        }
        val ticket = generation
        brushJob = viewModelScope.launch {
            mutableBusy.value = true; mutableError.value = null
            var rendered: Bitmap? = null
            try {
                checkOwner(owner)
                withContext(Dispatchers.Default) { rendered = brushSticker(brush.base, brush.original, brush.strokes) }
                checkOwner(owner)
                if (ticket != generation || mutableBrush.value !== brush) return@launch
                mutableDraft.value = mutableDraft.value.copy(manual = rendered)
                rendered = null; mutableBrush.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableError.value = userError(failure) }
            finally { rendered?.recycle(); mutableBusy.value = false }
        }
    }

    fun closeMaker() {
        generation++; brushJob?.cancel(); brushJob = null; mutableBrush.value = null
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
        if (mutableBrush.value != null) return
        val source = (if (cutOut.value) draft.manual ?: draft.cutOut ?: draft.original else draft.original) ?: return
        val crop = if (cutOut.value) crop.value else StickerCrop()
        val trim = if (cutOut.value) trim.value else StickerTrim()
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
