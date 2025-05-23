package com.example.proyectito

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
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
import java.util.*

class AlertasActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: AlertaAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var childId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alertas)

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
        loadAlertas()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerApps)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = AlertaAdapter { alerta ->
            when (alerta.tipo) {
                Alerta.TIPO_SOLICITUD_TIEMPO -> showTimePickerDialog(alerta)
                Alerta.TIPO_DESBLOQUEO -> showDesbloqueoDialog(alerta)
                else -> showInfoDialog(alerta)
            }
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
                        toolbar.title = "Alertas - $childName"
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al cargar información del niño", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadAlertas() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        childId?.let { id ->
            db.collection("children")
                .document(id)
                .collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    progressBar.visibility = View.GONE

                    if (error != null) {
                        Toast.makeText(this, "Error al cargar alertas: ${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val alertas = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            Alerta(
                                id = doc.id,
                                tipo = doc.getString("type") ?: "",
                                mensaje = doc.getString("message") ?: "",
                                estado = doc.getString("status") ?: Alerta.ESTADO_PENDIENTE,
                                fecha = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                                tiempoAprobado = doc.getLong("approvedTime")?.toInt() ?: 0
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()

                    if (alertas.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(alertas)
                    }
                }
        }
    }

    private fun showTimePickerDialog(alerta: Alerta) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val minutos = selectedHour * 60 + selectedMinute
                updateAlertStatus(alerta, Alerta.ESTADO_APROBADO, minutos)
            },
            hour,
            minute,
            true
        ).show()
    }

    private fun showDesbloqueoDialog(alerta: Alerta) {
        AlertDialog.Builder(this)
            .setTitle("Solicitud de desbloqueo")
            .setMessage(alerta.mensaje)
            .setPositiveButton("Aprobar") { _, _ ->
                updateAlertStatus(alerta, Alerta.ESTADO_APROBADO)
            }
            .setNegativeButton("Denegar") { _, _ ->
                updateAlertStatus(alerta, Alerta.ESTADO_DENEGADO)
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun showInfoDialog(alerta: Alerta) {
        AlertDialog.Builder(this)
            .setTitle(when (alerta.tipo) {
                Alerta.TIPO_INSTALACION -> "Instalación de app"
                Alerta.TIPO_USO_BLOQUEADO -> "Intento de uso bloqueado"
                else -> "Información"
            })
            .setMessage(alerta.mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun updateAlertStatus(alerta: Alerta, nuevoEstado: String, tiempoAprobado: Int = 0) {
        childId?.let { id ->
            val updates = hashMapOf<String, Any>(
                "status" to nuevoEstado,
                "lastUpdated" to com.google.firebase.Timestamp.now()
            )

            if (tiempoAprobado > 0) {
                updates["approvedTime"] = tiempoAprobado
            }

            db.collection("children")
                .document(id)
                .collection("alerts")
                .document(alerta.id)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        when (nuevoEstado) {
                            Alerta.ESTADO_APROBADO -> "Solicitud aprobada"
                            Alerta.ESTADO_DENEGADO -> "Solicitud denegada"
                            else -> "Estado actualizado"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
} 