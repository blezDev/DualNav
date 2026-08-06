package com.blez.dualnav.core.data.datasource

import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface FirebaseDataSource {
    suspend fun connect(): EmptyResult<DataError.Connection>
    suspend fun disconnect(): EmptyResult<DataError.Connection>
    suspend fun sendMessage(message: String): EmptyResult<DataError.Connection>
    fun receiveMessage(): Flow<String>
    fun getConnectionStatus(): Flow<ConnectionStatus>

    /** Control-only: generates a short shareable code and registers this device as that
     * channel's Control side. Messages route through this channel from here on. */
    suspend fun createChannel(): Result<String, DataError.Connection>

    /** Control-only: re-registers on an already-established channel (e.g. after a relaunch),
     * without generating a new code. */
    suspend fun registerAsControl(code: String): EmptyResult<DataError.Connection>

    /** Companion-only: joins a code Control generated/shared (first time or on reconnect) -
     * registers as that channel's Companion side and returns Control's Firebase uid. */
    suspend fun joinChannel(code: String): Result<String, DataError.Connection>

    /** Control-only: emits Companion's Firebase uid once it joins this channel. */
    fun observePeerJoined(code: String): Flow<String>
}
