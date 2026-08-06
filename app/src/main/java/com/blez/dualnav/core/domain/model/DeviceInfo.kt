package com.blez.dualnav.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val role: AppRole,
    val connectionType: ConnectionType,
    val lastSeen: Long,
    val isConnected: Boolean,
    /** Persistent per-install identity (see EnsureLocalDeviceIdentityUseCase), independent of
     * [deviceId] — which for WiFi is a transient `host:port` that goes stale whenever the peer's
     * IP changes. Used to re-identify the same physical device after that happens. Null for
     * transports/pairings that don't carry it. */
    val stableId: String? = null
)
