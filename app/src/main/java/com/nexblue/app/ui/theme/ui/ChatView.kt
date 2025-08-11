package com.nexblue.app.ui.theme.ui


import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.nexblue.app.data.dao.ChatDao
import com.nexblue.app.data.dao.MessageDao
import com.nexblue.app.data.entities.ChatEntity
import com.nexblue.app.data.entities.MessageEntity
import com.nexblue.app.data.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    userId: String,
    userName: String = "Desco", // Alias basado en distancia o nombre temporal
    introMessage: String? = "Este dispositivo tendrá primero que aceptar o bloquear tu solicitud de mensaje. En cuanto la acepte, se habilitará la opción para cambiar el alias de amigo.",
    onBack: () -> Unit = {},
    onEditAlias: () -> Unit = {},
    chatDao: ChatDao,
    messageDao: MessageDao,
    context: Context
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var hasShownIntro by remember { mutableStateOf(false) }
    var showAliasLockedDialog by remember { mutableStateOf(false) }

    // Leer mensajes desde la base de datos
    val messageFlow = remember(userId) {
        messageDao.getMessagesForChat(userId)
    }
    LaunchedEffect(Unit) {
        Log.d("BLE", "ChatScreen cargado con userId: $userId")
    }

    val messages by messageFlow.collectAsState(initial = emptyList())

    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.size.coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .padding(horizontal = 12.dp)
    ) {
        // Encabezado con alias y botón de editar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Avatar",
                tint = Color.Gray,
                modifier = Modifier.size(50.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = userName,
                color = Color(0xFF1D9FFD),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAliasLockedDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar alias",
                    tint = Color.White
                )
            }
        }

        Divider(color = Color.DarkGray)

        // Mensaje introductorio (solo se muestra una vez)
        if (introMessage != null && !hasShownIntro) {
            Text(
                text = introMessage,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )

            LaunchedEffect(Unit) { hasShownIntro = true }
        }

        // Lista de mensajes
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    horizontalArrangement = if (msg.sender == "me") Arrangement.End else Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (msg.sender == "me") Color(0xFF202AE4) else Color.DarkGray,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomEnd = if (msg.sender == "me") 0.dp else 16.dp,
                                    bottomStart = if (msg.sender == "me") 16.dp else 0.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }


        // Input + botón enviar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Mensaje", color = Color.Gray) },
                modifier = Modifier
                    .weight(1f)
                    .height(55.dp),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Black,
                    focusedContainerColor = Color.Black,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        val trimmed = input.trim()

                        CoroutineScope(Dispatchers.IO).launch {
                            Log.d("DB", "Insertando mensaje: $trimmed")
                            // ✅ PRIMERO el chat (aunque sea temporal)
                            chatDao.insertChat(
                                ChatEntity(
                                    userId = userId,
                                    alias = userName, // puede ser "Desconocido" o "Aprox. 0.74m"
                                    lastMessage = trimmed,
                                    lastTimestamp = now
                                )
                            )
                            chatDao.updateLastMessage(userId, trimmed, now)

                            // ✅ DESPUÉS el mensaje
                            messageDao.insertMessage(
                                MessageEntity(
                                    chatUserId = userId,
                                    sender = "me",
                                    text = trimmed,
                                    timestamp = now
                                )
                            )
                        }

                        input = ""
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFF1D9FFD), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Enviar",
                    tint = Color.White
                )
            }
        }
        LaunchedEffect(messages) {
            Log.d("UI", "Mensajes recibidos: ${messages.size}")
            messages.forEach { msg ->
                Log.d("UI", "→ ${msg.id}: ${msg.text} [${msg.sender}]")
            }
        }


        // Diálogo si intenta editar alias antes de tiempo
        if (showAliasLockedDialog) {
            AlertDialog(
                onDismissRequest = { showAliasLockedDialog = false },
                title = { Text("Alias bloqueado", color = Color.White) },
                text = {
                    Text(
                        text = "El cambio de alias está deshabilitado hasta que el otro usuario acepte tu solicitud de mensaje.",
                        color = Color.Gray
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAliasLockedDialog = false }) {
                        Text("Entendido", color = Color(0xFF1D9FFD))
                    }
                },
                containerColor = Color(0xFF2D2D2D)
            )
        }
    }
}

