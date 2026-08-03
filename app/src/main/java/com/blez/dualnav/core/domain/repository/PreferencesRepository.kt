package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    suspend fun saveConnectionPreferences(connectionType: ConnectionType): EmptyResult<DataError.Local>
    fun getConnectionPreferences(): Flow<ConnectionType?>
    suspend fun saveDeviceInfo(deviceInfo: DeviceInfo): EmptyResult<DataError.Local>
    fun getDeviceInfo(): Flow<DeviceInfo?>
    suspend fun saveThemeMode(themeMode: AppThemeMode): EmptyResult<DataError.Local>
    fun getThemeMode(): Flow<AppThemeMode?>
    suspend fun clearAllPreferences(): EmptyResult<DataError.Local>
}
