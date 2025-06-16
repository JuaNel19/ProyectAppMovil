package com.example.proyectito

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import android.util.Log
import androidx.activity.addCallback

class AppBlockedActivity : AppCompatActivity() {
    private val TAG = "AppBlockedActivity"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = Firebase.functions("us-central1")
    private lateinit var appName: String
    private lateinit var packageName: String
    private lateinit var childName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_blocked)

        // Configurar pantalla completa
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Obtener datos de la app bloqueada
        appName = intent.getStringExtra("appName") ?: "Aplicación"
        packageName = intent.getStringExtra("packageName") ?: ""
        childName = intent.getStringExtra("childName") ?: "Hijo"

        Log.d(TAG, "Datos recibidos - App: $appName, Package: $packageName, Child: $childName")

        // Configurar UI
        findViewById<TextView>(R.id.tvAppName).text = appName
        findViewById<Button>(R.id.btnRequestUnblock).setOnClickListener {
            requestUnblock()
        }

        // Prevenir que el usuario salga de la pantalla
        onBackPressedDispatcher.addCallback(this) {
            // No hacer nada, prevenir que el usuario salga
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun requestUnblock() {
        Log.d(TAG, "Iniciando solicitud de desbloqueo")
        val childId = auth.currentUser?.uid
        if (childId == null) {
            Log.e(TAG, "No hay usuario autenticado")
            Toast.makeText(this, "Error: No hay usuario autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        // Primero obtener el parentId desde parent_child_relations
        db.collection("parent_child_relations")
            .whereEqualTo("child_id", childId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "No se encontró relación padre-hijo")
                    Toast.makeText(this, "Error: No se encontró relación con el padre", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val parentId = documents.documents[0].getString("parent_id")
                if (parentId == null) {
                    Log.e(TAG, "ParentId es null")
                    Toast.makeText(this, "Error: ID del padre no encontrado", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                Log.d(TAG, "ParentId encontrado: $parentId")

                // Crear la solicitud de desbloqueo
                val request = hashMapOf(
                    "appName" to appName,
                    "packageName" to packageName,
                    "childId" to childId,
                    "childName" to childName,
                    "parentId" to parentId,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                Log.d(TAG, "Guardando solicitud en Firestore: $request")

                // Guardar en Firestore
                db.collection("unblockRequests")
                    .add(request)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "Solicitud guardada exitosamente con ID: ${documentReference.id}")
                        Toast.makeText(this, "Solicitud enviada", Toast.LENGTH_SHORT).show()
                        findViewById<Button>(R.id.btnRequestUnblock).isEnabled = false
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al guardar la solicitud", e)
                        Toast.makeText(this, "Error al enviar la solicitud", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al buscar relación padre-hijo", e)
                Toast.makeText(this, "Error al buscar relación con el padre", Toast.LENGTH_SHORT).show()
            }
    }
}