package com.blez.dualnav.feature.settings.presentation

import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.model.AppUpdate
import com.blez.dualnav.core.presentation.util.UiText

data class SettingsState(
    val themeMode: AppThemeMode = AppThemeMode.DEFAULT,
    val isConnected: Boolean = false,
    val showDisconnectConfirmation: Boolean = false,
    val isCheckingForUpdate: Boolean = false,
    val availableUpdate: AppUpdate? = null,
    val isDownloadingUpdate: Boolean = false
)

sealed interface SettingsAction {
    data class OnThemeSelected(val themeMode: AppThemeMode) : SettingsAction
    data object OnDisconnectClick : SettingsAction
    data object OnDisconnectConfirmed : SettingsAction
    data object OnDisconnectCancelled : SettingsAction
    data object OnCheckForUpdateClick : SettingsAction
    data object OnDownloadUpdateClick : SettingsAction
    data object OnDismissUpdateDialog : SettingsAction
}

sealed interface SettingsEvent {
    data object NavigateToRoleSelection : SettingsEvent
    data class ShowSnackbar(val message: UiText) : SettingsEvent
}
