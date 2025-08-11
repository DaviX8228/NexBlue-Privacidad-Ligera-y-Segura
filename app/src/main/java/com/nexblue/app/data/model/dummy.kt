package com.nexblue.app.data.model

import java.util.Date

data class Message(
    val sender: String, // "me" o "them"
    val text: String,
    val timestamp: Date = Date()
)
