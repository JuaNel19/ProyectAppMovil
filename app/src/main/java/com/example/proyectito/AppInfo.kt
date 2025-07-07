package com.example.proyectito

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val nombre: String,
    val bloqueado: Boolean,
    val icono: Drawable
)