package com.blez.dualnav.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val role: AppRole,
    val connectionType: ConnectionType,
    val lastSeen: Long,
    val isConnected: Boolean
)
