package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result

interface DeviceRepository {
    suspend fun getDeviceRole(): AppRole?
    suspend fun setDeviceRole(role: AppRole): EmptyResult<DataError.Local>
    suspend fun getPairedDevice(): Result<DeviceInfo?, DataError.Local>
    suspend fun getPairedDevices(): Result<List<DeviceInfo>, DataError.Local>
    /** Upserts [device] into the paired-devices list, matched by [DeviceInfo.deviceId]. */
    suspend fun savePairedDevice(device: DeviceInfo): EmptyResult<DataError.Local>
}
