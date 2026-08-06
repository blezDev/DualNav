package com.blez.dualnav.core.data.repository

import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
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

    override suspend fun clearDeviceRole(): EmptyResult<DataError.Local> {
        return preferencesDataSource.clearDeviceRole()
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

    /**
     * Replaces the entire stored list rather than upserting by [DeviceInfo.deviceId] - the app
     * only ever has one active pairing at a time, but `deviceId`'s format differs per transport
     * (WiFi host:port, Bluetooth MAC, Firebase's own id), so a dedup keyed on it would never match
     * across a transport switch and would leave a stale entry from a previous
     * transport/session behind. [getPairedDevice] picking that up instead of the current one is
     * exactly how a WiFi-era `host:port` ended up being passed to Bluetooth's `pairDevice`.
     */
    override suspend fun savePairedDevice(device: DeviceInfo): EmptyResult<DataError.Local> {
        return try {
            preferencesDataSource.savePairedDevices(listOf(device))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override fun isConnectionEstablished(): Flow<Boolean> {
        return preferencesDataSource.isConnectionEstablished()
    }

    override suspend fun setConnectionEstablished(established: Boolean): EmptyResult<DataError.Local> {
        return preferencesDataSource.saveConnectionEstablished(established)
    }
}
