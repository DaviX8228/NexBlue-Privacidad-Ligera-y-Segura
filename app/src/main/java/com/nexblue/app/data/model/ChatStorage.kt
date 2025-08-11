package com.nexblue.app.data.model

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs



// ChatStorage.kt - Versión mejorada con mejor gestión de mensajes
object ChatStorage {
    private const val TAG = "ChatStorage"
    private const val MAX_MESSAGES_PER_CHAT = 100
    private const val DUPLICATE_THRESHOLD_MS = 2000L // 2 segundos

    // Usar ConcurrentHashMap para thread safety
    private val privados = ConcurrentHashMap<String, MutableList<MensajePrivado>>()
    private val mensajesLeidos = ConcurrentHashMap<String, MutableSet<Long>>() // timestamp de mensajes leídos
    private val listeners = mutableListOf<ChatStorageListener>()

    /**
     * Interface para escuchar cambios en el storage
     */
    interface ChatStorageListener {
        fun onMensajeAgregado(alias: String, mensaje: MensajePrivado)
        fun onConversacionLimpiada(alias: String)
        fun onMensajeLeido(alias: String, timestamp: Long)
    }

    /**
     * Agrega un listener para recibir notificaciones de cambios
     */
    fun agregarListener(listener: ChatStorageListener) {
        listeners.add(listener)
        Log.d(TAG, "🔔 Listener agregado. Total: ${listeners.size}")
    }

    /**
     * Remueve un listener
     */
    fun removerListener(listener: ChatStorageListener) {
        listeners.remove(listener)
        Log.d(TAG, "🔕 Listener removido. Total: ${listeners.size}")
    }

    /**
     * Agrega un mensaje al historial de conversación con un alias específico
     */
    fun agregarMensaje(alias: String, mensaje: MensajePrivado) {
        Log.d(TAG, "📝 Agregando mensaje:")
        Log.d(TAG, "   Alias: $alias")
        Log.d(TAG, "   De: ${mensaje.emisor} Para: ${mensaje.receptor}")
        Log.d(TAG, "   Texto: '${mensaje.texto}'")
        Log.d(TAG, "   Timestamp: ${mensaje.timestamp}")

        val lista = privados.getOrPut(alias) { mutableListOf() }

        // Verificar duplicados basados en contenido y timestamp cercano
        val isDuplicate = lista.any { existente ->
            existente.texto == mensaje.texto &&
                    existente.emisor == mensaje.emisor &&
                    existente.receptor == mensaje.receptor &&
                    abs(existente.timestamp - mensaje.timestamp) < DUPLICATE_THRESHOLD_MS
        }

        if (!isDuplicate) {
            // Agregar mensaje manteniendo orden cronológico
            val insertIndex = lista.binarySearch { it.timestamp.compareTo(mensaje.timestamp) }
            val index = if (insertIndex < 0) -(insertIndex + 1) else insertIndex
            lista.add(index, mensaje)

            Log.d(TAG, "✅ Mensaje agregado en posición $index. Total para $alias: ${lista.size}")

            // Mantener solo los últimos MAX_MESSAGES_PER_CHAT mensajes por conversación
            while (lista.size > MAX_MESSAGES_PER_CHAT) {
                val removed = lista.removeAt(0)
                // También remover de mensajes leídos si existe
                mensajesLeidos[alias]?.remove(removed.timestamp)
                Log.d(TAG, "🧹 Mensaje más antiguo eliminado")
            }

            // Notificar a los listeners
            listeners.forEach { it.onMensajeAgregado(alias, mensaje) }

        } else {
            Log.d(TAG, "⚠️ Mensaje duplicado ignorado")
        }
    }

    /**
     * Agrega múltiples mensajes de forma eficiente
     */
    fun agregarMensajes(alias: String, mensajes: List<MensajePrivado>) {
        if (mensajes.isEmpty()) return

        Log.d(TAG, "📝 Agregando ${mensajes.size} mensajes para $alias")
        mensajes.forEach { agregarMensaje(alias, it) }
    }

    /**
     * Obtiene todos los mensajes de una conversación específica
     */
    fun obtenerMensajes(alias: String): List<MensajePrivado> {
        val mensajes = privados[alias]?.toList() ?: emptyList()
        Log.d(TAG, "📂 Obteniendo mensajes para '$alias': ${mensajes.size} mensajes")
        return mensajes.sortedBy { it.timestamp }
    }

    /**
     * Obtiene mensajes paginados para optimizar la carga
     */
    fun obtenerMensajesPaginados(alias: String, limite: Int = 20, offset: Int = 0): List<MensajePrivado> {
        val todosMensajes = obtenerMensajes(alias)
        val start = maxOf(0, todosMensajes.size - offset - limite)
        val end = maxOf(0, todosMensajes.size - offset)

        val mensajesPaginados = if (start < end) {
            todosMensajes.subList(start, end)
        } else {
            emptyList()
        }

        Log.d(TAG, "📄 Mensajes paginados para '$alias': ${mensajesPaginados.size} de $limite solicitados")
        return mensajesPaginados
    }

    /**
     * Obtiene el último mensaje de una conversación
     */
    fun obtenerUltimoMensaje(alias: String): MensajePrivado? {
        val ultimo = privados[alias]?.maxByOrNull { it.timestamp }
        Log.d(TAG, "📨 Último mensaje para '$alias': ${ultimo?.texto?.take(50) ?: "ninguno"}...")
        return ultimo
    }

    /**
     * Busca mensajes por texto
     */
    fun buscarMensajes(alias: String, query: String): List<MensajePrivado> {
        if (query.isBlank()) return emptyList()

        val mensajes = privados[alias] ?: return emptyList()
        val resultados = mensajes.filter {
            it.texto.contains(query, ignoreCase = true)
        }.sortedByDescending { it.timestamp }

        Log.d(TAG, "🔍 Búsqueda en '$alias' con query '$query': ${resultados.size} resultados")
        return resultados
    }

    /**
     * Busca en todas las conversaciones
     */
    fun buscarEnTodasLasConversaciones(query: String): Map<String, List<MensajePrivado>> {
        if (query.isBlank()) return emptyMap()

        val resultados = mutableMapOf<String, List<MensajePrivado>>()
        privados.forEach { (alias, mensajes) ->
            val coincidencias = mensajes.filter {
                it.texto.contains(query, ignoreCase = true)
            }.sortedByDescending { it.timestamp }

            if (coincidencias.isNotEmpty()) {
                resultados[alias] = coincidencias
            }
        }

        Log.d(TAG, "🔍 Búsqueda global con query '$query': ${resultados.size} conversaciones con resultados")
        return resultados
    }

    /**
     * Obtiene una lista de todas las conversaciones activas ordenadas por actividad
     */
    fun obtenerConversacionesActivas(): List<String> {
        val conversaciones = privados.keys.filter { privados[it]?.isNotEmpty() == true }
        val ordenadas = conversaciones.sortedByDescending {
            obtenerUltimoMensaje(it)?.timestamp ?: 0
        }

        Log.d(TAG, "💬 Conversaciones activas: ${conversaciones.size}")
        return ordenadas
    }

    /**
     * Marca un mensaje como leído
     */
    fun marcarMensajeLeido(alias: String, timestamp: Long) {
        val leidosSet = mensajesLeidos.getOrPut(alias) { mutableSetOf() }
        leidosSet.add(timestamp)

        Log.d(TAG, "✅ Mensaje marcado como leído en '$alias': $timestamp")
        listeners.forEach { it.onMensajeLeido(alias, timestamp) }
    }

    /**
     * Marca todos los mensajes de una conversación como leídos
     */
    fun marcarTodosLeidos(alias: String) {
        val mensajes = privados[alias] ?: return
        val leidosSet = mensajesLeidos.getOrPut(alias) { mutableSetOf() }

        var nuevosLeidos = 0
        mensajes.forEach { mensaje ->
            if (leidosSet.add(mensaje.timestamp)) {
                nuevosLeidos++
                listeners.forEach { it.onMensajeLeido(alias, mensaje.timestamp) }
            }
        }

        Log.d(TAG, "✅ Todos los mensajes marcados como leídos en '$alias': $nuevosLeidos nuevos")
    }

    /**
     * Verifica si un mensaje está leído
     */
    fun estaMensajeLeido(alias: String, timestamp: Long): Boolean {
        return mensajesLeidos[alias]?.contains(timestamp) == true
    }

    /**
     * Cuenta mensajes no leídos de un usuario
     */
    fun contarMensajesNoLeidos(alias: String, miAlias: String): Int {
        val mensajes = privados[alias] ?: return 0
        val leidos = mensajesLeidos[alias] ?: emptySet()

        val noLeidos = mensajes.count { mensaje ->
            mensaje.receptor == miAlias && !leidos.contains(mensaje.timestamp)
        }

        Log.d(TAG, "🔔 Mensajes no leídos de '$alias': $noLeidos")
        return noLeidos
    }

    /**
     * Obtiene el total de mensajes no leídos de todas las conversaciones
     */
    fun contarTotalMensajesNoLeidos(miAlias: String): Int {
        val total = privados.keys.sumOf { alias ->
            contarMensajesNoLeidos(alias, miAlias)
        }

        Log.d(TAG, "🔔 Total mensajes no leídos: $total")
        return total
    }

    /**
     * Elimina un mensaje específico
     */
    fun eliminarMensaje(alias: String, timestamp: Long): Boolean {
        val lista = privados[alias] ?: return false
        val removed = lista.removeAll { it.timestamp == timestamp }

        if (removed) {
            mensajesLeidos[alias]?.remove(timestamp)
            Log.d(TAG, "🗑️ Mensaje eliminado de '$alias': $timestamp")
        }

        return removed
    }

    /**
     * Limpia todos los mensajes de una conversación
     */
    fun limpiarConversacion(alias: String) {
        val cantidad = privados[alias]?.size ?: 0
        privados.remove(alias)
        mensajesLeidos.remove(alias)

        Log.d(TAG, "🧹 Conversación con '$alias' limpiada: $cantidad mensajes eliminados")
        listeners.forEach { it.onConversacionLimpiada(alias) }
    }

    /**
     * Limpia todas las conversaciones
     */
    fun limpiarTodo() {
        val totalConversaciones = privados.size
        val totalMensajes = privados.values.sumOf { it.size }

        privados.clear()
        mensajesLeidos.clear()

        Log.d(TAG, "🧹 Todas las conversaciones limpiadas: $totalConversaciones conversaciones, $totalMensajes mensajes")
    }

    /**
     * Obtiene conversaciones con mensajes no leídos
     */
    fun obtenerConversacionesConNoLeidos(miAlias: String): List<Pair<String, Int>> {
        return privados.keys.mapNotNull { alias ->
            val noLeidos = contarMensajesNoLeidos(alias, miAlias)
            if (noLeidos > 0) alias to noLeidos else null
        }.sortedByDescending { it.second }
    }

    /**
     * Obtiene estadísticas detalladas de almacenamiento
     */
    fun obtenerEstadisticas(): String {
        val totalConversaciones = privados.size
        val totalMensajes = privados.values.sumOf { it.size }
        val totalMensajesLeidos = mensajesLeidos.values.sumOf { it.size }
        val conversacionMasActiva = privados.maxByOrNull { it.value.size }
        val promedioMensajesPorConversacion = if (totalConversaciones > 0)
            totalMensajes / totalConversaciones else 0

        // Calcular uso de memoria estimado
        val memoriaEstimada = totalMensajes * 200 // ~200 bytes por mensaje estimado
        val memoriaFormateada = when {
            memoriaEstimada > 1024 * 1024 -> "${memoriaEstimada / (1024 * 1024)} MB"
            memoriaEstimada > 1024 -> "${memoriaEstimada / 1024} KB"
            else -> "$memoriaEstimada bytes"
        }

        return buildString {
            appendLine("📊 Estadísticas ChatStorage:")
            appendLine("   Conversaciones activas: $totalConversaciones")
            appendLine("   Total mensajes: $totalMensajes")
            appendLine("   Mensajes marcados como leídos: $totalMensajesLeidos")
            appendLine("   Promedio mensajes/conversación: $promedioMensajesPorConversacion")
            appendLine("   Conversación más activa: ${conversacionMasActiva?.key ?: "ninguna"} (${conversacionMasActiva?.value?.size ?: 0} mensajes)")
            appendLine("   Uso de memoria estimado: $memoriaFormateada")
            appendLine("   Listeners activos: ${listeners.size}")
        }
    }

    /**
     * Exporta los datos para backup (formato JSON simplificado)
     */
    fun exportarDatos(): String {
        return buildString {
            appendLine("{")
            appendLine("  \"conversaciones\": {")

            privados.entries.forEachIndexed { index, (alias, mensajes) ->
                appendLine("    \"$alias\": [")
                mensajes.forEachIndexed { msgIndex, mensaje ->
                    appendLine("      {")
                    appendLine("        \"emisor\": \"${mensaje.emisor}\",")
                    appendLine("        \"receptor\": \"${mensaje.receptor}\",")
                    appendLine("        \"texto\": \"${mensaje.texto.replace("\"", "\\\"")}\",")
                    appendLine("        \"timestamp\": ${mensaje.timestamp}")
                    append("      }")
                    if (msgIndex < mensajes.size - 1) appendLine(",")
                    else appendLine()
                }
                append("    ]")
                if (index < privados.size - 1) appendLine(",")
                else appendLine()
            }

            appendLine("  }")
            appendLine("}")
        }
    }

    /**
     * Limpia conversaciones inactivas (sin mensajes en X días)
     */
    fun limpiarConversacionesInactivas(diasInactividad: Int = 30) {
        val tiempoLimite = System.currentTimeMillis() - (diasInactividad * 24 * 60 * 60 * 1000L)
        val conversacionesAEliminar = mutableListOf<String>()

        privados.forEach { (alias, mensajes) ->
            val ultimoMensaje = mensajes.maxByOrNull { it.timestamp }
            if (ultimoMensaje != null && ultimoMensaje.timestamp < tiempoLimite) {
                conversacionesAEliminar.add(alias)
            }
        }

        conversacionesAEliminar.forEach { alias ->
            limpiarConversacion(alias)
        }

        Log.d(TAG, "🧹 Limpieza automática: ${conversacionesAEliminar.size} conversaciones inactivas eliminadas")
    }
}

data class MensajePrivado(
    val emisor: String,
    val receptor: String,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis()
)
