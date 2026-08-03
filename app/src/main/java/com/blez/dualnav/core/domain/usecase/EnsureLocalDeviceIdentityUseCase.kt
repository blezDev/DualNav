package com.blez.dualnav.core.domain.usecase

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Generates and persists a stable identity for this device the first time it's needed, so
 * [com.blez.dualnav.core.domain.util.MessageProtocol] envelopes have a real `senderId` instead
 * of an empty string. Idempotent — a second call returns the already-saved identity untouched.
 */
class EnsureLocalDeviceIdentityUseCase(
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(deviceName: String, connectionType: ConnectionType): DeviceInfo {
        preferencesRepository.getDeviceInfo().first()?.let { return it }

        val role = deviceRepository.getDeviceRole() ?: AppRole.CONTROL
        val identity = DeviceInfo(
            deviceId = UUID.randomUUID().toString(),
            deviceName = deviceName,
            role = role,
            connectionType = connectionType,
            lastSeen = System.currentTimeMillis(),
            isConnected = false
        )
        preferencesRepository.saveDeviceInfo(identity)
        return identity
    }
}
