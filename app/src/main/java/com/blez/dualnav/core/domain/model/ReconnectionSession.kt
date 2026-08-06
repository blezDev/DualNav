package com.blez.dualnav.core.domain.model

/**
 * Cross-device record of a control/companion pairing, mirrored in Firebase so either phone can
 * tell - even after being fully closed and relaunched - whether the other side is still expecting
 * a reconnection or explicitly ended the session.
 */
data class ReconnectionSession(
    val controlDeviceId: String,
    val companionDeviceId: String,
    val status: Status,
    val endedByRole: AppRole? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    enum class Status { ACTIVE, ENDED }
}
