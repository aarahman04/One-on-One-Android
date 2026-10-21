package app.web.oneonone.data.games

import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.transport.Transport
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** UI -> GameViewModel -> GameService -> Transport; lifecycle/presence stay with MessageService. */
class GameService @Inject constructor(private val transport: Transport, messages: MessageService) : MatchChannel {
    override val states = transport.matchStates
    override val frames = transport.pongFrames
    override val reactions = transport.matchReactions
    val connection = transport.state
    val presence = messages.presence
    val active = messages.active
    val ended = messages.ended
    override suspend fun call(event: String, payload: JsonObject) = transport.match(event, payload)
    override fun paddle(matchId: String, x: Float, y: Float?, released: Boolean) = transport.paddle(matchId, x, y, released)
}
