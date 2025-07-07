package com.example.proyectito

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth

class LocationForegroundService : Service() {
    private val TAG = "LocationForegroundService"
    private val CHANNEL_ID = "LocationServiceChannel"
    private val NOTIFICATION_ID = 222
    private var isRunning = false
    private lateinit var locationManager: CustomLocationManager
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio de ubicación creado")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        auth = FirebaseAuth.getInstance()
        locationManager = CustomLocationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio de ubicación iniciado")
        userId = auth.currentUser?.uid
        if (!isRunning && userId != null) {
            isRunning = true
            locationManager.startLocationUpdates(userId!!)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio de ubicación destruido")
        isRunning = false
        locationManager.stopLocationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ubicación en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio para obtener la ubicación en segundo plano"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ubicación activa")
            .setContentText("La app está obteniendo la ubicación en segundo plano")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
} 