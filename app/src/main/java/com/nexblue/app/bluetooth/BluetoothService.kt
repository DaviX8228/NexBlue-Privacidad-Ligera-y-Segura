package com.nexblue.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlin.math.pow

// ====== SOLUCIÓN 1: Unificar generación de userId ======
// BluetoothScanner.kt - CORREGIDO COMPLETAMENTE

@SuppressLint("MissingPermission")
object BluetoothScanner {
    private val TAG = "BluetoothScanner"
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var scanCallback: ScanCallback? = null

    // CACHÉ PARA EVITAR DUPLICADOS
    private val foundDevices = mutableMapOf<String, BluetoothDeviceInfo>()

    fun startScan(
        context: Context,
        onDeviceFound: (BluetoothDeviceInfo) -> Unit,
        onPublicMessage: ((MensajePublicoCompleto) -> Unit)? = null
    ) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth no está habilitado")
            return
        }

        // LIMPIAR CACHÉ AL INICIAR NUEVO ESCANEO
        foundDevices.clear()

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "No se pudo obtener el scanner BLE")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        val scanFilters = emptyList<ScanFilter>()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processDevice(result.device, result.scanRecord, result.rssi, onDeviceFound)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                super.onBatchScanResults(results)
                Log.d(TAG, "Batch scan results: ${results.size} dispositivos")
                results.forEach { result ->
                    processDevice(result.device, result.scanRecord, result.rssi, onDeviceFound)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                val errorMessage = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Ya hay un escaneo en curso"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Fallo en el registro de la aplicación"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                    SCAN_FAILED_INTERNAL_ERROR -> "Error interno"
                    SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Sin recursos de hardware"
                    else -> "Error desconocido: $errorCode"
                }
                Log.e(TAG, "Error en el escaneo BLE: $errorMessage")
                isScanning = false
            }
        }

        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            Log.d(TAG, "Escaneo BLE iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar escaneo BLE: ${e.message}")
        }

        // TAMBIÉN ESCANEAR DISPOSITIVOS EMPAREJADOS (PERO SIN DUPLICAR)
        scanPairedDevices(onDeviceFound)
    }

    // FUNCIÓN UNIFICADA PARA PROCESAR CUALQUIER DISPOSITIVO
    private fun processDevice(
        device: BluetoothDevice,
        scanRecord: ScanRecord? = null,
        rssi: Int? = null,
        onDeviceFound: (BluetoothDeviceInfo) -> Unit,
        onPublicMessage: ((MensajePublicoCompleto) -> Unit)? = null
    ) {
        val userId = generateConsistentUserId(device)

        if (foundDevices.containsKey(userId)) return

        val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
        val serviceData = scanRecord?.getServiceData(ParcelUuid(BluetoothConstants.NEXBLUE_SERVICE_UUID))

        // Extraer mensaje si lo hay
        val decodedMessage = try {
            serviceData?.let { String(it, Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }

        val hasNexBlueApp = serviceUuids.contains(BluetoothConstants.NEXBLUE_SERVICE_UUID) ||
                serviceData != null || checkPairedDeviceForNexBlueService(device)

        // === 🆕 Nuevo: si hay mensaje válido, procesarlo como mensaje público
        if (decodedMessage != null && decodedMessage.isNotBlank() && onPublicMessage != null) {
            val parts = decodedMessage.split("|")
            if (parts.size == 5 && parts[0] == "mensaje") {
                val mensaje = parts[1]
                val alias = parts[2]
                val id = parts[3]
                val etiqueta = parts[4]

                Log.d(TAG, "💬 Mensaje público recibido: $mensaje ($alias | $id | $etiqueta)")
                onPublicMessage?.invoke(
                    MensajePublicoCompleto(
                        mensaje = mensaje,
                        alias = alias,
                        id = id,
                        etiqueta = etiqueta
                    )
                )

            } else {
                Log.d(TAG, "⚠️ Mensaje recibido con formato desconocido: $decodedMessage")
            }
        }


        // Continuar con lo demás (lista de dispositivos)
        val alias = decodedMessage ?: device.name ?: "Dispositivo BLE"
        val deviceInfo = BluetoothDeviceInfo(
            userId = userId,
            alias = alias,
            hasNexBlueApp = hasNexBlueApp,
            bluetoothDevice = device,
            distanceMeters = rssi?.let {
                val txPower = -59.0
                10.0.pow((txPower - it) / (10.0 * 2))
            }
        )

        foundDevices[userId] = deviceInfo
        onDeviceFound(deviceInfo)
    }


    // FUNCIÓN PARA GENERAR userId CONSISTENTE
    private fun generateConsistentUserId(device: BluetoothDevice): String {
        // USAR SIEMPRE LA DIRECCIÓN MAC SIN PUNTOS, ÚLTIMOS 4 CARACTERES
        return device.address.replace(":", "").takeLast(4).uppercase()
    }

    private fun scanPairedDevices(onDeviceFound: (BluetoothDeviceInfo) -> Unit) {
        try {
            val pairedDevices = getPairedDevices()
            Log.d(TAG, "Dispositivos emparejados encontrados: ${pairedDevices?.size ?: 0}")

            pairedDevices?.forEach { device ->
                // USAR LA MISMA FUNCIÓN PARA PROCESAR
                processDevice(device, null, null, onDeviceFound)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error al obtener dispositivos emparejados: ${e.message}")
        }
    }

    private fun checkPairedDeviceForNexBlueService(device: BluetoothDevice): Boolean {
        return try {
            device.uuids?.any { it.uuid == BluetoothConstants.NEXBLUE_SERVICE_UUID } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando servicio en dispositivo emparejado: ${e.message}")
            false
        }
    }

    fun stopScan(context: Context) {
        try {
            scanCallback?.let { callback ->
                bluetoothLeScanner?.stopScan(callback)
                scanCallback = null
            }
            isScanning = false
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            // LIMPIAR CACHÉ
            foundDevices.clear()
            Log.d(TAG, "Escaneo detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener escaneo: ${e.message}")
        }
    }

    fun getPairedDevices(): Set<BluetoothDevice>? {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter?.bondedDevices
    }
}