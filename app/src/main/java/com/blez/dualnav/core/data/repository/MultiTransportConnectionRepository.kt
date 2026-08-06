package com.blez.dualnav.core.data.repository

import com.blez.dualnav.core.data.datasource.BluetoothDataSource
import com.blez.dualnav.core.data.datasource.FirebaseDataSource
import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.data.datasource.ReconnectionRegistryDataSource
import com.blez.dualnav.core.data.datasource.WiFiDataSource
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.PairingState
import com.blez.dualnav.core.domain.model.ReconnectionSession
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Hello
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.MessageProtocol
import com.blez.dualnav.core.domain.util.PairingRequest
import com.blez.dualnav.core.domain.util.Result
import com.blez.dualnav.core.domain.util.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class MultiTransportConnectionRepository(
    private val bluetoothDataSource: BluetoothDataSource,
    private val wifiDataSource: WiFiDataSource,
    private val firebaseDataSource: FirebaseDataSource,
    private val preferencesDataSource: PreferencesDataSource,
    private val deviceRepository: DeviceRepository,
    private val reconnectionRegistryDataSource: ReconnectionRegistryDataSource,
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
    private var relayPeerWaitJob: Job? = null

    /** Firebase's raw `.info/connected` only means "this phone is online" - true the instant
     * Cloud Relay is selected, well before any pairing code exists. Gates [rawConnectionStatus]
     * so Connected isn't reported (and Continue/auto-navigate don't fire) until a channel is
     * actually established on both sides. */
    @Volatile
    private var firebaseChannelReady = false

    init {
        watchForUnexpectedDisconnects()
        watchIncomingPairingRequests()
        watchIncomingBluetoothHellos()
        sendHelloOnBluetoothConnect()
    }

    override suspend fun initializeConnection(
        deviceRole: AppRole,
        connectionType: ConnectionType
    ): EmptyResult<DataError> {
        isManualDisconnect = false
        lastPairedDeviceId = null
        // Switching connection types (e.g. abandoning a Cloud Relay "waiting for peer" attempt
        // for Bluetooth instead) must not leave a stale AwaitingConfirmation/WaitingForRelayPeer
        // behind - getConnectionStatus() would keep masking the new transport's genuinely-
        // Connected status as Reconnecting forever, since nothing else ever clears it.
        _pairingState.value = PairingState.Idle
        relayPeerWaitJob?.cancel()
        relayPeerWaitJob = null
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
        relayPeerWaitJob?.cancel()
        relayPeerWaitJob = null
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
                    // Companion never dials CONTROL by address (see reconnectWifi) - this is only
                    // ever used as the Firebase-session peer id, not a socket target, so the
                    // sender's stable id doubles as deviceId here rather than a host:port.
                    deviceId = request.senderId,
                    deviceName = request.deviceName,
                    role = AppRole.CONTROL,
                    connectionType = connectionType,
                    lastSeen = System.currentTimeMillis(),
                    isConnected = true,
                    stableId = request.senderId
                )
            )
            repositoryScope.launch { syncSessionActive(request.senderId) }
        }
        deviceRepository.setConnectionEstablished(true)
        return sendResult
    }

    override suspend fun createRelayChannel(): Result<String, DataError> {
        val result = firebaseDataSource.createChannel()
        return when (result) {
            is Result.Error -> result
            is Result.Success -> {
                val code = result.data
                preferencesDataSource.saveConnectionType(ConnectionType.FIREBASE)
                deviceRepository.savePairedDevice(
                    DeviceInfo(
                        deviceId = code,
                        deviceName = "Cloud relay",
                        role = AppRole.COMPANION,
                        connectionType = ConnectionType.FIREBASE,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = false
                    )
                )
                _pairingState.value = PairingState.WaitingForRelayPeer(code)
                relayPeerWaitJob?.cancel()
                relayPeerWaitJob = repositoryScope.launch { awaitRelayPeer(code) }
                Result.Success(code)
            }
        }
    }

    /** Waits for Companion to join [code], then finalizes the pairing exactly like the WiFi PIN
     * handshake's acceptance does: persists Companion's stable id, marks the connection
     * established, and syncs the shared Firebase session - then clears [PairingState], which
     * un-masks [getConnectionStatus] so Control's screen (and its auto-navigate watcher) can
     * finally treat this as Connected. */
    private suspend fun awaitRelayPeer(code: String) {
        logger.info("awaitRelayPeer: waiting for companion to join code=$code")
        val companionUid = firebaseDataSource.observePeerJoined(code).first()
        logger.info("awaitRelayPeer: companion joined with uid=$companionUid")
        val existing = (deviceRepository.getPairedDevice() as? Result.Success)?.data
        val info = (existing ?: DeviceInfo(
            deviceId = code,
            deviceName = "Cloud relay",
            role = AppRole.COMPANION,
            connectionType = ConnectionType.FIREBASE,
            lastSeen = System.currentTimeMillis(),
            isConnected = false
        )).copy(stableId = companionUid, isConnected = true, lastSeen = System.currentTimeMillis())
        deviceRepository.savePairedDevice(info)
        deviceRepository.setConnectionEstablished(true)
        syncSessionActive(companionUid)
        firebaseChannelReady = true
        _pairingState.value = PairingState.Idle
    }

    override suspend fun joinRelayChannel(code: String): EmptyResult<DataError> {
        val result = firebaseDataSource.joinChannel(code)
        return when (result) {
            is Result.Error -> result
            is Result.Success -> {
                val controlUid = result.data
                preferencesDataSource.saveConnectionType(ConnectionType.FIREBASE)
                deviceRepository.savePairedDevice(
                    DeviceInfo(
                        deviceId = code,
                        deviceName = "Cloud relay",
                        role = AppRole.CONTROL,
                        connectionType = ConnectionType.FIREBASE,
                        lastSeen = System.currentTimeMillis(),
                        isConnected = true,
                        stableId = controlUid
                    )
                )
                deviceRepository.setConnectionEstablished(true)
                repositoryScope.launch { syncSessionActive(controlUid) }
                firebaseChannelReady = true
                Result.Success(Unit)
            }
        }
    }

    override fun getPairingState(): Flow<PairingState> = _pairingState.asStateFlow()

    override fun getConnectionStatus(): Flow<ConnectionStatus> {
        // Masks a raw-Connected socket as Reconnecting while a pairing handshake hasn't been
        // confirmed yet (WiFi), or while Control is still waiting for Companion to join its relay
        // code (Firebase) - Firebase's raw status only reflects this device's own online-ness, not
        // whether the peer has actually joined, so nothing downstream (e.g. the Continue button,
        // or ConnectionSetupViewModel's auto-navigate) treats it as usable too early.
        return combine(rawConnectionStatus(), _pairingState) { raw, pairing ->
            val awaitingPeer =
                pairing is PairingState.AwaitingConfirmation || pairing is PairingState.WaitingForRelayPeer
            if (raw is ConnectionStatus.Connected && awaitingPeer) {
                ConnectionStatus.Reconnecting
            } else {
                raw
            }
        }
    }

    /**
     * An explicit disconnect from either side marks the shared session as intentionally ended in
     * Firebase, so the other phone won't keep trying to reconnect — even after being fully closed
     * and relaunched.
     */
    override suspend fun disconnect(): EmptyResult<DataError> {
        markSessionEnded()
        return tearDownTransport()
    }

    /** Tears down the local transport without touching the shared Firebase record — used both by
     * [disconnect] (after it writes its own ENDED record) and by [acknowledgeRemoteDisconnect]
     * (where the peer's ENDED record is already authoritative and shouldn't be overwritten). */
    private suspend fun tearDownTransport(): EmptyResult<DataError> {
        isManualDisconnect = true
        relayPeerWaitJob?.cancel()
        relayPeerWaitJob = null
        firebaseChannelReady = false
        deviceRepository.setConnectionEstablished(false)
        return when (preferencesDataSource.getConnectionType().first()) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.disconnect()
            ConnectionType.WIFI -> wifiDataSource.disconnect()
            ConnectionType.FIREBASE -> firebaseDataSource.disconnect()
            null -> Result.Success(Unit)
        }
    }

    override suspend fun acknowledgeRemoteDisconnect(): EmptyResult<DataError> = tearDownTransport()

    /** Live: resolves the current pairing once (role + local/peer device IDs) and then follows the
     * shared Firebase record for it, emitting whenever the *other* role records an ENDED status —
     * so a still-connected phone finds out immediately, without waiting on the socket to drop.
     * Retries indefinitely on any failure (e.g. a transient anonymous-auth hiccup right as this
     * device lands on its home screen) - a single upstream sign-in failure would otherwise close
     * this cold flow forever, since it's only collected once per ViewModel lifetime. */
    override fun observeRemoteSessionEnded(): Flow<Unit> = flow {
        val localId = preferencesDataSource.getDeviceInfo().first()?.deviceId ?: return@flow
        val role = deviceRepository.getDeviceRole() ?: return@flow
        val peerId = peerSessionId() ?: return@flow
        val (controlId, companionId) = if (role == AppRole.CONTROL) localId to peerId else peerId to localId
        emitAll(
            reconnectionRegistryDataSource.observeSession(controlId, companionId)
                .mapNotNull { session ->
                    val endedByOther = session?.status == ReconnectionSession.Status.ENDED &&
                            session.endedByRole != null &&
                            session.endedByRole != role
                    if (endedByOther) Unit else null
                }
        )
    }.retryWhen { cause, _ ->
        logger.warn("Remote-session-ended listener failed, retrying", cause)
        delay(REMOTE_LISTENER_RETRY_DELAY_MS)
        true
    }

    /** Re-dials the last paired device (or just restarts listening, if we were the acceptor) using
     * the persisted role/connection type — used when landing directly on a home screen, including
     * right after a fresh process start. Bails out if the other side recorded an explicit end to
     * this session in Firebase, even if this device never disconnected locally. */
    override suspend fun resumeConnection(): EmptyResult<DataError> {
        val connectionType = preferencesDataSource.getConnectionType().first()
            ?: return Result.Error(DataError.Connection.NOT_CONNECTED)
        val sessionPeerId = peerSessionId()
        if (sessionPeerId != null && !shouldAttemptReconnect(sessionPeerId)) {
            isManualDisconnect = true
            deviceRepository.setConnectionEstablished(false)
            return Result.Error(DataError.Connection.SESSION_ENDED)
        }
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
                ConnectionType.FIREBASE -> firebaseDataSource.getConnectionStatus().map { status ->
                    if (status is ConnectionStatus.Connected && !firebaseChannelReady) {
                        ConnectionStatus.Reconnecting
                    } else {
                        status
                    }
                }
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
            isConnected = true,
            stableId = known?.stableId
        )
        deviceRepository.savePairedDevice(info)
        deviceRepository.setConnectionEstablished(true)
        // Firebase session tracking keys off the peer's stable identity, not a WiFi host:port -
        // otherwise Control and Companion would independently compute two different session
        // nodes and never see each other's writes.
        repositoryScope.launch { syncSessionActive(known?.stableId ?: deviceId) }
    }

    /** Marks the shared Firebase session ACTIVE for [peerDeviceId] and the local device, so both
     * sides know a future reconnect attempt (relaunch or unexpected drop) is expected. Best-effort:
     * failures here don't affect the pairing result itself. */
    private suspend fun syncSessionActive(peerDeviceId: String) {
        val (controlId, companionId) = controlAndCompanionIds(peerDeviceId) ?: return
        reconnectionRegistryDataSource.upsertSession(
            ReconnectionSession(
                controlDeviceId = controlId,
                companionDeviceId = companionId,
                status = ReconnectionSession.Status.ACTIVE
            )
        )
    }

    /** Records that the local device explicitly ended this session, so the other phone won't keep
     * trying to reconnect to it — even after its app is fully closed and relaunched. Awaited (with
     * a timeout) rather than fire-and-forget, since this is the authoritative "stop" signal. */
    private suspend fun markSessionEnded() {
        val peerId = peerSessionId() ?: return
        val role = deviceRepository.getDeviceRole() ?: return
        val (controlId, companionId) = controlAndCompanionIds(peerId) ?: return
        withTimeoutOrNull(REMOTE_SESSION_TIMEOUT_MS) {
            reconnectionRegistryDataSource.upsertSession(
                ReconnectionSession(
                    controlDeviceId = controlId,
                    companionDeviceId = companionId,
                    status = ReconnectionSession.Status.ENDED,
                    endedByRole = role
                )
            )
        }
    }

    /**
     * False only when Firebase has an on-record, explicit end for this exact pairing. Defaults to
     * true (allow reconnecting) when there's no record yet or Firebase can't be reached, so this
     * never makes the underlying BT/WiFi transport depend on internet access.
     */
    private suspend fun shouldAttemptReconnect(peerId: String): Boolean {
        val (controlId, companionId) = controlAndCompanionIds(peerId) ?: return true
        val result = withTimeoutOrNull(REMOTE_SESSION_TIMEOUT_MS) {
            reconnectionRegistryDataSource.getSession(controlId, companionId)
        }
        val session = (result as? Result.Success)?.data ?: return true
        return session.status != ReconnectionSession.Status.ENDED
    }

    private suspend fun controlAndCompanionIds(peerDeviceId: String): Pair<String, String>? {
        val localId = preferencesDataSource.getDeviceInfo().first()?.deviceId ?: return null
        return when (deviceRepository.getDeviceRole()) {
            AppRole.CONTROL -> localId to peerDeviceId
            AppRole.COMPANION -> peerDeviceId to localId
            null -> null
        }
    }

    /** The peer's identity for Firebase session-tracking, as opposed to [DeviceInfo.deviceId] -
     * for WiFi the latter is a transient `host:port` that Control and Companion wouldn't
     * independently arrive at the same value for, which would silently split them onto two
     * different session records. Prefers the persistent [DeviceInfo.stableId], falling back to
     * [DeviceInfo.deviceId] for transports where that already is a stable identifier (Bluetooth's
     * MAC address, Firebase's own peer id). */
    private suspend fun peerSessionId(): String? {
        val paired = (deviceRepository.getPairedDevice() as? Result.Success)?.data ?: return null
        return paired.stableId ?: paired.deviceId
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
     * Bluetooth has no PIN handshake like WiFi's - Control dials Companion's MAC directly, so
     * Companion never otherwise learns Control's persistent identity, and neither side gets the
     * other's [DeviceInfo.stableId] for Firebase session-tracking. Both sides send this the moment
     * their raw status goes Connected (fresh pairing or a reconnect), so it's symmetric and
     * needs no coordination about who goes first.
     */
    private fun sendHelloOnBluetoothConnect() {
        repositoryScope.launch {
            rawConnectionStatus().distinctUntilChanged().collect { status ->
                if (status == ConnectionStatus.Connected &&
                    preferencesDataSource.getConnectionType().first() == ConnectionType.BLUETOOTH
                ) {
                    val localInfo = preferencesDataSource.getDeviceInfo().first() ?: return@collect
                    bluetoothDataSource.sendMessage(
                        MessageProtocol.wrapHello(
                            deviceName = localInfo.deviceName,
                            senderId = localInfo.deviceId,
                            receiverId = "",
                            json = json
                        )
                    )
                }
            }
        }
    }

    private fun watchIncomingBluetoothHellos() {
        repositoryScope.launch {
            bluetoothDataSource.receiveMessage()
                .mapNotNull { MessageProtocol.unwrapHello(it, json) }
                .collect { hello -> handleIncomingHello(hello) }
        }
    }

    /**
     * Merges the peer's stable id into the existing paired-device record (Control already has one
     * from [persistPairedDevice], keyed by the dialable MAC address - this must not replace that
     * `deviceId` with the peer's identity id, or reconnecting would try to dial a UUID as a MAC
     * address) or creates one (Companion never otherwise gets one for Bluetooth, since it only
     * ever accepts an incoming connection and is never told who connected).
     */
    private suspend fun handleIncomingHello(hello: Hello) {
        val role = deviceRepository.getDeviceRole()
        val peerRole = if (role == AppRole.CONTROL) AppRole.COMPANION else AppRole.CONTROL
        val existing = (deviceRepository.getPairedDevice() as? Result.Success)?.data
        val info = existing?.copy(
            deviceName = hello.deviceName,
            stableId = hello.senderId,
            isConnected = true,
            lastSeen = System.currentTimeMillis()
        ) ?: DeviceInfo(
            deviceId = hello.senderId,
            deviceName = hello.deviceName,
            role = peerRole,
            connectionType = ConnectionType.BLUETOOTH,
            lastSeen = System.currentTimeMillis(),
            isConnected = true,
            stableId = hello.senderId
        )
        deviceRepository.savePairedDevice(info)
        deviceRepository.setConnectionEstablished(true)
        repositoryScope.launch { syncSessionActive(info.stableId ?: info.deviceId) }
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
                    if (!isManualDisconnect) {
                        val peerId = peerSessionId()
                        if (peerId != null && !shouldAttemptReconnect(peerId)) {
                            logger.info("Session was ended by the control phone, giving up on reconnecting")
                            isManualDisconnect = true
                            deviceRepository.setConnectionEstablished(false)
                        } else {
                            reconnectWithBackoff()
                        }
                    }
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
            ConnectionType.BLUETOOTH -> reconnectBluetooth(deviceId)
            ConnectionType.WIFI -> reconnectWifi(deviceId)
            ConnectionType.FIREBASE -> reconnectFirebase()
        }
    }

    /** Re-signs in (this repository, and any prior channel registration, doesn't survive process
     * death) and re-registers on the persisted relay code - Control's controlUid write and
     * Companion's companionUid write are both idempotent, so replaying either is safe. Only
     * reachable for a pairing that already completed once (the persisted record proves it), so
     * [firebaseChannelReady] can be set immediately rather than waiting on anything. */
    private suspend fun reconnectFirebase(): EmptyResult<DataError.Connection> {
        val connectResult = firebaseDataSource.connect()
        if (connectResult is Result.Error) return connectResult
        val code = (deviceRepository.getPairedDevice() as? Result.Success)?.data?.deviceId
            ?: return connectResult
        val result = when (deviceRepository.getDeviceRole()) {
            AppRole.CONTROL -> firebaseDataSource.registerAsControl(code)
            AppRole.COMPANION -> when (val joinResult = firebaseDataSource.joinChannel(code)) {
                is Result.Success -> Result.Success(Unit)
                is Result.Error -> joinResult
            }
            null -> connectResult
        }
        if (result is Result.Success) firebaseChannelReady = true
        return result
    }

    /**
     * Control dials Companion's MAC directly; Companion only ever accepts. Since the HELLO
     * handshake now gives Companion a paired-device record too (for Firebase session-tracking),
     * its `deviceId` there is Control's *identity* id, not a MAC address - passing that to
     * `pairDevice` would try to treat a UUID as a Bluetooth address. Companion must always just
     * restart listening instead.
     */
    private suspend fun reconnectBluetooth(deviceId: String?): EmptyResult<DataError.Connection> {
        if (deviceRepository.getDeviceRole() == AppRole.COMPANION) return bluetoothDataSource.connect()
        if (deviceId == null) return bluetoothDataSource.connect()
        return bluetoothDataSource.pairDevice(deviceId)
    }

    /**
     * The WiFi PIN handshake always has CONTROL dial out and COMPANION accept - COMPANION never
     * has a real address to dial, so its persisted "peer id" is a stable identity, not a
     * `host:port`. Reconnecting as COMPANION therefore just means listening again, never
     * `connectToDevice`.
     *
     * For CONTROL, the stored `host:port` goes stale the moment the peer's IP changes (e.g. a
     * DHCP lease renewal) — the most common cause of a reconnect failing with DEVICE_NOT_FOUND
     * even though the same physical device is right there. Before giving up, re-run NSD discovery
     * and look for that same device by its persisted, IP-independent stable id (falling back to
     * its name, for pairings recorded before that id was tracked), then retry once against
     * whatever address it resolves to now.
     */
    private suspend fun reconnectWifi(deviceId: String?): EmptyResult<DataError.Connection> {
        if (deviceRepository.getDeviceRole() == AppRole.COMPANION) return wifiDataSource.connect()
        if (deviceId == null) return wifiDataSource.connect()

        val result = wifiDataSource.connectToDevice(deviceId)
        if (result !is Result.Error || result.error != DataError.Connection.DEVICE_NOT_FOUND) return result

        val paired = (deviceRepository.getPairedDevice() as? Result.Success)?.data ?: return result
        val candidates =
            (wifiDataSource.discoverDevices() as? Result.Success)?.data ?: return result
        val rediscovered =
            candidates.firstOrNull { it.stableId != null && it.stableId == paired.stableId }
                ?: candidates.firstOrNull { it.deviceName == paired.deviceName }
                ?: return result

        logger.info("WiFi peer's address changed, reconnecting via freshly discovered ${rediscovered.deviceId}")
        val retryResult = wifiDataSource.connectToDevice(rediscovered.deviceId)
        if (retryResult is Result.Success) {
            lastPairedDeviceId = rediscovered.deviceId
            persistPairedDevice(rediscovered.deviceId, ConnectionType.WIFI)
        }
        return retryResult
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val PAIRING_TIMEOUT_MS = 30_000L
        const val REMOTE_SESSION_TIMEOUT_MS = 5_000L
        const val REMOTE_LISTENER_RETRY_DELAY_MS = 5_000L
    }
}
