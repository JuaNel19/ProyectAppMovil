package com.example.proyectito

import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.*

class AlertasActivity : AppCompatActivity() {
    private val TAG = "AlertasActivity"
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: AlertasAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var childId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alertas)

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
        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.emptyView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = AlertasAdapter { alerta ->
            when (alerta.tipo) {
                TipoAlerta.DESBLOQUEO_APP -> showUnblockDialog(alerta)
                TipoAlerta.TIEMPO_AGOTADO -> marcarAlertaComoLeida(alerta.id)
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
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        val userId = auth.currentUser?.uid ?: return
        val alertasList = mutableListOf<Alerta>()

        Log.d(TAG, "Cargando alertas para usuario: $userId")

        // Cargar solicitudes de desbloqueo
        db.collection("unblockRequests")
            .whereEqualTo("parentId", userId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "Solicitudes de desbloqueo encontradas: ${snapshot.documents.size}")

                // Limpiar alertas de desbloqueo anteriores
                alertasList.removeAll { it.tipo == TipoAlerta.DESBLOQUEO_APP }

                // Agregar nuevas solicitudes de desbloqueo
                snapshot.documents.forEach { doc ->
                    val childId = doc.getString("childId")
                    val childName = doc.getString("childName")
                    val appName = doc.getString("appName")
                    val packageName = doc.getString("packageName")
                    val timestamp = doc.getLong("timestamp")

                    if (childId != null && childName != null && appName != null && packageName != null && timestamp != null) {
                        alertasList.add(
                            Alerta(
                                id = doc.id,
                                tipo = TipoAlerta.DESBLOQUEO_APP,
                                mensaje = "$childName solicita desbloquear $appName",
                                timestamp = timestamp,
                                childId = childId,
                                childName = childName,
                                leida = false,
                                appName = appName,
                                packageName = packageName
                            )
                        )
                    }
                }

                // Ordenar todas las alertas por timestamp
                val alertasOrdenadas = alertasList.sortedByDescending { it.timestamp }
                adapter.submitList(alertasOrdenadas)
                recyclerView.visibility = if (alertasOrdenadas.isEmpty()) View.GONE else View.VISIBLE
                tvEmpty.visibility = if (alertasOrdenadas.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar solicitudes de desbloqueo", e)
            }

        // Cargar alertas de tiempo agotado
        db.collection("alertas")
            .whereEqualTo("parentId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "Alertas de tiempo encontradas: ${snapshot.documents.size}")

                // Limpiar alertas de tiempo anteriores
                alertasList.removeAll { it.tipo == TipoAlerta.TIEMPO_AGOTADO }

                // Agregar nuevas alertas de tiempo
                snapshot.documents.forEach { doc ->
                    val tipo = doc.getString("tipo")?.let { TipoAlerta.valueOf(it) }
                    val mensaje = doc.getString("mensaje")
                    val timestamp = doc.getLong("timestamp")
                    val childId = doc.getString("childId")
                    val childName = doc.getString("childName")
                    val leida = doc.getBoolean("leida") ?: false

                    if (tipo != null && mensaje != null && timestamp != null && childId != null && childName != null) {
                        alertasList.add(
                            Alerta(
                                id = doc.id,
                                tipo = tipo,
                                mensaje = mensaje,
                                timestamp = timestamp,
                                childId = childId,
                                childName = childName,
                                leida = leida
                            )
                        )
                    }
                }

                // Ordenar todas las alertas por timestamp
                val alertasOrdenadas = alertasList.sortedByDescending { it.timestamp }
                adapter.submitList(alertasOrdenadas)
                recyclerView.visibility = if (alertasOrdenadas.isEmpty()) View.GONE else View.VISIBLE
                tvEmpty.visibility = if (alertasOrdenadas.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al cargar alertas de tiempo", e)
            }
    }

    private fun showUnblockDialog(alerta: Alerta) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Solicitud de desbloqueo")
            .setMessage("${alerta.childName} solicita desbloquear ${alerta.appName}")
            .setPositiveButton("Aprobar") { _, _ ->
                aprobarDesbloqueo(alerta)
            }
            .setNegativeButton("Rechazar") { _, _ ->
                rechazarDesbloqueo(alerta)
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun aprobarDesbloqueo(alerta: Alerta) {
        // Actualizar estado de la solicitud en Firestore
        db.collection("unblockRequests")
            .document(alerta.id)
            .update("status", "approved")
            .addOnSuccessListener {
                // Crear notificación para el hijo
                val notification = hashMapOf(
                    "childId" to alerta.childId,
                    "title" to "Solicitud aprobada",
                    "message" to "Tu solicitud para desbloquear ${alerta.appName} ha sido aprobada",
                    "timestamp" to System.currentTimeMillis(),
                    "type" to "unblock_approved"
                )

                db.collection("notifications")
                    .add(notification)
                    .addOnSuccessListener {
                        marcarAlertaComoLeida(alerta.id)
                        Toast.makeText(this, "Solicitud aprobada", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al aprobar solicitud", e)
                Toast.makeText(this, "Error al aprobar solicitud", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rechazarDesbloqueo(alerta: Alerta) {
        // Actualizar estado de la solicitud en Firestore
        db.collection("unblockRequests")
            .document(alerta.id)
            .update("status", "rejected")
            .addOnSuccessListener {
                // Crear notificación para el hijo
                val notification = hashMapOf(
                    "childId" to alerta.childId,
                    "title" to "Solicitud rechazada",
                    "message" to "Tu solicitud para desbloquear ${alerta.appName} ha sido rechazada",
                    "timestamp" to System.currentTimeMillis(),
                    "type" to "unblock_rejected"
                )

                db.collection("notifications")
                    .add(notification)
                    .addOnSuccessListener {
                        marcarAlertaComoLeida(alerta.id)
                        Toast.makeText(this, "Solicitud rechazada", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al rechazar solicitud", e)
                Toast.makeText(this, "Error al rechazar solicitud", Toast.LENGTH_SHORT).show()
            }
    }

    private fun marcarAlertaComoLeida(alertaId: String) {
        db.collection("alertas")
            .document(alertaId)
            .update("leida", true)
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al marcar alerta como leída", e)
            }
    }

    private fun showInfoDialog(alerta: Alerta) {
        AlertDialog.Builder(this)
            .setTitle(when (alerta.tipo) {
                TipoAlerta.DESBLOQUEO_APP -> "Desbloqueo de aplicación"
                TipoAlerta.TIEMPO_AGOTADO -> "Tiempo agotado"
                else -> "Información"
            })
            .setMessage(alerta.mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadAlertas()
    }
}
