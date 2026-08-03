package com.blez.dualnav.navigation

sealed interface AppStartDestination {
    data object Loading : AppStartDestination
    data object RoleSelection : AppStartDestination
    data object ResumeControl : AppStartDestination
    data object ResumeCompanion : AppStartDestination
}
