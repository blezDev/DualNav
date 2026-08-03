package com.blez.dualnav.core.domain.model

sealed class ConnectionStatus {
    object Connected : ConnectionStatus()
    object Disconnected : ConnectionStatus()
    object Reconnecting : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}
