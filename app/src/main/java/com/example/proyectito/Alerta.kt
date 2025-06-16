package com.example.proyectito

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TipoAlerta {
    DESBLOQUEO_APP,
    TIEMPO_AGOTADO
}

data class Alerta(
    val id: String,
    val tipo: TipoAlerta,
    val mensaje: String,
    val timestamp: Long,
    val childId: String,
    val childName: String,
    val leida: Boolean,
    val appName: String? = null,
    val packageName: String? = null
) {
    fun getFormattedTime(): String {
        val date = Date(timestamp)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return dateFormat.format(date)
    }
}