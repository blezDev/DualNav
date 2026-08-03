package com.blez.dualnav.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class NavigationCommand {
    @Serializable
    data class Navigate(val destination: Destination) : NavigationCommand()
    @Serializable
    object Stop : NavigationCommand()
    @Serializable
    object Resume : NavigationCommand()
    @Serializable
    data class AddStop(val destination: Destination) : NavigationCommand()
    @Serializable
    data class StatusCheck(val deviceId: String) : NavigationCommand()
}
