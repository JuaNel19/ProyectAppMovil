package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

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

            //Saltar logeo para probar demas pantallas descomentar la linea loginUser al terminar
            navigateBasedOnRole()

            //loginUser(email, password)
        }

        // Set up register text click listener
        registerTextView.setOnClickListener {
            startActivity(Intent(this, MainActivity2::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Login successful
                    navigateBasedOnRole()
                    finish()
                } else {
                    // Login failed
                    Toast.makeText(this, "Error al iniciar sesión: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateBasedOnRole() {
        val role = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getString("rol_usuario", null)

        val intent = when (role) {
            "padre" -> Intent(this, MenuTutorActivity::class.java)
            "hijo" -> Intent(this, PantallaHijoActivity::class.java)
            else -> {
                Toast.makeText(this, "Error: Rol no definido", Toast.LENGTH_SHORT).show()
                Intent(this, RoleSelectionActivity::class.java)
            }
        }
        startActivity(intent)
        finish()
    }
}