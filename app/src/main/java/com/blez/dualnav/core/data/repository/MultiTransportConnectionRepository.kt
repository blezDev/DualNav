package com.blez.dualnav.core.data.repository

import com.blez.dualnav.core.data.datasource.BluetoothDataSource
import com.blez.dualnav.core.data.datasource.FirebaseDataSource
import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.data.datasource.WiFiDataSource
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.PairingState
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.MessageProtocol
import com.blez.dualnav.core.domain.util.PairingRequest
import com.blez.dualnav.core.domain.util.Result
import com.blez.dualnav.core.domain.util.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class MultiTransportConnectionRepository(
    private val bluetoothDataSource: BluetoothDataSource,
    private val wifiDataSource: WiFiDataSource,
    private val firebaseDataSource: FirebaseDataSource,
    private val preferencesDataSource: PreferencesDataSource,
    private val deviceRepository: DeviceRepository,
    private val logger: Logger,
    private val json: Json
) : ConnectionRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)

    @Volatile private var isManualDisconnect = true
    @Volatile private var lastPairedDeviceId: String? = null
    @Volatile private var lastDiscoveredDevices: List<DeviceInfo> = emptyList()
    @Volatile
    private var pendingIncomingRequest: PairingRequest? = null

    init {
        watchForUnexpectedDisconnects()
        watchIncomingPairingRequests()
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

    /**
     * Control-only entry point. Every WiFi pairing initiated from the UI goes through the PIN
     * handshake, even for a previously-paired device — once the user has explicitly
     * disconnected, the old pairing no longer counts and the Companion's human has to accept
     * again. (Automatic reconnection after an unexpected drop or app relaunch bypasses this
     * entirely via [resumeConnection]/[reconnectOnce], which never disconnected in the first
     * place.) Bluetooth/Firebase, for now, still just dial directly.
     */
    override suspend fun initiatePairing(deviceId: String): EmptyResult<DataError> {
        val connectionType = preferencesDataSource.getConnectionType().first()
            ?: return Result.Error(DataError.Connection.NOT_CONNECTED)

        if (connectionType != ConnectionType.WIFI) {
            return legacyPairDevice(deviceId, connectionType)
        }

        val connectResult = wifiDataSource.connectToDevice(deviceId)
        if (connectResult is Result.Error) return connectResult

        val localInfo = preferencesDataSource.getDeviceInfo().first()
        val pin = generatePin()
        val remoteName =
            lastDiscoveredDevices.firstOrNull { it.deviceId == deviceId }?.deviceName ?: deviceId

        _pairingState.value = PairingState.AwaitingConfirmation(deviceName = remoteName, pin = pin)

        val requestMessage = MessageProtocol.wrapPairingRequest(
            deviceName = localInfo?.deviceName.orEmpty(),
            pin = pin,
            senderId = localInfo?.deviceId.orEmpty(),
            receiverId = deviceId,
            json = json
        )
        logger.info("Sending pairing request to $deviceId: $requestMessage")
        val sendResult = wifiDataSource.sendMessage(requestMessage)
        if (sendResult is Result.Error) {
            logger.warn("Failed to send pairing request to $deviceId")
            _pairingState.value = PairingState.Idle
            wifiDataSource.disconnect()
            return sendResult
        }

        val accepted = withTimeoutOrNull(PAIRING_TIMEOUT_MS) {
            wifiDataSource.receiveMessage()
                .mapNotNull { MessageProtocol.unwrapPairingResponse(it, json) }.first()
        }
        _pairingState.value = PairingState.Idle

        return when (accepted) {
            true -> {
                lastPairedDeviceId = deviceId
                persistPairedDevice(deviceId, connectionType)
                Result.Success(Unit)
            }

            false -> {
                wifiDataSource.disconnect()
                Result.Error(DataError.Connection.PAIRING_REJECTED)
            }

            null -> {
                wifiDataSource.disconnect()
                Result.Error(DataError.Connection.TIMEOUT)
            }
        }
    }

    override suspend fun cancelPairing(): EmptyResult<DataError> {
        _pairingState.value = PairingState.Idle
        pendingIncomingRequest = null
        return disconnect()
    }

    /** Companion-only: answers the request currently reflected by [getPairingState]. */
    override suspend fun respondToPairingRequest(accepted: Boolean): EmptyResult<DataError> {
        val connectionType = preferencesDataSource.getConnectionType().first()
            ?: return Result.Error(DataError.Connection.NOT_CONNECTED)
        val request = pendingIncomingRequest
        val localInfo = preferencesDataSource.getDeviceInfo().first()

        val sendResult = wifiDataSource.sendMessage(
            MessageProtocol.wrapPairingResponse(
                accepted = accepted,
                senderId = localInfo?.deviceId.orEmpty(),
                receiverId = request?.senderId.orEmpty(),
                json = json
            )
        )

        _pairingState.value = PairingState.Idle
        pendingIncomingRequest = null

        if (!accepted) {
            wifiDataSource.disconnect()
            return sendResult
        }

        if (request != null) {
            deviceRepository.savePairedDevice(
                DeviceInfo(
                    deviceId = request.senderId,
                    deviceName = request.deviceName,
                    role = AppRole.CONTROL,
                    connectionType = connectionType,
                    lastSeen = System.currentTimeMillis(),
                    isConnected = true
                )
            )
        }
        deviceRepository.setConnectionEstablished(true)
        return sendResult
    }

    override fun getPairingState(): Flow<PairingState> = _pairingState.asStateFlow()

    override fun getConnectionStatus(): Flow<ConnectionStatus> {
        // Masks a raw-Connected socket as Reconnecting while a pairing handshake hasn't been
        // confirmed yet, so nothing downstream (e.g. the Continue button) treats it as usable.
        return combine(rawConnectionStatus(), _pairingState) { raw, pairing ->
            if (raw is ConnectionStatus.Connected && pairing is PairingState.AwaitingConfirmation) {
                ConnectionStatus.Reconnecting
            } else {
                raw
            }
        }
    }

    override suspend fun disconnect(): EmptyResult<DataError> {
        isManualDisconnect = true
        deviceRepository.setConnectionEstablished(false)
        return when (preferencesDataSource.getConnectionType().first()) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.disconnect()
            ConnectionType.WIFI -> wifiDataSource.disconnect()
            ConnectionType.FIREBASE -> firebaseDataSource.disconnect()
            null -> Result.Success(Unit)
        }
    }

    /** Re-dials the last paired device (or just restarts listening, if we were the acceptor) using
     * the persisted role/connection type — used when landing directly on a home screen. */
    override suspend fun resumeConnection(): EmptyResult<DataError> {
        val connectionType = preferencesDataSource.getConnectionType().first()
            ?: return Result.Error(DataError.Connection.NOT_CONNECTED)
        isManualDisconnect = false
        lastPairedDeviceId = (deviceRepository.getPairedDevice() as? Result.Success)?.data?.deviceId
        return reconnectOnce(connectionType)
    }

    private suspend fun connect(connectionType: ConnectionType): EmptyResult<DataError.Connection> {
        return when (connectionType) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.connect()
            ConnectionType.WIFI -> wifiDataSource.connect()
            ConnectionType.FIREBASE -> firebaseDataSource.connect()
        }
    }

    /** Bluetooth/Firebase, for now, until they get their own PIN handshake — dials directly, no confirmation. */
    private suspend fun legacyPairDevice(
        deviceId: String,
        connectionType: ConnectionType
    ): EmptyResult<DataError> {
        val result = when (connectionType) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.pairDevice(deviceId)
            ConnectionType.WIFI -> wifiDataSource.connectToDevice(deviceId)
            ConnectionType.FIREBASE -> Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        }
        result.onSuccess {
            lastPairedDeviceId = deviceId
            persistPairedDevice(deviceId, connectionType)
        }
        return result
    }

    private fun generatePin(): String = (1000..9999).random().toString()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun rawConnectionStatus(): Flow<ConnectionStatus> {
        return preferencesDataSource.getConnectionType().flatMapLatest { connectionType ->
            when (connectionType) {
                ConnectionType.BLUETOOTH -> bluetoothDataSource.getConnectionStatus()
                ConnectionType.WIFI -> wifiDataSource.getConnectionStatus()
                ConnectionType.FIREBASE -> firebaseDataSource.getConnectionStatus()
                null -> flowOf(ConnectionStatus.Disconnected)
            }
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
        deviceRepository.setConnectionEstablished(true)
    }

    /** Companion-only in practice: Control never receives a pairing request from itself. */
    private fun watchIncomingPairingRequests() {
        repositoryScope.launch {
            wifiDataSource.receiveMessage()
                .onEach { logger.info("WiFi message received: $it") }
                .mapNotNull { MessageProtocol.unwrapPairingRequest(it, json) }
                .collect { request ->
                    logger.info("Pairing request parsed from ${request.senderId}: ${request.deviceName} / ${request.pin}")
                    pendingIncomingRequest = request
                    _pairingState.value =
                        PairingState.AwaitingConfirmation(request.deviceName, request.pin)
                }
        }
    }

    /**
     * A deliberate [disconnect] sets [isManualDisconnect] so this watcher leaves it alone;
     * anything else (a dropped socket, the app relaunching after a saved role) retries the
     * last connection with capped exponential backoff until it succeeds or a manual disconnect
     * happens. Also clears a stale pairing prompt if the socket drops mid-handshake.
     */
    private fun watchForUnexpectedDisconnects() {
        repositoryScope.launch {
            rawConnectionStatus().distinctUntilChanged().collect { status ->
                if (status == ConnectionStatus.Disconnected) {
                    if (_pairingState.value is PairingState.AwaitingConfirmation) {
                        _pairingState.value = PairingState.Idle
                        pendingIncomingRequest = null
                    }
                    if (!isManualDisconnect) reconnectWithBackoff()
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
        const val PAIRING_TIMEOUT_MS = 30_000L
    }
}
