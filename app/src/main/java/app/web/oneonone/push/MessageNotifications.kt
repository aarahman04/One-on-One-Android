package app.web.oneonone.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import app.web.oneonone.MainActivity
import app.web.oneonone.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun allowed(): Boolean = manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    @Synchronized fun show(push: MessagePush, ownerAuthId: String) {
        if (!allowed()) return
        val previous = manager.activeNotifications.find { it.tag == tag(push.connectionId) && it.id == MESSAGE_ID }?.notification
        val style = previous?.let(NotificationCompat.MessagingStyle::extractMessagingStyleFromNotification)
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
        // Lines the user already swiped away or read never come back with the next message.
        val cleared = clearedAt(context, push.connectionId)
        style.messages.removeAll { it.timestamp <= cleared }
        style.setConversationTitle(push.senderName.ifBlank { "Your One on One" }).setGroupConversation(false)
        style.addMessage(push.preview, System.currentTimeMillis(), Person.Builder()
            .setName(push.senderName.ifBlank { "Your One on One" }).build())
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setData("oneonone://message/${push.connectionId}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reply = NotificationCompat.Action.Builder(0, "Reply", action(push.connectionId, ownerAuthId, "reply", mutable = true))
            .addRemoteInput(RemoteInput.Builder(REPLY_KEY).setLabel("Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).build()
        val read = NotificationCompat.Action.Builder(0, "Mark as read", action(push.connectionId, ownerAuthId, "read", mutable = false))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()
        val notification = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notify).setStyle(style).setContentIntent(tap)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setOnlyAlertOnce(false).setGroup("conversation:${push.connectionId}")
            .setDeleteIntent(action(push.connectionId, ownerAuthId, "dismiss", mutable = false))
            .addAction(reply).addAction(read).build()
        manager.notify(tag(push.connectionId), MESSAGE_ID, notification)
    }

    private fun action(connection: String, owner: String, action: String, mutable: Boolean): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action).setData("oneonone://action/$connection/$action".toUri())
            .putExtra("connectionId", connection).putExtra("ownerAuthId", owner)
        val mutability = if (mutable) {
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        } else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
    }

    @Synchronized fun replied(connectionId: String, content: String) {
        val previous = manager.activeNotifications.find { it.tag == tag(connectionId) && it.id == MESSAGE_ID }?.notification ?: return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(previous) ?: return
        style.addMessage(content, System.currentTimeMillis(), Person.Builder().setName("You").build())
        manager.notify(tag(connectionId), MESSAGE_ID, NotificationCompat.Builder(context, previous)
            .setStyle(style).setOnlyAlertOnce(true).setRemoteInputHistory(arrayOf(content)).build())
    }
    fun cancel(connectionId: String) = clear(context, connectionId)
    fun cancelMessages() { manager.activeNotifications.filter { it.notification.channelId == NotificationChannels.MESSAGES }
        .forEach { manager.cancel(it.tag, it.id) } }
    /** Same tag as the conversation, so it replaces the message lines; tapping opens the app, which routes to the save screen. */
    fun left(push: LeftPush) {
        if (!allowed()) return
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setData("oneonone://left/${push.connectionId}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(tag(push.connectionId), MESSAGE_ID, NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(push.title).setContentText(push.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(push.body)).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(tap).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
    /** Opens the chat, where the invite banner is; gone with the invite after 30 s. */
    fun gameInvite(push: GameInvitePush, nickname: String) {
        if (!allowed()) return
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setData("oneonone://message/${push.connectionId}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify("game:${push.connectionId}", MESSAGE_ID, NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle("$nickname wants to play ${push.game.title}")
            .setContentText("Open the chat to accept.").setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setTimeoutAfter(30_000).setContentIntent(tap).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
    fun generic(title: String, body: String) {
        if (!allowed()) return
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify("message:notice", MESSAGE_ID, NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(title).setContentText(body)
            .setContentIntent(tap).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
    companion object {
        const val MESSAGE_ID = 4101; const val REPLY_KEY = "message_reply"
        private fun tag(connectionId: String) = "message:$connectionId"
        private fun cleared(context: Context) = context.getSharedPreferences("message_notifications", Context.MODE_PRIVATE)
        internal fun clearedAt(context: Context, connectionId: String) = cleared(context).getLong(connectionId, 0L)
        /** Marks everything shown so far as seen (swiped away, read, or the chat was opened). */
        internal fun markCleared(context: Context, connectionId: String) {
            cleared(context).edit { putLong(connectionId, System.currentTimeMillis()) }
        }
        fun clear(context: Context, connectionId: String) {
            markCleared(context, connectionId)
            context.getSystemService(NotificationManager::class.java).cancel(tag(connectionId), MESSAGE_ID)
        }
    }
}
