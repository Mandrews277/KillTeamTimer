package com.killteamtimer.session

import com.killteamtimer.engine.Clock
import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameConfig
import com.killteamtimer.engine.Phase
import com.killteamtimer.net.Transport
import com.killteamtimer.net.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory star network: one host transport, any number of client transports, synchronous delivery. */
private class FakeNetwork {
    val host = FakeTransport("host")

    /** When false, discovery and connection attempts fail, as if the host were out of range. */
    var hostReachable = true
    private val clients = mutableMapOf<String, FakeTransport>()

    fun client(id: String) = FakeTransport(id).also { clients[id] = it }

    inner class FakeTransport(val id: String) : Transport {
        override val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)
        private var hostName = ""

        override fun startHosting(hostName: String) { this.hostName = hostName }
        override fun startDiscovery() {
            if (hostReachable) events.tryEmit(TransportEvent.EndpointFound("host", host.hostName))
        }
        override fun stopDiscovery() = Unit
        override fun connect(endpointId: String, myName: String) {
            if (!hostReachable) {
                events.tryEmit(TransportEvent.Error("unreachable"))
                return
            }
            host.events.tryEmit(TransportEvent.Connected(id))
            events.tryEmit(TransportEvent.Connected("host"))
        }
        override fun send(endpointId: String, payload: String) {
            val target = if (this === host) clients.getValue(endpointId) else host
            val from = if (this === host) "host" else id
            target.events.tryEmit(TransportEvent.Received(from, payload))
        }
        override fun disconnect(endpointId: String) = drop(if (this === host) endpointId else id)
        override fun stop() = Unit

        /** Simulates the link between the host and [clientId] dropping. */
        fun drop(clientId: String) {
            host.events.tryEmit(TransportEvent.Disconnected(clientId))
            clients.getValue(clientId).events.tryEmit(TransportEvent.Disconnected("host"))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HostClientSyncTest {

    private var time = 1_000L
    private val clock = Clock { time }

    private fun TestScope.setup(): Triple<FakeNetwork, HostSession, List<ClientSession>> {
        val net = FakeNetwork()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val host = HostSession(net.host, "Host", clock, scope, heartbeatMs = 1_000_000)
        val clients = listOf("a", "b", "c").map { id ->
            ClientSession(net.client(id), "P-$id", "dev-$id", clock, scope).also { c ->
                c.join(c.hosts.value.single())
            }
        }
        return Triple(net, host, clients)
    }

    @Test
    fun clientsJoinLobbyAndReceiveGame() = runTest {
        val (_, host, clients) = setup()
        assertEquals(listOf("Host", "P-a", "P-b", "P-c"), host.lobby.value.map { it.name })
        assertEquals(listOf(1, 2, 3), clients.map { it.myPlayerId.value })
        assertEquals(4, clients[2].lobby.value.size)

        host.startGame(GameConfig(votesToPause = 2))
        clients.forEach { assertEquals(Phase.NOT_STARTED, it.game.value!!.phase) }
    }

    @Test
    fun actionsFlowThroughHost() = runTest {
        val (_, host, clients) = setup()
        host.startGame(GameConfig(votesToPause = 2))
        clients[0].send(GameAction.Start(1))
        assertEquals(1, host.game.value!!.activePlayerId)

        time += 500
        clients[0].send(GameAction.PassTo(1, 3))
        clients.forEach { assertEquals(3, it.game.value!!.activePlayerId) }

        clients[1].send(GameAction.VotePause(2))
        host.send(GameAction.VotePause(0))
        clients.forEach { assertEquals(Phase.PAUSED, it.game.value!!.phase) }
    }

    @Test
    fun clientCannotActAsSomeoneElse() = runTest {
        val (_, host, clients) = setup()
        host.startGame(GameConfig())
        clients[0].send(GameAction.Start(2))
        assertEquals(Phase.NOT_STARTED, host.game.value!!.phase)
    }

    @Test
    fun clientClockIsMappedToHostTimebase() = runTest {
        val (_, host, clients) = setup()
        host.startGame(GameConfig())
        host.send(GameAction.Start(0))
        assertEquals(host.now(), clients[0].now())
    }

    @Test
    fun droppedPlayerRejoinsSameSlot() = runTest {
        val (net, host, clients) = setup()
        host.startGame(GameConfig())
        net.hostReachable = false
        net.host.drop("b")
        assertTrue(host.lobby.value.single { it.id == 2 }.connected.not())
        assertEquals(ClientStatus.HostLost, clients[1].status.value)

        // Client keeps redialling and rejoins in its old slot once the host is reachable.
        net.hostReachable = true
        advanceTimeBy(3_001)
        assertEquals(ClientStatus.Joined, clients[1].status.value)
        assertEquals(2, clients[1].myPlayerId.value)
        assertTrue(host.lobby.value.single { it.id == 2 }.connected)
        assertEquals(4, host.lobby.value.size)
    }

    @Test
    fun lateJoinerRejected() = runTest {
        val (net, host, _) = setup()
        host.startGame(GameConfig())
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val late = ClientSession(net.client("z"), "Late", "dev-z", clock, scope)
        late.join(late.hosts.value.single())
        assertTrue(late.status.value is ClientStatus.Rejected)
    }

    @Test
    fun lobbyDropRemovesPlayer() = runTest {
        val (net, host, _) = setup()
        net.hostReachable = false
        net.host.drop("a")
        assertEquals(listOf("Host", "P-b", "P-c"), host.lobby.value.map { it.name })
    }
}

