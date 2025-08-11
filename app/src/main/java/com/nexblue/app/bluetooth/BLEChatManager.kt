package com.nexblue.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.Charset
import java.util.*
import kotlin.math.pow


// BLEChatManager.kt - Versión corregida para mensajes públicos y privados
@SuppressLint("MissingPermission")
object BLEChatManager {
    private const val TAG = "BLEChatManager"
    private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")

    private var advertiser: BluetoothLeAdvertiser? = null
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "✅ Advertising iniciado.")
        }
        override fun onStartFailure(errorCode: Int) {
            val error = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Datos demasiado largos"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Error interno"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Demasiados advertisers"
                else -> "Error desconocido: $errorCode"
            }
            Log.e(TAG, "❌ Error advertising: $error")
        }
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private val processedMessages = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    // CALLBACKS SEPARADOS PARA PÚBLICO Y PRIVADO
    private var onPublicMessageReceived: ((MensajePublicoCompleto) -> Unit)? = null
    private var onPrivateMessageReceived: ((String, String, String) -> Unit)? = null // (deAlias, paraAlias, mensaje)

    private val mensajeFragmentos = mutableMapOf<String, MutableList<String>>()
    private val mensajeConteo = mutableMapOf<String, Int>()
    private var isSending = false

    fun initialize(context: Context) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!adapter.isEnabled) {
            Log.e(TAG, "❌ Bluetooth no está habilitado")
            return
        }
        advertiser = adapter.bluetoothLeAdvertiser
        scanner = adapter.bluetoothLeScanner
        Log.d(TAG, "✅ BLEChatManager inicializado")
    }

    // ENVÍO DE MENSAJES PÚBLICOS
    fun startAdvertising(
        context: Context,
        mensaje: String,
        alias: String,
        userId: String,
        etiqueta: String
    ) {
        if (advertiser == null) {
            Log.e(TAG, "❌ Advertiser no disponible")
            return
        }

        stopAdvertising()
        val shortAlias = alias.take(8)
        val shortId = userId.takeLast(3)

        Log.d(TAG, "📤 ENVIANDO MENSAJE PÚBLICO:")
        Log.d(TAG, "   Mensaje: '$mensaje'")
        Log.d(TAG, "   Alias: '$shortAlias'")
        Log.d(TAG, "   UserID: '$shortId'")

        val fragments = fragmentarMensajeCompleto(mensaje, shortAlias, shortId, "PUBLIC")

        if (fragments.size == 1) {
            startAdvertisingWithMessage(fragments[0])
            onSentSuccessfully(alias, userId, mensaje)
        } else {
            enviarFragmentosSecuenciales(fragments) {
                onSentSuccessfully(alias, userId, mensaje)
            }
        }
    }

    // ENVÍO DE MENSAJES PRIVADOS
    fun sendPrivateMessage(
        context: Context,
        mensaje: String,
        alias: String,
        userId: String,
        destinatarioAlias: String
    ) {
        if (advertiser == null) {
            Log.e(TAG, "❌ Advertiser no disponible para mensaje privado")
            return
        }

        stopAdvertising()
        val shortAlias = alias.take(8)
        val shortId = userId.takeLast(3)
        val mensajePrivado = "PRIVATE:$destinatarioAlias:$mensaje"

        Log.d(TAG, "📤 ENVIANDO MENSAJE PRIVADO:")
        Log.d(TAG, "   De: '$shortAlias' Para: '$destinatarioAlias'")
        Log.d(TAG, "   Mensaje: '$mensaje'")
        Log.d(TAG, "   Formato: '$mensajePrivado'")

        val fragments = fragmentarMensajeCompleto(mensajePrivado, shortAlias, shortId, "PRIVATE")

        if (fragments.size == 1) {
            startAdvertisingWithMessage(fragments[0])
            Log.d(TAG, "✅ Mensaje privado enviado")
        } else {
            enviarFragmentosSecuenciales(fragments) {
                Log.d(TAG, "✅ Mensaje privado fragmentado enviado")
            }
        }
    }

    private fun startAdvertisingWithMessage(message: String) {
        Log.d(TAG, "📡 Transmitiendo: '$message' (${message.length} chars)")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), messageBytes)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        Log.d(TAG, "⏹️ Advertising detenido")
    }

    // NUEVO: FUNCIÓN UNIFICADA PARA ESCUCHAR PÚBLICOS Y PRIVADOS
    fun startScanning(
        context: Context,
        onPublicMessage: (MensajePublicoCompleto) -> Unit,
        onPrivateMessage: (String, String, String) -> Unit = { _, _, _ -> } // deAlias, paraAlias, mensaje
    ) {
        if (scanner == null) {
            Log.e(TAG, "❌ Scanner no disponible")
            return
        }

        stopScanning()
        processedMessages.clear()
        onPublicMessageReceived = onPublicMessage
        onPrivateMessageReceived = onPrivateMessage

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setReportDelay(0L)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processDevice(result)
            }
            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { processDevice(it) }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "❌ Error scanning: $errorCode")
                isScanning = false
            }
        }

        scanner?.startScan(filters, settings, scanCallback)
        isScanning = true
        Log.d(TAG, "🔍 Scanning iniciado (público + privado)")
    }

    private fun processDevice(result: ScanResult) {
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID)) ?: return

        try {
            val message = String(serviceData, Charsets.UTF_8)
            val deviceAddress = result.device.address
            val messageKey = "$deviceAddress:${message.hashCode()}"

            Log.d(TAG, "📨 MENSAJE RAW RECIBIDO:")
            Log.d(TAG, "   Device: $deviceAddress")
            Log.d(TAG, "   Mensaje: '$message'")

            if (!processedMessages.add(messageKey)) {
                Log.d(TAG, "⚠️ Mensaje duplicado ignorado")
                return
            }

            // Limpiar caché
            if (processedMessages.size > 50) {
                processedMessages.removeAll(processedMessages.take(25).toSet())
            }

            val parts = message.split(":", limit = 3)
            if (parts.size >= 3) {
                val header = parts[0]
                val parteInfo = parts[1]
                val contenido = parts[2]

                val (parteActual, totalPartes) = parteInfo.split("/").map { it.toIntOrNull() ?: 0 }

                if (parteActual in 1..totalPartes) {
                    val lista = mensajeFragmentos.getOrPut(header) { MutableList(totalPartes) { "" } }
                    lista[parteActual - 1] = contenido
                    mensajeConteo[header] = (mensajeConteo[header] ?: 0) + 1

                    Log.d(TAG, "🧩 Fragmento almacenado: ${mensajeConteo[header]}/$totalPartes")

                    if (mensajeConteo[header] == totalPartes && lista.all { it.isNotEmpty() }) {
                        val mensajeFinal = lista.joinToString("")
                        mensajeFragmentos.remove(header)
                        mensajeConteo.remove(header)

                        processMensajeCompleto(mensajeFinal)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando mensaje: ${e.message}")
        }
    }

    private fun processMensajeCompleto(mensajeFinal: String) {
        Log.d(TAG, "🔍 PROCESANDO MENSAJE FINAL: '$mensajeFinal'")

        val campos = mensajeFinal.split("|", limit = 4)
        if (campos.size < 4) {
            Log.e(TAG, "❌ Formato inválido: se esperan 4 campos, recibidos ${campos.size}")
            return
        }

        val alias = campos[0]
        val id = campos[1]
        val tipo = campos[2] // PUBLIC o PRIVATE
        val contenido = campos[3]

        Log.d(TAG, "📋 CAMPOS PROCESADOS:")
        Log.d(TAG, "   Alias: '$alias'")
        Log.d(TAG, "   ID: '$id'")
        Log.d(TAG, "   Tipo: '$tipo'")
        Log.d(TAG, "   Contenido: '$contenido'")

        when (tipo) {
            "PUBLIC" -> {
                if (contenido.isNotBlank() || contenido.isEmpty()) { // Permitir pings vacíos
                    val mensajeCompleto = MensajePublicoCompleto(
                        mensaje = contenido,
                        alias = alias,
                        id = id,
                        etiqueta = "Social"
                    )

                    handler.post {
                        onPublicMessageReceived?.invoke(mensajeCompleto)
                        Log.d(TAG, "✅ Mensaje público enviado al callback")
                    }
                }
            }
            "PRIVATE" -> {
                if (contenido.startsWith("PRIVATE:") && contenido.contains(":")) {
                    val partes = contenido.split(":", limit = 3)
                    if (partes.size == 3) {
                        val destinatario = partes[1]
                        val mensaje = partes[2]

                        Log.d(TAG, "💬 MENSAJE PRIVADO DETECTADO:")
                        Log.d(TAG, "   De: '$alias' Para: '$destinatario'")
                        Log.d(TAG, "   Mensaje: '$mensaje'")

                        handler.post {
                            onPrivateMessageReceived?.invoke(alias, destinatario, mensaje)
                            Log.d(TAG, "✅ Mensaje privado enviado al callback")
                        }
                    }
                }
            }
            else -> {
                Log.w(TAG, "⚠️ Tipo de mensaje desconocido: '$tipo'")
            }
        }
    }

    fun stopScanning() {
        if (isScanning) {
            scanner?.stopScan(scanCallback)
            scanCallback = null
            isScanning = false
            onPublicMessageReceived = null
            onPrivateMessageReceived = null
            processedMessages.clear()
            Log.d(TAG, "⏹️ Scanning detenido")
        }
    }

    fun shutdown() {
        stopAdvertising()
        stopScanning()
        Log.d(TAG, "🔌 BLEChatManager apagado")
    }

    fun isAdvertising(): Boolean = advertiseCallback != null
    fun isScanning(): Boolean = isScanning

    private fun fragmentarMensajeCompleto(
        msg: String,
        alias: String,
        userId: String,
        tipo: String // "PUBLIC" o "PRIVATE"
    ): List<String> {
        val msgId = (System.currentTimeMillis() % 10000).toString(16)
        val contenido = "$alias|$userId|$tipo|$msg"

        Log.d(TAG, "✂️ FRAGMENTANDO:")
        Log.d(TAG, "   MsgID: '$msgId'")
        Log.d(TAG, "   Tipo: '$tipo'")
        Log.d(TAG, "   Contenido: '$contenido' (${contenido.length} chars)")

        val chunkSize = 12
        val partes = contenido.chunked(chunkSize)
        val total = partes.size

        val fragments = partes.mapIndexed { index, parte ->
            "$msgId:${index + 1}/$total:$parte"
        }

        Log.d(TAG, "   Fragmentos generados: ${fragments.size}")
        return fragments
    }

    private fun enviarFragmentosSecuenciales(fragments: List<String>, onFinished: (() -> Unit)? = null) {
        if (fragments.isEmpty()) return

        Log.d(TAG, "📦 Enviando ${fragments.size} fragmentos secuenciales")
        var index = 0

        val sendNext = object : Runnable {
            override fun run() {
                stopAdvertising()
                if (index < fragments.size) {
                    Log.d(TAG, "📤 Fragmento ${index + 1}/${fragments.size}: '${fragments[index]}'")
                    startAdvertisingWithMessage(fragments[index])
                    index++
                    handler.postDelayed(this, 900) // 900ms entre fragmentos
                } else {
                    startAdvertisingWithMessage(fragments.last())
                    Log.d(TAG, "✅ Todos los fragmentos enviados")
                    onFinished?.invoke()
                }
            }
        }
        handler.post(sendNext)
    }

    private fun onSentSuccessfully(alias: String, userId: String, mensaje: String) {
        Log.d(TAG, "✅ Mensaje enviado exitosamente:")
        Log.d(TAG, "   Alias: '$alias'")
        Log.d(TAG, "   UserID: '$userId'")
        Log.d(TAG, "   Mensaje: '$mensaje'")
    }
}