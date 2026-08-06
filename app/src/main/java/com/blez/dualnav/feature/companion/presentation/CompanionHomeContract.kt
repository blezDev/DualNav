package com.blez.dualnav.feature.companion.presentation

import androidx.compose.runtime.Stable
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.presentation.util.UiText

@Stable
data class CompanionHomeState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val recentActivity: List<UiText> = emptyList(),
    val showDisconnectConfirmation: Boolean = false,
    val isReconnecting: Boolean = false,
    val showRemoteDisconnectedDialog: Boolean = false
)

sealed interface CompanionHomeAction {
    data object OnBackPress : CompanionHomeAction
    data object OnReconnectClick : CompanionHomeAction
    data object OnDisconnectConfirmed : CompanionHomeAction
    data object OnDisconnectCancelled : CompanionHomeAction
    data object OnRemoteDisconnectedAcknowledged : CompanionHomeAction
}

sealed interface CompanionHomeEvent {
    data class ShowSnackbar(val message: UiText) : CompanionHomeEvent
    data object NavigateToRoleSelection : CompanionHomeEvent
}
