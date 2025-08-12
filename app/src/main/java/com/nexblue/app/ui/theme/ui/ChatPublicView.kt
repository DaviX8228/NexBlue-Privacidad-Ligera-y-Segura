package com.nexblue.app.ui.theme.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.nexblue.app.bluetooth.BLEChatManager
import com.nexblue.app.data.model.ChatStorage
import com.nexblue.app.data.model.MensajePrivado
import com.nexblue.app.data.model.UserPreferences
import com.nexblue.app.data.model.UserPreferences.generateUserId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString

// ChatPublicScreen.kt - Versión corregida con manejo de mensajes privados
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatPublicScreen(
    context: Context,
    onBack: () -> Unit = {},
    onNavigateToPrivateChat: (String, String) -> Unit = { _, _ -> }
) {
    var myAlias by remember { mutableStateOf(UserPreferences.getAlias(context)) }
    var myEtiqueta by remember { mutableStateOf(UserPreferences.getEtiqueta(context)) }
    val mensajes = remember { mutableStateListOf<MensajePublico>() }

    // Estados para notificaciones
    val notificaciones = remember { mutableStateListOf<NotificacionPrivada>() }
    var showDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Conectando...") }

    val myUserId = generateUserId(context)
    val myShortId = remember { myUserId.takeLast(3) }

    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    // Inicializar BLEChatManager
    LaunchedEffect(Unit) {
        try {
            BLEChatManager.initialize(context)
            connectionStatus = "Inicializado"
            Log.d("ChatPublico", "✅ BLEChatManager inicializado")
        } catch (e: Exception) {
            connectionStatus = "Error: ${e.message}"
            Log.e("ChatPublico", "❌ Error inicializando: ${e.message}")
        }
    }

    // Iniciar advertising (presencia)
    LaunchedEffect(myAlias, myUserId, myEtiqueta) {
        if (myAlias.isNotBlank()) {
            try {
                BLEChatManager.startAdvertising(
                    context = context,
                    mensaje = "", // Presencia vacía
                    alias = myAlias,
                    userId = myUserId,
                    etiqueta = myEtiqueta
                )
                connectionStatus = "Transmitiendo como $myAlias"
                Log.d("ChatPublico", "📡 Advertising iniciado para $myAlias")
            } catch (e: Exception) {
                connectionStatus = "Error advertising: ${e.message}"
                Log.e("ChatPublico", "❌ Error en advertising: ${e.message}")
            }
        }
    }

    // Iniciar scanning con AMBOS callbacks
    LaunchedEffect(Unit) {
        try {
            BLEChatManager.startScanning(
                context = context,
                onPublicMessage = { mensajeCompleto ->
                    Log.d("ChatPublico", "📨 Mensaje público recibido:")
                    Log.d("ChatPublico", "   Mensaje: '${mensajeCompleto.mensaje}'")
                    Log.d("ChatPublico", "   De: '${mensajeCompleto.alias}'")
                    Log.d("ChatPublico", "   ID: '${mensajeCompleto.id}'")

                    // Ignorar mis propios mensajes
                    if (mensajeCompleto.id == myShortId) {
                        Log.d("ChatPublico", "⚠️ Ignorando mi propio mensaje")
                        return@startScanning
                    }

                    // Verificar duplicados
                    val isDuplicate = mensajes.any {
                        it.id == mensajeCompleto.id &&
                                it.texto == mensajeCompleto.mensaje &&
                                System.currentTimeMillis() - it.timestamp < 5000
                    }

                    val esPing = mensajeCompleto.mensaje.isEmpty()
                    val yaMostroPing = mensajes.any {
                        it.id == mensajeCompleto.id && it.texto == "Se conectó"
                    }

                    if (!isDuplicate && (!esPing || !yaMostroPing)) {
                        mensajes.add(
                            MensajePublico(
                                alias = mensajeCompleto.alias,
                                id = mensajeCompleto.id,
                                etiqueta = mensajeCompleto.etiqueta,
                                texto = if (esPing) "Se conectó" else mensajeCompleto.mensaje,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        Log.d("ChatPublico", "✅ Mensaje público agregado")
                    }
                },
                onPrivateMessage = { deAlias, paraAlias, mensaje ->
                    Log.d("ChatPublico", "💬 Mensaje privado interceptado:")
                    Log.d("ChatPublico", "   De: '$deAlias' Para: '$paraAlias'")
                    Log.d("ChatPublico", "   Mensaje: '$mensaje'")

                    // Solo procesar si es para MÍ
                    if (paraAlias == myAlias) {
                        Log.d("ChatPublico", "✅ Mensaje privado es para mí - agregando notificación")

                        notificaciones.add(
                            NotificacionPrivada(
                                deAlias = deAlias,
                                deId = "", // No necesario para notificaciones
                                mensaje = mensaje,
                                timestamp = System.currentTimeMillis()
                            )
                        )

                        // Guardar en ChatStorage
                        ChatStorage.agregarMensaje(
                            deAlias,
                            MensajePrivado(
                                emisor = deAlias,
                                receptor = myAlias,
                                texto = mensaje
                            )
                        )

                        Log.d("ChatPublico", "🔔 Notificación agregada de $deAlias")
                    } else {
                        Log.d("ChatPublico", "ℹ️ Mensaje privado no es para mí")
                    }
                }
            )
            Log.d("ChatPublico", "🔍 Scanning iniciado")
        } catch (e: Exception) {
            Log.e("ChatPublico", "❌ Error en scanning: ${e.message}")
        }
    }

    // Limpiar al salir
    DisposableEffect(Unit) {
        onDispose {
            BLEChatManager.shutdown()
            Log.d("ChatPublico", "🔌 Recursos liberados")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Encabezado con estado y notificaciones
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Público",
                        tint = Color(0xFF0048FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Chat Público",
                        color = Color(0xFF0048FF),
                        fontSize = 20.sp
                    )
                }
                Text(
                    text = connectionStatus,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            // Botones de notificación y configuración
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Estado de conexión
                Icon(
                    imageVector = if (BLEChatManager.isAdvertising() && BLEChatManager.isScanning())
                        Icons.Default.CheckCircle else Icons.Default.Clear,
                    contentDescription = "Estado",
                    tint = if (BLEChatManager.isAdvertising() && BLEChatManager.isScanning())
                        Color.Green else Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Ícono de notificaciones con badge
                Box {
                    IconButton(onClick = { showNotificationDialog = true }) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notificaciones",
                            tint = if (notificaciones.isNotEmpty()) Color(0xFFFF9800) else Color.Gray
                        )
                    }
                    // Badge de conteo
                    if (notificaciones.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .background(Color.Red, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${notificaciones.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Configuración
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Config", tint = Color.White)
                }
            }
        }

        Divider(color = Color.DarkGray)

        // Info debug
        Text(
            text = "ID: ${myUserId.takeLast(3)} | Alias: $myAlias | Mensajes: ${mensajes.size} | Notifs: ${notificaciones.size}",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Lista de mensajes
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            items(mensajes.reversed()) { mensaje ->
                val isMyMessage = mensaje.id == myShortId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                color = if (isMyMessage) Color(0xFF1D9FFD) else Color(0xFF404040),
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (isMyMessage) 20.dp else 2.dp,
                                    bottomEnd = if (isMyMessage) 2.dp else 20.dp
                                )
                            )
                            .combinedClickable(
                                onLongClick = {
                                    // Vibración háptica
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Copiar al clipboard
                                    clipboardManager.setText(AnnotatedString(mensaje.texto))
                                    // Mostrar un Toast
                                    Log.d("ChatPublico", "✅ Mensaje copiado '${mensaje.texto}'")
                                    //toast
                                    Toast.makeText(context, "✅ Mensaje copiado", Toast.LENGTH_SHORT).show()
                                },
                                onClick = {
                                    // Si no es mi mensaje, ir al chat privado
                                    if (!isMyMessage) {
                                        Log.d("ChatPublico", "📱 Navegando a chat privado con ${mensaje.alias}")
                                        onNavigateToPrivateChat(mensaje.alias, "")
                                    }
                                }
                            )
                            .padding(12.dp)
                            .widthIn(max = 220.dp)
                    ) {
                        if (!isMyMessage) {
                            // Hacer clic en el alias para ir al chat privado
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "${mensaje.alias} (${mensaje.id})",
                                    color = Color(0xFF90CAF9),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.MailOutline,
                                    contentDescription = "Enviar mensaje privado",
                                    tint = Color(0xFF90CAF9),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // Texto del mensaje con selección habilitada
                        SelectionContainer {
                            Text(
                                text = mensaje.texto,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(mensaje.timestamp)),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Público",
                                    tint = Color(0xFF0048FF),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (isMyMessage) "Enviado" else "Recibido",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Input mensaje
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Escribe algo público...", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedContainerColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                supportingText = {
                    Text(
                        text = "${input.length} caracteres",
                        color = if (input.length > 100) Color.Red else Color.Gray,
                        fontSize = 10.sp
                    )
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        val msg = input.trim()
                        Log.d("ChatPublico", "📤 Enviando mensaje público: '$msg'")
                        try {
                            // Transmitir mensaje via BLE
                            BLEChatManager.startAdvertising(
                                context = context,
                                mensaje = msg,
                                alias = myAlias,
                                userId = myUserId,
                                etiqueta = myEtiqueta
                            )
                            // Agregar a mi propia lista
                            mensajes.add(
                                MensajePublico(
                                    alias = myAlias,
                                    id = myShortId,
                                    etiqueta = myEtiqueta,
                                    texto = msg,
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            input = ""
                            connectionStatus = "Mensaje enviado: $msg"

                            // Volver a presencia después de 3 segundos
                            Handler(Looper.getMainLooper()).postDelayed({
                                BLEChatManager.startAdvertising(
                                    context = context,
                                    mensaje = "",
                                    alias = myAlias,
                                    userId = myUserId,
                                    etiqueta = myEtiqueta
                                )
                                connectionStatus = "Transmitiendo como $myAlias"
                            }, 3000)
                        } catch (e: Exception) {
                            Log.e("ChatPublico", "❌ Error enviando: ${e.message}")
                            connectionStatus = "Error enviando: ${e.message}"
                        }
                    }
                },
                enabled = input.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Enviar",
                    tint = if (input.isNotBlank()) Color.White else Color.Gray
                )
            }
        }
    }

    // Diálogo de configuración
    if (showDialog) {
        var aliasTemp by remember { mutableStateOf(myAlias) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Configuración", color = Color(0xFF1D9FFD)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = aliasTemp,
                        onValueChange = { if (it.length <= 8) aliasTemp = it },
                        label = { Text("Alias (máx. 8 chars)", color = Color.White) },
                        singleLine = true,
                        supportingText = {
                            Text("${aliasTemp.length}/8", color = Color.Gray, fontSize = 10.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1D9FFD),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray
                        )
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "ID: ${myUserId.takeLast(3)}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    myAlias = aliasTemp.trim().ifBlank { "User" }
                    UserPreferences.save(context, myAlias, myUserId, myEtiqueta)
                    showDialog = false
                }) {
                    Text("Guardar", color = Color(0xFF1D9FFD))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF2D2D2D)
        )
    }

    // Diálogo de notificaciones
    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Notificaciones",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mensajes Privados (${notificaciones.size})", color = Color(0xFF1D9FFD))
                }
            },
            text = {
                if (notificaciones.isEmpty()) {
                    Text(
                        text = "No tienes mensajes privados",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notificaciones.reversed()) { notificacion ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Log.d("ChatPublico", "🔗 Abriendo chat privado con ${notificacion.deAlias}")
                                        // Abrir chat privado con este usuario
                                        onNavigateToPrivateChat(notificacion.deAlias, "")
                                        showNotificationDialog = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF404040))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "De: ${notificacion.deAlias}",
                                            color = Color(0xFF90CAF9),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        IconButton(
                                            onClick = {
                                                Log.d("ChatPublico", "🗑️ Eliminando notificación de ${notificacion.deAlias}")
                                                notificaciones.remove(notificacion)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Eliminar",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = notificacion.mensaje,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notificacion.timestamp)),
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (notificaciones.isNotEmpty()) {
                    TextButton(onClick = {
                        Log.d("ChatPublico", "🧹 Limpiando todas las notificaciones")
                        notificaciones.clear()
                    }) {
                        Text("Limpiar Todo", color = Color.Red)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDialog = false }) {
                    Text("Cerrar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF2D2D2D)
        )
    }

}

// Data classes
data class MensajePublico(
    val alias: String,
    val id: String,
    val etiqueta: String,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NotificacionPrivada(
    val deAlias: String,
    val deId: String,
    val mensaje: String,
    val timestamp: Long = System.currentTimeMillis()
)
