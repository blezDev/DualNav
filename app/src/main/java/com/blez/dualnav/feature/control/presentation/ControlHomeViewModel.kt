package com.blez.dualnav.feature.control.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.model.NavigationCommand
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.MessageRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.onFailure
import com.blez.dualnav.core.domain.util.onSuccess
import com.blez.dualnav.core.presentation.util.UiText
import com.blez.dualnav.core.presentation.util.toUiText
import com.blez.dualnav.feature.control.domain.ParseCoordinatesUseCase
import com.blez.dualnav.feature.control.domain.ParseMapsLinkUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ControlHomeViewModel(
    private val parseMapsLinkUseCase: ParseMapsLinkUseCase,
    private val parseCoordinatesUseCase: ParseCoordinatesUseCase,
    private val messageRepository: MessageRepository,
    private val connectionRepository: ConnectionRepository,
    private val deviceRepository: DeviceRepository
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
        viewModelScope.launch {
            if (connectionRepository.getConnectionStatus().first() !is ConnectionStatus.Connected) {
                attemptReconnect()
            }
        }
        viewModelScope.launch {
            connectionRepository.observeRemoteSessionEnded().collect {
                _state.update { it.copy(showRemoteDisconnectedDialog = true) }
            }
        }
    }

    fun onAction(action: ControlHomeAction) {
        when (action) {
            is ControlHomeAction.OnMapsLinkChange -> _state.update { it.copy(mapsLink = action.value) }
            is ControlHomeAction.OnTravelModeSelected -> _state.update { it.copy(travelMode = action.travelMode) }
            ControlHomeAction.OnSendMapsLinkClick -> sendMapsLink()
            ControlHomeAction.OnOpenManualEntryClick -> openManualDialog(ManualDialogMode.NAVIGATE)
            ControlHomeAction.OnAddStopClick -> openManualDialog(ManualDialogMode.ADD_STOP)
            ControlHomeAction.OnDismissManualDialog -> _state.update { it.copy(showManualDialog = false) }
            is ControlHomeAction.OnManualMapsLinkChange -> _state.update { it.copy(manualMapsLink = action.value) }
            is ControlHomeAction.OnManualLatitudeChange -> _state.update { it.copy(manualLatitude = action.value) }
            is ControlHomeAction.OnManualLongitudeChange -> _state.update { it.copy(manualLongitude = action.value) }
            is ControlHomeAction.OnManualAddressChange -> _state.update { it.copy(manualAddress = action.value) }
            ControlHomeAction.OnManualConfirmClick -> confirmManualEntry()
            ControlHomeAction.OnStopClick -> sendSimpleCommand(NavigationCommand.Stop, R.string.control_home_stop_sent)
            ControlHomeAction.OnResumeClick -> sendSimpleCommand(NavigationCommand.Resume, R.string.control_home_resume_sent)
            ControlHomeAction.OnBackPress -> _state.update { it.copy(showDisconnectConfirmation = true) }
            ControlHomeAction.OnReconnectClick -> viewModelScope.launch { attemptReconnect() }
            ControlHomeAction.OnDisconnectCancelled -> _state.update {
                it.copy(
                    showDisconnectConfirmation = false
                )
            }

            ControlHomeAction.OnDisconnectConfirmed -> disconnect()
            ControlHomeAction.OnRemoteDisconnectedAcknowledged -> acknowledgeRemoteDisconnect()
        }
    }

    private fun acknowledgeRemoteDisconnect() {
        viewModelScope.launch {
            connectionRepository.acknowledgeRemoteDisconnect()
            deviceRepository.clearDeviceRole()
            _state.update { it.copy(showRemoteDisconnectedDialog = false) }
            _events.send(ControlHomeEvent.NavigateToRoleSelection)
        }
    }

    private suspend fun attemptReconnect() {
        _state.update { it.copy(isReconnecting = true) }
        connectionRepository.resumeConnection()
            .onFailure { error ->
                if (error == DataError.Connection.SESSION_ENDED) {
                    deviceRepository.clearDeviceRole()
                    _events.send(ControlHomeEvent.NavigateToRoleSelection)
                } else {
                    _events.send(ControlHomeEvent.ShowSnackbar(error.toUiText()))
                }
            }
        _state.update { it.copy(isReconnecting = false) }
    }

    private fun disconnect() {
        viewModelScope.launch {
            connectionRepository.disconnect()
            deviceRepository.clearDeviceRole()
            _state.update { it.copy(showDisconnectConfirmation = false) }
            _events.send(ControlHomeEvent.NavigateToRoleSelection)
        }
    }

    private fun openManualDialog(mode: ManualDialogMode) {
        _state.update {
            it.copy(
                showManualDialog = true,
                manualDialogMode = mode,
                manualMapsLink = if (mode == ManualDialogMode.ADD_STOP) it.mapsLink.trim() else "",
                manualLatitude = "",
                manualLongitude = "",
                manualAddress = ""
            )
        }
    }

    private fun sendMapsLink() {
        val link = _state.value.mapsLink
        val travelMode = _state.value.travelMode
        viewModelScope.launch {
            _state.update { it.copy(isSendingLink = true) }
            parseMapsLinkUseCase(link)
                .onSuccess { destination ->
                    sendCommand(
                        NavigationCommand.Navigate(destination, travelMode),
                        R.string.control_home_destination_sent
                    )
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
        val link = current.manualMapsLink.trim()
        val command = if (current.manualDialogMode == ManualDialogMode.ADD_STOP) {
            { destination: Destination ->
                NavigationCommand.AddStop(
                    destination,
                    current.travelMode
                )
            }
        } else {
            { destination: Destination ->
                NavigationCommand.Navigate(
                    destination,
                    current.travelMode
                )
            }
        }
        val confirmationRes = if (current.manualDialogMode == ManualDialogMode.ADD_STOP) {
            R.string.control_home_stop_added
        } else {
            R.string.control_home_destination_sent
        }

        if (link.isNotEmpty()) {
            viewModelScope.launch {
                _state.update { it.copy(isSendingLink = true) }
                parseMapsLinkUseCase(link)
                    .onSuccess { destination ->
                        sendCommand(command(destination), confirmationRes)
                        _state.update { it.copy(isSendingLink = false, showManualDialog = false) }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(isSendingLink = false) }
                        _events.send(ControlHomeEvent.ShowSnackbar(error.toUiText()))
                    }
            }
        } else {
            val result = parseCoordinatesUseCase(
                current.manualLatitude,
                current.manualLongitude,
                current.manualAddress
            )
            viewModelScope.launch {
                result
                    .onSuccess { destination ->
                        sendCommand(command(destination), confirmationRes)
                        _state.update { it.copy(showManualDialog = false) }
                    }
                    .onFailure { error ->
                        _events.send(ControlHomeEvent.ShowSnackbar(error.toUiText()))
                    }
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
