package app.web.oneonone.call

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallProtocolTest {
    @Test fun `sdp round-trips in the web RTCSessionDescriptionInit shape`() {
        val wire = CallProtocol.encode(Signal.Sdp("offer", "v=0"))
        assertEquals("offer", wire.getJSONObject("sdp").getString("type"))
        assertEquals(Signal.Sdp("offer", "v=0"), CallProtocol.decode(wire))
    }

    @Test fun `candidate round-trips in the web RTCIceCandidateInit shape`() {
        val signal = Signal.Candidate("candidate:1 1 udp 1 1.2.3.4 5 typ host", "0", 0)
        val wire = CallProtocol.encode(signal)
        assertEquals(0, wire.getJSONObject("candidate").getInt("sdpMLineIndex"))
        assertEquals(signal, CallProtocol.decode(wire))
    }

    @Test fun `web candidate JSON with usernameFragment and null mid decodes`() {
        val web = JSONObject("""{"candidate":{"candidate":"candidate:x","sdpMid":null,"sdpMLineIndex":1,"usernameFragment":"ab"}}""")
        assertEquals(Signal.Candidate("candidate:x", null, 1), CallProtocol.decode(web))
    }

    @Test fun `junk and end-of-candidates are ignored`() {
        assertNull(CallProtocol.decode(null))
        assertNull(CallProtocol.decode(JSONObject("""{"sdp":{"type":"pranswer","sdp":"x"}}""")))
        assertNull(CallProtocol.decode(JSONObject("""{"candidate":{"candidate":""}}""")))
        assertNull(CallProtocol.decode(JSONObject("""{"other":1}""")))
    }

    @Test fun `ice servers accept string or array urls and skip empties`() {
        val servers = CallProtocol.iceServers(JSONArray("""[
            {"urls":"stun:stun.cloudflare.com:3478"},
            {"urls":["turn:a:3478","turns:a:5349"],"username":"u","credential":"c"},
            {"urls":[]}
        ]"""))
        assertEquals(2, servers.size)
        assertEquals(listOf("turn:a:3478", "turns:a:5349"), servers[1].urls)
        assertEquals("u", servers[1].username)
        assertNull(servers[0].username)
    }

    @Test(expected = IllegalStateException::class)
    fun `an error ack throws with the server text`() {
        CallProtocol.requireOk(JSONObject("""{"error":"a call is already in progress on this connection"}"""))
    }

    @Test fun `call log text matches outcome and side`() {
        assertEquals("Voice call · 1:05", CallProtocol.logText("audio", "completed", 65, isMine = true))
        assertEquals("Missed video call", CallProtocol.logText("video", "missed", 0, isMine = false))
        assertEquals("No answer · voice call", CallProtocol.logText("audio", "missed", 0, isMine = true))
        assertEquals("1:01:01", CallProtocol.duration(3661))
    }
}

class RoutePolicyTest {
    @Test fun `voice defaults to the earpiece, video to the speaker`() {
        assertEquals(AudioRoute.Earpiece, RoutePolicy.choose(CallKind.Audio, null, wired = false, bluetooth = false, hasEarpiece = true))
        assertEquals(AudioRoute.Speaker, RoutePolicy.choose(CallKind.Video, null, wired = false, bluetooth = false, hasEarpiece = true))
    }

    @Test fun `a headset wins over the default`() {
        assertEquals(AudioRoute.Bluetooth, RoutePolicy.choose(CallKind.Video, null, wired = true, bluetooth = true, hasEarpiece = true))
        assertEquals(AudioRoute.Wired, RoutePolicy.choose(CallKind.Audio, null, wired = true, bluetooth = false, hasEarpiece = true))
    }

    @Test fun `speaker toggle flips between speaker and the private route`() {
        assertEquals(AudioRoute.Speaker, RoutePolicy.choose(CallKind.Audio, true, wired = false, bluetooth = false, hasEarpiece = true))
        assertEquals(AudioRoute.Earpiece, RoutePolicy.choose(CallKind.Video, false, wired = false, bluetooth = false, hasEarpiece = true))
        assertEquals(AudioRoute.Wired, RoutePolicy.choose(CallKind.Video, false, wired = true, bluetooth = false, hasEarpiece = true))
    }

    @Test fun `no earpiece (tablet) uses the speaker`() {
        assertEquals(AudioRoute.Speaker, RoutePolicy.choose(CallKind.Audio, null, wired = false, bluetooth = false, hasEarpiece = false))
    }
}
