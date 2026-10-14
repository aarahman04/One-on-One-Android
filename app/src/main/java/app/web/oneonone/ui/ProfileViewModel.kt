package app.web.oneonone.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.web.oneonone.data.api.AvatarUrls
import app.web.oneonone.data.api.ProfileApi
import app.web.oneonone.ui.chat.downscaledJpeg
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Your own profile photo and the signed URLs of yours and your partner's (contract §9). */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ProfileApi,
) : ViewModel() {
    private val mutableUrls = MutableStateFlow(AvatarUrls())
    val urls = mutableUrls.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    /** Refetch the signed URLs (call when an avatar id changes; they last an hour). Failure keeps the initials. */
    fun load() { viewModelScope.launch { runCatching { api.avatars() }.onSuccess { mutableUrls.value = it } } }

    fun upload(uri: Uri, onDone: () -> Unit) = action {
        val bytes = withContext(Dispatchers.IO) { downscaledJpeg(context, uri) }
        api.putAvatar(bytes.toRequestBody("image/jpeg".toMediaType()))
        onDone()
    }

    fun remove(onDone: () -> Unit) = action { api.deleteAvatar(); onDone() }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        if (mutableBusy.value) return@launch
        mutableBusy.value = true; mutableError.value = null
        try { block(); mutableUrls.value = api.avatars() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableError.value = userError(failure) }
        finally { mutableBusy.value = false }
    }
}
