package app.web.oneonone.push

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.*
import app.web.oneonone.data.DeviceStore
import app.web.oneonone.data.AccountMutationLock
import app.web.oneonone.data.api.AccountApi
import app.web.oneonone.data.api.PushTokenBody
import app.web.oneonone.data.api.TokenBody
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.auth.tokenSubject
import app.web.oneonone.data.chat.MessageService
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

@EntryPoint @InstallIn(SingletonComponent::class)
interface PushWorkDependencies {
    fun api(): AccountApi
    fun auth(): AccountSession
    fun preferences(): DeviceStore
    fun messages(): MessageService
    fun notifications(): MessageNotifications
    fun registration(): PushRegistration
    fun json(): Json
    fun mutations(): AccountMutationLock
}
private fun dependencies(context: Context) = EntryPointAccessors.fromApplication(context, PushWorkDependencies::class.java)

class PushRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @Suppress("DEPRECATION") // Keep the registration-token API required by the authoritative backend contract.
    override suspend fun doWork(): Result {
        val dependencies = dependencies(applicationContext)
        return try {
            val owner = dependencies.auth().accessToken()?.let(::tokenSubject) ?: return Result.success()
            val token = inputData.getString("token") ?: withTimeoutOrNull(20_000) {
                val result = CompletableDeferred<String>()
                FirebaseMessaging.getInstance().token.addOnSuccessListener { result.complete(it) }
                    .addOnFailureListener { result.completeExceptionally(it) }
                result.await()
            } ?: throw java.io.IOException("FCM token unavailable.")
            if (registerPushToken(token, owner, dependencies.api(), dependencies.auth(), dependencies.preferences(), dependencies.mutations()))
                dependencies.registration().problem(null)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val message = (error as? HttpException)?.response()?.errorBody()?.string()
            dependencies.registration().problem(if (message?.contains("constraint", ignoreCase = true) == true)
                "Push registration rejected by a database constraint. Apply migration 035 for android-native tokens."
                else "Push token registration failed. Try again (${(error as? HttpException)?.code() ?: "network"}).")
            if (error is HttpException && error.code() in setOf(400, 401, 403)) Result.failure() else Result.retry()
        }
    }
}

class MessageNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = dependencies(applicationContext)
        return try {
            val owner = dependencies.auth().accessToken()?.let(::tokenSubject) ?: return Result.success()
            val push = inputData.getString("push")?.let { dependencies.json().decodeFromString<MessagePush>(it) }
            if (push == null) {
                dependencies.notifications().generic(inputData.getString("title").orEmpty(), inputData.getString("body").orEmpty())
                return Result.success()
            }
            val connection = dependencies.api().current().connection ?: return Result.success()
            if (!shouldNotify(push, connection, dependencies.messages().isChatResumed(push.connectionId))) return Result.success()
            if (!dependencies.notifications().allowed()) return Result.success()
            if (dependencies.auth().accessToken()?.let(::tokenSubject) != owner) return Result.success()
            if (dependencies.preferences().claimNotification(push.messageId) &&
                !dependencies.messages().isChatResumed(push.connectionId) &&
                dependencies.auth().accessToken()?.let(::tokenSubject) == owner) {
                dependencies.notifications().show(push, owner)
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (error is HttpException && error.code() in setOf(401, 403, 404, 409)) Result.success() else Result.retry()
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connection = intent.getStringExtra("connectionId")
        val owner = intent.getStringExtra("ownerAuthId")
        val action = intent.action
        if (!validUuid(connection) || !validUuid(owner) || action !in setOf("reply", "read")) return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(MessageNotifications.REPLY_KEY)?.toString()?.trim()
        if (action == "reply" && (text.isNullOrEmpty() || text.length > 4_000)) return
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<NotificationActionWorker>()
            .setInputData(workDataOf("connectionId" to connection, "ownerAuthId" to owner, "action" to action, "text" to text))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
}

class NotificationActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = dependencies(applicationContext)
        return try {
            val owner = dependencies.auth().accessToken()?.let(::tokenSubject) ?: return Result.success()
            val action = inputData.getString("action")
            val connection = dependencies.api().current().connection ?: return Result.success()
            if (connection.status !in setOf("active", "leave_pending")) return Result.success()
            if (action != "sync" && !acceptsNotificationAction(owner, inputData.getString("ownerAuthId"), connection, inputData.getString("connectionId"))) return Result.success()
            when (action) {
                "read" -> {
                    dependencies.messages().activate(connection)
                    dependencies.messages().markRead()
                    dependencies.notifications().cancel(connection.id)
                }
                "sync" -> { dependencies.messages().activate(connection); dependencies.messages().resync() }
                "reply" -> {
                    val content = inputData.getString("text")?.trim() ?: return Result.failure()
                    if (content.isEmpty() || content.length > 4_000) return Result.failure()
                    val messages = dependencies.messages()
                    messages.activate(connection)
                    messages.send(content, clientTempId = id.toString())
                    val sent = withTimeoutOrNull(25_000) {
                        messages.messages.first { list -> list.any { it.tempId == id.toString() &&
                            (it.id != null || it.deliveryState in setOf("failed", "unknown")) } }
                    }
                    val status = sent?.find { it.tempId == id.toString() }
                    if (status?.id != null) {
                        messages.markRead()
                        dependencies.notifications().replied(connection.id, content)
                    } else if (status?.deliveryState in setOf("failed", "unknown")) {
                        dependencies.notifications().replied(connection.id, "Reply needs review — open the conversation.")
                        return Result.failure()
                    } else return Result.retry()
                }
                else -> return Result.failure()
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (error is HttpException && error.code() in setOf(400, 401, 403, 404, 409)) Result.failure() else Result.retry()
        }
    }
}

internal suspend fun registerPushToken(
    token: String, owner: String, api: AccountApi, auth: AccountSession, preferences: DeviceStore, mutations: AccountMutationLock,
): Boolean = mutations.mutex.withLock {
    if (auth.accessToken()?.let(::tokenSubject) != owner) return@withLock false
    // Finish an in-flight registration before sign-out can unregister/drop the session.
    withContext(NonCancellable) {
        val previous = preferences.pushToken()
        if (previous != null && previous != token) api.unregister(TokenBody(previous))
        // Save the candidate before HTTP: even a lost response remains unregisterable.
        preferences.savePushToken(token)
        api.register(PushTokenBody(token, "android-native"))
    }
    true
}
