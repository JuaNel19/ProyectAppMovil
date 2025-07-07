package com.example.proyectito

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Verificar si el usuario está logueado (es un hijo)
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                // Iniciar el servicio de bloqueo
                val serviceIntent = Intent(context, AppBlockerService::class.java)
                context.startService(serviceIntent)
            }
        }
    }
}