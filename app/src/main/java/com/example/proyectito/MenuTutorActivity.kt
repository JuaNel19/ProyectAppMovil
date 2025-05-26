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
    private lateinit var userName: TextView
    private var controlListener: ListenerRegistration? = null
    private var childrenListener: ListenerRegistration? = null

    // Mantener referencias a los fragmentos
    private var tiempoUsoFragment: TiempoUsoFragment? = null
    private var controlFragment: ControlFragment? = null
    private var ubicacionFragment: UbicacionFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu_tutor)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupFirestoreListeners()

        // Configurar Bottom Navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_tiempo_uso -> {
                    if (tiempoUsoFragment == null) {
                        tiempoUsoFragment = TiempoUsoFragment()
                    }
                    loadFragment(tiempoUsoFragment!!)
                    true
                }
                R.id.nav_control -> {
                    if (controlFragment == null) {
                        controlFragment = ControlFragment()
                    }
                    loadFragment(controlFragment!!)
                    true
                }
                R.id.nav_ubicacion -> {
                    if (ubicacionFragment == null) {
                        ubicacionFragment = UbicacionFragment()
                    }
                    loadFragment(ubicacionFragment!!)
                    true
                }
                else -> false
            }
        }

        // Cargar el fragmento inicial si no hay estado guardado
        if (savedInstanceState == null) {
            tiempoUsoFragment = TiempoUsoFragment()
            loadFragment(tiempoUsoFragment!!)
            bottomNavigation.selectedItemId = R.id.nav_tiempo_uso
        }

        loadUserData()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navView)
        toolbar = findViewById(R.id.toolbar)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Inicializar las variables del header
        val headerView = navigationView.getHeaderView(0)
        userName = headerView.findViewById(R.id.nav_header_name)
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

        // Listener para hijos asociados
        childrenListener = db.collection("parent_child_relations")
            .whereEqualTo("parent_id", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener hijos: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val childrenIds = snapshot?.documents?.mapNotNull { it.getString("child_id") } ?: emptyList()

                // Actualizar la lista de hijos en el fragmento actual
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (currentFragment is TiempoUsoFragment) {
                    currentFragment.updateChildrenList(childrenIds)
                }

                // Configurar listener para control de dispositivos
                setupControlListener(childrenIds)
            }
    }

    private fun setupControlListener(childrenIds: List<String>) {
        // Remover listener anterior si existe
        controlListener?.remove()

        // Crear listener para cada hijo
        childrenIds.forEach { childId ->
            db.collection("control_dispositivo_hijo")
                .document(childId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Toast.makeText(this, "Error al obtener datos de control: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    snapshot?.let { doc ->
                        val tiempoUsado = doc.getLong("tiempo_usado") ?: 0L
                        val limiteDiario = doc.getLong("limite_diario") ?: 0L

                        // Verificar si se ha alcanzado el límite
                        if (tiempoUsado >= limiteDiario) {
                            // Obtener nombre del hijo
                            db.collection("hijos").document(childId)
                                .get()
                                .addOnSuccessListener { childDoc ->
                                    val childName = childDoc.getString("nombre") ?: "Tu hijo"
                                    Toast.makeText(
                                        this,
                                        "$childName ha alcanzado el límite de tiempo diario",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                }
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
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
                startActivity(Intent(this, RoleSelectionActivity::class.java))
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
        controlListener?.remove()
        childrenListener?.remove()
    }
}