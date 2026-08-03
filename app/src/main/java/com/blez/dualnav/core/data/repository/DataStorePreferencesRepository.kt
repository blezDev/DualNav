package com.blez.dualnav.core.data.repository

import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

class DataStorePreferencesRepository(
    private val preferencesDataSource: PreferencesDataSource
) : PreferencesRepository {

    override suspend fun saveConnectionPreferences(connectionType: ConnectionType): EmptyResult<DataError.Local> {
        return preferencesDataSource.saveConnectionType(connectionType)
    }

    override fun getConnectionPreferences(): Flow<ConnectionType?> {
        return preferencesDataSource.getConnectionType()
    }

    override suspend fun saveDeviceInfo(deviceInfo: DeviceInfo): EmptyResult<DataError.Local> {
        return preferencesDataSource.saveDeviceInfo(deviceInfo)
    }

    override fun getDeviceInfo(): Flow<DeviceInfo?> {
        return preferencesDataSource.getDeviceInfo()
    }

    override suspend fun saveThemeMode(themeMode: AppThemeMode): EmptyResult<DataError.Local> {
        return preferencesDataSource.saveThemeMode(themeMode)
    }

    override fun getThemeMode(): Flow<AppThemeMode?> {
        return preferencesDataSource.getThemeMode()
    }

    override suspend fun clearAllPreferences(): EmptyResult<DataError.Local> {
        return preferencesDataSource.clearAll()
    }
}
