package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val loginButton = findViewById<Button>(R.id.loginButton)
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val registerTextView = findViewById<TextView>(R.id.registerTextView)

        // Set up login button click listener
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        // Set up register text click listener
        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Verificar si el email está verificado
                    val user = auth.currentUser
                    if (user?.isEmailVerified == true) {
                        // Email verificado, verificar el rol en la colección correcta
                        verifyUserRole(user.uid)
                    } else {
                        // Email no verificado
                        Toast.makeText(this, "Por favor verifica tu email antes de iniciar sesión", Toast.LENGTH_LONG).show()
                        // Enviar email de verificación nuevamente
                        user?.sendEmailVerification()
                            ?.addOnCompleteListener { verificationTask ->
                                if (verificationTask.isSuccessful) {
                                    Toast.makeText(this, "Se ha enviado un nuevo email de verificación", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "Error al enviar email de verificación: ${verificationTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        // Cerrar sesión
                        auth.signOut()
                    }
                } else {
                    // Login failed
                    handleAuthError(task.exception)
                }
            }
    }

    private fun verifyUserRole(userId: String) {
        // Primero buscar en la colección de tutores
        db.collection("tutores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Es un tutor
                    android.util.Log.d("LoginActivity", "Usuario encontrado en tutores")
                    navigateToRole("padre")
                } else {
                    // Si no es tutor, buscar en la colección de hijos
                    android.util.Log.d("LoginActivity", "Usuario no encontrado en tutores, buscando en hijos")
                    db.collection("hijos").document(userId)
                        .get()
                        .addOnSuccessListener { childDocument ->
                            if (childDocument.exists()) {
                                // Es un hijo
                                android.util.Log.d("LoginActivity", "Usuario encontrado en hijos")
                                navigateToRole("hijo")
                            } else {
                                // No se encontró en ninguna colección
                                android.util.Log.e("LoginActivity", "Usuario no encontrado en ninguna colección")
                                Toast.makeText(this, "Error: Usuario no encontrado en ninguna colección", Toast.LENGTH_SHORT).show()
                                auth.signOut()
                            }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("LoginActivity", "Error al buscar en hijos: ${e.message}")
                            Toast.makeText(this, "Error al verificar rol: ${e.message}", Toast.LENGTH_SHORT).show()
                            auth.signOut()
                        }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LoginActivity", "Error al buscar en tutores: ${e.message}")
                Toast.makeText(this, "Error al verificar rol: ${e.message}", Toast.LENGTH_SHORT).show()
                auth.signOut()
            }
    }

    private fun navigateToRole(role: String) {
        // Guardar el rol en SharedPreferences
        getSharedPreferences("app_preferences", MODE_PRIVATE)
            .edit()
            .putString("rol_usuario", role)
            .apply()

        try {
            // Navegar a la actividad correspondiente
            val intent = when (role) {
                "padre" -> Intent(this, MenuTutorActivity::class.java)
                "hijo" -> Intent(this, PantallaHijoActivity::class.java)
                else -> {
                    Toast.makeText(this, "Error: Rol no válido", Toast.LENGTH_SHORT).show()
                    Intent(this, RoleSelectionActivity::class.java)
                }
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al navegar: ${e.message}", Toast.LENGTH_LONG).show()
            // En caso de error, volver a la selección de rol
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finish()
        }
    }

    private fun handleAuthError(e: Exception?) {
        when (e) {
            is FirebaseAuthException -> {
                when (e.errorCode) {
                    "ERROR_INVALID_EMAIL" -> {
                        Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show()
                    }
                    "ERROR_WRONG_PASSWORD" -> {
                        Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
                    }
                    "ERROR_USER_NOT_FOUND" -> {
                        Toast.makeText(this, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                    }
                    "ERROR_USER_DISABLED" -> {
                        Toast.makeText(this, "Usuario deshabilitado", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "Error de autenticación: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                Toast.makeText(this, "Error inesperado: ${e?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}