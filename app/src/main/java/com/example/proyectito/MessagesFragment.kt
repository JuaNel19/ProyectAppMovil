package com.example.proyectito

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MessagesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var messagesListener: ListenerRegistration? = null
    private var currentUserRole: String = ""
    private var currentUserId: String = ""
    private var targetUserId: String = ""
    private val TAG = "MessagesFragment"
    private lateinit var messageInputArea: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""
        
        initializeViews(view)
        setupRecyclerView()
        setupSendButton()
        
        // Determinar el rol del usuario actual
        determineUserRole()
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.rvMessages)
        etMessage = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)
        messageInputArea = view.findViewById(R.id.messageInputArea)
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true // Los mensajes nuevos aparecen abajo
        }
        recyclerView.adapter = messagesAdapter
    }

    private fun setupMessageListener() {
        // Obtener mensajes en tiempo real
        val chatId = getChatId()
        messagesListener = db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error al obtener mensajes", e)
                    return@addSnapshotListener
                }

                val messages = mutableListOf<Message>()
                snapshot?.documents?.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    message?.let {
                        messages.add(it.copy(id = doc.id))
                    }
                }

                // FILTRO: Solo mostrar mensajes del padre si el usuario es hijo
                if (currentUserRole == "hijo") {
                    val mensajesPadre = messages.filter { it.senderRole == "padre" }
                    messagesAdapter.submitList(mensajesPadre)
                } else {
                    messagesAdapter.submitList(messages)
                }

                // Scroll al último mensaje
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
    }

    private fun setupSendButton() {
        btnSend.setOnClickListener {
            val messageText = etMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                etMessage.text.clear()
            }
        }
    }

    private fun sendMessage(content: String) {
        val chatId = getChatId()
        val message = Message(
            senderId = currentUserId,
            senderName = getCurrentUserName(),
            receiverId = targetUserId,
            content = content,
            timestamp = Timestamp.now(),
            senderRole = currentUserRole,
            isRead = false,
            chatId = chatId
        )

        db.collection("messages")
            .add(message)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Mensaje enviado con ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al enviar mensaje", e)
                Toast.makeText(context, "Error al enviar mensaje", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getChatId(): String {
        // Crear un ID único para el chat entre padre e hijo
        val ids = listOf(currentUserId, targetUserId).sorted()
        return "${ids[0]}_${ids[1]}"
    }

    private fun getCurrentUserName(): String {
        // Obtener el nombre del usuario actual desde Firestore
        return when (currentUserRole) {
            "padre" -> "Papá"
            "hijo" -> "Hijo"
            else -> "Usuario"
        }
    }

    private fun determineUserRole() {
        // Buscar en qué colección está el usuario para determinar su rol
        db.collection("tutores")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    currentUserRole = "padre"
                    messagesAdapter.setCurrentUserRole("padre")
                    messageInputArea.visibility = View.VISIBLE
                    // Si es padre, obtener el ID del hijo
                    getChildId()
                } else {
                    // Si no es tutor, es hijo
                    currentUserRole = "hijo"
                    messagesAdapter.setCurrentUserRole("hijo")
                    messageInputArea.visibility = View.GONE
                    // Si es hijo, obtener el ID del padre
                    getParentId()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al determinar rol", e)
                currentUserRole = "hijo" // Por defecto
                messagesAdapter.setCurrentUserRole("hijo")
                messageInputArea.visibility = View.GONE
                getParentId()
            }
    }

    private fun getChildId() {
        db.collection("parent_child_relations")
            .whereEqualTo("parent_id", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    targetUserId = documents.documents[0].getString("child_id") ?: ""
                    Log.d(TAG, "ID del hijo: $targetUserId")
                    setupMessageListener() // Aquí se inicializa el listener
                }
            }
    }

    private fun getParentId() {
        db.collection("parent_child_relations")
            .whereEqualTo("child_id", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    targetUserId = documents.documents[0].getString("parent_id") ?: ""
                    Log.d(TAG, "ID del padre: $targetUserId")
                    setupMessageListener() // Aquí se inicializa el listener
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        messagesListener?.remove()
    }
} 