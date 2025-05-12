package com.example.proyectito

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Switch
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity2 : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.administrar_aplicaciones)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main2)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val switchFacebook = findViewById<Switch>(R.id.switchFacebook)

        val switchLlamdas = findViewById<Switch>(R.id.switchLlamadas)

        val switchMensaje = findViewById<Switch>(R.id.switchMensaje)

        val switchYoutube = findViewById<Switch>(R.id.switchYoutube)

        val switchWasap = findViewById<Switch>(R.id.switchWasap)

        val switchClashRoyale = findViewById<Switch>(R.id.switchClashRoyale)

        val switchLudo = findViewById<Switch>(R.id.switchLudo)

        val switchGPT = findViewById<Switch>(R.id.switchGPT)

        val switchGoogle = findViewById<Switch>(R.id.switchGoogle)

        val switchInstagram = findViewById<Switch>(R.id.switchInstagram)

        val switchTiktok = findViewById<Switch>(R.id.switchTiktok)
    }
}