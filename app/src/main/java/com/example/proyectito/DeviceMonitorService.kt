package com.example.proyectito

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.TimeUnit
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Calendar

class DeviceMonitorService : Service() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var startTime: Long = 0
    private var totalUsedTime: Long = 0
    private var lastFirestoreUpdate: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    // Intervalos de actualización
    private val UI_UPDATE_INTERVAL = 1000L // 1 segundo para actualizaciones de UI
    private val FIRESTORE_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutos para actualizaciones en Firestore
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "device_monitor_channel"

    // Buffer local para acumular tiempo
    private var localTimeBuffer: Long = 0
    private var dailyLimit: Long = 0
    private var isDeviceLocked: Boolean = false
    private var limitsListener: ListenerRegistration? = null
    private var lastResetDay: Int = -1
    private var tiempoGraciaActivo: Boolean = false

    private lateinit var localBroadcastManager: LocalBroadcastManager

    companion object {
        const val ACTION_TIME_UPDATE = "com.example.proyectito.TIME_UPDATE"
        const val EXTRA_TIME_USED = "time_used"
        const val EXTRA_TIME_REMAINING = "time_remaining"
        const val EXTRA_LIMIT_REACHED = "limit_reached"
        const val HORA_REINICIO = 8 // 8:00 AM

        fun startService(context: Context) {
            val intent = Intent(context, DeviceMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, DeviceMonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Crear canal de notificación e iniciar como servicio en primer plano
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        // Iniciar monitoreo
        startMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo de dispositivo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo de uso del dispositivo"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Monitoreo activo")
        .setContentText("Monitoreando uso del dispositivo")
        .setSmallIcon(R.drawable.ic_time)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun isNewDay(): Boolean {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // Solo reiniciar si es un nuevo día Y es después de la hora de reinicio
        if (currentDay != lastResetDay && currentHour >= HORA_REINICIO) {
            lastResetDay = currentDay
            return true
        }
        return false
    }

    private fun startMonitoring() {
        val userId = auth.currentUser?.uid ?: return

        // Configurar listener para límites
        setupLimitsListener(userId)

        // Obtener datos iniciales
        db.collection("control_dispositivo_hijo")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                dailyLimit = document.getLong("limite_diario") ?: TimeUnit.HOURS.toMillis(2)
                tiempoGraciaActivo = document.getBoolean("tiempo_gracia_activo") ?: false

                if (isNewDay()) {
                    // Si es un nuevo día después de la hora de reinicio, reiniciar todo
                    totalUsedTime = 0
                    localTimeBuffer = 0
                    tiempoGraciaActivo = false
                    updateFirestoreWithZeroTime(userId)
                } else {
                    // Si es el mismo día, cargar el tiempo existente
                    totalUsedTime = document.getLong("tiempo_usado") ?: 0L
                    localTimeBuffer = 0
                }

                startTime = System.currentTimeMillis()
                lastFirestoreUpdate = startTime
                isDeviceLocked = false

                // Iniciar el contador
                handler.postDelayed(timeUpdateRunnable, UI_UPDATE_INTERVAL)
            }
    }

    private fun updateFirestoreWithZeroTime(userId: String) {
        db.collection("control_dispositivo_hijo")
            .document(userId)
            .update(
                mapOf(
                    "tiempo_usado" to 0L,
                    "ultima_actualizacion" to System.currentTimeMillis(),
                    "tiempo_gracia_activo" to false
                )
            )
            .addOnFailureListener { e ->
                Log.e("DeviceMonitor", "Error al reiniciar tiempo: ${e.message}")
            }
    }

    private fun setupLimitsListener(userId: String) {
        limitsListener = db.collection("control_dispositivo_hijo")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("DeviceMonitor", "Error al escuchar límites: ${e.message}")
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val newLimit = doc.getLong("limite_diario") ?: TimeUnit.HOURS.toMillis(2)
                    val newTiempoGracia = doc.getBoolean("tiempo_gracia_activo") ?: false
                    val newTiempoUsado = doc.getLong("tiempo_usado") ?: 0L

                    if (newLimit != dailyLimit || newTiempoGracia != tiempoGraciaActivo) {
                        dailyLimit = newLimit
                        tiempoGraciaActivo = newTiempoGracia

                        // Reiniciar contadores locales
                        totalUsedTime = newTiempoUsado
                        localTimeBuffer = 0
                        startTime = System.currentTimeMillis()

                        if (!tiempoGraciaActivo) {
                            // Si se desactiva el tiempo de gracia, verificar límite
                            checkDailyLimit()
                        }

                        // Actualizar UI con el tiempo reiniciado
                        updateLocalUI(totalUsedTime)
                    }
                }
            }
    }

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - startTime
            totalUsedTime += elapsedTime
            localTimeBuffer += elapsedTime
            startTime = currentTime

            // Verificar límite y actualizar UI
            if (!tiempoGraciaActivo) {
                checkDailyLimit()
            }
            updateLocalUI(totalUsedTime)

            // Verificar si es necesario actualizar Firestore
            if (currentTime - lastFirestoreUpdate >= FIRESTORE_UPDATE_INTERVAL) {
                updateFirestore()
            }

            // Programar la próxima actualización
            handler.postDelayed(this, UI_UPDATE_INTERVAL)
        }
    }

    private fun updateLocalUI(usedTimeMillis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(usedTimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(usedTimeMillis) % 60
        Log.d("DeviceMonitor", "Tiempo usado: ${hours}h ${minutes}m")

        // Enviar broadcast con la información actualizada
        val intent = Intent(ACTION_TIME_UPDATE).apply {
            putExtra(EXTRA_TIME_USED, usedTimeMillis)
            putExtra(EXTRA_TIME_REMAINING, dailyLimit - usedTimeMillis)
            putExtra(EXTRA_LIMIT_REACHED, usedTimeMillis >= dailyLimit && !tiempoGraciaActivo)
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun updateFirestore() {
        val userId = auth.currentUser?.uid ?: return
        if (localTimeBuffer <= 0) return

        // Actualizar tiempo en Firestore usando incremento atómico
        db.collection("control_dispositivo_hijo")
            .document(userId)
            .update(
                mapOf(
                    "tiempo_usado" to FieldValue.increment(localTimeBuffer),
                    "ultima_actualizacion" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                lastFirestoreUpdate = System.currentTimeMillis()
                localTimeBuffer = 0
            }
            .addOnFailureListener { e ->
                Log.e("DeviceMonitor", "Error al actualizar tiempo: ${e.message}")
            }
    }

    private fun checkDailyLimit() {
        if (!isDeviceLocked && !tiempoGraciaActivo && totalUsedTime >= dailyLimit) {
            isDeviceLocked = true
            lockDevice()
        }
    }

    private fun lockDevice() {
        val intent = Intent(this, DeviceLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Asegurarse de actualizar Firestore antes de destruir el servicio
        updateFirestore()
        handler.removeCallbacks(timeUpdateRunnable)
        limitsListener?.remove()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }
}