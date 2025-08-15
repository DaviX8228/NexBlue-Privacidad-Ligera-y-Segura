package com.nexblue.app.bluetooth

import android.bluetooth.BluetoothDevice

// BluetoothDeviceInfo.kt - Versión unificada final
data class BluetoothDeviceInfo(
    val userId: String,
    val alias: String,
    val hasNexBlueApp: Boolean = false,

    val bluetoothDevice: BluetoothDevice? = null,
    val distanceMeters: Double? = null 
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BluetoothDeviceInfo
        return userId == other.userId
    }

    override fun hashCode(): Int {
        return userId.hashCode()
    }
}
