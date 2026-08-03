package com.blez.dualnav.core.data.repository

import com.blez.dualnav.core.data.datasource.BluetoothDataSource
import com.blez.dualnav.core.data.datasource.FirebaseDataSource
import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.data.datasource.WiFiDataSource
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.NavigationCommand
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.MessageRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.MessageProtocol
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class MultiTransportMessageRepository(
    private val bluetoothDataSource: BluetoothDataSource,
    private val wifiDataSource: WiFiDataSource,
    private val firebaseDataSource: FirebaseDataSource,
    private val preferencesDataSource: PreferencesDataSource,
    private val deviceRepository: DeviceRepository,
    private val connectionRepository: ConnectionRepository,
    private val logger: Logger,
    private val json: Json
) : MessageRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueMutex = Mutex()
    private val pendingMessages = ArrayDeque<String>()

    init {
        repositoryScope.launch {
            connectionRepository.getConnectionStatus().collect { status ->
                if (status is ConnectionStatus.Connected) flushQueue()
            }
        }
    }

    override suspend fun sendCommand(command: NavigationCommand): EmptyResult<DataError> {
        val envelope = MessageProtocol.wrapCommand(command, senderId(), receiverId(), json)
        return sendOrQueue(envelope)
    }

    override suspend fun sendStatusUpdate(deviceInfo: DeviceInfo): EmptyResult<DataError> {
        val envelope = MessageProtocol.wrapStatus(deviceInfo, senderId(), receiverId(), json)
        return sendOrQueue(envelope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun receiveCommand(): Flow<NavigationCommand> {
        return preferencesDataSource.getConnectionType()
            .flatMapLatest { connectionType ->
                when (connectionType) {
                    ConnectionType.BLUETOOTH -> bluetoothDataSource.receiveMessage()
                    ConnectionType.WIFI -> wifiDataSource.receiveMessage()
                    ConnectionType.FIREBASE -> firebaseDataSource.receiveMessage()
                    null -> emptyFlow()
                }
            }
            .mapNotNull { raw -> MessageProtocol.unwrapCommand(raw, json) }
    }

    /** Sends immediately; if that fails because there's no live connection, queues for later delivery. */
    private suspend fun sendOrQueue(message: String): EmptyResult<DataError> {
        val result = sendRaw(message)
        if (result is Result.Error && result.error is DataError.Connection) {
            enqueue(message)
        }
        return result
    }

    private suspend fun sendRaw(message: String): EmptyResult<DataError> {
        return when (preferencesDataSource.getConnectionType().first()) {
            ConnectionType.BLUETOOTH -> bluetoothDataSource.sendMessage(message)
            ConnectionType.WIFI -> wifiDataSource.sendMessage(message)
            ConnectionType.FIREBASE -> firebaseDataSource.sendMessage(message)
            null -> Result.Error(DataError.Connection.NOT_CONNECTED)
        }
    }

    private suspend fun enqueue(message: String) {
        queueMutex.withLock {
            if (pendingMessages.size >= MAX_QUEUE_SIZE) pendingMessages.removeFirst()
            pendingMessages.addLast(message)
        }
        logger.info("Queued message for delivery once reconnected (${pendingMessages.size} pending)")
    }

    private suspend fun flushQueue() {
        queueMutex.withLock {
            while (pendingMessages.isNotEmpty()) {
                val next = pendingMessages.first()
                val result = sendRaw(next)
                if (result is Result.Success) {
                    pendingMessages.removeFirst()
                } else {
                    return@withLock
                }
            }
        }
        logger.info("Offline message queue flushed")
    }

    private suspend fun senderId(): String = preferencesDataSource.getDeviceInfo().first()?.deviceId.orEmpty()

    private suspend fun receiverId(): String {
        val paired = deviceRepository.getPairedDevice()
        return (paired as? Result.Success)?.data?.deviceId.orEmpty()
    }

    private companion object {
        const val MAX_QUEUE_SIZE = 50
    }
}
