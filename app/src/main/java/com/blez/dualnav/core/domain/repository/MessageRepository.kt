package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.NavigationCommand
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun sendCommand(command: NavigationCommand): Result<Boolean>
    fun receiveCommand(): Flow<NavigationCommand>
    suspend fun sendStatusUpdate(deviceInfo: DeviceInfo): Result<Boolean>
}
