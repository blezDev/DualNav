package com.blez.dualnav.core.data.datasource

import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

interface FirebaseDataSource {
    suspend fun connect(): EmptyResult<DataError.Connection>
    suspend fun disconnect(): EmptyResult<DataError.Connection>
    suspend fun sendMessage(message: String): EmptyResult<DataError.Connection>
    fun receiveMessage(): Flow<String>
    fun getConnectionStatus(): Flow<ConnectionStatus>
}
