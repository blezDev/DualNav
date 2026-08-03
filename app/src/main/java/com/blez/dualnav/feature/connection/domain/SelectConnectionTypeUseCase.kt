package com.blez.dualnav.feature.connection.domain

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult

/**
 * Persists the chosen transport before initializing it, so a later app restart can restore
 * the same [ConnectionType] without the user re-selecting it.
 */
class SelectConnectionTypeUseCase(
    private val connectionRepository: ConnectionRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(role: AppRole, connectionType: ConnectionType): EmptyResult<DataError> {
        preferencesRepository.saveConnectionPreferences(connectionType)
        return connectionRepository.initializeConnection(role, connectionType)
    }
}
