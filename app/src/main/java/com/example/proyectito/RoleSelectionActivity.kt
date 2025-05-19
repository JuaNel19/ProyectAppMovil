package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class RoleSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        val cardParent = findViewById<MaterialCardView>(R.id.cardParent)
        val cardChild = findViewById<MaterialCardView>(R.id.cardChild)

        cardParent.setOnClickListener {
            saveRoleAndNavigate("padre")
        }

        cardChild.setOnClickListener {
            saveRoleAndNavigate("hijo")
        }
    }

    private fun saveRoleAndNavigate(role: String) {
        // Guardar el rol en SharedPreferences
        getSharedPreferences("app_preferences", MODE_PRIVATE)
            .edit()
            .putString("rol_usuario", role)
            .apply()

        // Navegar a MainActivity
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
} 