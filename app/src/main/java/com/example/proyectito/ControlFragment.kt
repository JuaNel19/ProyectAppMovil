package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ControlFragment : Fragment() {
    private lateinit var childSelector: AutoCompleteTextView
    private lateinit var tilChildSelector: TextInputLayout
    private lateinit var cardBloquearApps: MaterialCardView
    private lateinit var cardLimitarTiempo: MaterialCardView
    private lateinit var cardAppsMasUsadas: MaterialCardView
    private lateinit var cardAlertas: MaterialCardView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
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
        return inflater.inflate(R.layout.fragment_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        loadChildren()
    }

    private fun initializeViews(view: View) {
        childSelector = view.findViewById(R.id.childSelector)
        tilChildSelector = view.findViewById(R.id.tilChildSelector)
        cardBloquearApps = view.findViewById(R.id.cardBloquearApps)
        cardLimitarTiempo = view.findViewById(R.id.cardLimitarTiempo)
        cardAppsMasUsadas = view.findViewById(R.id.cardAppsMasUsadas)
        cardAlertas = view.findViewById(R.id.cardAlertas)
    }

    private fun setupClickListeners() {
        childSelector.setOnItemClickListener { _, _, position, _ ->
            val selectedChild = childrenList[position]
            currentChildId = selectedChild.id
            updateCardsState(true)
        }

        cardBloquearApps.setOnClickListener {
            currentChildId?.let { childId ->
                val intent = Intent(requireContext(), BloquearAppsActivity::class.java).apply {
                    putExtra("childId", childId)
                }
                startActivity(intent)
            } ?: showSelectChildMessage()
        }

        cardLimitarTiempo.setOnClickListener {
            currentChildId?.let { childId ->
                val intent = Intent(requireContext(), LimitarTiempoActivity::class.java).apply {
                    putExtra("childId", childId)
                }
                startActivity(intent)
            } ?: showSelectChildMessage()
        }

        cardAppsMasUsadas.setOnClickListener {
            currentChildId?.let { childId ->
                val intent = Intent(requireContext(), AppsMasUsadasActivity::class.java).apply {
                    putExtra("childId", childId)
                }
                startActivity(intent)
            } ?: showSelectChildMessage()
        }

        cardAlertas.setOnClickListener {
            currentChildId?.let { childId ->
                val intent = Intent(requireContext(), AlertasActivity::class.java).apply {
                    putExtra("childId", childId)
                }
                startActivity(intent)
            } ?: showSelectChildMessage()
        }
    }

    private fun loadChildren() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("usuarios")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val hijosAsociados = document.get("hijos_asociados") as? List<String> ?: emptyList()
                if (hijosAsociados.isEmpty()) {
                    updateCardsState(false)
                    return@addOnSuccessListener
                }

                loadChildrenInfo(hijosAsociados)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al cargar hijos: ${e.message}", Toast.LENGTH_SHORT).show()
                updateCardsState(false)
            }
    }

    private fun loadChildrenInfo(childrenIds: List<String>) {
        var loadedCount = 0
        val tempList = mutableListOf<ChildInfo>()

        childrenIds.forEach { childId ->
            db.collection("hijos")
                .document(childId)
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
            updateCardsState(true)
        }
    }

    private fun updateCardsState(enabled: Boolean) {
        cardBloquearApps.isEnabled = enabled
        cardLimitarTiempo.isEnabled = enabled
        cardAppsMasUsadas.isEnabled = enabled
        cardAlertas.isEnabled = enabled

        // Actualizar la apariencia visual de las cards
        val alpha = if (enabled) 1.0f else 0.5f
        cardBloquearApps.alpha = alpha
        cardLimitarTiempo.alpha = alpha
        cardAppsMasUsadas.alpha = alpha
        cardAlertas.alpha = alpha
    }

    private fun showSelectChildMessage() {
        Toast.makeText(requireContext(), "Por favor selecciona un hijo primero", Toast.LENGTH_SHORT).show()
    }
}