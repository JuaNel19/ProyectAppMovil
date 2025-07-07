package com.example.proyectito

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class MessagesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        // Cargar el fragmento de mensajes
        val messagesFragment = MessagesFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, messagesFragment)
            .commit()
    }
} 