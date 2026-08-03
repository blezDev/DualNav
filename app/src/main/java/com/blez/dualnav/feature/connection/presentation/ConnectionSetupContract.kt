package com.blez.dualnav.feature.connection.presentation

import androidx.compose.runtime.Stable
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.presentation.util.UiText

@Stable
data class ConnectionSetupState(
    val role: AppRole? = null,
    val selectedType: ConnectionType? = null,
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
    val devices: List<DeviceInfoUi> = emptyList(),
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false
)

sealed interface ConnectionSetupAction {
    data class OnConnectionTypeSelected(val type: ConnectionType) : ConnectionSetupAction
    data object OnDiscoverClick : ConnectionSetupAction
    data class OnDeviceClick(val deviceId: String) : ConnectionSetupAction
    data object OnContinueClick : ConnectionSetupAction
}

sealed interface ConnectionSetupEvent {
    data class NavigateNext(val role: AppRole) : ConnectionSetupEvent
    data class ShowMessage(val message: UiText) : ConnectionSetupEvent
}
