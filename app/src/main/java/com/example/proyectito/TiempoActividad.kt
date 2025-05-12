package com.example.proyectito

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.w3c.dom.Text
import java.text.SimpleDateFormat
import java.util.*

class TiempoActividad : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var startTimeMillis: Long = 0
    private var endTimeMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tiempo_actividad)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val valorHoraInicio = findViewById<TextView>(R.id.valorHoraInicio)
        val valorHoraFin = findViewById<TextView>(R.id.valorHoraFin)
        val botonDefinirTiempo = findViewById<Button>(R.id.botonDefinirTiempo)
        val textoAhoraNo = findViewById<TextView>(R.id.textoAhoraNo)

        // Set default times
        setDefaultTimes(valorHoraInicio, valorHoraFin)

        // Click listeners for time pickers
        valorHoraInicio.setOnClickListener {
            showTimePickerDialog(true, valorHoraInicio, valorHoraFin)
        }

        valorHoraFin.setOnClickListener {
            showTimePickerDialog(false, valorHoraInicio, valorHoraFin)
        }

        // Click listener for the save button
        botonDefinirTiempo.setOnClickListener {
            saveTiempoActividad()
        }

        // Click listener for "Ahora no"
        textoAhoraNo.setOnClickListener {
            // Navigate back or close activity, for example:
            finish()
        }
    }

    private fun setDefaultTimes(valorHoraInicio: TextView, valorHoraFin: TextView) {
        val calendar = Calendar.getInstance()
        // Default start time: 7:00 AM
        calendar.set(Calendar.HOUR_OF_DAY, 7)
        calendar.set(Calendar.MINUTE, 0)
        startTimeMillis = calendar.timeInMillis
        valorHoraInicio.text = formatTime(startTimeMillis)

        // Default end time: 5:00 PM
        calendar.set(Calendar.HOUR_OF_DAY, 17)
        calendar.set(Calendar.MINUTE, 0)
        endTimeMillis = calendar.timeInMillis
        valorHoraFin.text = formatTime(endTimeMillis)
    }

    private fun showTimePickerDialog(isStartTime: Boolean,valorHoraInicio: TextView, valorHoraFin: TextView) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedCalendar.set(Calendar.MINUTE, minute)
                val selectedTimeMillis = selectedCalendar.timeInMillis

                if (isStartTime) {
                    startTimeMillis = selectedTimeMillis
                    valorHoraInicio.text = formatTime(startTimeMillis)
                } else {
                    endTimeMillis = selectedTimeMillis
                    valorHoraFin.text = formatTime(endTimeMillis)
                }
            },
            currentHour,
            currentMinute,
            false // Use false for 24-hour format if desired, true for AM/PM
        )
        timePickerDialog.show()
    }

    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    private fun saveTiempoActividad() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        if (startTimeMillis >= endTimeMillis) {
            Toast.makeText(this, "La hora de inicio debe ser anterior a la hora de finalización.", Toast.LENGTH_LONG).show()
            return
        }

        val tiempoData = hashMapOf(
            "userId" to currentUser.uid,
            "horaInicio" to startTimeMillis,
            "horaFin" to endTimeMillis,
            "timestamp" to System.currentTimeMillis() // Optional: for tracking when it was set
        )

        db.collection("tiemposActividad")
            .document(currentUser.uid) // Use user ID as document ID for easy retrieval
            .set(tiempoData)
            .addOnSuccessListener {
                Toast.makeText(this, "Tiempo de actividad guardado.", Toast.LENGTH_SHORT).show()
                // Optionally, navigate to another screen or give more feedback
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}