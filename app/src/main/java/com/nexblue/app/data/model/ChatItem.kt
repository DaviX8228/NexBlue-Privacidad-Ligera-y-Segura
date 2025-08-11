package com.nexblue.app.data.model

import java.util.*

// Asegúrate de tener esta data class
data class ChatItem(
    val userId: String,
    val name: String,
    val lastMessage: String,
    val timestamp: Date
)

