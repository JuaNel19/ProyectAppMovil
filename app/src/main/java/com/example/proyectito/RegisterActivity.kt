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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthException
import com.example.proyectito.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.text.Editable
import android.util.Log

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedRole: String? = null
    private lateinit var registerDni: EditText
    private lateinit var registerName: EditText
    private lateinit var registerEmail: EditText
    private lateinit var registerPassword: EditText
    private lateinit var confirmPassword: EditText
    private lateinit var registerButton: Button
    private lateinit var logginTextView: TextView
    private var isUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrer)

        // Obtener el rol seleccionado
        selectedRole = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getString("rol_usuario", null)

        if (selectedRole == null) {
            Toast.makeText(this, "Error: Rol no seleccionado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        registerDni = findViewById(R.id.registerDni)
        registerName = findViewById(R.id.registerName)
        registerEmail = findViewById(R.id.registerEmail)
        registerPassword = findViewById(R.id.registerPassword)
        confirmPassword = findViewById(R.id.confirmPassword)
        registerButton = findViewById(R.id.registerButton)
        logginTextView = findViewById(R.id.logginTextView)

        // Set up DNI field listener
        registerDni.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return

                val dni = s.toString()
                if (dni.length == 8) {
                    isUpdating = true
                    consultarDni(dni)
                }
            }
        })

        // Set up register button click listener
        registerButton.setOnClickListener {
            val dni = registerDni.text.toString().trim()
            val name = registerName.text.toString().trim()
            val email = registerEmail.text.toString().trim()
            val password = registerPassword.text.toString().trim()
            val confirmPassword = confirmPassword.text.toString().trim()

            if (dni.isEmpty() || name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (dni.length != 8) {
                Toast.makeText(this, "El DNI debe tener 8 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Por favor ingrese un email válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidPassword(password)) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(dni, name, email, password)
        }

        // Set up login text click listener
        logginTextView.setOnClickListener {
            finish() // Go back to login screen
        }
    }

    private fun consultarDni(dni: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("DNIConsulta", "Consultando DNI: $dni")
                val response = ApiClient.dniApiService.consultarDni(dni)
                Log.d("DNIConsulta", "Respuesta recibida: $response")

                withContext(Dispatchers.Main) {
                    if (response.nombreCompleto.isNotEmpty()) {
                        // Reordenar el nombre completo: nombres + apellido paterno + apellido materno
                        val nombreCompleto = "${response.nombres} ${response.apellidoPaterno} ${response.apellidoMaterno}"
                        Log.d("DNIConsulta", "Nombre encontrado: $nombreCompleto")
                        registerName.setText(nombreCompleto)
                        registerName.requestFocus()
                        Toast.makeText(this@RegisterActivity, "DNI encontrado", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("DNIConsulta", "Error: Nombre vacío")
                        Toast.makeText(this@RegisterActivity, "Error: No se encontró el nombre", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DNIConsulta", "Error en consulta: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMessage = when {
                        e.message?.contains("404") == true -> "DNI no encontrado"
                        e.message?.contains("422") == true -> "DNI inválido"
                        e.message?.contains("401") == true -> "Error de autenticación con la API"
                        e.message?.contains("timeout") == true -> "Error de conexión. Intente nuevamente"
                        else -> "Error al consultar DNI: ${e.message}"
                    }
                    Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isUpdating = false
                }
            }
        }
    }

    private fun registerUser(dni: String, name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    val user = User(
                        id = userId ?: "",
                        email = email,
                        nombre = name,
                        tipo = when (selectedRole) {
                            "padre" -> User.TIPO_TUTOR
                            "hijo" -> User.TIPO_HIJO
                            else -> User.TIPO_TUTOR
                        },
                        fechaCreacion = Timestamp.now()
                    )

                    userId?.let { uid ->
                        // Primero enviamos el email de verificación
                        sendVerificationEmail { success ->
                            if (success) {
                                // Si el email se envió correctamente, guardamos los datos del usuario
                                // en la colección correspondiente según el rol
                                val collection = when (selectedRole) {
                                    "padre" -> "tutores"
                                    "hijo" -> "hijos"
                                    else -> return@sendVerificationEmail
                                }

                                val userData = hashMapOf(
                                    "id" to user.id,
                                    "email" to user.email,
                                    "nombre" to user.nombre,
                                    "tipo" to user.tipo,
                                    "fechaCreacion" to user.fechaCreacion,
                                    "dni" to dni
                                )

                                db.collection(collection).document(uid)
                                    .set(userData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Registro exitoso. Por favor verifica tu email.", Toast.LENGTH_LONG).show()
                                        // Cerrar sesión hasta que el email sea verificado
                                        auth.signOut()
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        handleFirestoreError(e)
                                    }
                            } else {
                                // Si falló el envío del email, eliminamos el usuario creado
                                auth.currentUser?.delete()
                                Toast.makeText(this, "Error al enviar email de verificación", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    handleAuthError(task.exception)
                }
            }
    }

    private fun sendVerificationEmail(onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        user?.let {
            it.sendEmailVerification()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                }
        } ?: onComplete(false)
    }

    private fun handleFirestoreError(e: Exception) {
        when (e) {
            is FirebaseFirestoreException -> {
                when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        Toast.makeText(this, "Error de permisos al guardar datos", Toast.LENGTH_SHORT).show()
                    }
                    FirebaseFirestoreException.Code.NOT_FOUND -> {
                        Toast.makeText(this, "Error: Documento no encontrado", Toast.LENGTH_SHORT).show()
                    }
                    FirebaseFirestoreException.Code.ALREADY_EXISTS -> {
                        Toast.makeText(this, "El usuario ya existe", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "Error al guardar datos: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                Toast.makeText(this, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleAuthError(e: Exception?) {
        when (e) {
            is FirebaseAuthException -> {
                when (e.errorCode) {
                    "ERROR_EMAIL_ALREADY_IN_USE" -> {
                        Toast.makeText(this, "El email ya está registrado", Toast.LENGTH_SHORT).show()
                    }
                    "ERROR_INVALID_EMAIL" -> {
                        Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show()
                    }
                    "ERROR_WEAK_PASSWORD" -> {
                        Toast.makeText(this, "La contraseña es muy débil", Toast.LENGTH_SHORT).show()
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

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }
}