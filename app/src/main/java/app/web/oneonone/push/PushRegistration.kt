package app.web.oneonone.push

import android.content.Context
import androidx.work.*
import app.web.oneonone.data.auth.AccountSession
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRegistration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AccountSession,
    private val scope: CoroutineScope,
    private val notifications: MessageNotifications,
) {
    private val mutableProblem = MutableStateFlow<String?>(null)
    val problem = mutableProblem.asStateFlow()
    fun problem(value: String?) { mutableProblem.value = value }
    private var started = false
    fun start() {
        if (started) return
        started = true
        scope.launch {
            auth.signedIn.distinctUntilChanged().collect { signedIn ->
                if (signedIn) schedule()
                else {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                    notifications.cancelMessages()
                    if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseMessaging.getInstance().isAutoInitEnabled = false
                }
            }
        }
    }
    fun schedule(token: String? = null) {
        if (FirebaseApp.getApps(context).isEmpty()) { problem("Firebase is not configured in this build."); return }
        if (token == null) FirebaseMessaging.getInstance().isAutoInitEnabled = true
        val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("token" to token)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
    companion object { const val WORK_NAME = "push-token-registration" }
}
