package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity2 : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrer)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Set up register button click listener
        val registerButton = findViewById<Button>(R.id.registerButton)
        val registerEmail = findViewById<EditText>(R.id.registerEmail)
        val registerPassword = findViewById<EditText>(R.id.registerPassword)
        val confirmPassword = findViewById<EditText>(R.id.confirmPassword)
        val logginTextView = findViewById<TextView>(R.id.logginTextView)
        registerButton.setOnClickListener {

            val email = registerEmail.text.toString().trim()
            val password = registerPassword.text.toString().trim()
            val confirmPassword = confirmPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(email, password)
        }

        // Set up login text click listener
        logginTextView.setOnClickListener {
            finish() // Go back to login screen
        }
    }

    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Registration successful, save additional user data
                    val userId = auth.currentUser?.uid
                    val user = hashMapOf(
                        "email" to email
                    )

                    // Save user data to Firestore
                    userId?.let { uid ->
                        db.collection("users").document(uid)
                            .set(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MenuTutorActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error al guardar datos: ${e.message}",
                                    Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    // Registration failed
                    Toast.makeText(this, "Error al registrar: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }
}