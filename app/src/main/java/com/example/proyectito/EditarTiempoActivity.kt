package com.example.proyectito

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class EditarTiempoActivity : AppCompatActivity() {
    private lateinit var tvChildName: TextView
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private val db = FirebaseFirestore.getInstance()
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
        db.collection("limites_apps")
            .document(childId!!)
            .get()
            .addOnSuccessListener { document ->
                val dailyLimit = document.getLong("limite_diario") ?: 0L
                val hours = TimeUnit.MILLISECONDS.toHours(dailyLimit)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(dailyLimit) % 60

                etHours.setText(hours.toString())
                etMinutes.setText(minutes.toString())
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

        db.collection("limites_apps")
            .document(childId!!)
            .update("limite_diario", totalMillis)
            .addOnSuccessListener {
                Toast.makeText(this, "Límite de tiempo actualizado", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al actualizar límite: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}