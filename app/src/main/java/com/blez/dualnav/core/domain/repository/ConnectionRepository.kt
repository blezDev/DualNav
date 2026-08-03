package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    suspend fun initializeConnection(deviceRole: AppRole, connectionType: ConnectionType): EmptyResult<DataError>
    suspend fun discoverDevices(): Result<List<DeviceInfo>, DataError>
    suspend fun pairDevice(deviceId: String): EmptyResult<DataError>
    fun getConnectionStatus(): Flow<ConnectionStatus>
    suspend fun disconnect(): EmptyResult<DataError>
}
