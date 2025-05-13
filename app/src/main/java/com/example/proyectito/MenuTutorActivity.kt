package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MenuTutorActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: com.google.android.material.bottomnavigation.BottomNavigationView
    private lateinit var userEmail: TextView
    private lateinit var userName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu_tutor)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        loadUserData()

        // Cargar el fragmento inicial
        if (savedInstanceState == null) {
            loadFragment(TiempoUsoFragment())
        }

        // Configurar Bottom Navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_tiempo_uso -> {
                    loadFragment(TiempoUsoFragment())
                    true
                }
                R.id.nav_control -> {
                    // Cargar fragmento de control
                    true
                }
                R.id.nav_ubicacion -> {
                    // Cargar fragmento de ubicación
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_tiempo_uso
        }
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navView)
        toolbar = findViewById(R.id.toolbar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun loadUserData() {
        val headerView = navigationView.getHeaderView(0)
        val tvParentName = headerView.findViewById<TextView>(R.id.tvParentName)

        val currentUser = auth.currentUser
        currentUser?.let { user ->
            // Mostrar email en el header
            userEmail.text = user.email

            // Obtener nombre del usuario desde Firestore
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: "Usuario"
                        userName.text = name
                    }
                }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_scan_qr -> {
                startActivity(Intent(this, EscanearCodigoQRActivity::class.java))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}