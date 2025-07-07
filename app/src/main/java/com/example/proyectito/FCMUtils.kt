package com.example.proyectito

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FCMUtils {
    private const val TAG = "FCMUtils"
    private const val PREF_NAME = "AppPrefs"
    private const val KEY_USER_ROLE = "user_role"

    fun updateFCMToken() {
        Log.d(TAG, "Iniciando actualización de token FCM")
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "Token FCM obtenido: $token")

                    // Guardar el token en Firestore
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        Log.d(TAG, "ID de usuario obtenido: $userId")

                        // Primero verificar si es tutor
                        FirebaseFirestore.getInstance()
                            .collection("tutores")
                            .document(userId)
                            .get()
                            .addOnSuccessListener { tutorDoc ->
                                if (tutorDoc.exists()) {
                                    Log.d(TAG, "Usuario encontrado en colección tutores")
                                    // Guardar token en tutores
                                    FirebaseFirestore.getInstance()
                                        .collection("tutores")
                                        .document(userId)
                                        .update("fcmToken", token)
                                        .addOnSuccessListener {
                                            Log.d(TAG, "Token FCM guardado exitosamente en tutores")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(TAG, "Error al guardar token FCM en tutores: ${e.message}")
                                        }
                                } else {
                                    // Verificar si es hijo
                                    FirebaseFirestore.getInstance()
                                        .collection("hijos")
                                        .document(userId)
                                        .get()
                                        .addOnSuccessListener { hijoDoc ->
                                            if (hijoDoc.exists()) {
                                                Log.d(TAG, "Usuario encontrado en colección hijos")
                                                // Guardar token en hijos
                                                FirebaseFirestore.getInstance()
                                                    .collection("hijos")
                                                    .document(userId)
                                                    .update("fcmToken", token)
                                                    .addOnSuccessListener {
                                                        Log.d(TAG, "Token FCM guardado exitosamente en hijos")
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e(TAG, "Error al guardar token FCM en hijos: ${e.message}")
                                                    }
                                            } else {
                                                Log.e(TAG, "Usuario no encontrado en ninguna colección")
                                            }
                                        }
                                }
                            }
                    } else {
                        Log.e(TAG, "No se pudo obtener el ID del usuario")
                    }
                } else {
                    Log.e(TAG, "Error al obtener token FCM: ${task.exception?.message}")
                }
            }
    }
}