package com.nexblue.app.bluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat

object BluetoothUtils {
    private const val TAG = "BluetoothUtils"

    fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val isEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled
        Log.d(TAG, "🔵 Bluetooth habilitado: $isEnabled")
        return isEnabled
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        val connectPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectPermission = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val scanPermission = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            val advertisePermission = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED

            val locationPermission = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "🔍 Verificación de permisos (Android 12+):")
            Log.d(TAG, "  📱 BLUETOOTH_CONNECT: $connectPermission")
            Log.d(TAG, "  🔍 BLUETOOTH_SCAN: $scanPermission")
            Log.d(TAG, "  📡 BLUETOOTH_ADVERTISE: $advertisePermission")
            Log.d(TAG, "  📍 ACCESS_FINE_LOCATION: $locationPermission")

            val allGranted = connectPermission && scanPermission && advertisePermission && locationPermission
            Log.d(TAG, "✅ Todos los permisos otorgados: $allGranted")

            allGranted
        } else {
            val coarseLocation = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val fineLocation = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "🔍 Verificación de permisos (Android <12):")
            Log.d(TAG, "  📍 ACCESS_COARSE_LOCATION: $coarseLocation")
            Log.d(TAG, "  📍 ACCESS_FINE_LOCATION: $fineLocation")

            val allGranted = coarseLocation && fineLocation
            Log.d(TAG, "✅ Todos los permisos otorgados: $allGranted")

            allGranted
        }

    }


    fun requestBluetoothPermissions(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ),
                requestCode
            )
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                requestCode
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableBluetooth(activity: Activity, requestCode: Int) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(enableBtIntent, requestCode)
    }

    /**
     * Función específica para verificar si podemos usar GATT Client
     */
    fun canUseGattClient(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "🔌 Puede usar GATT Client: $hasConnect")
            return hasConnect
        }
        return true // En versiones anteriores no se necesita este permiso específico
    }

    /**
     * Función específica para verificar si podemos usar GATT Server
     */
    fun canUseGattServer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val hasAdvertise = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "🖥️ Puede usar GATT Server: ${hasConnect && hasAdvertise}")
            return hasConnect && hasAdvertise
        }
        return true
    }
}