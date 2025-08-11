package com.nexblue.app.bluetooth

// MensajePublicoCompleto.kt
data class MensajePublicoCompleto(
    val mensaje: String,
    val alias: String,
    val id: String,
    val etiqueta: String,
    var status: MessageStatus = MessageStatus.SENDING

)

enum class MessageStatus {
    SENDING, SENT, RECEIVED
}
