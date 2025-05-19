package com.example.proyectito

data class AppTiempoInfo(
    val packageName: String = "",
    val nombre: String = "",
    val limiteDiario: Int = 0,
    val icono: String = "" // URL del ícono en Firebase Storage
)