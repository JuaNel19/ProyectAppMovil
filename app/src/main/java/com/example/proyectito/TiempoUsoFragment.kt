package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TiempoUsoFragment : Fragment() {
    private lateinit var tvTimeUsed: TextView
    private lateinit var tvTimeAllowed: TextView
    private lateinit var tvProgressText: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var btnEditTime: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tiempo_uso, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        loadTimeData()
    }

    private fun initializeViews(view: View) {
        tvTimeUsed = view.findViewById(R.id.tvTimeUsed)
        tvTimeAllowed = view.findViewById(R.id.tvTimeAllowed)
        tvProgressText = view.findViewById(R.id.tvProgressText)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        btnEditTime = view.findViewById(R.id.btnEditTime)
    }

    private fun setupClickListeners() {
        btnEditTime.setOnClickListener {
            startActivity(Intent(requireContext(), EditarTiempoActivity::class.java))
        }
    }

    private fun loadTimeData() {
        val childId = auth.currentUser?.uid ?: return

        db.collection("tiempo_uso")
            .document(childId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val tiempoUsado = document.getLong("tiempo_usado") ?: 0L
                    val tiempoPermitido = document.getLong("tiempo_permitido") ?: 4L

                    // Actualizar UI con los datos
                    updateUI(tiempoUsado, tiempoPermitido)
                } else {
                    // Si no existe el documento, crear uno con valores por defecto
                    val defaultData = hashMapOf(
                        "tiempo_usado" to 0L,
                        "tiempo_permitido" to 4L
                    )
                    db.collection("tiempo_uso")
                        .document(childId)
                        .set(defaultData)
                        .addOnSuccessListener {
                            updateUI(0L, 4L)
                        }
                }
            }
    }

    private fun updateUI(tiempoUsado: Long, tiempoPermitido: Long) {
        // Convertir tiempo a formato legible
        val tiempoUsadoFormateado = formatTime(tiempoUsado)
        val tiempoPermitidoFormateado = formatTime(tiempoPermitido)

        // Actualizar textos
        tvTimeUsed.text = "Tiempo usado hoy: $tiempoUsadoFormateado"
        tvTimeAllowed.text = "Tiempo permitido: $tiempoPermitidoFormateado"

        // Calcular y actualizar progreso
        val porcentaje = if (tiempoPermitido > 0) {
            ((tiempoUsado.toFloat() / tiempoPermitido.toFloat()) * 100).toInt()
        } else 0

        progressIndicator.progress = porcentaje.coerceIn(0, 100)
        tvProgressText.text = "$porcentaje% del tiempo permitido"
    }

    private fun formatTime(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
    }

    override fun onResume() {
        super.onResume()
        loadTimeData() // Recargar datos cuando el fragmento se reanuda
    }
}