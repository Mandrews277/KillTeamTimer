package com.killteamtimer.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Same-Wi-Fi transport. The host listens on a TCP socket and advertises it via NSD (mDNS);
 * clients discover it or dial an IP directly. Messages are newline-delimited UTF-8 JSON.
 * Client-side endpoint ids are "address:port", so they stay stable across reconnects.
 */
class LanTransport(context: Context) : Transport {

    override val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val io = Executors.newCachedThreadPool()
    private val resolver = Executors.newSingleThreadExecutor()
    private val connections = ConcurrentHashMap<String, Connection>()
    private val clientCounter = AtomicInteger()
    private val serviceIds = ConcurrentHashMap<String, String>()

    @Volatile private var server: ServerSocket? = null
    @Volatile private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var discovery: NsdManager.DiscoveryListener? = null

    override val hostAddress: String?
        get() = server?.let { s -> localIpv4()?.let { "$it:${s.localPort}" } }

    override fun startHosting(hostName: String) {
        io.execute {
            val socket = try {
                ServerSocket(DEFAULT_PORT)
            } catch (_: IOException) {
                ServerSocket(0)
            }
            server = socket
            register(hostName, socket.localPort)
            try {
                while (!socket.isClosed) {
                    val client = socket.accept().apply { tcpNoDelay = true }
                    val id = "c${clientCounter.incrementAndGet()}"
                    val conn = Connection(id, client)
                    connections[id] = conn
                    events.tryEmit(TransportEvent.Connected(id))
                    io.execute { conn.readLoop() }
                }
            } catch (_: IOException) {
                // Server closed.
            }
        }
    }

    private fun register(name: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                events.tryEmit(TransportEvent.Error("Wi-Fi advertising failed ($errorCode); join by IP instead"))
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun startDiscovery() {
        if (discovery != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discovery = null
                events.tryEmit(TransportEvent.Error("Wi-Fi discovery failed ($errorCode)"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                serviceIds.remove(info.serviceName)?.let { events.tryEmit(TransportEvent.EndpointLost(it)) }
            }
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** NSD only allows one resolve at a time, so resolves are queued on a single thread. */
    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) = resolver.execute {
        val done = CountDownLatch(1)
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = done.countDown()
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val address = resolved.host?.hostAddress
                if (address != null) {
                    val id = "$address:${resolved.port}"
                    serviceIds[resolved.serviceName] = id
                    events.tryEmit(TransportEvent.EndpointFound(id, resolved.serviceName))
                }
                done.countDown()
            }
        })
        done.await(5, TimeUnit.SECONDS)
    }

    override fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }

    override fun connect(endpointId: String, myName: String) {
        if (connections.containsKey(endpointId)) return
        val split = endpointId.lastIndexOf(':')
        val host = if (split > 0) endpointId.substring(0, split) else endpointId
        val port = if (split > 0) endpointId.substring(split + 1).toIntOrNull() ?: DEFAULT_PORT else DEFAULT_PORT
        io.execute {
            try {
                val socket = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                }
                val conn = Connection(endpointId, socket)
                connections[endpointId] = conn
                events.tryEmit(TransportEvent.Connected(endpointId))
                conn.readLoop()
            } catch (e: IOException) {
                events.tryEmit(TransportEvent.Error("Couldn't reach $host:$port"))
            }
        }
    }

    override fun send(endpointId: String, payload: String) {
        connections[endpointId]?.send(payload)
    }

    override fun disconnect(endpointId: String) {
        connections[endpointId]?.close()
    }

    override fun stop() {
        stopDiscovery()
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        runCatching { server?.close() }
        server = null
        connections.values.toList().forEach { it.close() }
        io.shutdownNow()
        resolver.shutdownNow()
    }

    private inner class Connection(val id: String, private val socket: Socket) {
        private val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
        private val sender = Executors.newSingleThreadExecutor()
        private val closed = AtomicBoolean(false)

        fun send(payload: String) {
            if (closed.get()) return
            sender.execute {
                try {
                    writer.write(payload)
                    writer.newLine()
                    writer.flush()
                } catch (_: IOException) {
                    close()
                }
            }
        }

        fun readLoop() {
            try {
                socket.getInputStream().bufferedReader(Charsets.UTF_8).forEachLine {
                    events.tryEmit(TransportEvent.Received(id, it))
                }
            } catch (_: IOException) {
                // Fall through to close.
            }
            close()
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { socket.close() }
            sender.shutdown()
            connections.remove(id, this)
            events.tryEmit(TransportEvent.Disconnected(id))
        }
    }

    companion object {
        const val DEFAULT_PORT = 47820
        private const val SERVICE_TYPE = "_ktimer._tcp"
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** Best guess at this phone's LAN address, preferring Wi-Fi interfaces. */
        fun localIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedByDescending { it.name.startsWith("wlan") }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()?.hostAddress
        }.getOrNull()
    }
}
