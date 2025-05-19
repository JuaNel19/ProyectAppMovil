package com.example.proyectito

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppsMasUsadasActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoApps: TextView
    private lateinit var adapter: AppUsoAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_mas_usadas)

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Configurar toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        // Inicializar vistas
        recyclerView = findViewById(R.id.recyclerApps)
        tvNoApps = findViewById(R.id.tvNoApps)

        // Configurar RecyclerView
        adapter = AppUsoAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Cargar datos
        cargarApps()
    }

    private fun cargarApps() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("uso_diario")
            .whereEqualTo("parentId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // Manejar error
                    return@addSnapshotListener
                }

                val apps = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AppUsoInfo::class.java)
                } ?: emptyList()

                if (apps.isEmpty()) {
                    tvNoApps.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvNoApps.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.updateApps(apps)
                }
            }
    }
} 