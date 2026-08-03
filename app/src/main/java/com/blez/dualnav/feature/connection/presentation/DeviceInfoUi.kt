package com.blez.dualnav.feature.connection.presentation

import com.blez.dualnav.core.domain.model.DeviceInfo

data class DeviceInfoUi(
    val id: String,
    val name: String,
    val isConnected: Boolean
)

fun DeviceInfo.toDeviceInfoUi(): DeviceInfoUi = DeviceInfoUi(
    id = deviceId,
    name = deviceName,
    isConnected = isConnected
)
