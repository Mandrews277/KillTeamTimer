package com.killteamtimer.session

import com.killteamtimer.engine.Clock
import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameConfig
import com.killteamtimer.engine.GameEngine
import com.killteamtimer.engine.GameState
import com.killteamtimer.engine.Player
import com.killteamtimer.net.LobbyPlayer
import com.killteamtimer.net.NetMessage
import com.killteamtimer.net.Transport
import com.killteamtimer.net.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Authoritative side of a multi-phone game. The host is always player 0. Clients send intents;
 * the host applies them and broadcasts the full state after every change and on a heartbeat.
 */
class HostSession(
    private val transport: Transport,
    hostName: String,
    private val clock: Clock,
    parentScope: CoroutineScope,
    heartbeatMs: Long = 2_000,
) : GameController {

    val myPlayerId = 0

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    private val _lobby = MutableStateFlow(listOf(LobbyPlayer(myPlayerId, hostName, 0, connected = true)))
    val lobby: StateFlow<List<LobbyPlayer>> = _lobby

    private val _game = MutableStateFlow<GameState?>(null)
    override val game: StateFlow<GameState?> = _game

    /** Latest transport problem (e.g. advertising failed), for display in the lobby. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val endpointToPlayer = mutableMapOf<String, Int>()
    private val deviceToPlayer = mutableMapOf<String, Int>()
    private var nextId = 1

    val hostAddress: String? get() = transport.hostAddress

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { transport.events.collect(::onEvent) }
        transport.startHosting(hostName)
        scope.launch {
            while (isActive) {
                delay(heartbeatMs)
                broadcastState()
            }
        }
    }

    override fun now(): Long = clock.now()

    override fun send(action: GameAction) = apply(action)

    fun startGame(config: GameConfig) {
        if (_game.value != null) return
        val players = _lobby.value.map { Player(it.id, it.name, it.colorIndex) }
        _game.value = GameEngine.newGame(players, config)
        broadcastState()
    }

    fun close() {
        scope.cancel()
        transport.stop()
    }

    private fun apply(action: GameAction) {
        val current = _game.value ?: return
        val next = GameEngine.reduce(current, action, clock.now())
        if (next != current) {
            _game.value = next
            broadcastState()
        }
    }

    private fun onEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.Received -> when (val msg = NetMessage.decode(event.payload)) {
                is NetMessage.Hello -> onHello(event.endpointId, msg)
                is NetMessage.Intent -> {
                    // A client may only act as itself.
                    if (endpointToPlayer[event.endpointId] == msg.action.by) apply(msg.action)
                }
                else -> Unit
            }
            is TransportEvent.Disconnected -> onDisconnected(event.endpointId)
            is TransportEvent.Error -> _error.value = event.message
            else -> Unit
        }
    }

    private fun onHello(endpointId: String, hello: NetMessage.Hello) {
        val known = deviceToPlayer[hello.deviceId]
        val playerId = when {
            known != null -> known
            _game.value != null -> {
                sendTo(endpointId, NetMessage.Rejected("Game already in progress"))
                return
            }
            else -> {
                val id = nextId++
                deviceToPlayer[hello.deviceId] = id
                _lobby.update { it + LobbyPlayer(id, hello.name, id, connected = true) }
                id
            }
        }
        endpointToPlayer.entries.removeAll { it.value == playerId }
        endpointToPlayer[endpointId] = playerId
        setConnected(playerId, true)
        sendTo(endpointId, NetMessage.Welcome(playerId))
        broadcastLobby()
        _game.value?.let { sendTo(endpointId, NetMessage.State(it, clock.now())) }
    }

    private fun onDisconnected(endpointId: String) {
        val playerId = endpointToPlayer.remove(endpointId) ?: return
        if (_game.value == null) {
            // Before the game starts, a dropped player simply leaves; they can join again.
            deviceToPlayer.entries.removeAll { it.value == playerId }
            _lobby.update { lobby -> lobby.filterNot { it.id == playerId } }
        } else {
            setConnected(playerId, false)
        }
        broadcastLobby()
    }

    private fun setConnected(playerId: Int, connected: Boolean) {
        _lobby.update { lobby -> lobby.map { if (it.id == playerId) it.copy(connected = connected) else it } }
    }

    private fun broadcastLobby() = broadcast(NetMessage.Lobby(_lobby.value))

    private fun broadcastState() {
        val game = _game.value ?: return
        broadcast(NetMessage.State(game, clock.now()))
    }

    private fun broadcast(message: NetMessage) {
        val payload = NetMessage.encode(message)
        endpointToPlayer.keys.toList().forEach { transport.send(it, payload) }
    }

    private fun sendTo(endpointId: String, message: NetMessage) =
        transport.send(endpointId, NetMessage.encode(message))
}
