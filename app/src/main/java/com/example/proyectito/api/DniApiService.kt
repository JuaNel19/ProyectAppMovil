package com.example.proyectito.api

import retrofit2.http.GET
import retrofit2.http.Query

interface DniApiService {
    @GET("reniec/dni")
    suspend fun consultarDni(@Query("numero") numero: String): DniResponse
}