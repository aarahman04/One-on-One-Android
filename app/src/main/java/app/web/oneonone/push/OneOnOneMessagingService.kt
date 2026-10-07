package app.web.oneonone.push

import android.os.Build
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class OneOnOneMessagingService : FirebaseMessagingService() {
    @Inject lateinit var router: PushRouter
    @Inject lateinit var registration: PushRegistration
    @Inject lateinit var json: Json
    // Backend uses registration tokens; the new FID registration API is a different protocol.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) { registration.schedule(token) }
    override fun onMessageReceived(message: RemoteMessage) {
        router.route(message.data) { push ->
            val request = OneTimeWorkRequestBuilder<MessageNotificationWorker>()
                .setInputData(workDataOf("push" to json.encodeToString(push)))
            // API 31+ expedited jobs avoid background delays without adding an A3 foreground service.
            if (Build.VERSION.SDK_INT >= 31) request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            WorkManager.getInstance(this).enqueueUniqueWork("notify:${push.messageId}", ExistingWorkPolicy.KEEP, request.build())
        }
        // The documented missed-call text fallback has notification title/body, but no chat/action IDs.
        if (message.data["type"] == null && message.notification != null) {
            WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<MessageNotificationWorker>()
                .setInputData(workDataOf("title" to message.notification?.title.orEmpty().take(40),
                    "body" to message.notification?.body.orEmpty().take(120))).build())
        }
    }
    override fun onDeletedMessages() {
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<NotificationActionWorker>()
            .setInputData(workDataOf("action" to "sync"))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
