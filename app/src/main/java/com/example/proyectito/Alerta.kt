package com.example.proyectito

import com.google.firebase.Timestamp

data class Alerta(
    val id: String = "",
    val tipo: String = "",
    val mensaje: String = "",
    val estado: String = "",
    val fecha: Timestamp = Timestamp.now(),
    val appId: String = "",
    val tiempoSolicitado: Int = 0
) {
    companion object {
        const val TIPO_INSTALACION = "instalacion"
        const val TIPO_SOLICITUD_TIEMPO = "solicitud_tiempo"
        const val TIPO_DESBLOQUEO = "desbloqueo"
        const val TIPO_USO_BLOQUEADO = "uso_bloqueado"

        const val ESTADO_PENDIENTE = "pendiente"
        const val ESTADO_APROBADO = "aprobado"
        const val ESTADO_DENEGADO = "denegado"
    }
}