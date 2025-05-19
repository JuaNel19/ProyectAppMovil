package com.example.proyectito

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LimitarTiempoActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: AppTiempoAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var childId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_limitar_tiempo)

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Obtener childId del intent
        childId = intent.getStringExtra("childId")
        if (childId == null) {
            Toast.makeText(this, "Error: No se especificó el niño", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadChildInfo()
        loadApps()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerApps)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvNoApps)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = AppTiempoAdapter { app ->
            showTimeLimitDialog(app)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadChildInfo() {
        childId?.let { id ->
            db.collection("children")
                .document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val childName = document.getString("name") ?: "Niño"
                        toolbar.title = "Limitar Tiempo - $childName"
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al cargar información del niño", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        childId?.let { id ->
            db.collection("children")
                .document(id)
                .collection("timeLimits")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    progressBar.visibility = View.GONE

                    if (error != null) {
                        Toast.makeText(this, "Error al cargar apps: ${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val apps = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            AppTiempoInfo(
                                nombre = doc.getString("name") ?: "",
                                packageName = doc.id,
                                icono = doc.getString("icon") ?: "",
                                limiteDiario = doc.getLong("dailyLimit")?.toInt() ?: 0
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()

                    if (apps.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(apps)
                    }
                }
        }
    }

    private fun showTimeLimitDialog(app: AppTiempoInfo) {
        val view = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val numberPicker = view.findViewById<NumberPicker>(R.id.numberPicker)

        // Configurar NumberPicker
        numberPicker.minValue = 5
        numberPicker.maxValue = 180
        numberPicker.value = app.limiteDiario.coerceIn(5, 180)

        AlertDialog.Builder(this)
            .setTitle("Establecer límite de tiempo")
            .setMessage("Selecciona el límite diario para ${app.nombre}")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val minutos = numberPicker.value
                updateAppTimeLimit(app, minutos)
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Sin límite") { _, _ ->
                updateAppTimeLimit(app, 0)
            }
            .show()
    }

    private fun updateAppTimeLimit(app: AppTiempoInfo, minutos: Int) {
        childId?.let { id ->
            val appRef = db.collection("children")
                .document(id)
                .collection("timeLimits")
                .document(app.packageName)

            appRef.set(
                hashMapOf(
                    "name" to app.nombre,
                    "packageName" to app.packageName,
                    "dailyLimit" to minutos,
                    "icon" to app.icono,
                    "lastUpdated" to System.currentTimeMillis()
                )
            )
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        if (minutos > 0) "Límite establecido: $minutos minutos" else "Límite eliminado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar límite: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}