package com.blez.dualnav.navigation

sealed interface AppStartDestination {
    data object Loading : AppStartDestination
    data object RoleSelection : AppStartDestination
    data object ResumeControlHome : AppStartDestination
    data object ResumeCompanionHome : AppStartDestination
}
