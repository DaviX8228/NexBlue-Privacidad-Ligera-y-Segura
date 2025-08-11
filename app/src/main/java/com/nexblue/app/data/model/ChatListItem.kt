package com.nexblue.app.data.model

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.nexblue.app.data.model.ChatItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatListItem(
    chat: ChatItem,
    avatarColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circular con color
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = avatarColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Aquí podrías agregar iniciales o imagen de perfil si las tienes
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Información del chat
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chat.name,
                color = Color(0xFF2196F3),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = "Últ.vez: ",
                    color = Color(0xFF2196F3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraLight
                )
                Text(
                    text = chat.lastMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraLight
                )
            }
        }
    }
}

