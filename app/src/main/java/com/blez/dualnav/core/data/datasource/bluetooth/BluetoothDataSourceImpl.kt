package com.blez.dualnav.core.data.datasource.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.blez.dualnav.core.data.datasource.BluetoothDataSource
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.coroutines.resume

/**
 * Bluetooth Classic (RFCOMM/SPP) transport. Every instance both listens for an incoming
 * connection (server role) and can dial out to a chosen peer (client role via [pairDevice]) —
 * which side actually ends up connecting is decided by the two apps' behavior, not this class.
 */
class BluetoothDataSourceImpl(
    private val context: Context,
    private val logger: Logger
) : BluetoothDataSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private var serverSocket: BluetoothServerSocket? = null
    private var connectedSocket: BluetoothSocket? = null
    private var output: OutputStream? = null

    override suspend fun connect(): EmptyResult<DataError.Connection> {
        val bluetoothAdapter = adapter ?: return Result.Error(DataError.Connection.BLUETOOTH_UNAVAILABLE)
        if (!bluetoothAdapter.isEnabled) return Result.Error(DataError.Connection.BLUETOOTH_UNAVAILABLE)
        if (!hasConnectPermission()) return Result.Error(DataError.Connection.PERMISSION_DENIED)

        startAcceptLoop(bluetoothAdapter)
        return Result.Success(Unit)
    }

    override suspend fun disconnect(): EmptyResult<DataError.Connection> {
        runCatching { connectedSocket?.close() }
        runCatching { serverSocket?.close() }
        connectedSocket = null
        serverSocket = null
        output = null
        _connectionStatus.value = ConnectionStatus.Disconnected
        return Result.Success(Unit)
    }

    override suspend fun discoverDevices(): Result<List<DeviceInfo>, DataError.Connection> {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            logger.warn("discoverDevices: no BluetoothAdapter available")
            return Result.Error(DataError.Connection.BLUETOOTH_UNAVAILABLE)
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            logger.warn("discoverDevices: missing permission (scan=${hasScanPermission()}, connect=${hasConnectPermission()})")
            return Result.Error(DataError.Connection.PERMISSION_DENIED)
        }

        val bonded = bondedDevices(bluetoothAdapter)
        logger.info("discoverDevices: ${bonded.size} bonded device(s): ${bonded.map { it.deviceName }}")
        val nearby = discoverNearbyDevices(bluetoothAdapter)
        logger.info("discoverDevices: ${nearby.size} freshly-scanned device(s): ${nearby.map { it.deviceName }}")
        val result = (bonded + nearby).distinctBy { it.deviceId }
        logger.info("discoverDevices: returning ${result.size} device(s) total")
        return Result.Success(result)
    }

    override suspend fun pairDevice(deviceAddress: String): EmptyResult<DataError.Connection> {
        val bluetoothAdapter = adapter ?: return Result.Error(DataError.Connection.BLUETOOTH_UNAVAILABLE)
        if (!hasConnectPermission()) return Result.Error(DataError.Connection.PERMISSION_DENIED)

        return try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            bluetoothAdapter.cancelDiscoverySafely()
            val socket = withContext(Dispatchers.IO) {
                val s = device.createRfcommSocketToServiceRecordSafely()
                s.connect()
                s
            }
            attachConnectedSocket(socket)
            Result.Success(Unit)
        } catch (e: IOException) {
            Result.Error(DataError.Connection.DEVICE_NOT_FOUND)
        } catch (e: SecurityException) {
            Result.Error(DataError.Connection.PERMISSION_DENIED)
        } catch (e: IllegalArgumentException) {
            // getRemoteDevice() throws this for anything that isn't a valid MAC address, e.g. a
            // stale non-Bluetooth id from a previous transport's pairing.
            logger.warn("pairDevice: '$deviceAddress' is not a valid Bluetooth address", e)
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

    private fun startAcceptLoop(bluetoothAdapter: BluetoothAdapter) {
        if (serverSocket != null) return
        scope.launch {
            try {
                val server = bluetoothAdapter.listenUsingRfcommSafely()
                serverSocket = server
                while (true) {
                    val socket = withContext(Dispatchers.IO) { server.accept() }
                    attachConnectedSocket(socket)
                }
            } catch (e: IOException) {
                logger.warn("Accept loop ended", e)
            }
        }
    }

    private fun attachConnectedSocket(socket: BluetoothSocket) {
        connectedSocket = socket
        output = socket.outputStream
        _connectionStatus.value = ConnectionStatus.Connected
        scope.launch { readLoop(socket) }
    }

    private suspend fun readLoop(socket: BluetoothSocket) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
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

    @SuppressLint("MissingPermission")
    private fun bondedDevices(bluetoothAdapter: BluetoothAdapter): List<DeviceInfo> {
        return bluetoothAdapter.bondedDevices.orEmpty().map { it.toDeviceInfo(isConnected = false) }
    }

    private suspend fun discoverNearbyDevices(bluetoothAdapter: BluetoothAdapter): List<DeviceInfo> =
        suspendCancellableCoroutine { continuation ->
            val found = mutableListOf<DeviceInfo>()
            val receiver = object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = intent.bluetoothDeviceExtra()
                            logger.info("discoverNearbyDevices: ACTION_FOUND ${device?.name} / ${device?.address}")
                            device?.let { found += it.toDeviceInfo(isConnected = false) }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            logger.info("discoverNearbyDevices: discovery finished, found ${found.size} device(s)")
                            runCatching { context.unregisterReceiver(this) }
                            if (continuation.isActive) continuation.resume(found.distinctBy { it.deviceId })
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            // Both actions are OS-protected broadcasts (only the system can ever send them), so
            // EXPORTED can't be spoofed by another app here - some OEM builds have been observed
            // to simply never deliver these to a NOT_EXPORTED dynamically-registered receiver.
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            val started = bluetoothAdapter.startDiscoverySafely()
            logger.info("discoverNearbyDevices: startDiscovery() returned $started")
            if (!started) {
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) continuation.resume(emptyList())
            }

            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
                bluetoothAdapter.cancelDiscoverySafely()
            }
        }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDeviceInfo(isConnected: Boolean): DeviceInfo = DeviceInfo(
        deviceId = address,
        deviceName = name ?: address,
        role = AppRole.COMPANION,
        connectionType = ConnectionType.BLUETOOTH,
        lastSeen = System.currentTimeMillis(),
        isConnected = isConnected
    )

    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.startDiscoverySafely() = startDiscovery()

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.cancelDiscoverySafely() = cancelDiscovery()

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.createRfcommSocketToServiceRecordSafely(): BluetoothSocket =
        createRfcommSocketToServiceRecord(BluetoothConstants.SERVICE_UUID)

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.listenUsingRfcommSafely(): BluetoothServerSocket =
        listenUsingRfcommWithServiceRecord(BluetoothConstants.SERVICE_NAME, BluetoothConstants.SERVICE_UUID)
}
