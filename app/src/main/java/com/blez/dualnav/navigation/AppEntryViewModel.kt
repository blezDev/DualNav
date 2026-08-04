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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resolves where the NavHost should start: role selection is the default unless a connection
 * has actually been established before, in which case the app opens directly on that role's
 * home screen (the transport itself is re-established there, since sockets don't survive
 * process death).
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
            val role = deviceRepository.getDeviceRole()
            val established = deviceRepository.isConnectionEstablished().first()
            val destination = when {
                role == AppRole.CONTROL && established -> AppStartDestination.ResumeControlHome
                role == AppRole.COMPANION && established -> AppStartDestination.ResumeCompanionHome
                else -> AppStartDestination.RoleSelection
            }
            _startDestination.update { destination }
        }
    }
}
