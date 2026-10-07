package app.web.oneonone.call

import org.json.JSONArray
import org.json.JSONObject

/** Wire shapes shared with the web client (client/src/features/call/session.ts). Pure; unit-tested. */
data class IceServerSpec(val urls: List<String>, val username: String?, val credential: String?)

sealed interface Signal {
    data class Sdp(val type: String, val sdp: String) : Signal
    data class Candidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int) : Signal
}

object CallProtocol {
    fun kindOf(value: String?): CallKind = if (value == "video") CallKind.Video else CallKind.Audio
    fun wire(kind: CallKind): String = if (kind == CallKind.Video) "video" else "audio"

    /** `{sdp:{type,sdp}}` or `{candidate:{candidate,sdpMid,sdpMLineIndex}}`, as RTCSessionDescriptionInit / RTCIceCandidateInit. */
    fun encode(signal: Signal): JSONObject = when (signal) {
        is Signal.Sdp -> JSONObject().put("sdp", JSONObject().put("type", signal.type).put("sdp", signal.sdp))
        is Signal.Candidate -> JSONObject().put("candidate", JSONObject()
            .put("candidate", signal.candidate)
            .put("sdpMid", signal.sdpMid ?: JSONObject.NULL)
            .put("sdpMLineIndex", signal.sdpMLineIndex))
    }

    fun decode(data: JSONObject?): Signal? {
        data ?: return null
        data.optJSONObject("sdp")?.let { sdp ->
            val type = sdp.optString("type")
            val body = sdp.optString("sdp")
            return if (type in setOf("offer", "answer") && body.isNotEmpty()) Signal.Sdp(type, body) else null
        }
        data.optJSONObject("candidate")?.let { c ->
            val candidate = c.optString("candidate")
            if (candidate.isEmpty()) return null // end-of-candidates marker
            val mid = if (c.isNull("sdpMid")) null else c.optString("sdpMid")
            return Signal.Candidate(candidate, mid, c.optInt("sdpMLineIndex", 0))
        }
        return null
    }

    /** `[{urls: string|string[], username?, credential?}]` from call:invite / call:accept acks. */
    fun iceServers(array: JSONArray?): List<IceServerSpec> {
        array ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val server = array.optJSONObject(i) ?: return@mapNotNull null
            val urls = when (val raw = server.opt("urls")) {
                is JSONArray -> (0 until raw.length()).map { raw.optString(it) }
                is String -> listOf(raw)
                else -> emptyList()
            }.filter { it.isNotBlank() }
            if (urls.isEmpty()) null
            else IceServerSpec(urls, server.optString("username").ifEmpty { null }, server.optString("credential").ifEmpty { null })
        }
    }

    /** Throws with the server's message when an ack carries `{error}`. */
    fun requireOk(ack: JSONObject): JSONObject {
        val error = ack.optString("error")
        check(error.isEmpty()) { error }
        return ack
    }

    /** Text for a server-authored call log row (`{kind, outcome, durationSec}`), matching the web card. */
    fun logText(kind: String?, outcome: String?, durationSec: Int, isMine: Boolean): String {
        val what = if (kind == "video") "video call" else "voice call"
        return when (outcome) {
            "completed" -> "${what.replaceFirstChar { it.uppercase() }} · ${duration(durationSec)}"
            "missed" -> if (isMine) "No answer · $what" else "Missed $what"
            "declined" -> if (isMine) "Declined · $what" else "You declined a $what"
            "cancelled" -> if (isMine) "Cancelled $what" else "Missed $what"
            "unreachable" -> if (isMine) "Couldn't reach them · $what" else "Missed $what"
            else -> "${what.replaceFirstChar { it.uppercase() }} failed"
        }
    }

    fun duration(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

/** Where call audio should play. Pure decision so it can be tested without AudioManager. */
enum class AudioRoute { Earpiece, Speaker, Wired, Bluetooth }

object RoutePolicy {
    /**
     * A connected headset always wins. Otherwise voice calls default to the earpiece and video
     * to the speaker; the speaker toggle flips between the speaker and that default.
     */
    fun choose(kind: CallKind, speakerToggled: Boolean?, wired: Boolean, bluetooth: Boolean, hasEarpiece: Boolean): AudioRoute {
        val headset = when {
            bluetooth -> AudioRoute.Bluetooth
            wired -> AudioRoute.Wired
            else -> null
        }
        val private = headset ?: if (hasEarpiece) AudioRoute.Earpiece else AudioRoute.Speaker
        val speaker = speakerToggled ?: (headset == null && (kind == CallKind.Video || !hasEarpiece))
        return if (speaker) AudioRoute.Speaker else private
    }
}
