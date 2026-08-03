package com.blez.dualnav.feature.settings.presentation

import com.blez.dualnav.core.domain.model.AppThemeMode

data class SettingsState(
    val themeMode: AppThemeMode = AppThemeMode.DEFAULT
)

sealed interface SettingsAction {
    data class OnThemeSelected(val themeMode: AppThemeMode) : SettingsAction
}
