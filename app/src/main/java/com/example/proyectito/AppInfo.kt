package com.example.proyectito

data class AppInfo(
    val packageName: String = "",
    val nombre: String = "",
    val bloqueado: Boolean = false,
    val icono: String = "" // URL del ícono en Firebase Storage
)