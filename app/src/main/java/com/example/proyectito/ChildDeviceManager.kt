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
        if (!checkUsageStatsPermission()) {
            Log.w(TAG, "No hay permiso de uso de apps")
            return
        }

        val userId = auth.currentUser?.uid ?: return

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

                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                val appsList = installedApps
                    .filter { !isSystemApp(it) }
                    .map { app ->
                        mapOf(
                            "packageName" to app.packageName,
                            "nombre" to packageManager.getApplicationLabel(app).toString(),
                            "usageTime" to getAppUsageTime(app.packageName),
                            "bloqueado" to (currentAppsMap[app.packageName] ?: false)
                        )
                    }

                // Guardar la lista de apps en Firestore
                db.collection("children")
                    .document(userId)
                    .collection("installedApps")
                    .document("apps")
                    .set(mapOf("apps" to appsList))
                    .addOnSuccessListener {
                        Log.d(TAG, "Apps sincronizadas exitosamente")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al sincronizar apps", e)
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
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}