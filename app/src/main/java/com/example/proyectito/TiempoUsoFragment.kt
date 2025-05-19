package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TiempoUsoFragment : Fragment() {
    private lateinit var tvTimeUsed: TextView
    private lateinit var tvTimeAllowed: TextView
    private lateinit var tvProgressText: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var btnEditTime: MaterialButton
    private lateinit var tvChildName: TextView
    private lateinit var childSelector: AutoCompleteTextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var usageListener: ListenerRegistration? = null
    private var limitsListener: ListenerRegistration? = null
    private var currentChildId: String? = null
    private var childrenList: List<ChildInfo> = emptyList()

    data class ChildInfo(
        val id: String,
        val name: String
    )

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
    }

    private fun initializeViews(view: View) {
        tvTimeUsed = view.findViewById(R.id.tvTimeUsed)
        tvTimeAllowed = view.findViewById(R.id.tvTimeAllowed)
        tvProgressText = view.findViewById(R.id.tvProgressText)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        btnEditTime = view.findViewById(R.id.btnEditTime)
        tvChildName = view.findViewById(R.id.tvChildName)
        childSelector = view.findViewById(R.id.childSelector)
    }

    private fun setupClickListeners() {
        btnEditTime.setOnClickListener {
            currentChildId?.let { childId ->
                val intent = Intent(requireContext(), EditarTiempoActivity::class.java).apply {
                    putExtra("childId", childId)
                }
                startActivity(intent)
            } ?: run {
                Toast.makeText(requireContext(), "Selecciona un hijo primero", Toast.LENGTH_SHORT).show()
            }
        }

        childSelector.setOnItemClickListener { _, _, position, _ ->
            val selectedChild = childrenList[position]
            currentChildId = selectedChild.id
            setupFirestoreListeners(selectedChild.id)
        }
    }

    fun updateChildrenList(childrenIds: List<String>) {
        if (childrenIds.isEmpty()) {
            // No hay hijos asociados
            tvChildName.text = "No hay hijos asociados"
            tvTimeUsed.text = "Tiempo usado hoy: 0h 0m"
            tvTimeAllowed.text = "Tiempo permitido: 0h 0m"
            progressIndicator.progress = 0
            tvProgressText.text = "0% del tiempo permitido"
            childSelector.isEnabled = false
            return
        }

        // Obtener información de los hijos
        childrenList = emptyList()
        var loadedCount = 0
        val tempList = mutableListOf<ChildInfo>()

        childrenIds.forEach { childId ->
            db.collection("hijos").document(childId)
                .get()
                .addOnSuccessListener { document ->
                    val name = document.getString("nombre") ?: "Hijo"
                    tempList.add(ChildInfo(childId, name))
                    loadedCount++

                    if (loadedCount == childrenIds.size) {
                        childrenList = tempList.sortedBy { it.name }
                        updateChildSelector()
                    }
                }
        }
    }

    private fun updateChildSelector() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            childrenList.map { it.name }
        )
        childSelector.setAdapter(adapter)
        childSelector.isEnabled = true

        // Seleccionar el primer hijo por defecto
        if (childrenList.isNotEmpty()) {
            currentChildId = childrenList.first().id
            setupFirestoreListeners(currentChildId!!)
        }
    }

    private fun setupFirestoreListeners(childId: String) {
        // Limpiar listeners anteriores
        usageListener?.remove()
        limitsListener?.remove()

        // Obtener nombre del hijo
        val childName = childrenList.find { it.id == childId }?.name ?: "Hijo"
        tvChildName.text = "Tiempo de uso de $childName"

        // Listener para uso diario
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        usageListener = db.collection("uso_diario")
            .document(childId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(requireContext(), "Error al obtener datos de uso: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val usedTime = doc.getLong("tiempo_usado") ?: 0L
                    updateUsageTime(usedTime)
                }
            }

        // Listener para límites
        limitsListener = db.collection("limites_apps")
            .document(childId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(requireContext(), "Error al obtener límites: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val dailyLimit = doc.getLong("limite_diario") ?: 0L
                    updateLimits(dailyLimit)
                }
            }
    }

    private fun updateUsageTime(usedTimeMillis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(usedTimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(usedTimeMillis) % 60
        tvTimeUsed.text = "Tiempo usado hoy: ${hours}h ${minutes}m"
    }

    private fun updateLimits(dailyLimitMillis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(dailyLimitMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(dailyLimitMillis) % 60
        tvTimeAllowed.text = "Tiempo permitido: ${hours}h ${minutes}m"

        // Obtener el tiempo usado actual
        db.collection("uso_diario")
            .document(currentChildId ?: return)
            .get()
            .addOnSuccessListener { doc ->
                val usedTime = doc.getLong("tiempo_usado") ?: 0L
                updateProgress(usedTime, dailyLimitMillis)
            }
    }

    private fun updateProgress(usedTimeMillis: Long, totalTimeMillis: Long) {
        val progress = if (totalTimeMillis > 0) {
            ((usedTimeMillis.toFloat() / totalTimeMillis.toFloat()) * 100).toInt()
        } else 0

        progressIndicator.progress = progress.coerceIn(0, 100)
        tvProgressText.text = "$progress% del tiempo permitido"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usageListener?.remove()
        limitsListener?.remove()
    }
}