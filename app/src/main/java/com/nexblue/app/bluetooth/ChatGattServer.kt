package com.nexblue.app.bluetooth

import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.*

//class ChatGattServer(
//    private val context: Context,
//    private val onMessageReceived: (String, BluetoothDevice) -> Unit
//) {
//    companion object {
//        val SERVICE_UUID: UUID = BluetoothConstants.NEXBLUE_SERVICE_UUID
//        val CHARACTERISTIC_UUID: UUID = BluetoothConstants.NEXBLUE_CHARACTERISTIC_UUID
//        private const val TAG = "ChatGattServer"
//    }
//
//    private var bluetoothManager = context.getSystemService(BluetoothManager::class.java)
//    private var bluetoothAdapter = bluetoothManager?.adapter
//    private var gattServer: BluetoothGattServer? = null
//    private var isStarted = false
//
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    fun start() {
//        if (isStarted && gattServer != null) {
//            Log.d(TAG, "Servidor GATT ya está iniciado")
//            return
//        }
//
//        try {
//            // Cerrar servidor previo si existe
//            gattServer?.close()
//
//            Log.d(TAG, "🚀 Iniciando servidor GATT...")
//            Log.d(TAG, "📡 Servicio UUID: $SERVICE_UUID")
//            Log.d(TAG, "📝 Característica UUID: $CHARACTERISTIC_UUID")
//
//            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
//            if (gattServer == null) {
//                Log.e(TAG, "❌ No se pudo crear el servidor GATT")
//                return
//            }
//
//            val service = buildChatService()
//            val success = gattServer?.addService(service) ?: false
//            if (success) {
//                isStarted = true
//                Log.d(TAG, "✅ Servidor GATT iniciado correctamente")
//            } else {
//                Log.e(TAG, "❌ Error al agregar servicio GATT")
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "💥 Excepción al iniciar servidor GATT: ${e.message}")
//            e.printStackTrace()
//        }
//    }
//
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    fun stop() {
//        try {
//            gattServer?.clearServices()
//            gattServer?.close()
//            gattServer = null
//            isStarted = false
//            Log.d(TAG, "🛑 Servidor GATT detenido")
//        } catch (e: Exception) {
//            Log.e(TAG, "Error al detener servidor GATT: ${e.message}")
//        }
//    }
//
//    private fun buildChatService(): BluetoothGattService {
//        val service = BluetoothGattService(
//            SERVICE_UUID,
//            BluetoothGattService.SERVICE_TYPE_PRIMARY
//        )
//
//        val messageCharacteristic = BluetoothGattCharacteristic(
//            CHARACTERISTIC_UUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE or
//                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
//                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
//            BluetoothGattCharacteristic.PERMISSION_WRITE
//        )
//
//        service.addCharacteristic(messageCharacteristic)
//        Log.d(TAG, "🔧 Servicio creado con características: WRITE, WRITE_NO_RESPONSE, NOTIFY")
//        return service
//    }
//
//    private val gattServerCallback = object : BluetoothGattServerCallback() {
//        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
//            super.onConnectionStateChange(device, status, newState)
//            when (newState) {
//                BluetoothProfile.STATE_CONNECTED -> {
//                    Log.d(TAG, "🔗 Cliente conectado: ${device?.address} (Status: $status)")
//                }
//                BluetoothProfile.STATE_DISCONNECTED -> {
//                    Log.d(TAG, "🔌 Cliente desconectado: ${device?.address} (Status: $status)")
//                }
//            }
//        }
//
//        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//        override fun onCharacteristicWriteRequest(
//            device: BluetoothDevice?,
//            requestId: Int,
//            characteristic: BluetoothGattCharacteristic?,
//            preparedWrite: Boolean,
//            responseNeeded: Boolean,
//            offset: Int,
//            value: ByteArray?
//        ) {
//            Log.d(TAG, "📩 onCharacteristicWriteRequest RECIBIDO!")
//            Log.d(TAG, "📱 Dispositivo: ${device?.address}")
//            Log.d(TAG, "🆔 RequestId: $requestId")
//            Log.d(TAG, "🔧 Característica: ${characteristic?.uuid}")
//            Log.d(TAG, "📝 PreparedWrite: $preparedWrite")
//            Log.d(TAG, "✅ ResponseNeeded: $responseNeeded")
//            Log.d(TAG, "📍 Offset: $offset")
//            Log.d(TAG, "📊 Value size: ${value?.size}")
//
//            // RESPONDER INMEDIATAMENTE PARA EVITAR TIMEOUTS
//            if (responseNeeded) {
//                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
//                val success = gattServer?.sendResponse(
//                    device,
//                    requestId,
//                    BluetoothGatt.GATT_SUCCESS,
//                    0,
//                    null
//                ) ?: false
//                Log.d(TAG, if (success) "✅ Respuesta enviada inmediatamente" else "❌ Error enviando respuesta")
//            }
//
//            // PROCESAR EL MENSAJE
//            if (characteristic?.uuid == CHARACTERISTIC_UUID && value != null && device != null) {
//                try {
//                    val message = String(value, Charsets.UTF_8)
//                    Log.d(TAG, "📨 MENSAJE RECIBIDO: '$message' de ${device.address}")
//
//                    // PROCESAR EN HILO PRINCIPAL
//                    Handler(Looper.getMainLooper()).post {
//                        try {
//                            onMessageReceived(message, device)
//                            Log.d(TAG, "✅ Callback onMessageReceived ejecutado exitosamente")
//                        } catch (e: Exception) {
//                            Log.e(TAG, "💥 Error en callback: ${e.message}")
//                            e.printStackTrace()
//                        }
//                    }
//                } catch (e: Exception) {
//                    Log.e(TAG, "💥 Error procesando mensaje: ${e.message}")
//                    e.printStackTrace()
//                }
//            } else {
//                Log.e(TAG, "❌ Datos inválidos en escritura")
//                Log.e(TAG, "🎯 Característica esperada: $CHARACTERISTIC_UUID")
//                Log.e(TAG, "📝 Característica recibida: ${characteristic?.uuid}")
//                Log.e(TAG, "📊 Value null: ${value == null}")
//                Log.e(TAG, "📱 Device null: ${device == null}")
//            }
//        }
//
//        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
//            super.onServiceAdded(status, service)
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.d(TAG, "✅ Servicio agregado exitosamente: ${service?.uuid}")
//            } else {
//                Log.e(TAG, "❌ Error agregando servicio: $status")
//            }
//        }
//
//        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
//            super.onMtuChanged(device, mtu)
//            Log.d(TAG, "📏 MTU cambiado para ${device?.address}: $mtu bytes")
//        }
//    }
//}