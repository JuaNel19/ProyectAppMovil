package com.example.proyectito

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AppsMasUsadasActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: AppUsoAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var childId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps_mas_usadas)

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Obtener childId del intent
        childId = intent.getStringExtra("childId")
        if (childId == null) {
            Toast.makeText(this, "Error: No se especificó el niño", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadChildInfo()
        loadApps()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerApps)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvNoApps)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = AppUsoAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadChildInfo() {
        childId?.let { id ->
            db.collection("children")
                .document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val childName = document.getString("name") ?: "Niño"
                        toolbar.title = "Apps más usadas - $childName"
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al cargar información del niño", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        childId?.let { id ->
            db.collection("children")
                .document(id)
                .collection("appUsage")
                .orderBy("timeUsed", Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener { snapshot, error ->
                    progressBar.visibility = View.GONE

                    if (error != null) {
                        Toast.makeText(this, "Error al cargar apps: ${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val apps = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            AppUsoInfo(
                                nombre = doc.getString("name") ?: "",
                                packageName = doc.id,
                                icono = doc.getString("icon") ?: "",
                                tiempoUsado = doc.getLong("timeUsed")?.toInt() ?: 0,
                                ultimoUso = doc.getLong("lastUsed") ?: 0
                            )
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()

                    if (apps.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(apps)
                    }
                }
        }
    }
} 