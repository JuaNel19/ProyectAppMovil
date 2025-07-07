package com.example.proyectito

import com.google.android.gms.maps.model.LatLng

data class UbicacionInfo(
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val timestamp: Long = 0L,
    val childId: String = "",
    val parentId: String = ""
) {
    fun toLatLng(): LatLng = LatLng(latitud, longitud)
}