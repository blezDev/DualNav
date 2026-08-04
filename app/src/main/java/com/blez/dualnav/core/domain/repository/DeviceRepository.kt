package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    suspend fun getDeviceRole(): AppRole?
    suspend fun setDeviceRole(role: AppRole): EmptyResult<DataError.Local>

    /** Un-finalizes the role — used when the user backs out of connection setup to reconsider. */
    suspend fun clearDeviceRole(): EmptyResult<DataError.Local>
    suspend fun getPairedDevice(): Result<DeviceInfo?, DataError.Local>
    suspend fun getPairedDevices(): Result<List<DeviceInfo>, DataError.Local>
    /** Upserts [device] into the paired-devices list, matched by [DeviceInfo.deviceId]. */
    suspend fun savePairedDevice(device: DeviceInfo): EmptyResult<DataError.Local>

    /** True once a pairing has actually succeeded; false again after a manual disconnect. */
    fun isConnectionEstablished(): Flow<Boolean>
    suspend fun setConnectionEstablished(established: Boolean): EmptyResult<DataError.Local>
}
