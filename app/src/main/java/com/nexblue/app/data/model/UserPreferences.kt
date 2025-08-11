package com.nexblue.app.data.model

import android.content.Context
import com.nexblue.app.bluetooth.BluetoothManager

// UserPreferences.kt
object UserPreferences {
    private const val PREF_NAME = "nexblue_user_prefs"
    private const val KEY_ALIAS = "alias"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ETIQUETA = "etiqueta"

    fun save(context: Context, alias: String, userId: String, etiqueta: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ALIAS, alias)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ETIQUETA, etiqueta)
            .apply()
    }

    fun getAlias(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALIAS, "Desc") ?: "Desc"

    fun getUserId(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, BluetoothManager.myUserId) ?: BluetoothManager.myUserId

    fun getEtiqueta(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ETIQUETA, "Social") ?: "Social"

    fun generateUserId(context: Context): String {
        val prefs = context.getSharedPreferences("nexblue_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("user_id", null)

        if (id == null) {
            id = (1000..9999).random().toString()
            prefs.edit().putString("user_id", id).apply()
        }

        return id
    }

}
