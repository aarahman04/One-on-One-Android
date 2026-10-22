package app.web.oneonone.data.transport

import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import app.web.oneonone.data.realtime.RealtimeSocket
import app.web.oneonone.data.games.MatchCodec
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

interface ChatApi {
    @GET("api/connections/{id}/messages") suspend fun history(
        @Path("id") id: String, @Query("before") before: String?, @Query("after") after: String?,
    ): MessagePage
    @POST("api/connections/{id}/read") suspend fun read(@Path("id") id: String)
    @POST("api/connections/{id}/delivered") suspend fun delivered(@Path("id") id: String)
    @GET("api/link-preview") suspend fun linkPreview(@Query("url") url: String): LinkPreviewResult
}

@Singleton
class InternetTransport @Inject constructor(
    private val realtime: RealtimeSocket,
    private val api: ChatApi,
    private val json: Json,
) : Transport {
    override val state = realtime.state
    private fun objectOf(value: Any): JsonObject? = runCatching { Json.parseToJsonElement(value.toString()).jsonObject }.getOrNull()
    override val matchStates = realtime.events("match:state").mapNotNull(::objectOf)
    override val pongFrames = realtime.events("match:pong").mapNotNull(::objectOf)
    override val matchReactions = realtime.events("match:reaction").mapNotNull(::objectOf)
    override suspend fun match(event: String, payload: JsonObject): JsonObject {
        require(event in MatchCodec.events)
        return objectOf(realtime.emitWithAck(event, JSONObject(payload.toString()))) ?: buildJsonObject { put("ok", false) }
    }
    override fun paddle(matchId: String, x: Float, y: Float?, released: Boolean, touch: Pair<Float, Float>?) {
        realtime.emit("match:paddle", JSONObject().put("matchId", matchId).put("x", x.toDouble()).apply {
            if (y != null) put("y", y.toDouble())
            if (released) put("released", true)
            if (touch != null) put("touch", org.json.JSONArray().put(touch.first.toDouble()).put(touch.second.toDouble()))
        })
    }
    override val incoming = realtime.events("message:new").decodeEvents<ChatMessage>(json)
    override val receipts = realtime.events("receipt:update").decodeEvents<ReceiptUpdate>(json)
    override val reactions = realtime.events("reaction:update").decodeEvents<ReactionUpdate>(json)
    override val ended = realtime.events("connection:ended").map { Unit }
    override val updates = realtime.events("message:update").decodeEvents<ChatMessage>(json)
    override val typing = realtime.events("typing").decodeEvents<TypingUpdate>(json)
    override val presence = realtime.events("presence").decodeEvents<PresenceUpdate>(json)
    override val pins = realtime.events("pin:update").decodeEvents<PinState>(json)
    override suspend fun edit(messageId: String, content: String) = messageAck(
        realtime.emitWithAck("message:edit", JSONObject().put("messageId", messageId).put("content", content)))
    override suspend fun delete(messageId: String) = messageAck(realtime.emitWithAck("message:delete", JSONObject().put("messageId", messageId)))
    override suspend fun pin(messageId: String?): PinState {
        val ack = realtime.emitWithAck("message:pin", JSONObject().put("messageId", messageId ?: JSONObject.NULL))
        check(!ack.has("error") && ack.optBoolean("ok")) { ack.optString("error", "Couldn't pin the message.") }
        return json.decodeFromString(ack.getJSONObject("pin").toString())
    }
    override fun typing(active: Boolean) = realtime.emit("typing", JSONObject().put("typing", active))
    override fun presence(active: Boolean) = realtime.emit("presence", JSONObject().put("active", active))
    override suspend fun linkPreview(url: String) = api.linkPreview(url).preview
    private fun messageAck(ack: JSONObject): ChatMessage {
        check(!ack.has("error") && ack.optBoolean("ok")) { ack.optString("error", "Something went wrong. Try again.") }
        return json.decodeFromString(ack.getJSONObject("message").toString())
    }
    override suspend fun start() { realtime.start() }
    override suspend fun stop() { realtime.stop() }
    override suspend fun send(message: SendMessage): ChatMessage = decodeSendAck(
        realtime.emitWithAck("message:send", JSONObject(json.encodeToString(message))).toString(), json,
    )
    override suspend fun history(connectionId: String, before: String?, after: String?) =
        api.history(connectionId, before, after).messages
    override suspend fun markRead(connectionId: String) {
        // Socket read is one hop to the sender's receipt:update; REST covers a closed socket or an older server (no ack).
        if (state.value == ConnectionState.Connected) {
            val ack = try { realtime.emitWithAck("message:read", JSONObject().put("connectionId", connectionId), timeoutMs = 2_000) }
                catch (_: IOException) { null }
            if (ack != null) {
                check(ack.optBoolean("ok") && !ack.has("error")) { ack.optString("error", "Couldn't mark as read.") }
                return
            }
        }
        api.read(connectionId)
    }
    override suspend fun markDelivered(connectionId: String) = api.delivered(connectionId)
    override suspend fun react(messageId: String, emoji: String, remove: Boolean) {
        require(emoji.isNotBlank() && emoji.length <= 32) // server checks it is a single emoji
        val ack = realtime.emitWithAck(if (remove) "reaction:remove" else "reaction:add",
            JSONObject().put("messageId", messageId).put("emoji", emoji))
        check(!ack.has("error") && ack.optBoolean("ok")) { ack.optString("error", "Reaction failed.") }
    }
}

internal inline fun <reified T> Flow<JSONObject>.decodeEvents(json: Json): Flow<T> = mapNotNull { raw ->
    runCatching {
        json.decodeFromString<T>(raw.toString()).also { value ->
            val dates = when (value) {
                is ChatMessage -> {
                    require(!value.id.isNullOrBlank() && value.senderId.isNotBlank())
                    listOf(value.createdAt, value.editedAt, value.deletedAt)
                }
                is ReceiptUpdate -> listOf(value.lastReadAt, value.lastDeliveredAt)
                is PresenceUpdate -> listOf(value.lastSeenAt)
                is PinState -> listOf(value.pinnedAt)
                else -> emptyList()
            }
            dates.filterNotNull().forEach { Instant.parse(it) }
        }
    }.getOrNull()
}

internal fun decodeSendAck(value: String, json: Json): ChatMessage {
    val ack = json.decodeFromString<SendAck>(value)
    check(ack.error == null) { ack.error.orEmpty() }
    check(ack.ok && ack.message?.id != null) { "Server did not confirm the message." }
    return checkNotNull(ack.message)
}
