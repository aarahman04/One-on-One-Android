package app.web.oneonone.data.chat

import androidx.room.*
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.data.model.ReactionSummary
import app.web.oneonone.data.model.ReactionUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

@Entity(tableName = "messages", primaryKeys = ["ownerId", "connectionId", "key"])
data class MessageEntity(
    val ownerId: String,
    val connectionId: String,
    val key: String,
    val tempId: String?,
    val createdAt: String,
    val body: String,
    val status: String,
    val firstAttemptAt: Long = 0,
    val error: String? = null,
)
@Entity(tableName = "sync_positions", primaryKeys = ["ownerId", "connectionId"])
data class SyncPosition(val ownerId: String, val connectionId: String, val watermark: String)

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages WHERE ownerId = :owner AND connectionId = :connection ORDER BY createdAt, `key`")
    fun observe(owner: String, connection: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE ownerId = :owner AND connectionId = :connection AND `key` = :key")
    suspend fun get(owner: String, connection: String, key: String): MessageEntity?
    @Query("SELECT * FROM messages WHERE ownerId = :owner AND connectionId = :connection AND tempId = :tempId LIMIT 1")
    suspend fun byTempId(owner: String, connection: String, tempId: String): MessageEntity?
    @Query("SELECT * FROM messages WHERE ownerId = :owner AND connectionId = :connection AND status != 'sent' ORDER BY createdAt, `key`")
    suspend fun pending(owner: String, connection: String): List<MessageEntity>
    @Upsert suspend fun put(message: MessageEntity)
    @Query("DELETE FROM messages WHERE ownerId = :owner AND connectionId = :connection AND `key` = :key")
    suspend fun delete(owner: String, connection: String, key: String)
    @Query("DELETE FROM messages WHERE ownerId = :owner AND connectionId = :connection")
    suspend fun clearMessages(owner: String, connection: String)
    @Query("DELETE FROM sync_positions WHERE ownerId = :owner AND connectionId = :connection")
    suspend fun clearPosition(owner: String, connection: String)
    @Query("SELECT watermark FROM sync_positions WHERE ownerId = :owner AND connectionId = :connection")
    suspend fun watermark(owner: String, connection: String): String?
    @Upsert suspend fun position(position: SyncPosition)
    @Query("SELECT MIN(createdAt) FROM messages WHERE ownerId = :owner AND connectionId = :connection AND status = 'sent'")
    suspend fun oldest(owner: String, connection: String): String?
}

@Database(entities = [MessageEntity::class, SyncPosition::class], version = 1, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() { abstract fun messages(): ChatDao }

interface MessageStore {
    fun observe(owner: String, connection: String): Flow<List<ChatMessage>>
    suspend fun enqueue(owner: String, connection: String, message: ChatMessage)
    suspend fun pending(owner: String, connection: String): List<MessageEntity>
    suspend fun attempt(entity: MessageEntity, now: Long)
    suspend fun fail(entity: MessageEntity, status: String, error: String)
    suspend fun reconcile(owner: String, connection: String, message: ChatMessage)
    suspend fun reaction(owner: String, connection: String, update: ReactionUpdate)
    suspend fun watermark(owner: String, connection: String): String?
    suspend fun position(owner: String, connection: String, at: String)
    suspend fun oldest(owner: String, connection: String): String?
    suspend fun clear(owner: String, connection: String)
    suspend fun byTempId(owner: String, connection: String, tempId: String): ChatMessage?
}

internal fun resolvedPendingKey(owner: String, message: ChatMessage): String? =
    message.tempId?.takeIf { message.senderId == owner }?.let { "pending:$it" }

internal fun reconcileStored(existing: ChatMessage?, message: ChatMessage): ChatMessage =
    (if (message.tempId != null && existing != null) message.copy(reactions = existing.reactions) else message)
        .copy(tempId = message.tempId ?: existing?.tempId, deliveryState = "sent", error = null)

private fun sortableTime(value: String): String = DateTimeFormatterBuilder().appendInstant(9)
    .toFormatter().format(Instant.parse(value))

internal fun updateReactions(reactions: List<ReactionSummary>, update: ReactionUpdate): List<ReactionSummary> {
    val cleared = reactions.map { summary -> summary.copy(userIds = summary.userIds.filterNot {
        it == update.userId && (update.op == "add" || summary.emoji == update.emoji)
    }) }.filter { it.userIds.isNotEmpty() }
    if (update.op != "add") return cleared
    val existing = cleared.find { it.emoji == update.emoji }
    return cleared.filterNot { it.emoji == update.emoji } +
        ReactionSummary(update.emoji, (existing?.userIds.orEmpty() + update.userId).distinct())
}

@Singleton
class RoomMessageStore @Inject constructor(private val db: ChatDatabase, private val json: Json) : MessageStore {
    private val dao = db.messages()
    override fun observe(owner: String, connection: String) = dao.observe(owner, connection).map { rows ->
        // ponytail: decoding loaded rows is O(n); use Room/Paging projections if chats reach tens of thousands.
        rows.map { json.decodeFromString<ChatMessage>(it.body).copy(deliveryState = it.status, error = it.error) }
    }
    override suspend fun enqueue(owner: String, connection: String, message: ChatMessage) {
        dao.put(MessageEntity(owner, connection, "pending:${message.tempId}", message.tempId,
            sortableTime(message.createdAt), json.encodeToString(message), "queued"))
    }
    override suspend fun pending(owner: String, connection: String) = dao.pending(owner, connection)
    override suspend fun attempt(entity: MessageEntity, now: Long) = db.withTransaction {
        dao.get(entity.ownerId, entity.connectionId, entity.key)?.let {
            dao.put(it.copy(status = "sending", firstAttemptAt = it.firstAttemptAt.takeIf { at -> at != 0L } ?: now, error = null))
        }
        Unit
    }
    override suspend fun fail(entity: MessageEntity, status: String, error: String) = db.withTransaction {
        dao.get(entity.ownerId, entity.connectionId, entity.key)?.let { dao.put(it.copy(status = status, error = error)) }
        Unit
    }
    override suspend fun reconcile(owner: String, connection: String, message: ChatMessage) = db.withTransaction {
        val id = checkNotNull(message.id) { "Missing server message ID." }
        resolvedPendingKey(owner, message)?.let { dao.delete(owner, connection, it) }
        val existing = dao.get(owner, connection, id)?.let { json.decodeFromString<ChatMessage>(it.body) }
        val canonical = reconcileStored(existing, message)
        dao.put(MessageEntity(owner, connection, id, canonical.tempId, sortableTime(message.createdAt),
            json.encodeToString(canonical.copy(deliveryState = "sent", error = null)), "sent"))
    }
    override suspend fun reaction(owner: String, connection: String, update: ReactionUpdate) = db.withTransaction {
        dao.get(owner, connection, update.messageId)?.let {
            val message = json.decodeFromString<ChatMessage>(it.body)
            dao.put(it.copy(body = json.encodeToString(message.copy(reactions = updateReactions(message.reactions, update)))))
        }
        Unit
    }
    override suspend fun watermark(owner: String, connection: String) = dao.watermark(owner, connection)
    override suspend fun position(owner: String, connection: String, at: String) = dao.position(SyncPosition(owner, connection, at))
    override suspend fun oldest(owner: String, connection: String) = dao.oldest(owner, connection)
    override suspend fun clear(owner: String, connection: String) = db.withTransaction {
        dao.clearMessages(owner, connection); dao.clearPosition(owner, connection)
    }
    override suspend fun byTempId(owner: String, connection: String, tempId: String): ChatMessage? =
        dao.byTempId(owner, connection, tempId)?.let {
            json.decodeFromString<ChatMessage>(it.body).copy(deliveryState = it.status, error = it.error)
        }
}
