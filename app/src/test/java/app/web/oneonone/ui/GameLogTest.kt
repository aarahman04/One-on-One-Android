package app.web.oneonone.ui

import app.web.oneonone.data.games.GameKind
import app.web.oneonone.data.model.ChatMessage
import app.web.oneonone.push.GameInvitePush
import app.web.oneonone.push.decodeGameInvitePush
import app.web.oneonone.ui.chat.cards.gameLogText
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class GameLogTest {
    private fun row(winner: String?, reason: String, seconds: Int = 252, game: String = "chess") =
        ChatMessage("g1", "me", "Played Chess · 4 min", "2026-10-20T10:00:00Z", "game", buildJsonObject {
            put("game", game); put("inviter", "me"); if (winner == null) put("winner", JsonNull) else put("winner", winner)
            put("reason", reason); put("durationSec", seconds)
        })

    @Test fun gameRowsSayWhatWasPlayedForHowLongAndWhoWon() {
        assertEquals("Chess" to "You won · 4 min 12 sec", gameLogText(row("me", "checkmate"), "me", "Alex"))
        assertEquals("Chess" to "Alex won · 4 min 12 sec", gameLogText(row("them", "resigned"), "me", "Alex"))
        assertEquals("Connect 4" to "A draw · 45 sec", gameLogText(row(null, "board_full", 45, "connect4"), "me", "Alex"))
        assertEquals("Table Tennis" to "Game ended · 1 hr", gameLogText(row(null, "abandoned", 3600, "pong"), "me", "Alex"))
        assertEquals("Game" to "Game ended · 0 sec", gameLogText(ChatMessage("g2", "me", "", "2026-10-20T10:00:00Z", "game"), "me", "Alex"))
    }

    @Test fun gameInvitePushIsValidatedAtTheTrustBoundary() {
        val ok = mapOf("type" to "game_invite", "matchId" to "33333333-3333-4333-8333-333333333333",
            "connectionId" to "11111111-1111-4111-8111-111111111111", "game" to "dots", "inviterName" to "Alex")
        assertEquals(GameInvitePush(ok.getValue("matchId"), ok.getValue("connectionId"), GameKind.Dots), decodeGameInvitePush(ok))
        assertNull(decodeGameInvitePush(ok + ("game" to "poker")))
        assertNull(decodeGameInvitePush(ok + ("matchId" to "x")))
        assertNull(decodeGameInvitePush(ok + ("connectionId" to "../../")))
        assertNull(decodeGameInvitePush(ok + ("type" to "call")))
    }
}
