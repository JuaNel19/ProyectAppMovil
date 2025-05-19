package com.example.proyectito

import com.google.android.gms.maps.model.LatLng

data class UbicacionInfo(
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val timestamp: Long = 0,
    val hijoId: String = ""
) {
    fun toLatLng() = LatLng(latitud, longitud)
}