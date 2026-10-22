package com.killteamtimer.net

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow

/** Google Nearby Connections transport (Bluetooth / Wi-Fi Direct, no shared network needed). */
class NearbyTransport(context: Context) : Transport {

    override val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)

    private val client = Nearby.getConnectionsClient(context)

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            events.tryEmit(TransportEvent.Received(endpointId, String(bytes, Charsets.UTF_8)))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Everyone at the table is trusted; accept without a confirmation prompt.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                events.tryEmit(TransportEvent.Connected(endpointId))
            } else {
                events.tryEmit(TransportEvent.Error("Connection failed: ${describe(resolution.status.statusCode)}"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            events.tryEmit(TransportEvent.Disconnected(endpointId))
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == SERVICE_ID) {
                events.tryEmit(TransportEvent.EndpointFound(endpointId, info.endpointName))
            }
        }

        override fun onEndpointLost(endpointId: String) {
            events.tryEmit(TransportEvent.EndpointLost(endpointId))
        }
    }

    override fun startHosting(hostName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(hostName, SERVICE_ID, lifecycle, options)
            .addOnFailureListener { reportFailure("Couldn't start hosting", it) }
    }

    override fun startDiscovery() {
        client.stopDiscovery()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnFailureListener { reportFailure("Couldn't search for hosts", it) }
    }

    override fun stopDiscovery() = client.stopDiscovery()

    override fun connect(endpointId: String, myName: String) {
        client.requestConnection(myName, endpointId, lifecycle)
            .addOnFailureListener { reportFailure("Couldn't connect", it) }
    }

    override fun send(endpointId: String, payload: String) {
        client.sendPayload(endpointId, Payload.fromBytes(payload.toByteArray(Charsets.UTF_8)))
    }

    override fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        // Nearby doesn't call onDisconnected for locally initiated disconnects.
        events.tryEmit(TransportEvent.Disconnected(endpointId))
    }

    override fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private fun reportFailure(what: String, e: Exception) {
        val code = (e as? ApiException)?.statusCode
        if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT ||
            code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING ||
            code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING
        ) return
        val reason = code?.let(::describe) ?: e.message ?: "unknown error"
        events.tryEmit(TransportEvent.Error("$what: $reason"))
    }

    private fun describe(code: Int): String = ConnectionsStatusCodes.getStatusCodeString(code)

    private companion object {
        const val SERVICE_ID = "com.killteamtimer"
        val STRATEGY: Strategy = Strategy.P2P_STAR
    }
}
