package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val TAG = "LoginActivity"

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
            .addOnSuccessListener {
                // Verificar el rol del usuario y guardar el token FCM
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    verifyUserRoleAndSaveToken(userId)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al iniciar sesión: ${e.message}")
                Toast.makeText(this, "Error al iniciar sesión: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun verifyUserRoleAndSaveToken(userId: String) {
        // Primero buscar en la colección de tutores
        db.collection("tutores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Es un tutor
                    Log.d(TAG, "Usuario encontrado en tutores")
                    saveFCMTokenAndNavigate(userId, "tutores", "padre")
                } else {
                    // Si no es tutor, buscar en la colección de hijos
                    Log.d(TAG, "Usuario no encontrado en tutores, buscando en hijos")
                    db.collection("hijos").document(userId)
                        .get()
                        .addOnSuccessListener { childDocument ->
                            if (childDocument.exists()) {
                                // Es un hijo
                                Log.d(TAG, "Usuario encontrado en hijos")
                                saveFCMTokenAndNavigate(userId, "hijos", "hijo")
                            } else {
                                // No se encontró en ninguna colección
                                Log.e(TAG, "Usuario no encontrado en ninguna colección")
                                Toast.makeText(this, "Error: Usuario no encontrado en ninguna colección", Toast.LENGTH_SHORT).show()
                                auth.signOut()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al buscar en hijos: ${e.message}")
                            Toast.makeText(this, "Error al verificar rol: ${e.message}", Toast.LENGTH_SHORT).show()
                            auth.signOut()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al buscar en tutores: ${e.message}")
                Toast.makeText(this, "Error al verificar rol: ${e.message}", Toast.LENGTH_SHORT).show()
                auth.signOut()
            }
    }

    private fun saveFCMTokenAndNavigate(userId: String, collection: String, role: String) {
        // Obtener el token FCM
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "Token FCM obtenido: $token")

                    // Guardar el token en la colección correcta
                    db.collection(collection)
                        .document(userId)
                        .update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d(TAG, "Token FCM guardado exitosamente en $collection")
                            navigateToRole(role)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al guardar token FCM en $collection: ${e.message}")
                            // Continuar con la navegación aunque falle el guardado del token
                            navigateToRole(role)
                        }
                } else {
                    Log.e(TAG, "Error al obtener token FCM: ${task.exception?.message}")
                    // Continuar con la navegación aunque falle la obtención del token
                    navigateToRole(role)
                }
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