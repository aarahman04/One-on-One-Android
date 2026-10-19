package app.web.oneonone.data.games

import kotlinx.serialization.json.*

data class GameEvent(val name: String, val gameId: String, val from: String, val payload: JsonObject)

/** Only the documented relay envelope lives here. Game-specific JSON belongs to the local engines. */
object GameProtocol {
    val events = listOf("game:invite", "game:accept", "game:decline", "game:move", "game:end")
    private val id = Regex("^[A-Za-z0-9-]{1,64}$")
    fun payload(gameId: String, build: JsonObjectBuilder.() -> Unit = {}): JsonObject {
        require(id.matches(gameId)) { "Invalid game ID." }
        return buildJsonObject { put("gameId", gameId); build() }.also {
            require(it.toString().toByteArray(Charsets.UTF_8).size <= 4096) { "Game move is too large." }
        }
    }
    fun decode(name: String, value: String): GameEvent? = runCatching {
        val data = Json.parseToJsonElement(value).jsonObject
        val gameId = data["gameId"]?.jsonPrimitive?.takeIf { it.isString }?.content.orEmpty()
        val from = data["from"]?.jsonPrimitive?.takeIf { it.isString }?.content.orEmpty()
        require(name in events && id.matches(gameId) && from.isNotBlank())
        GameEvent(name, gameId, from, data)
    }.getOrNull()
}

class GameRelayException(val code: String) : Exception(code)
