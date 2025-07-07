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
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.util.Calendar

class AppBlockerService : Service() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val executor = Executors.newSingleThreadExecutor()
    private var isRunning = false
    private var lastCheckedPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastApp = ""
    private var lastAppStartTime = 0L
    private val updateInterval = 60000L // 1 minuto

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AppBlockerChannel"
        private const val TAG = "AppBlockerService"
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateAppUsage()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio creado")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio iniciado")
        if (!isRunning) {
            isRunning = true
            startMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio destruido")
        isRunning = false
        executor.shutdown()
        handler.removeCallbacks(updateRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitorea y bloquea aplicaciones"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control Parental Activo")
            .setContentText("Monitoreando aplicaciones")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        Log.d(TAG, "Iniciando monitoreo")
        executor.execute {
            while (isRunning) {
                try {
                    checkCurrentApp()
                    TimeUnit.SECONDS.sleep(1)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en el monitoreo: ${e.message}")
                }
            }
        }
    }

    private fun checkCurrentApp() {
        val userId = auth.currentUser?.uid ?: return
        val currentPackage = getCurrentPackage() ?: return

        // Solo verificar si el paquete ha cambiado
        if (currentPackage == lastCheckedPackage) {
            return
        }
        lastCheckedPackage = currentPackage

        Log.d(TAG, "Verificando app: $currentPackage")

        // Verificar si la app está bloqueada en la colección installedApps
        db.collection("children")
            .document(userId)
            .collection("installedApps")
            .document("apps")
            .get()
            .addOnSuccessListener { document ->
                val appsList = document.get("apps") as? List<Map<String, Any>> ?: emptyList()
                val app = appsList.find { it["packageName"] == currentPackage }

                if (app != null) {
                    val isBlocked = app["bloqueado"] == true
                    Log.d(TAG, "App $currentPackage - Bloqueada: $isBlocked")

                    if (isBlocked) {
                        Log.d(TAG, "Mostrando pantalla de bloqueo para: $currentPackage")
                        showBlockScreen(currentPackage)
                    }
                } else {
                    Log.d(TAG, "App $currentPackage no encontrada en la lista")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al verificar app: ${e.message}")
            }
    }

    private fun getCurrentPackage(): String? {
        return try {
            val packageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val time = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    time - 1000 * 1000,
                    time
                )
                stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            } else {
                @Suppress("DEPRECATION")
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activityManager.appTasks[0].taskInfo.topActivity?.packageName
                } else {
                    @Suppress("DEPRECATION")
                    activityManager.getRunningTasks(1)[0].topActivity?.packageName
                }
            }

            if (packageName != null) {
                Log.d(TAG, "App actual detectada: $packageName")
            } else {
                Log.d(TAG, "No se pudo detectar la app actual")
            }

            packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener la app actual: ${e.message}")
            null
        }
    }

    private fun showBlockScreen(packageName: String) {
        Log.d(TAG, "Mostrando pantalla de bloqueo para: $packageName")
        val intent = Intent(this, AppBlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("packageName", packageName)
            putExtra("appName", getAppName(packageName))

            // Obtener datos del hijo actual
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                FirebaseFirestore.getInstance()
                    .collection("hijos")
                    .document(userId)
                    .get()
                    .addOnSuccessListener { doc ->
                        val childName = doc.getString("nombre") ?: "Hijo"
                        val parentId = doc.getString("parentId") ?: ""

                        putExtra("childName", childName)
                        putExtra("parentId", parentId)

                        Log.d(TAG, "Datos del hijo - Nombre: $childName, ParentId: $parentId")
                        startActivity(this)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al obtener datos del hijo", e)
                        startActivity(this)
                    }
            } else {
                Log.e(TAG, "No hay usuario autenticado")
                startActivity(this)
            }
        }
    }

    private fun getAppName(packageName: String): String {
        // Implementa la lógica para obtener el nombre de la aplicación
        // Puedes usar el paquete android.content.pm para obtener el nombre de la aplicación
        // Aquí se devuelve el nombre del paquete como ejemplo
        return packageName
    }

    private fun updateAppUsage() {
        val userId = auth.currentUser?.uid ?: return
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.MINUTE, -1) // Último minuto
        val startTime = calendar.timeInMillis

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var currentApp = ""
        var appStartTime = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (currentApp.isNotEmpty() && appStartTime > 0) {
                        updateAppUsageTime(currentApp, endTime - appStartTime)
                    }
                    currentApp = event.packageName
                    appStartTime = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentApp == event.packageName) {
                        updateAppUsageTime(currentApp, event.timeStamp - appStartTime)
                        currentApp = ""
                        appStartTime = 0
                    }
                }
            }
        }

        // Actualizar el tiempo de la app actual si aún está en uso
        if (currentApp.isNotEmpty() && appStartTime > 0) {
            updateAppUsageTime(currentApp, endTime - appStartTime)
        }
    }

    private fun updateAppUsageTime(packageName: String, usageTime: Long) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("children")
            .document(userId)
            .collection("installedApps")
            .document("apps")
            .get()
            .addOnSuccessListener { document ->
                val appsList = document.get("apps") as? List<Map<String, Any>> ?: emptyList()
                val updatedAppsList = appsList.map { appMap ->
                    if (appMap["packageName"] == packageName) {
                        val currentUsageTime = appMap["usageTime"] as? Long ?: 0L
                        appMap.toMutableMap().apply {
                            put("usageTime", currentUsageTime + usageTime)
                        }
                    } else {
                        appMap
                    }
                }

                document.reference
                    .set(mapOf("apps" to updatedAppsList))
                    .addOnSuccessListener {
                        Log.d(TAG, "Tiempo de uso actualizado para $packageName")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al actualizar tiempo de uso", e)
                    }
            }
    }
}