package com.nexblue.app.navigation

import java.net.URLEncoder

object NavRoutes {
    const val Splash = "splash"
    const val BluetoothSetup = "bluetooth_setup"
    const val ChatList = "chat_list"
    const val Scan = "scan"
    const val Chat = "chat/{userId}"
    const val ChatPublic = "chat_public"
    const val ChatPrivado = "chat_privado/{aliasDestinatario}/{mensajeInicial}"

    fun chatWith(userId: String) = "chat/$userId"
    fun chatPrivadoWith(aliasDestinatario: String, mensajeInicial: String = ""): String {
        return "chat_privado/$aliasDestinatario/${URLEncoder.encode(mensajeInicial, "UTF-8")}"
    }
}