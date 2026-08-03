package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.DeviceInfo

interface DeviceRepository {
    suspend fun getDeviceRole(): AppRole?
    suspend fun setDeviceRole(role: AppRole): Result<Boolean>
    suspend fun getPairedDevice(): Result<DeviceInfo?>
    suspend fun getPairedDevices(): Result<List<DeviceInfo>>
}
