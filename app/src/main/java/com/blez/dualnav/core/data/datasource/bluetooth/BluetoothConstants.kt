package com.blez.dualnav.core.data.datasource.bluetooth

import java.util.UUID

internal object BluetoothConstants {
    const val SERVICE_NAME = "DualNavBluetoothService"
    val SERVICE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
}
