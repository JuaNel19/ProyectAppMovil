package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.Timestamp
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
    private lateinit var etMessageToChild: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var rvRecentMessages: RecyclerView
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var sentMessageArea: View
    private lateinit var tvSentMessage: TextView
    private lateinit var btnDeleteMessage: ImageButton

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
    private var esHijo: Boolean = false

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
        detectarRolYConfigurarUI(view)
    }

    override fun onResume() {
        super.onResume()
        loadChildrenForParent()
        currentChildId?.let { childId ->
            syncWithFirestore(childId)
            lastUpdateTime = System.currentTimeMillis()
            handler.post(timeUpdateRunnable)
            handler.post(syncRunnable)
            setupSingleMessageListener()
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
        etMessageToChild = view.findViewById(R.id.etMessageToChild)
        btnSendMessage = view.findViewById(R.id.btnSendMessage)
        btnSendMessage.setImageResource(android.R.drawable.ic_menu_send)
        rvRecentMessages = view.findViewById(R.id.rvRecentMessages)
        sentMessageArea = view.findViewById(R.id.sentMessageArea)
        tvSentMessage = view.findViewById(R.id.tvSentMessage)
        btnDeleteMessage = view.findViewById(R.id.btnDeleteMessage)
        val messageArea = view.findViewById<View>(R.id.messageArea)
        Log.d("TiempoUsoFragment", "Message area initialized - visibility: ${messageArea?.visibility}")
        setupMessagesRecyclerView()
    }

    private fun setupSingleMessageListener() {
        val parentId = auth.currentUser?.uid ?: return
        val childId = currentChildId ?: return
        val chatId = getChatId(parentId, childId)
        db.collection("last_messages")
            .document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    sentMessageArea.visibility = View.GONE
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val content = snapshot.getString("content") ?: ""
                    if (content.isNotBlank()) {
                        tvSentMessage.text = content
                        sentMessageArea.visibility = View.VISIBLE
                    } else {
                        sentMessageArea.visibility = View.GONE
                    }
                } else {
                    sentMessageArea.visibility = View.GONE
                }
            }
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
            setupSingleMessageListener()
        }
        btnSendMessage.setOnClickListener {
            sendMessageToChild()
        }
        btnDeleteMessage.setOnClickListener {
            val parentId = auth.currentUser?.uid ?: return@setOnClickListener
            val childId = currentChildId ?: return@setOnClickListener
            val chatId = getChatId(parentId, childId)
            db.collection("last_messages").document(chatId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Mensaje eliminado", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error al eliminar mensaje: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        etMessageToChild.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                etMessageToChild.isEnabled = true
                etMessageToChild.isFocusable = true
                etMessageToChild.isFocusableInTouchMode = true
            }
        }
        etMessageToChild.setOnClickListener {
            Log.d("TiempoUsoFragment", "EditText clicked")
            etMessageToChild.requestFocus()
        }
        Log.d("TiempoUsoFragment", "EditText initialized - enabled: ${etMessageToChild.isEnabled}, focusable: ${etMessageToChild.isFocusable}")
    }

    private fun detectarRolYConfigurarUI(view: View) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val messageArea = view.findViewById<View>(R.id.messageArea)

        db.collection("tutores").document(userId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                // Es padre
                esHijo = false
                messageArea.visibility = View.VISIBLE
            } else {
                db.collection("hijos").document(userId).get().addOnSuccessListener { childDoc ->
                    if (childDoc.exists()) {
                        // Es hijo
                        esHijo = true
                        messageArea.visibility = View.GONE
                    }
                }
            }
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
            // Buscar el hijo previamente seleccionado
            val selectedIndex = childrenList.indexOfFirst { it.id == currentChildId }
            val indexToSelect = if (selectedIndex != -1) selectedIndex else 0
            childSelector.setText(childrenList[indexToSelect].name, false)
            currentChildId = childrenList[indexToSelect].id
            loadChildData(currentChildId!!)
            setupSingleMessageListener()
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

    private fun setupMessagesRecyclerView() {
        messagesAdapter = MessagesAdapter()
        messagesAdapter.setCurrentUserRole("padre")
        rvRecentMessages.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        rvRecentMessages.adapter = messagesAdapter
    }

    private fun sendMessageToChild() {
        val messageText = etMessageToChild.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(requireContext(), "Escribe un mensaje", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentChildId == null) {
            Toast.makeText(requireContext(), "Selecciona un hijo primero", Toast.LENGTH_SHORT).show()
            return
        }
        val parentId = auth.currentUser?.uid ?: return
        val chatId = getChatId(parentId, currentChildId!!)
        val message = hashMapOf(
            "senderId" to parentId,
            "senderName" to "Papá",
            "receiverId" to currentChildId!!,
            "content" to messageText,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "senderRole" to "padre",
            "isRead" to false,
            "chatId" to chatId
        )
        db.collection("last_messages")
            .document(chatId)
            .set(message)
            .addOnSuccessListener {
                etMessageToChild.text.clear()
                Toast.makeText(requireContext(), "Mensaje enviado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al enviar mensaje: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getChatId(parentId: String, childId: String): String {
        val ids = listOf(parentId, childId).sorted()
        return "${ids[0]}_${ids[1]}"
    }

    private fun loadRecentMessages() {
        if (currentChildId == null) return

        val parentId = auth.currentUser?.uid ?: return
        val chatId = getChatId(parentId, currentChildId!!)

        db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp")
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                val messages = mutableListOf<Message>()
                documents.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    message?.let {
                        messages.add(it.copy(id = doc.id))
                    }
                }
                if (esHijo) {
                    // Solo mostrar mensajes enviados por el padre
                    val mensajesPadre = messages.filter { it.senderRole == "padre" }
                    messagesAdapter.submitList(mensajesPadre)
                } else {
                    messagesAdapter.submitList(messages)
                }
            }
            .addOnFailureListener { e ->
                // Silenciar error para no molestar al usuario
            }
    }

    private fun loadChildrenForParent() {
        val parentId = auth.currentUser?.uid ?: return
        db.collection("parent_child_relations")
            .whereEqualTo("parent_id", parentId)
            .get()
            .addOnSuccessListener { documents ->
                val childrenIds = documents.mapNotNull { it.getString("child_id") }
                updateChildrenList(childrenIds)
            }
    }

}