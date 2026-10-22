package com.killteamtimer.session

import com.killteamtimer.engine.Clock
import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameState
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
import kotlinx.coroutines.launch

data class HostEndpoint(val id: String, val name: String)

sealed interface ClientStatus {
    data object Discovering : ClientStatus
    data object Connecting : ClientStatus
    data object Joined : ClientStatus
    data object HostLost : ClientStatus
    data class Rejected(val reason: String) : ClientStatus
    data class Error(val message: String) : ClientStatus
}

/** Joining side of a multi-phone game. Mirrors the host state and forwards this player's actions. */
class ClientSession(
    private val transport: Transport,
    private val myName: String,
    private val deviceId: String,
    private val clock: Clock,
    parentScope: CoroutineScope,
    private val retryMs: Long = 3_000,
) : GameController {

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    private val _hosts = MutableStateFlow<List<HostEndpoint>>(emptyList())
    val hosts: StateFlow<List<HostEndpoint>> = _hosts

    private val _status = MutableStateFlow<ClientStatus>(ClientStatus.Discovering)
    val status: StateFlow<ClientStatus> = _status

    private val _myPlayerId = MutableStateFlow<Int?>(null)
    val myPlayerId: StateFlow<Int?> = _myPlayerId

    private val _lobby = MutableStateFlow<List<LobbyPlayer>>(emptyList())
    val lobby: StateFlow<List<LobbyPlayer>> = _lobby

    private val _game = MutableStateFlow<GameState?>(null)
    override val game: StateFlow<GameState?> = _game

    /** localClock - hostClock, measured from the latest snapshot. */
    private var offset = 0L
    private var host: HostEndpoint? = null
    private var retryJob: Job? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { transport.events.collect(::onEvent) }
        transport.startDiscovery()
    }

    override fun now(): Long = clock.now() - offset

    override fun send(action: GameAction) {
        val endpoint = host?.id ?: return
        transport.send(endpoint, NetMessage.encode(NetMessage.Intent(action)))
    }

    fun join(endpoint: HostEndpoint) {
        host = endpoint
        _status.value = ClientStatus.Connecting
        transport.connect(endpoint.id, myName)
    }

    /** Back to the host list after a failed join. */
    fun retryDiscovery() {
        host = null
        _status.value = ClientStatus.Discovering
        transport.startDiscovery()
    }

    fun close() {
        scope.cancel()
        transport.stop()
    }

    private fun onEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.EndpointFound -> {
                val found = HostEndpoint(event.endpointId, event.name)
                _hosts.update { list -> list.filterNot { it.id == found.id } + found }
                if (_status.value == ClientStatus.HostLost && found.name == host?.name) join(found)
            }
            is TransportEvent.EndpointLost -> _hosts.update { list -> list.filterNot { it.id == event.endpointId } }
            is TransportEvent.Connected -> if (event.endpointId == host?.id) {
                retryJob?.cancel()
                transport.stopDiscovery()
                transport.send(event.endpointId, NetMessage.encode(NetMessage.Hello(myName, deviceId)))
            }
            is TransportEvent.Received -> if (event.endpointId == host?.id) onMessage(event.payload)
            is TransportEvent.Disconnected -> if (event.endpointId == host?.id) onHostLost()
            is TransportEvent.Error -> if (_status.value == ClientStatus.Connecting || _status.value == ClientStatus.Discovering) {
                _status.value = ClientStatus.Error(event.message)
            }
        }
    }

    private fun onMessage(payload: String) {
        when (val msg = NetMessage.decode(payload)) {
            is NetMessage.Welcome -> {
                _myPlayerId.value = msg.playerId
                _status.value = ClientStatus.Joined
            }
            is NetMessage.Rejected -> {
                _status.value = ClientStatus.Rejected(msg.reason)
                host?.let { transport.disconnect(it.id) }
            }
            is NetMessage.Lobby -> _lobby.value = msg.players
            is NetMessage.State -> {
                offset = clock.now() - msg.hostNow
                _game.value = msg.game
            }
            else -> Unit
        }
    }

    private fun onHostLost() {
        if (_status.value is ClientStatus.Rejected) return
        if (_myPlayerId.value == null) {
            _status.value = ClientStatus.Error("Connection to host failed")
            return
        }
        _status.value = ClientStatus.HostLost
        _hosts.value = emptyList()
        transport.startDiscovery()
        // Endpoints with stable ids (Wi-Fi) can be redialled directly; discovery covers the rest.
        retryJob?.cancel()
        retryJob = scope.launch {
            while (_status.value == ClientStatus.HostLost) {
                delay(retryMs)
                host?.let { transport.connect(it.id, myName) }
            }
        }
    }
}
