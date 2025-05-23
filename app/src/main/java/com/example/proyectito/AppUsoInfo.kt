package com.example.proyectito

data class AppUsoInfo(
    val packageName: String = "",
    val nombre: String = "",
    val tiempoUsado: Int = 0, // en minutos
    val icono: String = "", // URL del ícono en Firebase Storage
    val ultimoUso: Long = 0 // tiempo ultimo uso
)