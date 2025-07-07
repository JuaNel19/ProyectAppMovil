package com.example.proyectito

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BloquearAppsActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewNoApps: TextView
    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bloquear_apps)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Configurar Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Bloquear Apps"

        // Inicializar vistas
        recyclerView = findViewById(R.id.recyclerApps)
        progressBar = findViewById(R.id.progressBar)
        textViewNoApps = findViewById(R.id.tvNoApps)

        // Configurar RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppsAdapter { app, isBlocked ->
            updateAppBlockStatus(app, isBlocked)
        }
        recyclerView.adapter = adapter

        // Cargar apps
        loadApps()
    }

    private fun loadApps() {
        val userId = auth.currentUser?.uid ?: return
        val childId = intent.getStringExtra("childId") ?: return

        progressBar.visibility = View.VISIBLE
        textViewNoApps.visibility = View.GONE

        // Obtener apps instaladas del hijo usando addSnapshotListener para actualizaciones en tiempo real
        db.collection("children")
            .document(childId)
            .collection("installedApps")
            .document("apps")
            .addSnapshotListener { document, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e("BloquearAppsActivity", "Error al cargar apps", error)
                    textViewNoApps.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val pm = packageManager
                val appsList = document?.get("apps") as? List<Map<String, Any>> ?: emptyList()
                val apps = appsList.map { appMap ->
                    val packageName = appMap["packageName"] as String
                    val nombre = appMap["nombre"] as String
                    val bloqueado = appMap["bloqueado"] as? Boolean ?: false
                    val iconDrawable = try {
                        pm.getApplicationIcon(packageName)
                    } catch (e: Exception) {
                        androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)!!
                    }
                    AppInfo(
                        packageName = packageName,
                        nombre = nombre,
                        bloqueado = bloqueado,
                        icono = iconDrawable
                    )
                }

                adapter.submitList(apps)
                textViewNoApps.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun updateAppBlockStatus(app: AppInfo, isBlocked: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val childId = intent.getStringExtra("childId") ?: return

        Log.d("BloquearAppsActivity", "Actualizando estado de bloqueo para ${app.packageName} a $isBlocked")

        // Obtener la lista actual de apps
        db.collection("children")
            .document(childId)
            .collection("installedApps")
            .document("apps")
            .get()
            .addOnSuccessListener { document ->
                val appsList = document.get("apps") as? List<Map<String, Any>> ?: emptyList()
                Log.d("BloquearAppsActivity", "Lista actual de apps: $appsList")

                // Actualizar el estado de bloqueo de la app específica
                val updatedAppsList = appsList.map { appMap ->
                    if (appMap["packageName"] == app.packageName) {
                        Log.d("BloquearAppsActivity", "Encontrada app ${app.packageName}, actualizando estado a $isBlocked")
                        appMap.toMutableMap().apply {
                            put("bloqueado", isBlocked)
                        }
                    } else {
                        appMap
                    }
                }

                Log.d("BloquearAppsActivity", "Lista actualizada de apps: $updatedAppsList")

                // Guardar la lista actualizada usando update
                db.collection("children")
                    .document(childId)
                    .collection("installedApps")
                    .document("apps")
                    .update("apps", updatedAppsList)
                    .addOnSuccessListener {
                        Log.d("BloquearAppsActivity", "Estado de bloqueo actualizado exitosamente para ${app.packageName}")
                    }
                    .addOnFailureListener { e ->
                        Log.e("BloquearAppsActivity", "Error al actualizar estado", e)
                        Toast.makeText(this, "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("BloquearAppsActivity", "Error al obtener lista de apps", e)
                Toast.makeText(this, "Error al obtener lista de apps: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}