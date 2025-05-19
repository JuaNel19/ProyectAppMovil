package com.example.proyectito

import com.google.firebase.Timestamp

data class User(
    val id: String = "",
    val email: String = "",
    val nombre: String = "",
    val tipo: String = "", // "tutor" o "hijo"
    val fechaCreacion: Timestamp = Timestamp.now(),
    val ultimoAcceso: Timestamp = Timestamp.now()
) {
    companion object {
        const val TIPO_TUTOR = "tutor"
        const val TIPO_HIJO = "hijo"
    }
}