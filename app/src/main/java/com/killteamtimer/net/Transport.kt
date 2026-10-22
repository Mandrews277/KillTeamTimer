package com.killteamtimer.net

import kotlinx.coroutines.flow.SharedFlow

sealed interface TransportEvent {
    data class EndpointFound(val endpointId: String, val name: String) : TransportEvent
    data class EndpointLost(val endpointId: String) : TransportEvent
    data class Connected(val endpointId: String) : TransportEvent
    data class Disconnected(val endpointId: String) : TransportEvent
    data class Received(val endpointId: String, val payload: String) : TransportEvent
    data class Error(val message: String) : TransportEvent
}

/**
 * Star-topology link: one host, many clients. Implementations emit [events] from any thread
 * and must make [send] safe to call from the main thread.
 */
interface Transport {
    val events: SharedFlow<TransportEvent>

    /** Short user-facing description of how to reach the host (e.g. an IP address), if any. */
    val hostAddress: String? get() = null

    fun startHosting(hostName: String)
    fun startDiscovery()
    fun stopDiscovery()
    fun connect(endpointId: String, myName: String)
    fun send(endpointId: String, payload: String)
    fun disconnect(endpointId: String)
    fun stop()
}
