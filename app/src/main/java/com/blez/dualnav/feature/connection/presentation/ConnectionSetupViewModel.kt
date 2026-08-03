package com.blez.dualnav.feature.connection.presentation

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.usecase.EnsureLocalDeviceIdentityUseCase
import com.blez.dualnav.core.domain.util.onFailure
import com.blez.dualnav.core.domain.util.onSuccess
import com.blez.dualnav.core.presentation.util.toUiText
import com.blez.dualnav.feature.connection.domain.SelectConnectionTypeUseCase
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
    }

    fun onAction(action: ConnectionSetupAction) {
        when (action) {
            is ConnectionSetupAction.OnConnectionTypeSelected -> selectConnectionType(action.type)
            ConnectionSetupAction.OnDiscoverClick -> discoverDevices()
            is ConnectionSetupAction.OnDeviceClick -> pairDevice(action.deviceId)
            ConnectionSetupAction.OnContinueClick -> continueToNext()
        }
    }

    private fun continueToNext() {
        val role = _state.value.role ?: return
        viewModelScope.launch { _events.send(ConnectionSetupEvent.NavigateNext(role)) }
    }

    private fun selectConnectionType(type: ConnectionType) {
        val role = _state.value.role ?: return
        _state.update { it.copy(selectedType = type, isConnecting = true, devices = emptyList()) }
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    private fun pairDevice(deviceId: String) {
        viewModelScope.launch {
            connectionRepository.pairDevice(deviceId)
                .onFailure { error ->
                    _events.send(ConnectionSetupEvent.ShowMessage(error.toUiText()))
                }
        }
    }
}
