package com.blez.dualnav.feature.role.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.presentation.util.toUiText
import com.blez.dualnav.core.domain.util.onFailure
import com.blez.dualnav.core.domain.util.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoleSelectionViewModel(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RoleSelectionState())
    val state = _state.asStateFlow()

    private val _events = Channel<RoleSelectionEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: RoleSelectionAction) {
        when (action) {
            is RoleSelectionAction.OnRoleSelected -> {
                _state.update { it.copy(selectedRole = action.role) }
            }
            RoleSelectionAction.OnContinueClick -> confirmRole()
            RoleSelectionAction.OnBackPress -> _state.update { it.copy(showExitConfirmation = true) }
            RoleSelectionAction.OnExitCancelled -> _state.update { it.copy(showExitConfirmation = false) }
            RoleSelectionAction.OnExitConfirmed -> viewModelScope.launch {
                _events.send(
                    RoleSelectionEvent.ExitApp
                )
            }
        }
    }

    private fun confirmRole() {
        val role = _state.value.selectedRole ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            deviceRepository.setDeviceRole(role)
                .onSuccess {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(RoleSelectionEvent.NavigateNext(role))
                }
                .onFailure { error ->
                    _state.update { it.copy(isSaving = false) }
                    _events.send(RoleSelectionEvent.ShowError(error.toUiText()))
                }
        }
    }
}
