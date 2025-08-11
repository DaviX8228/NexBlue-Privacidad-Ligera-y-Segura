package com.nexblue.app.bluetooth

// BluetoothConstants.kt
import java.util.UUID

object BluetoothConstants {
    // USAR LOS MISMOS UUIDs EN TODA LA APP
    val NEXBLUE_SERVICE_UUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    val NEXBLUE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")

    const val NEXBLUE_SERVICE_NAME = "NexBlue"
    const val CONNECTION_TIMEOUT = 10000L
    const val SCAN_TIMEOUT = 15000L
}
