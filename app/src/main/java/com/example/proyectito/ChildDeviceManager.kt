package com.example.proyectito

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ChildDeviceManager(private val context: Context) {
    private val TAG = "ChildDeviceManager"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val packageManager = context.packageManager

    fun syncInstalledApps() {
        Log.d(TAG, "Iniciando sincronización de apps instaladas")

        if (!checkUsageStatsPermission()) {
            Log.w(TAG, "No hay permiso de uso de apps, no se puede sincronizar")
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "No hay usuario autenticado")
            return
        }

        Log.d(TAG, "Sincronizando apps para usuario: $userId")

        // Primero obtener las apps actuales para preservar el estado de bloqueo
        db.collection("children")
            .document(userId)
            .collection("installedApps")
            .document("apps")
            .get()
            .addOnSuccessListener { document ->
                val currentAppsList = document.get("apps") as? List<Map<String, Any>> ?: emptyList()
                val currentAppsMap = currentAppsList.associate {
                    it["packageName"] as String to (it["bloqueado"] as? Boolean ?: false)
                }

                Log.d(TAG, "Apps actuales en BD: ${currentAppsList.size}")
                Log.d(TAG, "Apps bloqueadas actuales: ${currentAppsMap.count { it.value }}")

                try {
                    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    Log.d(TAG, "Total de apps instaladas: ${installedApps.size}")

                    val userApps = installedApps.filter { !isSystemApp(it) }
                    Log.d(TAG, "Apps de usuario (no sistema): ${userApps.size}")

                    val appsList = userApps.map { app ->
                        mapOf(
                            "packageName" to app.packageName,
                            "nombre" to packageManager.getApplicationLabel(app).toString(),
                            "usageTime" to getAppUsageTime(app.packageName),
                            "bloqueado" to (currentAppsMap[app.packageName] ?: false)
                        )
                    }

                    Log.d(TAG, "Lista de apps procesada: ${appsList.size} apps")

                    // Guardar la lista de apps en Firestore
                    db.collection("children")
                        .document(userId)
                        .collection("installedApps")
                        .document("apps")
                        .set(mapOf("apps" to appsList))
                        .addOnSuccessListener {
                            Log.d(TAG, "Apps sincronizadas exitosamente: ${appsList.size} apps")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al sincronizar apps", e)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al obtener apps instaladas", e)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener apps actuales", e)
            }
    }

    private fun getAppUsageTime(packageName: String): Long {
        if (!checkUsageStatsPermission()) {
            Log.w(TAG, "No hay permiso de uso de apps")
            return 0L
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, -7) // Obtener estadísticas de la última semana
        val startTime = calendar.timeInMillis

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return usageStats
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }

    fun checkUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )

        val hasPermission = mode == AppOpsManager.MODE_ALLOWED
        Log.d(TAG, "Permiso de uso de apps: ${if (hasPermission) "CONCEDIDO" else "DENEGADO"} (mode: $mode)")

        return hasPermission
    }

    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}