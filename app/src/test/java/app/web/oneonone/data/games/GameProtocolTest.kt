package app.web.oneonone.data.games

import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class GameProtocolTest {
    @Test fun onlyDocumentedEnvelopesAndValidIds() {
        assertEquals(5, GameProtocol.events.size)
        for (name in GameProtocol.events) assertNotNull(GameProtocol.decode(name, """{"gameId":"a-1","from":"partner"}"""))
        assertNull(GameProtocol.decode("game:new", """{"gameId":"a-1","from":"partner"}"""))
        for (bad in listOf("", "a_b", "x".repeat(65))) {
            assertThrows(IllegalArgumentException::class.java) { GameProtocol.payload(bad) }
            assertNull(GameProtocol.decode("game:move", """{"gameId":"$bad","from":"partner"}"""))
        }
        assertNull(GameProtocol.decode("game:move", """{"gameId":"a"}"""))
        assertNull(GameProtocol.decode("game:move", "not json"))
    }
    @Test fun payloadLimitCountsUtf8Bytes() {
        GameProtocol.payload("a") { put("value", "x".repeat(4000)) }
        assertThrows(IllegalArgumentException::class.java) { GameProtocol.payload("a") { put("value", "अ".repeat(1400)) } }
    }
}
