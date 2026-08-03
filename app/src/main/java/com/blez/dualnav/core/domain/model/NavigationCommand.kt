package com.blez.dualnav.core.domain.model

sealed class NavigationCommand {
    data class Navigate(val destination: Destination) : NavigationCommand()
    object Stop : NavigationCommand()
    object Resume : NavigationCommand()
    data class AddStop(val destination: Destination) : NavigationCommand()
    data class StatusCheck(val deviceId: String) : NavigationCommand()
}
