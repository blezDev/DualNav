package com.blez.dualnav.feature.role.presentation

import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.presentation.util.UiText

data class RoleSelectionState(
    val selectedRole: AppRole? = null,
    val isSaving: Boolean = false,
    val showExitConfirmation: Boolean = false
)

sealed interface RoleSelectionAction {
    data class OnRoleSelected(val role: AppRole) : RoleSelectionAction
    data object OnContinueClick : RoleSelectionAction
    data object OnBackPress : RoleSelectionAction
    data object OnExitConfirmed : RoleSelectionAction
    data object OnExitCancelled : RoleSelectionAction
}

sealed interface RoleSelectionEvent {
    data class NavigateNext(val role: AppRole) : RoleSelectionEvent
    data class ShowError(val message: UiText) : RoleSelectionEvent
    data object ExitApp : RoleSelectionEvent
}
