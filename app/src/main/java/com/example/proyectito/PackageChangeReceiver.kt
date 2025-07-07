package com.example.proyectito

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PackageChangeReceiver : BroadcastReceiver() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val userId = auth.currentUser?.uid ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                handleAppInstalled(context, packageName, userId)
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                handleAppRemoved(packageName, userId)
            }
        }
    }

    private fun handleAppInstalled(context: Context, packageName: String, userId: String) {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val applicationInfo = packageInfo.applicationInfo ?: return

            val appName = applicationInfo.loadLabel(packageManager).toString()
            val isSystemApp = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

            // Solo actualizar apps no del sistema
            if (!isSystemApp) {
                val iconDrawable = try {
                    applicationInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
                }
                val appInfo = AppInfo(
                    packageName = packageName,
                    nombre = appName,
                    bloqueado = false,
                    icono = iconDrawable
                )

                db.collection("children")
                    .document(userId)
                    .collection("blockedApps")
                    .document(packageName)
                    .set(appInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleAppRemoved(packageName: String, userId: String) {
        db.collection("children")
            .document(userId)
            .collection("blockedApps")
            .document(packageName)
            .delete()
    }
}