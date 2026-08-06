package com.blez.dualnav.feature.connection.presentation

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.usecase.EnsureLocalDeviceIdentityUseCase
import com.blez.dualnav.core.domain.util.onFailure
import com.blez.dualnav.core.domain.util.onSuccess
import com.blez.dualnav.core.presentation.util.toUiText
import com.blez.dualnav.feature.connection.domain.SelectConnectionTypeUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionSetupViewModel(
    private val connectionRepository: ConnectionRepository,
    private val deviceRepository: DeviceRepository,
    private val selectConnectionTypeUseCase: SelectConnectionTypeUseCase,
    private val ensureLocalDeviceIdentityUseCase: EnsureLocalDeviceIdentityUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionSetupState())
    val state = _state.asStateFlow()

    private val _events = Channel<ConnectionSetupEvent>()
    val events = _events.receiveAsFlow()

    private var discoverJob: Job? = null
    private var pairingJob: Job? = null

    init {
        viewModelScope.launch {
            val role = deviceRepository.getDeviceRole()
            _state.update { it.copy(role = role) }
        }
        viewModelScope.launch {
            connectionRepository.getConnectionStatus().collect { status ->
                _state.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            connectionRepository.getPairingState().collect { pairingState ->
                _state.update { it.copy(pairingState = pairingState) }
            }
        }
        viewModelScope.launch {
            // Neither of these has an explicit "accept" step that can send NavigateNext directly
            // (unlike WiFi's PIN handshake, or Control dialing a device): Bluetooth's Companion
            // socket just connects silently once Control dials in, and Firebase's Control side
            // only finds out Companion joined via a live listener - so both need to advance on
            // their own once [getConnectionStatus] reports Connected, instead of waiting on a
            // manual Continue tap.
            connectionRepository.getConnectionStatus().collect { status ->
                val current = _state.value
                val shouldAutoAdvance = status is ConnectionStatus.Connected && (
                        (current.role == AppRole.COMPANION && current.selectedType == ConnectionType.BLUETOOTH) ||
                                (current.role == AppRole.CONTROL && current.selectedType == ConnectionType.FIREBASE)
                        )
                if (shouldAutoAdvance && current.role != null) {
                    _events.send(ConnectionSetupEvent.NavigateNext(current.role))
                }
            }
        }
    }

    fun onAction(action: ConnectionSetupAction) {
        when (action) {
            is ConnectionSetupAction.OnConnectionTypeSelected -> selectConnectionType(action.type)
            ConnectionSetupAction.OnDiscoverClick -> discoverDevices()
            is ConnectionSetupAction.OnDeviceClick -> selectDevice(action.deviceId)
            ConnectionSetupAction.OnContinueClick -> continueToNext()
            ConnectionSetupAction.OnBackPress -> _state.update { it.copy(showBackConfirmation = true) }
            ConnectionSetupAction.OnBackCancelled -> _state.update { it.copy(showBackConfirmation = false) }
            ConnectionSetupAction.OnBackConfirmed -> confirmBackToRoleSelection()
            ConnectionSetupAction.OnCancelPairingClick -> cancelPairing()
            ConnectionSetupAction.OnAcceptPairingClick -> respondToPairingRequest(accepted = true)
            ConnectionSetupAction.OnRejectPairingClick -> respondToPairingRequest(accepted = false)
            ConnectionSetupAction.OnGenerateRelayCodeClick -> generateRelayCode()
            is ConnectionSetupAction.OnRelayCodeInputChange -> _state.update {
                it.copy(
                    relayCodeInput = action.value
                )
            }

            ConnectionSetupAction.OnJoinRelayCodeClick -> joinRelayCode()
        }
    }

    private fun confirmBackToRoleSelection() {
        discoverJob?.cancel()
        discoverJob = null
        pairingJob?.cancel()
        pairingJob = null
        viewModelScope.launch {
            connectionRepository.cancelPairing()
            // The role isn't final until Continue is clicked again, so un-persist it here —
            // otherwise a process death while back on role selection would resume straight
            // into connection setup with the old role on the next launch.
            deviceRepository.clearDeviceRole()
            _state.update { ConnectionSetupState() }
            _events.send(ConnectionSetupEvent.NavigateBackToRoleSelection)
        }
    }

    private fun continueToNext() {
        val current = _state.value
        val role = current.role ?: return
        when {
            current.status is ConnectionStatus.Connected -> {
                viewModelScope.launch { _events.send(ConnectionSetupEvent.NavigateNext(role)) }
            }

            current.selectedDeviceId != null -> initiatePairing(current.selectedDeviceId, role)
        }
    }

    private fun selectDevice(deviceId: String) {
        _state.update { it.copy(selectedDeviceId = deviceId) }
    }

    private fun selectConnectionType(type: ConnectionType) {
        // state.role is set by a separate init coroutine reading DataStore - tapping a
        // connection type before that read finishes (very plausible on a freshly-composed
        // screen) used to silently no-op here. Fall back to a direct read so this never
        // depends on winning that race.
        viewModelScope.launch {
            val role = _state.value.role ?: deviceRepository.getDeviceRole() ?: return@launch
            _state.update {
                it.copy(
                    role = role,
                    selectedType = type,
                    isConnecting = true,
                    devices = emptyList(),
                    selectedDeviceId = null,
                    relayCode = null,
                    relayCodeInput = ""
                )
            }
            selectConnectionTypeUseCase(role, type)
                .onSuccess {
                    ensureLocalDeviceIdentityUseCase(deviceName = Build.MODEL, connectionType = type)
                    _state.update { it.copy(isConnecting = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isConnecting = false) }
                    _events.send(ConnectionSetupEvent.ShowMessage(error.toUiText()))
                }
        }
    }

    private fun discoverDevices() {
        _state.update { it.copy(isDiscovering = true) }
        discoverJob = viewModelScope.launch {
            connectionRepository.discoverDevices()
                .onSuccess { devices ->
                    _state.update { it.copy(isDiscovering = false, devices = devices.map { d -> d.toDeviceInfoUi() }) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isDiscovering = false) }
                    _events.send(ConnectionSetupEvent.ShowMessage(error.toUiText()))
                }
        }
    }

    private fun initiatePairing(deviceId: String, role: AppRole) {
        _state.update { it.copy(isPairing = true) }
        pairingJob = viewModelScope.launch {
            connectionRepository.initiatePairing(deviceId)
                .onSuccess {
                    _state.update { it.copy(isPairing = false) }
                    _events.send(ConnectionSetupEvent.NavigateNext(role))
                }
                .onFailure { error ->
                    _state.update { it.copy(isPairing = false) }
                    _events.send(ConnectionSetupEvent.ShowMessage(error.toUiText()))
                }
        }
    }

    private fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _state.update { it.copy(isPairing = false) }
        viewModelScope.launch { connectionRepository.cancelPairing() }
    }

    private fun respondToPairingRequest(accepted: Boolean) {
        viewModelScope.launch {
            connectionRepository.respondToPairingRequest(accepted)
                .onSuccess {
                    if (accepted) {
                        _events.send(ConnectionSetupEvent.NavigateNext(AppRole.COMPANION))
                    }
                }
                .onFailure { error ->
                    _events.send(ConnectionSetupEvent.ShowMessage(error.toUiText()))
                }
        }
    }

    private fun generateRelayCode() {
        viewModelScope.launch {
            connectionRepository.createRelayChannel()
                .onSuccess { code -> _state.update { it.copy(relayCode = code) } }
                .onFailure { error -> _events.send(ConnectionSetupEvent.ShowMessage(error.toUiText())) }
        }
    }

    private fun joinRelayCode() {
        val code = _state.value.relayCodeInput.trim()
        if (code.isEmpty()) return
        _state.update { it.copy(isPairing = true) }
        pairingJob = viewModelScope.launch {
            connectionRepository.joinRelayChannel(code)
                .onSuccess {
                    _state.update { it.copy(isPairing = false) }
                    _events.send(ConnectionSetupEvent.NavigateNext(AppRole.COMPANION))
                }
                .onFailure { error ->
                    _state.update { it.copy(isPairing = false) }
                    _events.send(ConnectionSetupEvent.ShowMessage(error.toUiText()))
                }
        }
    }
}
