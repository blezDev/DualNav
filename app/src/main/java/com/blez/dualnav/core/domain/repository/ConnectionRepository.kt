package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.PairingState
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    suspend fun initializeConnection(deviceRole: AppRole, connectionType: ConnectionType): EmptyResult<DataError>
    suspend fun discoverDevices(): Result<List<DeviceInfo>, DataError>

    /** Control-only: dials [deviceId]. For an unknown WiFi device this runs a PIN handshake first. */
    suspend fun initiatePairing(deviceId: String): EmptyResult<DataError>

    /** Aborts an in-flight [initiatePairing] call and tears down its socket. */
    suspend fun cancelPairing(): EmptyResult<DataError>

    /** Companion-only: answers the pairing request currently reflected in [getPairingState]. */
    suspend fun respondToPairingRequest(accepted: Boolean): EmptyResult<DataError>
    fun getPairingState(): Flow<PairingState>
    fun getConnectionStatus(): Flow<ConnectionStatus>
    suspend fun disconnect(): EmptyResult<DataError>

    /** Re-establishes the transport using the persisted role/connection type/paired device, for
     * when the app lands directly on a home screen (no live socket survives process death). */
    suspend fun resumeConnection(): EmptyResult<DataError>
}
