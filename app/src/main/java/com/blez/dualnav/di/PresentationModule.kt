package com.blez.dualnav.di

import com.blez.dualnav.feature.companion.presentation.CompanionHomeViewModel
import com.blez.dualnav.feature.connection.presentation.ConnectionSetupViewModel
import com.blez.dualnav.feature.control.presentation.ControlHomeViewModel
import com.blez.dualnav.feature.role.presentation.RoleSelectionViewModel
import com.blez.dualnav.feature.settings.presentation.SettingsViewModel
import com.blez.dualnav.navigation.AppEntryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::AppEntryViewModel)
    viewModelOf(::RoleSelectionViewModel)
    viewModelOf(::ConnectionSetupViewModel)
    viewModelOf(::ControlHomeViewModel)
    viewModelOf(::CompanionHomeViewModel)
    viewModelOf(::SettingsViewModel)
}
