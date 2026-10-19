package app.web.oneonone.data.transport

import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.RealtimeSocket
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.json.JSONObject
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
    @GET("api/link-preview") suspend fun linkPreview(@Query("url") url: String): LinkPreviewResult
}

@Singleton
class InternetTransport @Inject constructor(
    private val realtime: RealtimeSocket,
    private val api: ChatApi,
    private val json: Json,
) : Transport {
    override val state = realtime.state
    override val incoming = realtime.events("message:new").map { json.decodeFromString<ChatMessage>(it.toString()) }
    override val receipts = realtime.events("receipt:update").map { json.decodeFromString<ReceiptUpdate>(it.toString()) }
    override val reactions = realtime.events("reaction:update").map { json.decodeFromString<ReactionUpdate>(it.toString()) }
    override val ended = realtime.events("connection:ended").map { Unit }
    override val updates = realtime.events("message:update").map { json.decodeFromString<ChatMessage>(it.toString()) }
    override val typing = realtime.events("typing").map { json.decodeFromString<TypingUpdate>(it.toString()) }
    override val presence = realtime.events("presence").map { json.decodeFromString<PresenceUpdate>(it.toString()) }
    override val pins = realtime.events("pin:update").map { json.decodeFromString<PinState>(it.toString()) }
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
    override suspend fun markRead(connectionId: String) = api.read(connectionId)
    override suspend fun react(messageId: String, emoji: String, remove: Boolean) {
        require(emoji.isNotBlank() && emoji.length <= 32) // server checks it is a single emoji
        val ack = realtime.emitWithAck(if (remove) "reaction:remove" else "reaction:add",
            JSONObject().put("messageId", messageId).put("emoji", emoji))
        check(!ack.has("error") && ack.optBoolean("ok")) { ack.optString("error", "Reaction failed.") }
    }
}

internal fun decodeSendAck(value: String, json: Json): ChatMessage {
    val ack = json.decodeFromString<SendAck>(value)
    check(ack.error == null) { ack.error.orEmpty() }
    check(ack.ok && ack.message?.id != null) { "Server did not confirm the message." }
    return checkNotNull(ack.message)
}
