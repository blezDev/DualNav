package com.blez.dualnav.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionType {
    BLUETOOTH,
    WIFI,
    FIREBASE
}
