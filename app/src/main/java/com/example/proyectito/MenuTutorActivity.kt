package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MenuTutorActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: com.google.android.material.bottomnavigation.BottomNavigationView
    private lateinit var userEmail: TextView
    private lateinit var userName: TextView
    private var alertsListener: ListenerRegistration? = null
    private var childrenListener: ListenerRegistration? = null

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
        setupFirestoreListeners()

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
                    loadFragment(ControlFragment())
                    true
                }
                R.id.nav_ubicacion -> {
                    loadFragment(UbicacionFragment())
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
        //userEmail = findViewById(R.id.tvParentEmail)
        userName = findViewById(R.id.tvParentName)
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

    private fun setupFirestoreListeners() {
        val userId = auth.currentUser?.uid ?: return

        // Listener para alertas
        alertsListener = db.collection("alertas")
            .whereEqualTo("parentId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener alertas: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { documents ->
                    for (doc in documents) {
                        val tipo = doc.getString("tipo")
                        val mensaje = doc.getString("mensaje")
                        val childId = doc.getString("childId")

                        when (tipo) {
                            "limite_alcanzado" -> {
                                // Obtener nombre del hijo
                                db.collection("hijos").document(childId ?: "")
                                    .get()
                                    .addOnSuccessListener { childDoc ->
                                        val childName = childDoc.getString("nombre") ?: "Tu hijo"
                                        Toast.makeText(this, "$childName: $mensaje", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }
                }
            }

        // Listener para hijos asociados
        childrenListener = db.collection("parent_child_relations")
            .whereEqualTo("parent_id", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener hijos: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // Actualizar la lista de hijos en el fragmento actual
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (currentFragment is TiempoUsoFragment) {
                    currentFragment.updateChildrenList(snapshot?.documents?.mapNotNull { it.getString("child_id") } ?: emptyList())
                }
            }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            // Mostrar email en el header
            //userEmail.text = user.email

            // Obtener datos del tutor desde Firestore
            db.collection("tutores").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("nombre") ?: "Tutor"
                        userName.text = name
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_SHORT).show()
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
            R.id.nav_logout -> {
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
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

    override fun onDestroy() {
        super.onDestroy()
        alertsListener?.remove()
        childrenListener?.remove()
    }
}