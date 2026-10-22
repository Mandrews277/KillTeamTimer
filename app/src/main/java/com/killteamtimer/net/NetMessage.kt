package com.killteamtimer.net

import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LobbyPlayer(val id: Int, val name: String, val colorIndex: Int, val connected: Boolean)

/** Wire protocol between host and clients. Clients only send [Hello] and [Intent]. */
@Serializable
sealed interface NetMessage {
    @Serializable
    @SerialName("hello")
    data class Hello(val name: String, val deviceId: String) : NetMessage

    @Serializable
    @SerialName("welcome")
    data class Welcome(val playerId: Int) : NetMessage

    @Serializable
    @SerialName("rejected")
    data class Rejected(val reason: String) : NetMessage

    @Serializable
    @SerialName("lobby")
    data class Lobby(val players: List<LobbyPlayer>) : NetMessage

    /** Full game snapshot. [hostNow] is the host clock at send time, used to map timestamps locally. */
    @Serializable
    @SerialName("state")
    data class State(val game: GameState, val hostNow: Long) : NetMessage

    @Serializable
    @SerialName("intent")
    data class Intent(val action: GameAction) : NetMessage

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }

        fun encode(message: NetMessage): String = json.encodeToString(serializer(), message)

        fun decode(text: String): NetMessage? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
