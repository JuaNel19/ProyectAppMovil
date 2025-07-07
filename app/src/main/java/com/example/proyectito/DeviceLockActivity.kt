package com.example.proyectito

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class DeviceLockActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var etUnlockCode: EditText
    private lateinit var btnUnlock: Button
    private lateinit var tvMessage: TextView

    companion object {
        const val TIEMPO_GRACIA = 30L // 30 minutos de tiempo de gracia
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_lock)

        // Configurar el callback para el botón de retroceso
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // No hacer nada, bloqueando el botón de retroceso
            }
        })

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Inicializar vistas
        etUnlockCode = findViewById(R.id.etUnlockCode)
        btnUnlock = findViewById(R.id.btnUnlock)
        tvMessage = findViewById(R.id.tvMessage)

        // Configurar mensaje inicial
        tvMessage.text = "Has alcanzado tu límite diario de tiempo. Ingresa el código de desbloqueo para obtener tiempo adicional."

        // Configurar botón de desbloqueo
        btnUnlock.setOnClickListener {
            verificarCodigo()
        }
    }

    private fun verificarCodigo() {
        val codigoIngresado = etUnlockCode.text.toString()
        val userId = auth.currentUser?.uid ?: return

        db.collection("control_dispositivo_hijo")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val codigoCorrecto = document.getString("codigo_desbloqueo")

                if (codigoIngresado == codigoCorrecto) {
                    // Asignar tiempo de gracia
                    val tiempoGraciaMillis = TimeUnit.MINUTES.toMillis(TIEMPO_GRACIA)
                    val tiempoActual = document.getLong("tiempo_usado") ?: 0L
                    val nuevoTiempo = tiempoActual + tiempoGraciaMillis

                    // Actualizar Firestore con el nuevo tiempo
                    db.collection("control_dispositivo_hijo")
                        .document(userId)
                        .update(
                            mapOf(
                                "tiempo_usado" to nuevoTiempo,
                                "ultima_actualizacion" to System.currentTimeMillis(),
                                "tiempo_gracia_activo" to true
                            )
                        )
                        .addOnSuccessListener {
                            // Notificar al padre
                            notificarPadre(userId)
                            // Desbloquear dispositivo
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Error al actualizar el tiempo: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(this, "Código incorrecto", Toast.LENGTH_SHORT).show()
                    etUnlockCode.text.clear()
                }
            }
    }

    private fun notificarPadre(userId: String) {
        // Obtener el ID del padre
        db.collection("usuarios")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val parentId = document.getString("parentId")
                if (parentId != null) {
                    // Crear notificación para el padre
                    db.collection("notificaciones")
                        .add(
                            mapOf(
                                "parentId" to parentId,
                                "childId" to userId,
                                "mensaje" to "Tu hijo ha usado el código de desbloqueo. Tiene $TIEMPO_GRACIA minutos adicionales.",
                                "timestamp" to System.currentTimeMillis(),
                                "leido" to false
                            )
                        )
                }
            }
    }
}