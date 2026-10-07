package app.web.oneonone.call

import app.web.oneonone.data.realtime.RealtimeSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingCall(val callId: String, val kind: CallKind, val fromUserId: String?)

/** `call:*` socket events from docs/API-CONTRACT.md, over the shared [RealtimeSocket] seam. */
@Singleton
class CallSignaling @Inject constructor(private val socket: RealtimeSocket) {
    val incoming: Flow<IncomingCall> = socket.events("call:incoming").map {
        IncomingCall(it.optString("callId"), CallProtocol.kindOf(it.optString("kind")), it.optString("fromUserId").ifEmpty { null })
    }
    val accepted: Flow<String> = socket.events("call:accepted").map { it.optString("callId") }
    val ended: Flow<Pair<String, String>> = socket.events("call:ended").map { it.optString("callId") to it.optString("reason") }
    val signals: Flow<Pair<String, Signal?>> = socket.events("call:signal").map {
        it.optString("callId") to CallProtocol.decode(it.optJSONObject("data"))
    }
    val connection = socket.state

    /** Returns `callId` and ICE servers. Errors (busy, unreachable, rate limit) throw with the server's text. */
    suspend fun invite(kind: CallKind): Pair<String, List<IceServerSpec>> {
        val ack = CallProtocol.requireOk(socket.emitWithAck("call:invite", JSONObject().put("kind", CallProtocol.wire(kind))))
        return ack.getString("callId") to CallProtocol.iceServers(ack.optJSONArray("iceServers"))
    }

    suspend fun accept(callId: String): List<IceServerSpec> =
        CallProtocol.iceServers(CallProtocol.requireOk(socket.emitWithAck("call:accept", JSONObject().put("callId", callId))).optJSONArray("iceServers"))

    suspend fun decline(callId: String) { CallProtocol.requireOk(socket.emitWithAck("call:decline", JSONObject().put("callId", callId))) }

    suspend fun end(callId: String) { CallProtocol.requireOk(socket.emitWithAck("call:end", JSONObject().put("callId", callId))) }

    fun signal(callId: String, signal: Signal) =
        socket.emit("call:signal", JSONObject().put("callId", callId).put("data", CallProtocol.encode(signal)))

    suspend fun ensureConnected() = socket.start()
}
