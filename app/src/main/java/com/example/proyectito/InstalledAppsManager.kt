package com.example.proyectito

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class InstalledAppsManager(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val executor = Executors.newSingleThreadExecutor()

    fun getInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { !isSystemApp(it) }
            .map { appInfo ->
                val iconDrawable = try {
                    appInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
                }
                AppInfo(
                    packageName = appInfo.packageName,
                    nombre = appInfo.loadLabel(packageManager).toString(),
                    bloqueado = false,
                    icono = iconDrawable
                )
            }
    }

    fun syncInstalledApps(onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val installedApps = getInstalledApps()

        executor.execute {
            try {
                // Obtener apps actuales de Firebase
                val currentApps = db.collection("children")
                    .document(userId)
                    .collection("blockedApps")
                    .get()
                    .result
                    .documents
                    .mapNotNull { doc ->
                        try {
                            val iconDrawable = try {
                                context.packageManager.getApplicationIcon(doc.id)
                            } catch (e: Exception) {
                                androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
                            }
                            AppInfo(
                                packageName = doc.id,
                                nombre = doc.getString("name") ?: "",
                                bloqueado = doc.getBoolean("blocked") ?: false,
                                icono = iconDrawable
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                // Comparar y actualizar
                val changes = compareAndUpdateApps(installedApps, currentApps)

                // Actualizar Firebase
                changes.forEach { change ->
                    when (change) {
                        is AppChange.INSTALLED -> {
                            uploadAppIcon(change.app) { iconUrl ->
                                // No actualices el campo icono (Drawable) con un String
                                updateAppInFirebase(userId, change.app)
                            }
                        }
                        is AppChange.REMOVED -> {
                            removeAppFromFirebase(userId, change.app.packageName)
                        }
                        is AppChange.UPDATED -> {
                            uploadAppIcon(change.app) { iconUrl ->
                                // No actualices el campo icono (Drawable) con un String
                                updateAppInFirebase(userId, change.app)
                            }
                        }
                    }
                }

                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    private fun compareAndUpdateApps(
        localApps: List<AppInfo>,
        firebaseApps: List<AppInfo>
    ): List<AppChange> {
        val changes = mutableListOf<AppChange>()

        // Encontrar apps nuevas
        localApps.forEach { localApp ->
            if (!firebaseApps.any { it.packageName == localApp.packageName }) {
                changes.add(AppChange.INSTALLED(localApp))
            }
        }

        // Encontrar apps removidas
        firebaseApps.forEach { firebaseApp ->
            if (!localApps.any { it.packageName == firebaseApp.packageName }) {
                changes.add(AppChange.REMOVED(firebaseApp))
            }
        }

        // Encontrar apps actualizadas
        localApps.forEach { localApp ->
            firebaseApps.find { it.packageName == localApp.packageName }?.let { firebaseApp ->
                if (localApp.nombre != firebaseApp.nombre) {
                    changes.add(AppChange.UPDATED(localApp))
                }
            }
        }

        return changes
    }

    private fun uploadAppIcon(app: AppInfo, onComplete: (String) -> Unit) {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(app.packageName, 0)
            val applicationInfo = packageInfo.applicationInfo ?: return onComplete("")
            val icon = applicationInfo.loadIcon(packageManager)

            val bitmap = when (icon) {
                is BitmapDrawable -> icon.bitmap
                else -> {
                    val drawable = icon as Drawable
                    val bitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth,
                        drawable.intrinsicHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                    drawable.draw(android.graphics.Canvas(bitmap))
                    bitmap
                }
            }

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val data = baos.toByteArray()

            val storageRef = storage.reference
                .child("app_icons")
                .child("${app.packageName}.png")

            storageRef.putBytes(data)
                .continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let { throw it }
                    }
                    storageRef.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val downloadUri = task.result
                        onComplete(downloadUri.toString())
                    } else {
                        onComplete("")
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete("")
        }
    }

    private fun updateAppInFirebase(userId: String, app: AppInfo) {
        db.collection("children")
            .document(userId)
            .collection("blockedApps")
            .document(app.packageName)
            .set(app)
    }

    private fun removeAppFromFirebase(userId: String, packageName: String) {
        db.collection("children")
            .document(userId)
            .collection("blockedApps")
            .document(packageName)
            .delete()
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        val flags = appInfo.flags
        val systemFlag = ApplicationInfo.FLAG_SYSTEM
        return (flags and systemFlag) == systemFlag
    }
}

sealed class AppChange {
    data class INSTALLED(val app: AppInfo) : AppChange()
    data class REMOVED(val app: AppInfo) : AppChange()
    data class UPDATED(val app: AppInfo) : AppChange()
}