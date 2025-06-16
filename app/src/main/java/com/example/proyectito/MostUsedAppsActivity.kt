package com.example.proyectito

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MostUsedAppsActivity : AppCompatActivity() {
    private val TAG = "MostUsedAppsActivity"
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppUsageAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var statsRangeText: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_most_used_apps)

        // Configurar ToolBar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Inicializar TextView para el rango de tiempo
        statsRangeText = findViewById(R.id.statsRangeText)
        updateStatsRangeText()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppUsageAdapter()
        recyclerView.adapter = adapter

        // Obtener las apps del hijo
        loadChildApps()
    }

    private fun updateStatsRangeText() {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_MONTH, -7) // Últimos 7 días
        val startDate = calendar.time

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val rangeText = "Estadísticas de uso de los últimos 7 días\n" +
                "Desde ${dateFormat.format(startDate)} hasta ${dateFormat.format(endDate)}"

        statsRangeText.text = rangeText
    }

    private fun loadChildApps() {
        val userId = auth.currentUser?.uid ?: return

        // Primero obtener el ID del hijo desde parent_child_relations
        db.collection("parent_child_relations")
            .whereEqualTo("parent_id", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "No se encontró relación padre-hijo")
                    Toast.makeText(this, "No se encontró ningún hijo asociado", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val childId = documents.documents[0].getString("child_id")
                if (childId == null) {
                    Log.e(TAG, "ChildId es null")
                    Toast.makeText(this, "Error: ID del hijo no encontrado", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Obtener las apps instaladas del hijo
                db.collection("children")
                    .document(childId)
                    .collection("installedApps")
                    .document("apps")
                    .get()
                    .addOnSuccessListener { document ->
                        val appsList = document.get("apps") as? List<Map<String, Any>> ?: emptyList()
                        val appList = appsList.mapNotNull { appMap ->
                            val packageName = appMap["packageName"] as? String
                            val nombre = appMap["nombre"] as? String
                            val usageTime = appMap["usageTime"] as? Long ?: 0L

                            if (packageName != null && nombre != null) {
                                AppUsageInfo(
                                    packageName = packageName,
                                    appName = nombre,
                                    usageTime = usageTime
                                )
                            } else null
                        }.sortedByDescending { it.usageTime }

                        // Actualizar el RecyclerView
                        adapter.submitList(appList)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al obtener apps del hijo", e)
                        Toast.makeText(this, "Error al cargar las apps", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al buscar relación padre-hijo", e)
                Toast.makeText(this, "Error al buscar relación con el hijo", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUsageStatsToFirestore(childId: String, appUsageList: List<AppUsageInfo>) {
        val statsData = appUsageList.map { app ->
            mapOf(
                "packageName" to app.packageName,
                "appName" to app.appName,
                "usageTime" to app.usageTime,
                "lastUpdated" to System.currentTimeMillis()
            )
        }

        db.collection("usuarios")
            .document(childId)
            .collection("appUsageStats")
            .document("current")
            .set(mapOf(
                "stats" to statsData,
                "lastUpdated" to System.currentTimeMillis()
            ))
            .addOnSuccessListener {
                Log.d(TAG, "Estadísticas guardadas exitosamente")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar estadísticas", e)
            }
    }

    override fun onResume() {
        super.onResume()
        loadChildApps()
        updateStatsRangeText()
    }
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTime: Long
) {
    fun getFormattedUsageTime(): String {
        val hours = TimeUnit.MILLISECONDS.toHours(usageTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(usageTime) % 60
        return when {
            hours > 0 -> "$hours h $minutes min"
            else -> "$minutes min"
        }
    }
} 