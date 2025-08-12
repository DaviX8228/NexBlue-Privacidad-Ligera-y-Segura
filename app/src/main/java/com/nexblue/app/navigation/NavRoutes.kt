package com.nexblue.app.navigation

import java.net.URLEncoder

object NavRoutes {
    const val Splash = "splash"
    const val BluetoothSetup = "bluetooth_setup"
    const val ChatPublic = "chat_public"
    const val ChatPrivado = "chat_privado/{aliasDestinatario}/{mensajeInicial}"

    fun chatPrivadoWith(aliasDestinatario: String, mensajeInicial: String = ""): String {
        return "chat_privado/$aliasDestinatario/${URLEncoder.encode(mensajeInicial, "UTF-8")}"
    }
}