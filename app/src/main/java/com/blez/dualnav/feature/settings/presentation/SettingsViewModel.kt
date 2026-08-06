package com.blez.dualnav.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import com.blez.dualnav.core.domain.repository.UpdateInstaller
import com.blez.dualnav.core.domain.repository.UpdateRepository
import com.blez.dualnav.core.domain.util.Result
import com.blez.dualnav.core.presentation.util.UiText
import com.blez.dualnav.core.presentation.util.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val connectionRepository: ConnectionRepository,
    private val updateRepository: UpdateRepository,
    private val updateInstaller: UpdateInstaller
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
            SettingsAction.OnCheckForUpdateClick -> checkForUpdate()
            SettingsAction.OnDownloadUpdateClick -> downloadUpdate()
            SettingsAction.OnDismissUpdateDialog -> _state.update { it.copy(availableUpdate = null) }
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

    private fun checkForUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingForUpdate = true) }
            when (val result = updateRepository.checkForUpdate()) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            isCheckingForUpdate = false,
                            availableUpdate = result.data
                        )
                    }
                    if (result.data == null) {
                        _events.send(
                            SettingsEvent.ShowSnackbar(UiText.StringResource(R.string.settings_update_up_to_date))
                        )
                    }
                }

                is Result.Error -> {
                    _state.update { it.copy(isCheckingForUpdate = false) }
                    _events.send(SettingsEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }

    private fun downloadUpdate() {
        val update = _state.value.availableUpdate ?: return
        viewModelScope.launch {
            if (!updateInstaller.canInstallPackages()) {
                updateInstaller.requestInstallPermission()
                _state.update { it.copy(availableUpdate = null) }
                _events.send(
                    SettingsEvent.ShowSnackbar(UiText.StringResource(R.string.settings_update_grant_install_permission))
                )
                return@launch
            }

            _state.update { it.copy(availableUpdate = null, isDownloadingUpdate = true) }
            when (val result = updateInstaller.downloadAndInstall(update)) {
                is Result.Success -> _state.update { it.copy(isDownloadingUpdate = false) }
                is Result.Error -> {
                    _state.update { it.copy(isDownloadingUpdate = false) }
                    _events.send(SettingsEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }
}
