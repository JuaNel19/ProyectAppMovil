package com.example.proyectito.api

import com.google.gson.annotations.SerializedName

data class DniResponse(
    @SerializedName("nombres")
    val nombres: String,
    @SerializedName("apellidoPaterno")
    val apellidoPaterno: String,
    @SerializedName("apellidoMaterno")
    val apellidoMaterno: String,
    @SerializedName("nombreCompleto")
    val nombreCompleto: String,
    @SerializedName("numeroDocumento")
    val numeroDocumento: String,
    @SerializedName("tipoDocumento")
    val tipoDocumento: String,
    @SerializedName("digitoVerificador")
    val digitoVerificador: String
)