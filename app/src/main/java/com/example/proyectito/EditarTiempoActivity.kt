package com.example.proyectito

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView

class EditarTiempoActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var slider: Slider
    private lateinit var tvTimeValue: MaterialTextView
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_tiempo)

        initializeViews()
        setupToolbar()
        setupSlider()
        setupSaveButton()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        slider = findViewById(R.id.slider)
        tvTimeValue = findViewById(R.id.tvTimeValue)
        btnSave = findViewById(R.id.btnSave)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Editar tiempo permitido"
        }
    }

    private fun setupSlider() {
        slider.addOnChangeListener { _, value, _ ->
            val hours = value.toInt()
            tvTimeValue.text = "$hours horas"
        }
    }

    private fun setupSaveButton() {
        btnSave.setOnClickListener {
            val newTime = slider.value.toInt()
            // TODO: Save the new time limit to your data store
            Toast.makeText(this, "Tiempo actualizado: $newTime horas", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}