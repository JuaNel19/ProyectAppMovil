package com.example.proyectito

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.TimeUnit
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.location.LocationManager
import android.util.Log

class PantallaHijoActivity : AppCompatActivity() {
    private lateinit var tvUsedTime: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvBlockedApps: RecyclerView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var locationManager: CustomLocationManager
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val handler = Handler(Looper.getMainLooper())
    private var locationUpdateRunnable: Runnable? = null
    private var limitsListener: ListenerRegistration? = null
    private var dailyLimit: Long = 0
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val LOCATION_UPDATE_INTERVAL = 3 * 60 * 1000L // 3 minutos

    private lateinit var localBroadcastManager: LocalBroadcastManager
    private var timeUpdateReceiver: BroadcastReceiver? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                startLocationUpdates()
            }
            else -> {
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_hijo)

        Log.d("PantallaHijoActivity", "onCreate iniciado")

        // Iniciar servicios de monitoreo
        DeviceMonitorService.startService(this)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Inicializar LocationManager
        locationManager = CustomLocationManager(this)
        Log.d("PantallaHijoActivity", "LocationManager inicializado")

        // Verificar y solicitar permisos de ubicación
        checkLocationPermission()

        // Iniciar listener de límites
        setupFirestoreListener()

        // Configurar receptor de broadcasts
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        setupBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()
        Log.d("PantallaHijoActivity", "onResume")
        if (locationManager.hasLocationPermission()) {
            Log.d("PantallaHijoActivity", "Iniciando actualizaciones de ubicación")
            startLocationUpdates()
        } else {
            Log.e("PantallaHijoActivity", "No hay permisos de ubicación")
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager.stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        limitsListener?.remove()
        DeviceMonitorService.stopService(this)
        timeUpdateReceiver?.let {
            localBroadcastManager.unregisterReceiver(it)
        }
        locationManager.stopLocationUpdates()
    }

    private fun initializeViews() {
        tvUsedTime = findViewById(R.id.tvUsedTime)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        progressBar = findViewById(R.id.progressBar)
        rvBlockedApps = findViewById(R.id.rvBlockedApps)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navView)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_generate_qr -> {
                    startActivity(Intent(this, GenerarCodigoQRActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_logout -> {
                    auth.signOut()
                    startActivity(Intent(this, RoleSelectionActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        val headerView = navigationView.getHeaderView(0)
        val tvChildName = headerView.findViewById<TextView>(R.id.tvChildName)
        val currentUser = FirebaseAuth.getInstance().currentUser
        tvChildName.text = "Hola, ${currentUser?.displayName ?: "Usuario"}"
    }

    private fun setupRecyclerView() {
        rvBlockedApps.layoutManager = LinearLayoutManager(this)
        // TODO: Implement adapter for blocked apps
    }

    private fun setupFirestoreListener() {
        val userId = auth.currentUser?.uid ?: return

        // Listener solo para el límite diario
        limitsListener = db.collection("control_dispositivo_hijo")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener límite: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    dailyLimit = doc.getLong("limite_diario") ?: TimeUnit.HOURS.toMillis(2)
                }
            }
    }

    private fun setupBroadcastReceiver() {
        timeUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DeviceMonitorService.ACTION_TIME_UPDATE) {
                    val timeUsed = intent.getLongExtra(DeviceMonitorService.EXTRA_TIME_USED, 0)
                    val timeRemaining = intent.getLongExtra(DeviceMonitorService.EXTRA_TIME_REMAINING, 0)
                    val limitReached = intent.getBooleanExtra(DeviceMonitorService.EXTRA_LIMIT_REACHED, false)

                    updateUI(timeUsed, timeRemaining, limitReached)
                }
            }
        }

        val filter = IntentFilter(DeviceMonitorService.ACTION_TIME_UPDATE)
        localBroadcastManager.registerReceiver(timeUpdateReceiver!!, filter)
    }

    private fun updateUI(timeUsed: Long, timeRemaining: Long, limitReached: Boolean) {
        // Actualizar tiempo usado
        val hoursUsed = TimeUnit.MILLISECONDS.toHours(timeUsed)
        val minutesUsed = TimeUnit.MILLISECONDS.toMinutes(timeUsed) % 60
        tvUsedTime.text = "Tiempo usado: ${hoursUsed}h ${minutesUsed}m"

        // Actualizar tiempo restante
        val hoursRemaining = TimeUnit.MILLISECONDS.toHours(timeRemaining)
        val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(timeRemaining) % 60
        tvRemainingTime.text = "Tiempo restante: ${hoursRemaining}h ${minutesRemaining}m"

        // Actualizar progreso
        val totalTime = timeUsed + timeRemaining
        val progress = if (totalTime > 0) {
            ((timeUsed.toFloat() / totalTime.toFloat()) * 100).toInt()
        } else {
            0
        }
        progressBar.progress = progress

        // Actualizar estado visual si se alcanzó el límite
        if (limitReached) {
            tvRemainingTime.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            tvRemainingTime.setTextColor(getColor(android.R.color.black))
        }
    }

    private fun startLocationUpdates() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            Log.d("PantallaHijoActivity", "Iniciando actualizaciones para userId: $userId")
            locationManager.startLocationUpdates(userId)
        } else {
            Log.e("PantallaHijoActivity", "No hay usuario autenticado")
        }
    }

    private fun stopLocationUpdates() {
        locationUpdateRunnable?.let {
            handler.removeCallbacks(it)
            locationUpdateRunnable = null
        }
    }

    private fun checkLocationPermission() {
        Log.d("PantallaHijoActivity", "Verificando permisos de ubicación")
        when {
            locationManager.hasLocationPermission() -> {
                Log.d("PantallaHijoActivity", "Ya tiene permisos de ubicación")
                startLocationUpdates()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d("PantallaHijoActivity", "Mostrando diálogo de explicación")
                showPermissionRationaleDialog()
            }
            else -> {
                Log.d("PantallaHijoActivity", "Solicitando permisos de ubicación")
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de ubicación necesario")
            .setMessage("Esta aplicación necesita acceso a la ubicación para funcionar correctamente.")
            .setPositiveButton("Solicitar permiso") { _, _ ->
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso denegado")
            .setMessage("Esta aplicación necesita acceso a la ubicación para funcionar correctamente. Por favor, habilita el permiso en la configuración.")
            .setPositiveButton("Ir a configuración") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }
}