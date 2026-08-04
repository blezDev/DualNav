package com.blez.dualnav.feature.settings.presentation

import com.blez.dualnav.core.domain.model.AppThemeMode

data class SettingsState(
    val themeMode: AppThemeMode = AppThemeMode.DEFAULT,
    val isConnected: Boolean = false,
    val showDisconnectConfirmation: Boolean = false
)

sealed interface SettingsAction {
    data class OnThemeSelected(val themeMode: AppThemeMode) : SettingsAction
    data object OnDisconnectClick : SettingsAction
    data object OnDisconnectConfirmed : SettingsAction
    data object OnDisconnectCancelled : SettingsAction
}

sealed interface SettingsEvent {
    data object NavigateToRoleSelection : SettingsEvent
}
