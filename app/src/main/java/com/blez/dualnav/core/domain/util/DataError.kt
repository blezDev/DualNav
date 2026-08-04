package com.blez.dualnav.core.domain.util

sealed interface DataError : Error {
    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN
    }

    enum class Connection : DataError {
        BLUETOOTH_UNAVAILABLE,
        WIFI_UNAVAILABLE,
        FIREBASE_UNAVAILABLE,
        PERMISSION_DENIED,
        DEVICE_NOT_FOUND,
        TIMEOUT,
        NOT_CONNECTED,
        PAIRING_REJECTED,
        UNKNOWN
    }
}