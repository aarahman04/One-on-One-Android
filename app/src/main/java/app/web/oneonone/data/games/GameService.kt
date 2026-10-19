package app.web.oneonone.data.games

import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.transport.Transport
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** UI -> GameViewModel -> GameService -> Transport; lifecycle/presence stay with MessageService. */
class GameService @Inject constructor(private val transport: Transport, messages: MessageService) {
    val events = transport.gameEvents
    val connection = transport.state
    val presence = messages.presence
    val active = messages.active
    val ended = messages.ended
    suspend fun invite(id: String, game: GameKind) = transport.gameInvite(id, game.wire)
    suspend fun accept(id: String) = transport.gameAccept(id)
    suspend fun decline(id: String) = transport.gameDecline(id)
    suspend fun move(id: String, seq: Int, data: JsonObject) = transport.gameMove(id, seq, data)
    suspend fun end(id: String, reason: String) = transport.gameEnd(id, reason)
}
