package com.blez.dualnav.feature.control.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.NavigationCommand
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.MessageRepository
import com.blez.dualnav.core.domain.util.onFailure
import com.blez.dualnav.core.domain.util.onSuccess
import com.blez.dualnav.core.presentation.util.UiText
import com.blez.dualnav.core.presentation.util.toUiText
import com.blez.dualnav.feature.control.domain.ParseCoordinatesUseCase
import com.blez.dualnav.feature.control.domain.ParseMapsLinkUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ControlHomeViewModel(
    private val parseMapsLinkUseCase: ParseMapsLinkUseCase,
    private val parseCoordinatesUseCase: ParseCoordinatesUseCase,
    private val messageRepository: MessageRepository,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ControlHomeState())
    val state = _state.asStateFlow()

    private val _events = Channel<ControlHomeEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectionRepository.getConnectionStatus().collect { status ->
                _state.update { it.copy(connectionStatus = status) }
            }
        }
    }

    fun onAction(action: ControlHomeAction) {
        when (action) {
            is ControlHomeAction.OnMapsLinkChange -> _state.update { it.copy(mapsLink = action.value) }
            ControlHomeAction.OnSendMapsLinkClick -> sendMapsLink()
            ControlHomeAction.OnOpenManualEntryClick -> openManualDialog(ManualDialogMode.NAVIGATE)
            ControlHomeAction.OnAddStopClick -> openManualDialog(ManualDialogMode.ADD_STOP)
            ControlHomeAction.OnDismissManualDialog -> _state.update { it.copy(showManualDialog = false) }
            is ControlHomeAction.OnManualLatitudeChange -> _state.update { it.copy(manualLatitude = action.value) }
            is ControlHomeAction.OnManualLongitudeChange -> _state.update { it.copy(manualLongitude = action.value) }
            is ControlHomeAction.OnManualAddressChange -> _state.update { it.copy(manualAddress = action.value) }
            ControlHomeAction.OnManualConfirmClick -> confirmManualEntry()
            ControlHomeAction.OnStopClick -> sendSimpleCommand(NavigationCommand.Stop, R.string.control_home_stop_sent)
            ControlHomeAction.OnResumeClick -> sendSimpleCommand(NavigationCommand.Resume, R.string.control_home_resume_sent)
        }
    }

    private fun openManualDialog(mode: ManualDialogMode) {
        _state.update {
            it.copy(
                showManualDialog = true,
                manualDialogMode = mode,
                manualLatitude = "",
                manualLongitude = "",
                manualAddress = ""
            )
        }
    }

    private fun sendMapsLink() {
        val link = _state.value.mapsLink
        viewModelScope.launch {
            _state.update { it.copy(isSendingLink = true) }
            parseMapsLinkUseCase(link)
                .onSuccess { destination ->
                    sendCommand(NavigationCommand.Navigate(destination), R.string.control_home_destination_sent)
                    _state.update { it.copy(isSendingLink = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isSendingLink = false) }
                    _events.send(ControlHomeEvent.ShowSnackbar(error.toUiText()))
                }
        }
    }

    private fun confirmManualEntry() {
        val current = _state.value
        val result = parseCoordinatesUseCase(current.manualLatitude, current.manualLongitude, current.manualAddress)
        viewModelScope.launch {
            result
                .onSuccess { destination ->
                    val command = if (current.manualDialogMode == ManualDialogMode.ADD_STOP) {
                        NavigationCommand.AddStop(destination)
                    } else {
                        NavigationCommand.Navigate(destination)
                    }
                    val confirmationRes = if (current.manualDialogMode == ManualDialogMode.ADD_STOP) {
                        R.string.control_home_stop_added
                    } else {
                        R.string.control_home_destination_sent
                    }
                    sendCommand(command, confirmationRes)
                    _state.update { it.copy(showManualDialog = false) }
                }
                .onFailure { error ->
                    _events.send(ControlHomeEvent.ShowSnackbar(error.toUiText()))
                }
        }
    }

    private fun sendSimpleCommand(command: NavigationCommand, confirmationRes: Int) {
        viewModelScope.launch { sendCommand(command, confirmationRes) }
    }

    private suspend fun sendCommand(command: NavigationCommand, confirmationRes: Int) {
        _state.update { it.copy(isSendingCommand = true) }
        messageRepository.sendCommand(command)
            .onSuccess {
                _events.send(ControlHomeEvent.ShowSnackbar(UiText.StringResource(confirmationRes)))
            }
            .onFailure { error ->
                _events.send(ControlHomeEvent.ShowSnackbar(error.toUiText()))
            }
        _state.update { it.copy(isSendingCommand = false) }
    }
}
