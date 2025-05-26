package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var tvUnlockCode: TextView
    private lateinit var btnGenerateNewCode: MaterialButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentChildId: String? = null
    private var childrenList: List<ChildInfo> = emptyList()
    private var currentUsedTime: Long = 0L
    private var currentLimit: Long = 0L
    private var currentUnlockCode: String? = null
    private var lastUpdateTime: Long = 0L
    private var lastDay: Int = -1
    private val handler = Handler(Looper.getMainLooper())
    private val UI_UPDATE_INTERVAL = 1000L // 1 segundo
    private val SYNC_INTERVAL = 5 * 60 * 1000L // 5 minutos

    data class ChildInfo(
        val id: String,
        val name: String
    )

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            if (currentChildId != null) {
                checkDayChange()
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - lastUpdateTime
                currentUsedTime += elapsedTime
                lastUpdateTime = currentTime
                updateUI(currentUsedTime, currentLimit, currentUnlockCode)
            }
            handler.postDelayed(this, UI_UPDATE_INTERVAL)
        }
    }

    private val syncRunnable = object : Runnable {
        override fun run() {
            currentChildId?.let { childId ->
                syncWithFirestore(childId)
            }
            handler.postDelayed(this, SYNC_INTERVAL)
        }
    }

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

    override fun onResume() {
        super.onResume()
        currentChildId?.let { childId ->
            syncWithFirestore(childId)
            lastUpdateTime = System.currentTimeMillis()
            handler.post(timeUpdateRunnable)
            handler.post(syncRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(timeUpdateRunnable)
        handler.removeCallbacks(syncRunnable)
        updateFirestoreTime()
    }

    private fun checkDayChange() {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)

        if (lastDay != currentDay) {
            currentUsedTime = 0L
            lastUpdateTime = System.currentTimeMillis()
            updateFirestoreTime()
        }
        lastDay = currentDay
    }

    private fun syncWithFirestore(childId: String) {
        db.collection("control_dispositivo_hijo")
            .document(childId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentUsedTime = document.getLong("tiempo_usado") ?: 0L
                    currentLimit = document.getLong("limite_diario") ?: TimeUnit.HOURS.toMillis(2)
                    currentUnlockCode = document.getString("codigo_desbloqueo")
                    lastUpdateTime = System.currentTimeMillis()
                    updateUI(currentUsedTime, currentLimit, currentUnlockCode)
                } else {
                    createDefaultDocument(childId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al sincronizar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateFirestoreTime() {
        currentChildId?.let { childId ->
            db.collection("control_dispositivo_hijo")
                .document(childId)
                .update(
                    "tiempo_usado", currentUsedTime,
                    "ultima_actualizacion", System.currentTimeMillis()
                )
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error al actualizar tiempo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun initializeViews(view: View) {
        tvTimeUsed = view.findViewById(R.id.tvTimeUsed)
        tvTimeAllowed = view.findViewById(R.id.tvTimeAllowed)
        tvProgressText = view.findViewById(R.id.tvProgressText)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        btnEditTime = view.findViewById(R.id.btnEditTime)
        tvChildName = view.findViewById(R.id.tvChildName)
        childSelector = view.findViewById(R.id.childSelector)
        tvUnlockCode = view.findViewById(R.id.tvUnlockCode)
        btnGenerateNewCode = view.findViewById(R.id.btnGenerateNewCode)
    }

    private fun setupClickListeners() {
        btnEditTime.setOnClickListener {
            currentChildId?.let { childId ->
                val intent = Intent(requireContext(), TiempoActividad::class.java).apply {
                    putExtra("childId", childId)
                }
                startActivity(intent)
            } ?: run {
                Toast.makeText(requireContext(), "Selecciona un hijo primero", Toast.LENGTH_SHORT).show()
            }
        }

        btnGenerateNewCode.setOnClickListener {
            currentChildId?.let { childId ->
                generateNewUnlockCode(childId)
            } ?: run {
                Toast.makeText(requireContext(), "Selecciona un hijo primero", Toast.LENGTH_SHORT).show()
            }
        }

        childSelector.setOnItemClickListener { _, _, position, _ ->
            val selectedChild = childrenList[position]
            currentChildId = selectedChild.id
            loadChildData(selectedChild.id)
        }
    }

    private fun loadChildData(childId: String) {
        syncWithFirestore(childId)
    }

    private fun createDefaultDocument(childId: String) {
        val newCode = generarCodigoDesbloqueo()
        val parentId = auth.currentUser?.uid ?: return

        val defaultData = hashMapOf(
            "tiempo_usado" to 0L,
            "limite_diario" to TimeUnit.HOURS.toMillis(2),
            "parentId" to parentId,
            "childId" to childId,
            "ultima_actualizacion" to System.currentTimeMillis(),
            "codigo_desbloqueo" to newCode,
            "tiempo_gracia_activo" to false
        )

        db.collection("control_dispositivo_hijo")
            .document(childId)
            .set(defaultData)
            .addOnSuccessListener {
                currentUsedTime = 0L
                currentLimit = TimeUnit.HOURS.toMillis(2)
                currentUnlockCode = newCode
                lastUpdateTime = System.currentTimeMillis()
                updateUI(currentUsedTime, currentLimit, currentUnlockCode)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al crear documento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateNewUnlockCode(childId: String) {
        val newCode = generarCodigoDesbloqueo()
        db.collection("control_dispositivo_hijo")
            .document(childId)
            .update("codigo_desbloqueo", newCode)
            .addOnSuccessListener {
                currentUnlockCode = newCode
                updateUI(currentUsedTime, currentLimit, newCode)
                Toast.makeText(requireContext(), "Nuevo código generado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al generar código: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generarCodigoDesbloqueo(): String {
        return (100000..999999).random().toString()
    }

    fun updateChildrenList(childrenIds: List<String>) {
        if (childrenIds.isEmpty()) {
            handleNoChildren()
            return
        }

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

    private fun handleNoChildren() {
        tvChildName.text = "No hay hijos asociados"
        tvTimeUsed.text = "Tiempo usado hoy: 0h 0m"
        tvTimeAllowed.text = "Tiempo permitido: 0h 0m"
        progressIndicator.progress = 0
        tvProgressText.text = "0% del tiempo permitido"
        tvUnlockCode.text = "No hay código generado"
        childSelector.isEnabled = false
    }

    private fun updateChildSelector() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            childrenList.map { it.name }
        )
        childSelector.setAdapter(adapter)
        childSelector.isEnabled = true

        if (childrenList.isNotEmpty()) {
            childSelector.setText(childrenList.first().name, false)
            currentChildId = childrenList.first().id
            loadChildData(currentChildId!!)
        }
    }

    private fun updateUI(usedTime: Long, limit: Long, unlockCode: String?) {
        if (currentChildId == null) {
            tvTimeUsed.text = "Selecciona un hijo"
            tvTimeAllowed.text = "Tiempo permitido: --"
            progressIndicator.progress = 0
            tvProgressText.text = "0% del tiempo permitido"
            tvUnlockCode.text = "No hay código generado"
            return
        }

        val hoursUsed = TimeUnit.MILLISECONDS.toHours(usedTime)
        val minutesUsed = TimeUnit.MILLISECONDS.toMinutes(usedTime) % 60
        tvTimeUsed.text = "Tiempo usado hoy: ${hoursUsed}h ${minutesUsed}m"

        val hoursAllowed = TimeUnit.MILLISECONDS.toHours(limit)
        val minutesAllowed = TimeUnit.MILLISECONDS.toMinutes(limit) % 60
        tvTimeAllowed.text = "Tiempo permitido: ${hoursAllowed}h ${minutesAllowed}m"

        val progress = if (limit > 0) {
            ((usedTime.toFloat() / limit.toFloat()) * 100).toInt()
        } else {
            0
        }
        progressIndicator.progress = progress.coerceIn(0, 100)
        tvProgressText.text = "$progress% del tiempo permitido"

        tvUnlockCode.text = unlockCode ?: "No hay código generado"
    }
}