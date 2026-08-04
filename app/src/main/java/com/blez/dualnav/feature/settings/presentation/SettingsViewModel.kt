package com.blez.dualnav.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.getThemeMode().collect { themeMode ->
                _state.update { it.copy(themeMode = themeMode ?: AppThemeMode.DEFAULT) }
            }
        }
        viewModelScope.launch {
            deviceRepository.isConnectionEstablished().collect { established ->
                _state.update { it.copy(isConnected = established) }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnThemeSelected -> {
                viewModelScope.launch { preferencesRepository.saveThemeMode(action.themeMode) }
            }
            SettingsAction.OnDisconnectClick -> _state.update { it.copy(showDisconnectConfirmation = true) }
            SettingsAction.OnDisconnectCancelled -> _state.update {
                it.copy(
                    showDisconnectConfirmation = false
                )
            }

            SettingsAction.OnDisconnectConfirmed -> disconnect()
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            connectionRepository.disconnect()
            deviceRepository.clearDeviceRole()
            _state.update { it.copy(showDisconnectConfirmation = false) }
            _events.send(SettingsEvent.NavigateToRoleSelection)
        }
    }
}
