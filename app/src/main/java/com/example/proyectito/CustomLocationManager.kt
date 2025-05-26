package com.example.proyectito

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class CustomLocationManager(private val context: Context) {
    private val TAG = "CustomLocationManager"
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var locationUpdateRunnable: Runnable? = null
    private var lastUpdateTime: Long = 0
    private var lastSuccessfulUpdate: Long = 0
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private val BASE_UPDATE_INTERVAL = 3 * 60 * 1000L // 3 minutos base
    private val LOW_BATTERY_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutos con batería baja
    private val MAX_LOCATION_AGE = 30 * 1000L // 30 segundos
    private val MIN_ACCURACY = 50f // 50 metros de precisión mínima
    private var bestLocation: Location? = null
    private var locationAttempts = 0
    private val MAX_LOCATION_ATTEMPTS = 5

    fun startLocationUpdates(userId: String) {
        Log.d(TAG, "Iniciando actualizaciones de ubicación para userId: $userId")
        stopLocationUpdates()

        locationUpdateRunnable = object : Runnable {
            override fun run() {
                if (hasLocationPermission()) {
                    if (isGpsEnabled()) {
                        Log.d(TAG, "Obteniendo ubicación actual...")
                        bestLocation = null
                        locationAttempts = 0
                        getCurrentLocation(userId)
                    } else {
                        Log.e(TAG, "GPS no está activo")
                        Toast.makeText(context, "Por favor, activa el GPS para una mejor precisión", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e(TAG, "No hay permisos de ubicación")
                }
                handler.postDelayed(this, getUpdateInterval())
            }
        }

        handler.post(locationUpdateRunnable!!)
    }

    fun stopLocationUpdates() {
        Log.d(TAG, "Deteniendo actualizaciones de ubicación")
        locationUpdateRunnable?.let {
            handler.removeCallbacks(it)
            locationUpdateRunnable = null
        }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun getCurrentLocation(userId: String) {
        try {
            locationAttempts++
            Log.d(TAG, "Intento de ubicación $locationAttempts de $MAX_LOCATION_ATTEMPTS")

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, object : CancellationToken() {
                override fun onCanceledRequested(listener: OnTokenCanceledListener) = CancellationTokenSource().token
                override fun isCancellationRequested() = false
            }).addOnSuccessListener { location ->
                location?.let {
                    Log.d(TAG, "Ubicación obtenida: lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}m, age=${System.currentTimeMillis() - location.time}ms")

                    // Verificar si la ubicación es reciente
                    if (System.currentTimeMillis() - location.time > MAX_LOCATION_AGE) {
                        Log.d(TAG, "Ubicación demasiado antigua, intentando de nuevo")
                        if (locationAttempts < MAX_LOCATION_ATTEMPTS) {
                            handler.postDelayed({ getCurrentLocation(userId) }, 2000)
                        } else {
                            handleLocationError("No se pudo obtener una ubicación reciente", userId)
                        }
                        return@addOnSuccessListener
                    }

                    // Actualizar la mejor ubicación si es más precisa
                    if (bestLocation == null || location.accuracy < bestLocation!!.accuracy) {
                        bestLocation = location
                        Log.d(TAG, "Nueva mejor ubicación: accuracy=${location.accuracy}m")
                    }

                    // Si tenemos una ubicación suficientemente precisa o alcanzamos el máximo de intentos
                    if (location.accuracy <= MIN_ACCURACY || locationAttempts >= MAX_LOCATION_ATTEMPTS) {
                        bestLocation?.let { finalLocation ->
                            Log.d(TAG, "Usando mejor ubicación encontrada: accuracy=${finalLocation.accuracy}m")
                            updateLocationInFirestore(finalLocation, userId)
                        } ?: run {
                            Log.e(TAG, "No se pudo obtener una ubicación precisa")
                            handleLocationError("No se pudo obtener una ubicación precisa", userId)
                        }
                    } else {
                        // Intentar obtener una ubicación más precisa
                        handler.postDelayed({ getCurrentLocation(userId) }, 2000)
                    }
                } ?: run {
                    Log.e(TAG, "No se pudo obtener la ubicación")
                    if (locationAttempts < MAX_LOCATION_ATTEMPTS) {
                        handler.postDelayed({ getCurrentLocation(userId) }, 2000)
                    } else {
                        handleLocationError("No se pudo obtener la ubicación", userId)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener ubicación: ${e.message}")
                if (locationAttempts < MAX_LOCATION_ATTEMPTS) {
                    handler.postDelayed({ getCurrentLocation(userId) }, 2000)
                } else {
                    handleLocationError("Error al obtener ubicación: ${e.message}", userId)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos: ${e.message}")
            handleLocationError("Error de permisos: ${e.message}", userId)
        }
    }

    private fun updateLocationInFirestore(location: Location, userId: String) {
        Log.d(TAG, "Buscando parentId para userId: $userId")

        // Buscar en parent_child_relations usando child_id
        db.collection("parent_child_relations")
            .whereEqualTo("child_id", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "No se encontró la relación padre-hijo para userId: $userId")
                    handleLocationError("No se encontró la relación padre-hijo", userId)
                    return@addOnSuccessListener
                }

                val document = documents.documents.first()
                Log.d(TAG, "Documento encontrado: ${document.id}")
                Log.d(TAG, "Campos: ${document.data?.keys?.joinToString()}")

                val parentId = document.getString("parent_id")
                if (parentId == null) {
                    Log.e(TAG, "No se encontró parent_id en el documento")
                    handleLocationError("La relación padre-hijo no tiene el campo 'parent_id'", userId)
                    return@addOnSuccessListener
                }

                Log.d(TAG, "ParentId encontrado: $parentId")
                val locationData = hashMapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "timestamp" to System.currentTimeMillis(),
                    "battery_level" to getBatteryLevel(),
                    "update_interval" to getUpdateInterval(),
                    "childId" to userId,
                    "parentId" to parentId
                )

                Log.d(TAG, "Actualizando ubicación en Firestore...")
                db.collection("ubicacion_actual")
                    .document(userId)
                    .set(locationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Ubicación actualizada correctamente")
                        lastSuccessfulUpdate = System.currentTimeMillis()
                        retryCount = 0
                        Toast.makeText(context, "Ubicación actualizada correctamente", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al actualizar ubicación: ${e.message}")
                        handleLocationError("Error al actualizar ubicación: ${e.message}", userId)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al buscar relación padre-hijo: ${e.message}")
                handleLocationError("Error al buscar relación: ${e.message}", userId)
            }
    }

    private fun handleLocationError(error: String, userId: String) {
        Log.e(TAG, "Error de ubicación: $error")
        if (retryCount < MAX_RETRIES) {
            retryCount++
            Log.d(TAG, "Reintentando... Intento $retryCount de $MAX_RETRIES")
            handler.postDelayed({
                getCurrentLocation(userId)
            }, TimeUnit.SECONDS.toMillis(30)) // Reintentar después de 30 segundos
        } else {
            Toast.makeText(context, "Error de ubicación: $error", Toast.LENGTH_LONG).show()
            retryCount = 0
        }
    }

    private fun getUpdateInterval(): Long {
        // Reducir el intervalo de actualización para obtener ubicaciones más frecuentes
        return when {
            isBatteryLow() -> LOW_BATTERY_UPDATE_INTERVAL
            else -> 1 * 60 * 1000L // 1 minuto para actualizaciones más frecuentes
        }
    }

    private fun isBatteryLow(): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            -1
        }
        return batteryLevel in 0..20
    }

    private fun isHighAccuracyNeeded(): Boolean {
        // Implementar lógica para determinar si se necesita alta precisión
        // Por ejemplo, basado en la velocidad del dispositivo o el estado de la aplicación
        return false
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            -1
        }
    }

    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}