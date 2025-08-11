package com.nexblue.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission", "StaticFieldLeak")
object BluetoothManager {
    private const val TAG = "BluetoothManager"

    // Estados del sistema
    private var isInitialized = false
    private var gattServer: BluetoothGattServer? = null
    private var context: Context? = null

    // Callbacks registrados
    private val messageCallbacks = mutableListOf<(String, BluetoothDevice) -> Unit>()

    // Caché para evitar solicitudes duplicadas
    private val processedRequests = mutableSetOf<String>()

    // Mi propio userId consistente
    val myUserId: String by lazy {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter?.address?.replace(":", "")?.takeLast(4)?.uppercase()
            ?: UUID.randomUUID().toString().take(4).uppercase()
    }

    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Ya está inicializado")
            return
        }

        this.context = context.applicationContext
        startGattServer()
        BluetoothAdvertiser.startAdvertising(context)
        isInitialized = true
        Log.d(TAG, "Sistema inicializado con userId: $myUserId")
    }

    fun addMessageCallback(callback: (String, BluetoothDevice) -> Unit) {
        messageCallbacks.add(callback)
    }

    fun removeMessageCallback(callback: (String, BluetoothDevice) -> Unit) {
        messageCallbacks.remove(callback)
    }

    private fun startGattServer() {
        val androidBluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager
        val adapter = androidBluetoothManager?.adapter

        if (adapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth no está habilitado")
            return
        }

        gattServer = androidBluetoothManager.openGattServer(context, gattCallback)

        if (gattServer == null) {
            Log.e(TAG, "No se pudo crear servidor GATT")
            return
        }

        val service = BluetoothGattService(
            BluetoothConstants.NEXBLUE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val characteristic = BluetoothGattCharacteristic(
            BluetoothConstants.NEXBLUE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        Log.d(TAG, "Servidor GATT iniciado")
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Cliente conectado: ${device?.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Cliente desconectado: ${device?.address}")
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.d(TAG, "Solicitud de escritura recibida de: ${device?.address}")

            if (characteristic?.uuid == BluetoothConstants.NEXBLUE_CHARACTERISTIC_UUID
                && value != null && device != null) {

                try {
                    val message = String(value, Charsets.UTF_8)
                    Log.d(TAG, "Mensaje recibido: '$message'")

                    // Crear clave única para evitar duplicados
                    val requestKey = "${device.address}_${message}_${System.currentTimeMillis() / 1000}"

                    if (!processedRequests.contains(requestKey)) {
                        processedRequests.add(requestKey)

                        // Notificar a todos los callbacks en el hilo principal
                        Handler(Looper.getMainLooper()).post {
                            messageCallbacks.forEach { callback ->
                                try {
                                    callback(message, device)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error en callback: ${e.message}")
                                }
                            }
                        }

                        // Limpiar caché antigua (mantener solo últimos 50)
                        if (processedRequests.size > 50) {
                            val oldRequests = processedRequests.take(processedRequests.size - 40)
                            processedRequests.removeAll(oldRequests.toSet())
                        }
                    } else {
                        Log.d(TAG, "Solicitud duplicada ignorada: $requestKey")
                    }

                    // Responder siempre
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando mensaje: ${e.message}")
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                }
            } else {
                Log.e(TAG, "Datos inválidos en escritura")
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Servicio agregado exitosamente")
            } else {
                Log.e(TAG, "Error agregando servicio: $status")
            }
        }
    }

    fun sendMessage(context: Context, device: BluetoothDevice, message: String) {
        Log.d(TAG, "Enviando mensaje: '$message' a ${device.address}")

        // Usar el cliente mejorado
        ChatGattClient().sendMessage(context, device, message)
    }

    fun startServices(context: Context) {
        try {
            initialize(context)
            Log.d(TAG, "Servicios BLE iniciados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar servicios BLE: ${e.message}")
            throw e
        }
    }

    fun shutdown() {
        try {
            gattServer?.close()
            gattServer = null
            BluetoothAdvertiser.stopAdvertising()
            messageCallbacks.clear()
            processedRequests.clear()
            isInitialized = false
            Log.d(TAG, "Sistema desconectado")
        } catch (e: Exception) {
            Log.e(TAG, "Error en shutdown: ${e.message}")
        }
    }
}