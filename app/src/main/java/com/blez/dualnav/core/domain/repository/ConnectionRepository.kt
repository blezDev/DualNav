package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    suspend fun initializeConnection(deviceRole: AppRole, connectionType: ConnectionType): Result<Boolean>
    suspend fun discoverDevices(): Result<List<DeviceInfo>>
    suspend fun pairDevice(deviceId: String): Result<Boolean>
    fun getConnectionStatus(): Flow<ConnectionStatus>
    suspend fun disconnect(): Result<Boolean>
}
