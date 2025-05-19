package com.example.proyectito

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoAlertas: TextView
    private lateinit var adapter: AlertaAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alertas)

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Configurar toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        // Inicializar vistas
        recyclerView = findViewById(R.id.recyclerAlertas)
        tvNoAlertas = findViewById(R.id.tvNoAlertas)

        // Configurar RecyclerView
        adapter = AlertaAdapter(emptyList()) { alerta ->
            when (alerta.tipo) {
                Alerta.TIPO_SOLICITUD_TIEMPO -> showTimePickerDialog(alerta)
                Alerta.TIPO_DESBLOQUEO -> showDesbloqueoDialog(alerta)
                else -> showInfoDialog(alerta)
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Cargar alertas
        cargarAlertas()
    }

    private fun cargarAlertas() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("alertas")
            .whereEqualTo("parentId", userId)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                val alertas = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Alerta::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                adapter.updateAlertas(alertas)
                tvNoAlertas.visibility = if (alertas.isEmpty()) View.VISIBLE else View.GONE
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
                actualizarEstadoAlerta(alerta, Alerta.ESTADO_APROBADO, minutos)
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
                actualizarEstadoAlerta(alerta, Alerta.ESTADO_APROBADO)
            }
            .setNegativeButton("Denegar") { _, _ ->
                actualizarEstadoAlerta(alerta, Alerta.ESTADO_DENEGADO)
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

    private fun actualizarEstadoAlerta(alerta: Alerta, nuevoEstado: String, tiempoAprobado: Int = 0) {
        val updates = hashMapOf<String, Any>(
            "estado" to nuevoEstado
        )

        if (tiempoAprobado > 0) {
            updates["tiempoAprobado"] = tiempoAprobado
        }

        db.collection("alertas").document(alerta.id)
            .update(updates)
            .addOnFailureListener { e ->
                // Manejar error
            }
    }
} 