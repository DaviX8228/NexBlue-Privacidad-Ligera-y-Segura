package com.nexblue.app.bluetooth

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.*
import kotlinx.coroutines.*

// ChatGattClient.kt - Versión mejorada con mejor conectividad
class ChatGattClient {
    private var bluetoothGatt: BluetoothGatt? = null
    private var pendingMessage: String? = null
    private var messageDelivered = false
    private var connectionTimeout: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    fun sendMessage(context: Context, device: BluetoothDevice?, message: String) {
        if (device == null) {
            Log.e("GATT_CLIENT", "Dispositivo nulo")
            return
        }

        // Limpiar conexión anterior
       // cleanup()
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No se requiere permiso en Android < 12
        }

        Log.d("GATT_CLIENT", "🔍 Verificación manual de permiso CONNECT: $hasPermission")


        pendingMessage = message
        messageDelivered = false

        Log.d("GATT_CLIENT", "Conectando a ${device.address} para enviar: '$message'")

        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("GATT_CLIENT", "Sin permisos BLUETOOTH_CONNECT")
            return
        }

        // Timeout de conexión
        connectionTimeout = Runnable {
            if (!messageDelivered) {
                Log.e("GATT_CLIENT", "Timeout de conexión - cerrando")
                cleanup()
            }
        }
        handler.postDelayed(connectionTimeout!!, 15000) // 15 segundos

        // Conectar con autoConnect = false para conexión directa
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d("GATT_CLIENT", "Estado: $newState, Status: $status")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d("GATT_CLIENT", "Conectado exitosamente")

                        // Delay antes de descubrir servicios (importante para estabilidad)
                        handler.postDelayed({
                            if (gatt?.discoverServices() == true) {
                                Log.d("GATT_CLIENT", "Descubriendo servicios...")
                            } else {
                                Log.e("GATT_CLIENT", "Error iniciando descubrimiento")
                                cleanup()
                            }
                        }, 600) // 600ms delay

                    } else {
                        Log.e("GATT_CLIENT", "Error en conexión, status: $status")
                        cleanup()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("GATT_CLIENT", "Desconectado")
                    cleanup()
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    Log.d("GATT_CLIENT", "Conectando...")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d("GATT_CLIENT", "Servicios descubiertos, status: $status")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("GATT_CLIENT", "Error descubriendo servicios")
                cleanup()
                return
            }

            // Buscar nuestro servicio
            val service = gatt?.getService(BluetoothConstants.NEXBLUE_SERVICE_UUID)
            if (service == null) {
                Log.e("GATT_CLIENT", "Servicio NexBlue no encontrado")
                // Listar servicios disponibles para debug
                gatt?.services?.forEach { srv ->
                    Log.d("GATT_CLIENT", "Servicio disponible: ${srv.uuid}")
                }
                cleanup()
                return
            }

            // Buscar característica
            val characteristic = service.getCharacteristic(BluetoothConstants.NEXBLUE_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                Log.e("GATT_CLIENT", "Característica no encontrada")
                cleanup()
                return
            }

            // Verificar propiedades de escritura
            val canWrite = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            val canWriteNoResponse = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

            if (!canWrite && !canWriteNoResponse) {
                Log.e("GATT_CLIENT", "Característica no permite escritura")
                cleanup()
                return
            }

            // Escribir mensaje
            pendingMessage?.let { msg ->
                try {
                    val messageBytes = msg.toByteArray(Charsets.UTF_8)

                    if (messageBytes.size > 512) { // MTU típico es 517, dejamos margen
                        Log.e("GATT_CLIENT", "Mensaje demasiado largo: ${messageBytes.size} bytes")
                        cleanup()
                        return
                    }

                    characteristic.value = messageBytes
                    characteristic.writeType = if (canWriteNoResponse) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }

                    Log.d("GATT_CLIENT", "Escribiendo mensaje (${messageBytes.size} bytes)")

                    if (!gatt.writeCharacteristic(characteristic)) {
                        Log.e("GATT_CLIENT", "Error iniciando escritura")
                        cleanup()
                    }

                } catch (e: Exception) {
                    Log.e("GATT_CLIENT", "Excepción escribiendo: ${e.message}")
                    cleanup()
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            messageDelivered = true

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("GATT_CLIENT", "✅ Mensaje enviado exitosamente")
            } else {
                Log.e("GATT_CLIENT", "❌ Error enviando mensaje, status: $status")
            }

            // Desconectar después de un breve delay
            handler.postDelayed({
                cleanup()
            }, 200)
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d("GATT_CLIENT", "MTU cambiado: $mtu, status: $status")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun cleanup() {
        try {
            // Cancelar timeout
            connectionTimeout?.let { handler.removeCallbacks(it) }

            // Cerrar conexión GATT
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null

            // Limpiar variables
            pendingMessage = null
            connectionTimeout = null

        } catch (e: Exception) {
            Log.e("GATT_CLIENT", "Error en cleanup: ${e.message}")
        }
    }
}