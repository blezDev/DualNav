package com.blez.dualnav.feature.control.presentation

import androidx.compose.runtime.Stable
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.TravelMode
import com.blez.dualnav.core.presentation.util.UiText

enum class ManualDialogMode { NAVIGATE, ADD_STOP }

@Stable
data class ControlHomeState(
    val mapsLink: String = "",
    val travelMode: TravelMode = TravelMode.CAR,
    val isSendingLink: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isSendingCommand: Boolean = false,
    val showManualDialog: Boolean = false,
    val manualDialogMode: ManualDialogMode = ManualDialogMode.NAVIGATE,
    val manualLatitude: String = "",
    val manualLongitude: String = "",
    val manualAddress: String = "",
    val showDisconnectConfirmation: Boolean = false
)

sealed interface ControlHomeAction {
    data class OnMapsLinkChange(val value: String) : ControlHomeAction
    data class OnTravelModeSelected(val travelMode: TravelMode) : ControlHomeAction
    data object OnSendMapsLinkClick : ControlHomeAction
    data object OnOpenManualEntryClick : ControlHomeAction
    data object OnAddStopClick : ControlHomeAction
    data object OnDismissManualDialog : ControlHomeAction
    data class OnManualLatitudeChange(val value: String) : ControlHomeAction
    data class OnManualLongitudeChange(val value: String) : ControlHomeAction
    data class OnManualAddressChange(val value: String) : ControlHomeAction
    data object OnManualConfirmClick : ControlHomeAction
    data object OnStopClick : ControlHomeAction
    data object OnResumeClick : ControlHomeAction
    data object OnBackPress : ControlHomeAction
    data object OnDisconnectConfirmed : ControlHomeAction
    data object OnDisconnectCancelled : ControlHomeAction
}

sealed interface ControlHomeEvent {
    data class ShowSnackbar(val message: UiText) : ControlHomeEvent
    data object NavigateToRoleSelection : ControlHomeEvent
}
