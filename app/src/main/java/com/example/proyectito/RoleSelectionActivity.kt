package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RoleSelectionActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Verificar si hay una sesión activa
        if (auth.currentUser != null) {
            checkUserRoleAndNavigate()
        } else {
            setupRoleSelection()
        }
    }

    private fun checkUserRoleAndNavigate() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("tutores")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Es un tutor, ir a MenuTutorActivity
                    startActivity(Intent(this, MenuTutorActivity::class.java))
                    finish()
                } else {
                    // Verificar si es un hijo
                    db.collection("hijos")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { childDoc ->
                            if (childDoc.exists()) {
                                // Es un hijo, ir a PantallaHijoActivity
                                startActivity(Intent(this, PantallaHijoActivity::class.java))
                                finish()
                            } else {
                                // No se encontró el rol, mostrar selección de rol
                                setupRoleSelection()
                            }
                        }
                }
            }
    }

    private fun setupRoleSelection() {
        val cardTutor = findViewById<MaterialCardView>(R.id.cardParent)
        val cardHijo = findViewById<MaterialCardView>(R.id.cardChild)

        cardTutor.setOnClickListener {
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