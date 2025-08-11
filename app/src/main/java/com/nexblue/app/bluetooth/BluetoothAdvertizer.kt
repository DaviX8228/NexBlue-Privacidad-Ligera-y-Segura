package com.nexblue.app.bluetooth

// BluetoothAdvertiser.kt - Nueva clase para anunciar tu app
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log

@SuppressLint("MissingPermission")
object BluetoothAdvertiser {
    private val TAG = "BluetoothAdvertiser"
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var advertiseCallback: AdvertiseCallback? = null

    fun startAdvertising(context: Context): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth no está habilitado")
            return false
        }

        // Debe ser !isMultipleAdvertisementSupported
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Log.e(TAG, "El dispositivo no soporta advertising BLE")
            return false
        }

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "No se pudo obtener el advertiser BLE")
            return false
        }

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false) // Solo para descubrimiento
            .setTimeout(0) // Sin timeout
            .build()
        // Forzar un nombre corto temporal para evitar que lo meta en el paquete
        bluetoothAdapter.name = "${Build.MODEL.take(10)}" // Ej: NexBlue-POCOF5



        val alias = "${Build.MODEL.take(10)}"
        val aliasBytes = alias.toByteArray(Charsets.UTF_8)

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BluetoothConstants.NEXBLUE_SERVICE_UUID))
            .addServiceData(
                ParcelUuid(BluetoothConstants.NEXBLUE_SERVICE_UUID),
                aliasBytes // Ahora sí mandas el nombre
            )
            .build()






        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising = true
                Log.d(TAG, "Advertising iniciado correctamente")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                isAdvertising = false
                val errorMessage = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Ya está publicitando"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Datos demasiado largos"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Error interno"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Demasiados advertisers"
                    else -> "Error desconocido: $errorCode"
                }
                Log.e(TAG, "Error al iniciar advertising: $errorMessage")
            }
        }

        try {
            bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            Log.d(TAG, "Solicitando inicio de advertising...")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al iniciar advertising: ${e.message}")
            return false
        }
    }

    fun stopAdvertising() {
        try {
            advertiseCallback?.let { callback ->
                bluetoothLeAdvertiser?.stopAdvertising(callback)
                isAdvertising = false
                Log.d(TAG, "Advertising detenido")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener advertising: ${e.message}")
        }
    }

    fun isAdvertising(): Boolean = isAdvertising
}