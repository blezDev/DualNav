package com.blez.dualnav.core.domain.model

/** Drives the pairing confirmation UI on both sides of a WiFi pairing handshake. */
sealed interface PairingState {
    data object Idle : PairingState
    data class AwaitingConfirmation(val deviceName: String, val pin: String) : PairingState
}
