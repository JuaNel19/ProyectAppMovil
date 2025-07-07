package com.example.proyectito

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TiempoActividad : AppCompatActivity() {
    private lateinit var tvCurrentLimit: TextView
    private lateinit var btnSetTime: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var tvChildName: TextView
    private lateinit var toolbar: Toolbar

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedTimeMillis: Long = 0L
    private var childId: String? = null
    private var childName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tiempo)

        initializeViews()
        setupToolbar()
        setupClickListeners()
        loadChildData()
    }

    private fun initializeViews() {
        tvCurrentLimit = findViewById(R.id.tvCurrentLimit)
        btnSetTime = findViewById(R.id.btnSetTime)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        tvChildName = findViewById(R.id.tvChildName)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupClickListeners() {
        btnSetTime.setOnClickListener {
            showTimePickerDialog()
        }

        btnSave.setOnClickListener {
            saveTimeLimit()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadChildData() {
        childId = intent.getStringExtra("childId")
        if (childId == null) {
            Toast.makeText(this, "Error: ID de hijo no proporcionado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Obtener nombre del hijo
        db.collection("hijos").document(childId!!)
            .get()
            .addOnSuccessListener { document ->
                childName = document.getString("nombre") ?: "Hijo"
                tvChildName.text = "Configurar tiempo para $childName"
                loadCurrentLimit()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadCurrentLimit() {
        db.collection("control_dispositivo_hijo")
            .document(childId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentLimit = document.getLong("limite_diario") ?: TimeUnit.HOURS.toMillis(2)
                    selectedTimeMillis = currentLimit
                    updateTimeDisplay(currentLimit)
                } else {
                    // Si no existe, usar valor por defecto
                    selectedTimeMillis = TimeUnit.HOURS.toMillis(2)
                    updateTimeDisplay(selectedTimeMillis)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar límite: ${e.message}", Toast.LENGTH_SHORT).show()
                // Usar valor por defecto en caso de error
                selectedTimeMillis = TimeUnit.HOURS.toMillis(2)
                updateTimeDisplay(selectedTimeMillis)
            }
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val currentHours = TimeUnit.MILLISECONDS.toHours(selectedTimeMillis).toInt()
        val currentMinutes = (TimeUnit.MILLISECONDS.toMinutes(selectedTimeMillis) % 60).toInt()

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedTimeMillis = TimeUnit.HOURS.toMillis(hourOfDay.toLong()) +
                        TimeUnit.MINUTES.toMillis(minute.toLong())
                updateTimeDisplay(selectedTimeMillis)
            },
            currentHours,
            currentMinutes,
            true
        ).show()
    }

    private fun updateTimeDisplay(timeMillis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(timeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMillis) % 60
        tvCurrentLimit.text = "Tiempo seleccionado: ${hours}h ${minutes}m"
    }

    private fun saveTimeLimit() {
        if (childId == null) return

        val updates = hashMapOf<String, Any>(
            "limite_diario" to selectedTimeMillis,
            "ultima_actualizacion" to System.currentTimeMillis(),
            "tiempo_usado" to 0L,
            "tiempo_gracia_activo" to false
        )

        db.collection("control_dispositivo_hijo")
            .document(childId!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Límite de tiempo actualizado", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                // Si el documento no existe, crearlo
                if (e.message?.contains("No document to update") == true) {
                    val newDocument = hashMapOf(
                        "parentId" to (auth.currentUser?.uid ?: return@addOnFailureListener),
                        "childId" to childId,
                        "limite_diario" to selectedTimeMillis,
                        "tiempo_usado" to 0L,
                        "ultima_actualizacion" to System.currentTimeMillis(),
                        "tiempo_gracia_activo" to false,
                        "codigo_desbloqueo" to (100000..999999).random().toString()
                    )

                    db.collection("control_dispositivo_hijo")
                        .document(childId!!)
                        .set(newDocument)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Límite de tiempo guardado", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e2 ->
                            Toast.makeText(this, "Error al guardar: ${e2.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Error al actualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}