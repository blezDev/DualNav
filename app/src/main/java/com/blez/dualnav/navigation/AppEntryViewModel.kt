package com.blez.dualnav.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resolves where the NavHost should start: a device with no saved role goes through role
 * selection, a device with a saved role resumes at connection setup (transports don't
 * survive process death, so the connection always needs re-establishing on launch).
 * Also surfaces the persisted [AppThemeMode] so [com.blez.dualnav.MainActivity] can apply it
 * app-wide and react live when it's changed from Settings.
 */
class AppEntryViewModel(
    private val deviceRepository: DeviceRepository,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _startDestination = MutableStateFlow<AppStartDestination>(AppStartDestination.Loading)
    val startDestination = _startDestination.asStateFlow()

    val themeMode = preferencesRepository.getThemeMode()
        .map { it ?: AppThemeMode.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.DEFAULT)

    init {
        viewModelScope.launch {
            val destination = when (deviceRepository.getDeviceRole()) {
                AppRole.CONTROL -> AppStartDestination.ResumeControl
                AppRole.COMPANION -> AppStartDestination.ResumeCompanion
                null -> AppStartDestination.RoleSelection
            }
            _startDestination.update { destination }
        }
    }
}
