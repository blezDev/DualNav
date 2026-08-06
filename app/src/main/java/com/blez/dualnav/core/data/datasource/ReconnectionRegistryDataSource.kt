package com.blez.dualnav.core.data.datasource

import com.blez.dualnav.core.domain.model.ReconnectionSession
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Cloud-mirrored record of the current control/companion pairing, keyed by both device IDs, so a
 * phone can tell whether it should keep trying to reconnect even after being fully closed and
 * relaunched - independent of its own local, on-device state.
 */
interface ReconnectionRegistryDataSource {
    suspend fun upsertSession(session: ReconnectionSession): EmptyResult<DataError>
    suspend fun getSession(
        controlDeviceId: String,
        companionDeviceId: String
    ): Result<ReconnectionSession?, DataError>

    /** Live updates for this pairing's record, so the other phone can be notified the moment one
     * side ends the session, without waiting for the underlying socket to notice the drop. */
    fun observeSession(
        controlDeviceId: String,
        companionDeviceId: String
    ): Flow<ReconnectionSession?>
}
