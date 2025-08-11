package com.nexblue.app.bluetooth

import android.bluetooth.BluetoothDevice

// BluetoothDeviceInfo.kt - Versión unificada final
// Esta clase combina ambas versiones que tenías
data class BluetoothDeviceInfo(
    val userId: String,
    val alias: String,
    val hasNexBlueApp: Boolean = false, // Campo con valor por defecto
    val bluetoothDevice: BluetoothDevice? = null,
    val distanceMeters: Double? = null // NUEVO
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