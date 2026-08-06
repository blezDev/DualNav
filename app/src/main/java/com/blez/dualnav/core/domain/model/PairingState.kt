package com.blez.dualnav.core.domain.model

/** Drives the pairing confirmation UI on both sides of a WiFi pairing handshake, or Control's
 * side of a Firebase relay-channel pairing. */
sealed interface PairingState {
    data object Idle : PairingState
    data class AwaitingConfirmation(val deviceName: String, val pin: String) : PairingState

    /** Control-only: a relay code was generated and is waiting for Companion to join it. */
    data class WaitingForRelayPeer(val code: String) : PairingState
}
