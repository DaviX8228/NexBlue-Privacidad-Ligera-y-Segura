package com.nexblue.app.ui.theme.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.nexblue.app.bluetooth.BLEChatManager
import com.nexblue.app.data.model.ChatStorage
import com.nexblue.app.data.model.MensajePrivado
import com.nexblue.app.data.model.UserPreferences
import com.nexblue.app.data.model.UserPreferences.generateUserId
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ChatPrivadoScreen.kt - Versión corregida con comunicación bidireccional
@Composable
fun ChatPrivadoScreen(
    context: Context,
    aliasDestinatario: String,
    mensajeInicial: String = "",
    onBack: () -> Unit = {}
) {
    var myAlias by remember { mutableStateOf(UserPreferences.getAlias(context)) }
    var myEtiqueta by remember { mutableStateOf(UserPreferences.getEtiqueta(context)) }
    var input by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Conectando...") }

    val myUserId = generateUserId(context)
    val myShortId = myUserId.takeLast(3)
    val mensajes = remember { mutableStateListOf<MensajePrivado>() }

    // Cargar mensajes existentes del historial
    LaunchedEffect(aliasDestinatario) {
        Log.d("ChatPrivado", "📂 Cargando historial con $aliasDestinatario")
        mensajes.clear()
        val historial = ChatStorage.obtenerMensajes(aliasDestinatario)
        mensajes.addAll(historial)
        Log.d("ChatPrivado", "✅ Cargados ${historial.size} mensajes del historial")
    }

    // Agregar mensaje inicial si viene de notificación
    LaunchedEffect(mensajeInicial) {
        if (mensajeInicial.isNotBlank()) {
            try {
                val mensajeDecodificado = URLDecoder.decode(mensajeInicial, "UTF-8")
                Log.d("ChatPrivado", "📨 Agregando mensaje inicial: '$mensajeDecodificado'")

                val mensajeExistente = mensajes.any {
                    it.texto == mensajeDecodificado && it.emisor == aliasDestinatario
                }

                if (!mensajeExistente) {
                    val nuevoMensaje = MensajePrivado(
                        emisor = aliasDestinatario,
                        receptor = myAlias,
                        texto = mensajeDecodificado,
                        timestamp = System.currentTimeMillis()
                    )
                    mensajes.add(nuevoMensaje)
                    ChatStorage.agregarMensaje(aliasDestinatario, nuevoMensaje)
                    Log.d("ChatPrivado", "✅ Mensaje inicial agregado")
                }
            } catch (e: Exception) {
                Log.e("ChatPrivado", "❌ Error decodificando mensaje inicial: ${e.message}")
            }
        }
    }

    // Inicializar BLE (sin shutdown entre pantallas)
    LaunchedEffect(Unit) {
        BLEChatManager.initialize(context)
        connectionStatus = "Chat privado con $aliasDestinatario"
        Log.d("ChatPrivado", "✅ BLEChatManager inicializado para chat privado")
    }

    // Presencia continua
    LaunchedEffect(myAlias, myUserId, myEtiqueta) {
        BLEChatManager.startAdvertising(
            context = context,
            mensaje = "",
            alias = myAlias,
            userId = myUserId,
            etiqueta = myEtiqueta
        )
        Log.d("ChatPrivado", "📡 Advertising iniciado como presencia")
    }

    // Escucha de mensajes (SOLO PRIVADOS en esta pantalla)
    LaunchedEffect(Unit) {
        BLEChatManager.startScanning(
            context = context,
            onPublicMessage = { _ ->
                // Ignorar mensajes públicos en chat privado
            },
            onPrivateMessage = { deAlias, paraAlias, mensaje ->
                Log.d("ChatPrivado", "💬 Mensaje privado interceptado:")
                Log.d("ChatPrivado", "   De: '$deAlias' Para: '$paraAlias'")
                Log.d("ChatPrivado", "   Mensaje: '$mensaje'")

                // CASO 1: Mensaje dirigido a MÍ desde el destinatario actual
                if (paraAlias == myAlias && deAlias == aliasDestinatario) {
                    Log.d("ChatPrivado", "✅ Mensaje privado recibido de $aliasDestinatario")

                    // Verificar que no sea duplicado
                    val isDuplicate = mensajes.any {
                        it.texto == mensaje &&
                                it.emisor == deAlias &&
                                System.currentTimeMillis() - it.timestamp < 3000
                    }

                    if (!isDuplicate) {
                        val nuevoMensaje = MensajePrivado(
                            emisor = deAlias,
                            receptor = myAlias,
                            texto = mensaje,
                            timestamp = System.currentTimeMillis()
                        )

                        mensajes.add(nuevoMensaje)
                        ChatStorage.agregarMensaje(aliasDestinatario, nuevoMensaje)

                        Log.d("ChatPrivado", "📝 Mensaje agregado a la conversación")
                        connectionStatus = "Mensaje recibido de $aliasDestinatario"
                    } else {
                        Log.d("ChatPrivado", "⚠️ Mensaje duplicado ignorado")
                    }
                }
                // CASO 2: Verificar si alguien me está respondiendo (alias cambiado)
                else if (paraAlias == myAlias && deAlias != aliasDestinatario) {
                    Log.d("ChatPrivado", "ℹ️ Mensaje privado de usuario diferente ($deAlias), ignorado en este chat")
                }
                // CASO 3: Mensaje que YO envié (confirmación)
                else if (deAlias == myAlias && paraAlias == aliasDestinatario) {
                    Log.d("ChatPrivado", "📤 Confirmación de mi mensaje enviado")
                }
            }
        )
        Log.d("ChatPrivado", "🔍 Scanning iniciado para mensajes privados")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Encabezado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Privado",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = aliasDestinatario,
                        color = Color(0xFF1D9FFD),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = connectionStatus,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            // Indicador de estado y botón volver
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (BLEChatManager.isAdvertising() && BLEChatManager.isScanning())
                        Icons.Default.CheckCircle else Icons.Default.Clear,
                    contentDescription = "Estado",
                    tint = if (BLEChatManager.isAdvertising() && BLEChatManager.isScanning())
                        Color.Green else Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    Log.d("ChatPrivado", "🔙 Volviendo al chat público")
                    onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
            }
        }

        Divider(color = Color.DarkGray)

        // Info debug
        Text(
            text = "Chat con: $aliasDestinatario | Mi ID: ${myShortId} | Mensajes: ${mensajes.size}",
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
                val isMyMessage = mensaje.emisor == myAlias
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
                            .padding(12.dp)
                            .widthIn(max = 250.dp)
                    ) {
                        // Mostrar emisor si no soy yo
                        if (!isMyMessage) {
                            Text(
                                text = mensaje.emisor,
                                color = Color(0xFF90CAF9),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            text = mensaje.texto,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(mensaje.timestamp)),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Privado",
                                    tint = Color(0xFFFF9800),
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
                placeholder = { Text("Mensaje privado para $aliasDestinatario...", color = Color.Gray) },
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
                        val contenido = input.trim()
                        Log.d("ChatPrivado", "📤 Enviando mensaje privado: '$contenido'")

                        try {
                            // Enviar por BLE usando la nueva función
                            BLEChatManager.sendPrivateMessage(
                                context = context,
                                mensaje = contenido,
                                alias = myAlias,
                                userId = myUserId,
                                destinatarioAlias = aliasDestinatario
                            )

                            // Guardar en historial y UI inmediatamente
                            val nuevoMensaje = MensajePrivado(
                                emisor = myAlias,
                                receptor = aliasDestinatario,
                                texto = contenido,
                                timestamp = System.currentTimeMillis()
                            )

                            ChatStorage.agregarMensaje(aliasDestinatario, nuevoMensaje)
                            mensajes.add(nuevoMensaje)

                            input = ""
                            connectionStatus = "Mensaje enviado a $aliasDestinatario"

                            Log.d("ChatPrivado", "✅ Mensaje agregado localmente")

                            // Volver a presencia después de 3 segundos
                            Handler(Looper.getMainLooper()).postDelayed({
                                BLEChatManager.startAdvertising(
                                    context = context,
                                    mensaje = "",
                                    alias = myAlias,
                                    userId = myUserId,
                                    etiqueta = myEtiqueta
                                )
                                connectionStatus = "Chat privado con $aliasDestinatario"
                            }, 3000)

                        } catch (e: Exception) {
                            Log.e("ChatPrivado", "❌ Error enviando mensaje privado: ${e.message}")
                            connectionStatus = "Error enviando mensaje"
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
}