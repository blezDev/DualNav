package com.blez.dualnav.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.getThemeMode().collect { themeMode ->
                _state.update { it.copy(themeMode = themeMode ?: AppThemeMode.DEFAULT) }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnThemeSelected -> {
                viewModelScope.launch { preferencesRepository.saveThemeMode(action.themeMode) }
            }
        }
    }
}
