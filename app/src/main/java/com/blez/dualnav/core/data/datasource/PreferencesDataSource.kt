package com.blez.dualnav.core.data.datasource

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

interface PreferencesDataSource {
    suspend fun saveDeviceRole(role: AppRole): EmptyResult<DataError.Local>
    fun getDeviceRole(): Flow<AppRole?>
    suspend fun saveConnectionType(connectionType: ConnectionType): EmptyResult<DataError.Local>
    fun getConnectionType(): Flow<ConnectionType?>
    suspend fun saveDeviceInfo(deviceInfo: DeviceInfo): EmptyResult<DataError.Local>
    fun getDeviceInfo(): Flow<DeviceInfo?>
    suspend fun savePairedDevices(devices: List<DeviceInfo>): EmptyResult<DataError.Local>
    fun getPairedDevices(): Flow<List<DeviceInfo>>
    suspend fun saveThemeMode(themeMode: AppThemeMode): EmptyResult<DataError.Local>
    fun getThemeMode(): Flow<AppThemeMode?>
    suspend fun clearAll(): EmptyResult<DataError.Local>
}
