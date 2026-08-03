package com.blez.dualnav.core.data.repository

import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class PreferencesDeviceRepository(
    private val preferencesDataSource: PreferencesDataSource
) : DeviceRepository {

    override suspend fun getDeviceRole(): AppRole? {
        return preferencesDataSource.getDeviceRole().first()
    }

    override suspend fun setDeviceRole(role: AppRole): EmptyResult<DataError.Local> {
        return preferencesDataSource.saveDeviceRole(role)
    }

    override suspend fun getPairedDevice(): Result<DeviceInfo?, DataError.Local> {
        return try {
            val devices = preferencesDataSource.getPairedDevices().first()
            Result.Success(devices.firstOrNull { it.isConnected } ?: devices.firstOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun getPairedDevices(): Result<List<DeviceInfo>, DataError.Local> {
        return try {
            Result.Success(preferencesDataSource.getPairedDevices().first())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun savePairedDevice(device: DeviceInfo): EmptyResult<DataError.Local> {
        return try {
            val current = preferencesDataSource.getPairedDevices().first()
            val updated = current.filterNot { it.deviceId == device.deviceId } + device
            preferencesDataSource.savePairedDevices(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
}
