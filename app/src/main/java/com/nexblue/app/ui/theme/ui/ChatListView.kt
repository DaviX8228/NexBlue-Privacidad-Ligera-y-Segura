package com.nexblue.app.ui.theme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.nexblue.app.data.dao.ChatDao
import com.nexblue.app.data.model.ChatItem
import com.nexblue.app.data.model.ChatListItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    chatDao: ChatDao
) {
    val chats by chatDao.getAllChats().collectAsState(initial = emptyList())

    // Colores para los avatares (cíclicos)
    val avatarColors = listOf(
        Color(0xFF9E9E9E), // Gris
        Color(0xFFF44336), // Rojo
        Color(0xFF673AB7), // Púrpura
        Color(0xFF4CAF50), // Verde
        Color(0xFFFFC107)  // Amarillo
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                containerColor = Color(0xFF2196F3),
                contentColor = Color.White
            ) {
                Icon(
                    painter = painterResource(id = com.nexblue.app.R.drawable.bluetooth_b_brands_solid_full),
                    contentDescription = "Bluetooth",
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Logo en la parte superior
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = com.nexblue.app.R.drawable.isotipo_nexblue),
                    contentDescription = "Logo",
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(50.dp)
                )
            }

            // Lista real de chats
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(chats) { index, chat ->
                    ChatListItem(
                        chat = ChatItem(
                            userId = chat.userId,
                            name = chat.alias,
                            lastMessage = formatTime(chat.lastTimestamp),
                            timestamp = Date(chat.lastTimestamp)
                        ),
                        avatarColor = avatarColors[index % avatarColors.size],
                        onClick = { onChatClick(chat.userId) }
                    )
                }
            }
        }
    }

}

fun formatTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }

    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val fullDate = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())

    return when {
        now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR) -> "Hoy ${formatter.format(date.time)}"

        now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - date.get(Calendar.DAY_OF_YEAR) == 1 -> "Ayer ${formatter.format(date.time)}"

        else -> fullDate.format(date.time)
    }
}

