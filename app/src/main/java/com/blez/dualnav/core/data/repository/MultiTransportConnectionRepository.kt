package com.blez.dualnav.core.data.repository

import com.blez.dualnav.core.data.datasource.BluetoothDataSource
import com.blez.dualnav.core.data.datasource.FirebaseDataSource
import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.data.datasource.WiFiDataSource
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.Result
import com.blez.dualnav.core.domain.util.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MultiTransportConnectionRepository(
    private val bluetoothDataSource: BluetoothDataSource,
    private val wifiDataSource: WiFiDataSource,
    private val firebaseDataSource: FirebaseDataSource,
    private val preferencesDataSource: PreferencesDataSource,
    private val deviceRepository: DeviceRepository,
    private val logger: Logger
) : ConnectionRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var isManualDisconnect = true
    @Volatile private var lastPairedDeviceId: String? = null
    @Volatile private var lastDiscoveredDevices: List<DeviceInfo> = emptyList()

    init {
        watchForUnexpectedDisconnects()
    }

    override suspend fun initializeConnection(
        deviceRole: AppRole,
        connectionType: ConnectionType
    ): EmptyResult<DataError> {
        isManualDisconnect = false
        lastPairedDeviceId = null
        preferencesDataSource.saveDeviceRole(deviceRole)
        preferencesDataSource.saveConnectionType(connectionType)
        return connect(connectionType)
    }

    override suspend fun discoverDevices(): Result<List<DeviceInfo>, DataError> {
        val result = when (preferencesDataSource.getConnectionType().first()) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.discoverDevices()
            ConnectionType.WIFI -> wifiDataSource.discoverDevices()
            ConnectionType.FIREBASE -> Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
            null -> Result.Error(DataError.Connection.NOT_CONNECTED)
        }
        result.onSuccess { lastDiscoveredDevices = it }
        return result
    }

    override suspend fun pairDevice(deviceId: String): EmptyResult<DataError> {
        val connectionType = preferencesDataSource.getConnectionType().first()
        val result = when (connectionType) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.pairDevice(deviceId)
            ConnectionType.WIFI -> wifiDataSource.connectToDevice(deviceId)
            ConnectionType.FIREBASE -> Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
            null -> Result.Error(DataError.Connection.NOT_CONNECTED)
        }
        result.onSuccess {
            lastPairedDeviceId = deviceId
            if (connectionType != null) persistPairedDevice(deviceId, connectionType)
        }
        return result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getConnectionStatus(): Flow<ConnectionStatus> {
        return preferencesDataSource.getConnectionType().flatMapLatest { connectionType ->
            when (connectionType) {
                ConnectionType.BLUETOOTH -> bluetoothDataSource.getConnectionStatus()
                ConnectionType.WIFI -> wifiDataSource.getConnectionStatus()
                ConnectionType.FIREBASE -> firebaseDataSource.getConnectionStatus()
                null -> flowOf(ConnectionStatus.Disconnected)
            }
        }
    }

    override suspend fun disconnect(): EmptyResult<DataError> {
        isManualDisconnect = true
        return when (preferencesDataSource.getConnectionType().first()) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.disconnect()
            ConnectionType.WIFI -> wifiDataSource.disconnect()
            ConnectionType.FIREBASE -> firebaseDataSource.disconnect()
            null -> Result.Success(Unit)
        }
    }

    private suspend fun connect(connectionType: ConnectionType): EmptyResult<DataError.Connection> {
        return when (connectionType) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.connect()
            ConnectionType.WIFI -> wifiDataSource.connect()
            ConnectionType.FIREBASE -> firebaseDataSource.connect()
        }
    }

    private suspend fun persistPairedDevice(deviceId: String, connectionType: ConnectionType) {
        val role = deviceRepository.getDeviceRole()
        val pairedRole = if (role == AppRole.CONTROL) AppRole.COMPANION else AppRole.CONTROL
        val known = lastDiscoveredDevices.firstOrNull { it.deviceId == deviceId }
        val info = DeviceInfo(
            deviceId = deviceId,
            deviceName = known?.deviceName ?: deviceId,
            role = pairedRole,
            connectionType = connectionType,
            lastSeen = System.currentTimeMillis(),
            isConnected = true
        )
        deviceRepository.savePairedDevice(info)
    }

    /**
     * A deliberate [disconnect] sets [isManualDisconnect] so this watcher leaves it alone;
     * anything else (a dropped socket, the app relaunching after a saved role) retries the
     * last connection with capped exponential backoff until it succeeds or a manual disconnect
     * happens.
     */
    private fun watchForUnexpectedDisconnects() {
        repositoryScope.launch {
            getConnectionStatus().distinctUntilChanged().collect { status ->
                if (status == ConnectionStatus.Disconnected && !isManualDisconnect) {
                    reconnectWithBackoff()
                }
            }
        }
    }

    private suspend fun reconnectWithBackoff() {
        var delayMs = INITIAL_BACKOFF_MS
        while (!isManualDisconnect) {
            delay(delayMs)
            if (isManualDisconnect) return

            val connectionType = preferencesDataSource.getConnectionType().first() ?: return
            logger.info("Attempting to reconnect via $connectionType")
            val result = reconnectOnce(connectionType)
            if (result is Result.Success) {
                logger.info("Reconnected via $connectionType")
                return
            }
            delayMs = (delayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private suspend fun reconnectOnce(connectionType: ConnectionType): EmptyResult<DataError.Connection> {
        val deviceId = lastPairedDeviceId
        return when (connectionType) {
            ConnectionType.BLUETOOTH -> if (deviceId != null) bluetoothDataSource.pairDevice(deviceId) else bluetoothDataSource.connect()
            ConnectionType.WIFI -> if (deviceId != null) wifiDataSource.connectToDevice(deviceId) else wifiDataSource.connect()
            ConnectionType.FIREBASE -> firebaseDataSource.connect()
        }
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
