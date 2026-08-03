package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    suspend fun saveConnectionPreferences(connectionType: ConnectionType): Result<Boolean>
    fun getConnectionPreferences(): Flow<ConnectionType?>
    suspend fun saveDeviceInfo(deviceInfo: DeviceInfo): Result<Boolean>
    fun getDeviceInfo(): Flow<DeviceInfo?>
    suspend fun clearAllPreferences(): Result<Boolean>
}
