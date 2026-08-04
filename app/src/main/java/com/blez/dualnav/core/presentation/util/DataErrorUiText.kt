package com.blez.dualnav.core.presentation.util

import com.blez.dualnav.R
import com.blez.dualnav.core.domain.util.DataError

fun DataError.toUiText(): UiText = when (this) {
    DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
    DataError.Local.NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
    DataError.Local.UNKNOWN -> UiText.StringResource(R.string.error_unknown)
    DataError.Connection.BLUETOOTH_UNAVAILABLE -> UiText.StringResource(R.string.error_bluetooth_unavailable)
    DataError.Connection.WIFI_UNAVAILABLE -> UiText.StringResource(R.string.error_wifi_unavailable)
    DataError.Connection.FIREBASE_UNAVAILABLE -> UiText.StringResource(R.string.error_firebase_unavailable)
    DataError.Connection.PERMISSION_DENIED -> UiText.StringResource(R.string.error_permission_denied)
    DataError.Connection.DEVICE_NOT_FOUND -> UiText.StringResource(R.string.error_device_not_found)
    DataError.Connection.TIMEOUT -> UiText.StringResource(R.string.error_timeout)
    DataError.Connection.NOT_CONNECTED -> UiText.StringResource(R.string.error_not_connected)
    DataError.Connection.PAIRING_REJECTED -> UiText.StringResource(R.string.error_pairing_rejected)
    DataError.Connection.UNKNOWN -> UiText.StringResource(R.string.error_unknown)
}
