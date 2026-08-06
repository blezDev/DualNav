package com.blez.dualnav.feature.connection.presentation

import androidx.compose.runtime.Stable
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.PairingState
import com.blez.dualnav.core.presentation.util.UiText

@Stable
data class ConnectionSetupState(
    val role: AppRole? = null,
    val selectedType: ConnectionType? = null,
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
    val devices: List<DeviceInfoUi> = emptyList(),
    val selectedDeviceId: String? = null,
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val isPairing: Boolean = false,
    val showBackConfirmation: Boolean = false,
    val pairingState: PairingState = PairingState.Idle,
    val relayCode: String? = null,
    val relayCodeInput: String = ""
)

sealed interface ConnectionSetupAction {
    data class OnConnectionTypeSelected(val type: ConnectionType) : ConnectionSetupAction
    data object OnDiscoverClick : ConnectionSetupAction
    data class OnDeviceClick(val deviceId: String) : ConnectionSetupAction
    data object OnContinueClick : ConnectionSetupAction
    data object OnBackPress : ConnectionSetupAction
    data object OnBackConfirmed : ConnectionSetupAction
    data object OnBackCancelled : ConnectionSetupAction
    data object OnCancelPairingClick : ConnectionSetupAction
    data object OnAcceptPairingClick : ConnectionSetupAction
    data object OnRejectPairingClick : ConnectionSetupAction
    data object OnGenerateRelayCodeClick : ConnectionSetupAction
    data class OnRelayCodeInputChange(val value: String) : ConnectionSetupAction
    data object OnJoinRelayCodeClick : ConnectionSetupAction
}

sealed interface ConnectionSetupEvent {
    data class NavigateNext(val role: AppRole) : ConnectionSetupEvent
    data object NavigateBackToRoleSelection : ConnectionSetupEvent
    data class ShowMessage(val message: UiText) : ConnectionSetupEvent
}
