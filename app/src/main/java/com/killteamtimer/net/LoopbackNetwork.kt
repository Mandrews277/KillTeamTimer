package com.killteamtimer.net

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-memory star network for test mode: one host and any number of simulated clients living in
 * the same app, talking through the real host/client protocol. Main thread only.
 */
class LoopbackNetwork {
    val host = Endpoint(HOST_ID)
    private val clients = mutableMapOf<String, Endpoint>()

    fun client(id: String) = Endpoint(id).also { clients[id] = it }

    inner class Endpoint(private val id: String) : Transport {
        override val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)
        private var hostName = ""

        override fun startHosting(hostName: String) {
            this.hostName = hostName
        }

        override fun startDiscovery() {
            events.tryEmit(TransportEvent.EndpointFound(HOST_ID, host.hostName))
        }

        override fun stopDiscovery() = Unit

        override fun connect(endpointId: String, myName: String) {
            host.events.tryEmit(TransportEvent.Connected(id))
            events.tryEmit(TransportEvent.Connected(HOST_ID))
        }

        override fun send(endpointId: String, payload: String) {
            val target = if (this === host) clients[endpointId] else host
            target?.events?.tryEmit(TransportEvent.Received(id, payload))
        }

        override fun disconnect(endpointId: String) {
            val clientId = if (this === host) endpointId else id
            host.events.tryEmit(TransportEvent.Disconnected(clientId))
            clients[clientId]?.events?.tryEmit(TransportEvent.Disconnected(HOST_ID))
        }

        override fun stop() = Unit
    }

    companion object {
        const val HOST_ID = "host"
    }
}
