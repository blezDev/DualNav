package com.blez.dualnav.feature.companion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.MessageRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CompanionHomeViewModel(
    private val connectionRepository: ConnectionRepository,
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CompanionHomeState())
    val state = _state.asStateFlow()

    private val _events = Channel<CompanionHomeEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectionRepository.getConnectionStatus().collect { status ->
                _state.update { it.copy(connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            messageRepository.receiveCommand().collect { command ->
                _state.update {
                    it.copy(recentActivity = (listOf(command.toActivityUiText()) + it.recentActivity).take(MAX_ACTIVITY_ENTRIES))
                }
            }
        }
        viewModelScope.launch {
            if (connectionRepository.getConnectionStatus().first() !is ConnectionStatus.Connected) {
                connectionRepository.resumeConnection()
            }
        }
    }

    fun onAction(action: CompanionHomeAction) {
        when (action) {
            CompanionHomeAction.OnBackPress -> _state.update { it.copy(showDisconnectConfirmation = true) }
            CompanionHomeAction.OnDisconnectCancelled -> _state.update {
                it.copy(
                    showDisconnectConfirmation = false
                )
            }

            CompanionHomeAction.OnDisconnectConfirmed -> disconnect()
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            connectionRepository.disconnect()
            deviceRepository.clearDeviceRole()
            _state.update { it.copy(showDisconnectConfirmation = false) }
            _events.send(CompanionHomeEvent.NavigateToRoleSelection)
        }
    }

    private companion object {
        const val MAX_ACTIVITY_ENTRIES = 20
    }
}
