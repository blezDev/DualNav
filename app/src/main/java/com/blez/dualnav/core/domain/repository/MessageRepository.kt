package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.NavigationCommand
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun sendCommand(command: NavigationCommand): EmptyResult<DataError>
    fun receiveCommand(): Flow<NavigationCommand>
    suspend fun sendStatusUpdate(deviceInfo: DeviceInfo): EmptyResult<DataError>
}
