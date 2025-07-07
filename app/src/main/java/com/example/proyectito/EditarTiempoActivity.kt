package com.example.proyectito

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class EditarTiempoActivity : AppCompatActivity() {
    private lateinit var tvChildName: TextView
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var childId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_tiempo)

        childId = intent.getStringExtra("childId")
        if (childId == null) {
            Toast.makeText(this, "Error: ID de hijo no proporcionado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        loadCurrentLimits()
        setupClickListeners()
    }

    private fun initializeViews() {
        tvChildName = findViewById(R.id.tvChildName)
        etHours = findViewById(R.id.etHours)
        etMinutes = findViewById(R.id.etMinutes)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // Obtener nombre del hijo
        db.collection("hijos").document(childId!!)
            .get()
            .addOnSuccessListener { document ->
                val childName = document.getString("nombre") ?: "Hijo"
                tvChildName.text = "Editar tiempo para $childName"
            }
    }

    private fun loadCurrentLimits() {
        Log.d("EditarTiempo", "Cargando límites para hijo: $childId")
        db.collection("limite_dispositivo")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("EditarTiempo", "Búsqueda completada. Documentos encontrados: ${documents.size()}")
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    val dailyLimit = document.getLong("limite_diario") ?: 0L
                    Log.d("EditarTiempo", "Límite encontrado: $dailyLimit ms")
                    val hours = TimeUnit.MILLISECONDS.toHours(dailyLimit)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(dailyLimit) % 60

                    etHours.setText(hours.toString())
                    etMinutes.setText(minutes.toString())
                } else {
                    Log.d("EditarTiempo", "No se encontraron límites para este hijo")
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditarTiempo", "Error al cargar límites", e)
                Toast.makeText(this, "Error al cargar límites: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveNewLimits()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveNewLimits() {
        val hoursStr = etHours.text.toString()
        val minutesStr = etMinutes.text.toString()

        if (hoursStr.isEmpty() && minutesStr.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa al menos horas o minutos", Toast.LENGTH_SHORT).show()
            return
        }

        val hours = hoursStr.toLongOrNull() ?: 0L
        val minutes = minutesStr.toLongOrNull() ?: 0L

        if (hours == 0L && minutes == 0L) {
            Toast.makeText(this, "El tiempo no puede ser cero", Toast.LENGTH_SHORT).show()
            return
        }

        val totalMillis = TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes)
        Log.d("EditarTiempo", "Intentando guardar límite: $totalMillis ms para hijo: $childId")

        // Buscar si ya existe un límite para este hijo
        db.collection("limite_dispositivo")
            .whereEqualTo("childId", childId)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("EditarTiempo", "Búsqueda completada. Documentos encontrados: ${documents.size()}")

                if (documents.isEmpty) {
                    Log.d("EditarTiempo", "Creando nuevo documento de límite")
                    // Crear nuevo documento
                    val newLimit = hashMapOf(
                        "limite_diario" to totalMillis,
                        "parentId" to auth.currentUser?.uid,
                        "childId" to childId,
                        "ultima_actualizacion" to System.currentTimeMillis()
                    )

                    db.collection("limite_dispositivo").add(newLimit)
                        .addOnSuccessListener { documentReference ->
                            Log.d("EditarTiempo", "Nuevo límite creado con ID: ${documentReference.id}")
                            Toast.makeText(this, "Límite de tiempo actualizado", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("EditarTiempo", "Error al crear nuevo límite", e)
                            Toast.makeText(this, "Error al actualizar límite: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    val docRef = documents.documents[0].reference
                    Log.d("EditarTiempo", "Actualizando documento existente: ${docRef.id}")

                    // Actualización directa
                    val updates = mapOf(
                        "limite_diario" to totalMillis,
                        "ultima_actualizacion" to System.currentTimeMillis()
                    )

                    docRef.update(updates)
                        .addOnSuccessListener {
                            Log.d("EditarTiempo", "Límite actualizado exitosamente")
                            Toast.makeText(this, "Límite de tiempo actualizado", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("EditarTiempo", "Error al actualizar límite existente", e)
                            Toast.makeText(this, "Error al actualizar límite: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditarTiempo", "Error al buscar límite existente", e)
                Toast.makeText(this, "Error al buscar límite existente: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}