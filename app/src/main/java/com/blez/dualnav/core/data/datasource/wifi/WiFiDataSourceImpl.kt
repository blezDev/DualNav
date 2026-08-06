package com.blez.dualnav.core.data.datasource.wifi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.data.datasource.WiFiDataSource
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Plain TCP over the local network (phone hotspot / shared WiFi), with NSD (mDNS) used only to
 * find and advertise peers — same server+client duality as [com.blez.dualnav.core.data.datasource.bluetooth.BluetoothDataSourceImpl].
 */
class WiFiDataSourceImpl(
    private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
    private val logger: Logger
) : WiFiDataSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private var serverSocket: ServerSocket? = null
    private var connectedSocket: Socket? = null
    private var output: OutputStream? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile
    private var registeredServiceName: String? = null

    override suspend fun connect(): EmptyResult<DataError.Connection> {
        return try {
            withContext(Dispatchers.IO) { startAcceptLoop() }
            registerService(preferencesDataSource.getDeviceInfo().first()?.deviceId)
            Result.Success(Unit)
        } catch (e: IOException) {
            Result.Error(DataError.Connection.WIFI_UNAVAILABLE)
        }
    }

    override suspend fun disconnect(): EmptyResult<DataError.Connection> {
        registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        registrationListener = null
        runCatching { connectedSocket?.close() }
        runCatching { serverSocket?.close() }
        connectedSocket = null
        serverSocket = null
        output = null
        _connectionStatus.value = ConnectionStatus.Disconnected
        return Result.Success(Unit)
    }

    override suspend fun discoverDevices(): Result<List<DeviceInfo>, DataError.Connection> {
        return try {
            Result.Success(discoverNearbyServices())
        } catch (e: Exception) {
            Result.Error(DataError.Connection.WIFI_UNAVAILABLE)
        }
    }

    override suspend fun connectToDevice(deviceId: String): EmptyResult<DataError.Connection> {
        val (host, port) = deviceId.parseHostPort() ?: return Result.Error(DataError.Connection.DEVICE_NOT_FOUND)
        return try {
            val socket = withContext(Dispatchers.IO) { Socket(host, port) }
            attachConnectedSocket(socket)
            Result.Success(Unit)
        } catch (e: IOException) {
            Result.Error(DataError.Connection.DEVICE_NOT_FOUND)
        }
    }

    override suspend fun sendMessage(message: String): EmptyResult<DataError.Connection> {
        val stream = output ?: return Result.Error(DataError.Connection.NOT_CONNECTED)
        return try {
            withContext(Dispatchers.IO) {
                stream.write((message + "\n").toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            Result.Success(Unit)
        } catch (e: IOException) {
            Result.Error(DataError.Connection.NOT_CONNECTED)
        }
    }

    override fun receiveMessage(): Flow<String> = _messages.asSharedFlow()

    override fun getConnectionStatus(): Flow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private fun startAcceptLoop() {
        if (serverSocket != null) return
        val server = ServerSocket(WiFiConstants.PORT)
        serverSocket = server
        scope.launch {
            try {
                while (true) {
                    val socket = withContext(Dispatchers.IO) { server.accept() }
                    attachConnectedSocket(socket)
                }
            } catch (e: IOException) {
                logger.warn("Accept loop ended", e)
            }
        }
    }

    private fun registerService(stableId: String?) {
        val requestedName = localDeviceName()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = requestedName
            serviceType = WiFiConstants.SERVICE_TYPE
            port = WiFiConstants.PORT
            if (stableId != null) setAttribute(WiFiConstants.STABLE_ID_ATTRIBUTE, stableId)
        }
        val listener = object : NsdManager.RegistrationListener {
            // NSD may rename us on registration (e.g. "(2)" suffix) if our requested name collides
            // with something already on the network — track the name it actually assigned so
            // discovery can reliably filter this device's own advertised service back out.
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private suspend fun discoverNearbyServices(): List<DeviceInfo> {
        val resolved = mutableListOf<DeviceInfo>()
        val selfServiceName = registeredServiceName ?: localDeviceName()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceName == selfServiceName) return
                resolveService(service) { resolved += it }
            }
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsdManager.discoverServices(WiFiConstants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        delay(WiFiConstants.DISCOVERY_WINDOW_MS)
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }

        return resolved.filterNot { it.deviceName == selfServiceName }.distinctBy { it.deviceId }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(service: NsdServiceInfo, onResolved: (DeviceInfo) -> Unit) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                onResolved(
                    DeviceInfo(
                        deviceId = "$host:${info.port}",
                        deviceName = info.serviceName,
                        role = AppRole.COMPANION,
                        connectionType = ConnectionType.WIFI,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = false,
                        stableId = info.attributes[WiFiConstants.STABLE_ID_ATTRIBUTE]?.toString(
                            Charsets.UTF_8
                        )
                    )
                )
            }
        })
    }

    private fun attachConnectedSocket(socket: Socket) {
        connectedSocket = socket
        output = socket.getOutputStream()
        _connectionStatus.value = ConnectionStatus.Connected
        scope.launch { readLoop(socket) }
    }

    private suspend fun readLoop(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                _messages.emit(line)
            }
        } catch (e: IOException) {
            logger.warn("Connection read loop ended", e)
        } finally {
            if (connectedSocket === socket) {
                connectedSocket = null
                output = null
                _connectionStatus.value = ConnectionStatus.Disconnected
            }
        }
    }

    /**
     * Must match the self-identity name persisted by [com.blez.dualnav.core.domain.usecase.EnsureLocalDeviceIdentityUseCase]
     * (also just [Build.MODEL]) — that's the name sent in the pairing handshake and persisted as
     * the peer's [com.blez.dualnav.core.domain.model.DeviceInfo.deviceName], and reconnecting
     * after a stale IP re-matches the peer by that same name via a fresh NSD scan. A per-device
     * suffix here would make that match impossible.
     */
    private fun localDeviceName(): String = Build.MODEL

    private fun String.parseHostPort(): Pair<String, Int>? {
        val parts = split(":")
        if (parts.size != 2) return null
        val port = parts[1].toIntOrNull() ?: return null
        return parts[0] to port
    }
}
