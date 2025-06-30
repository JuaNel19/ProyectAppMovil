package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.proyectito.FCMUtils

class RoleSelectionActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val TAG = "RoleSelectionActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Verificar si hay una sesión activa
        if (auth.currentUser != null) {
            Log.d(TAG, "Sesión activa encontrada, verificando rol y actualizando token FCM")
            checkUserRoleAndNavigate()
        } else {
            Log.d(TAG, "No hay sesión activa, mostrando selección de rol")
            setupRoleSelection()
        }
    }

    private fun checkUserRoleAndNavigate() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "Verificando rol para usuario: $userId")

        // Actualizar token FCM antes de verificar el rol
        Log.d(TAG, "Actualizando token FCM")
        FCMUtils.updateFCMToken()

        db.collection("tutores")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Es un tutor, ir a MenuTutorActivity
                    Log.d(TAG, "Usuario encontrado en tutores, navegando a MenuTutorActivity")
                    startActivity(Intent(this, MenuTutorActivity::class.java))
                    finish()
                } else {
                    // Verificar si es un hijo
                    Log.d(TAG, "Usuario no encontrado en tutores, verificando en hijos")
                    db.collection("hijos")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { childDoc ->
                            if (childDoc.exists()) {
                                // Es un hijo, ir a PantallaHijoActivity
                                Log.d(TAG, "Usuario encontrado en hijos, navegando a PantallaHijoActivity")
                                startActivity(Intent(this, PantallaHijoActivity::class.java))
                                finish()
                            } else {
                                // No se encontró el rol, mostrar selección de rol
                                Log.e(TAG, "Usuario no encontrado en ninguna colección")
                                setupRoleSelection()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al verificar en hijos: ${e.message}")
                            setupRoleSelection()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al verificar en tutores: ${e.message}")
                setupRoleSelection()
            }
    }

    private fun setupRoleSelection() {
        Log.d(TAG, "Configurando selección de rol")
        val cardTutor = findViewById<MaterialCardView>(R.id.cardParent)
        val cardHijo = findViewById<MaterialCardView>(R.id.cardChild)

        cardTutor.setOnClickListener {
            Log.d(TAG, "Rol padre seleccionado")
            // Guardar el rol en las preferencias
            getSharedPreferences("app_preferences", MODE_PRIVATE)
                .edit()
                .putString("rol_usuario", "padre")
                .apply()

            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("role", "tutor")
            }
            startActivity(intent)
        }

        cardHijo.setOnClickListener {
            Log.d(TAG, "Rol hijo seleccionado")
            // Guardar el rol en las preferencias
            getSharedPreferences("app_preferences", MODE_PRIVATE)
                .edit()
                .putString("rol_usuario", "hijo")
                .apply()

            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("role", "hijo")
            }
            startActivity(intent)
        }
    }
} 