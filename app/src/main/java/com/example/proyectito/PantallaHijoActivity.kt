package com.example.proyectito

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
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

class PantallaHijoActivity : AppCompatActivity() {
    private lateinit var tvUsedTime: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvBlockedApps: RecyclerView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private var countDownTimer: CountDownTimer? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val handler = Handler(Looper.getMainLooper())
    private var locationUpdateRunnable: Runnable? = null
    private var usageListener: ListenerRegistration? = null
    private var limitsListener: ListenerRegistration? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val LOCATION_UPDATE_INTERVAL = 3 * 60 * 1000L // 3 minutos

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Permisos concedidos, iniciar actualizaciones de ubicación
                startLocationUpdates()
            }
            else -> {
                // Permisos denegados
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_hijo)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Inicializar FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Verificar y solicitar permisos de ubicación
        checkLocationPermission()

        // Iniciar listeners de Firestore
        setupFirestoreListeners()
    }

    private fun initializeViews() {
        tvUsedTime = findViewById(R.id.tvUsedTime)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvCountdown = findViewById(R.id.tvCountdown)
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
                else -> false
            }
        }

        // Update child's name in the header
        val headerView = navigationView.getHeaderView(0)
        val tvChildName = headerView.findViewById<TextView>(R.id.tvChildName)
        val currentUser = FirebaseAuth.getInstance().currentUser
        tvChildName.text = "Hola, ${currentUser?.displayName ?: "Usuario"}"
    }

    private fun setupRecyclerView() {
        rvBlockedApps.layoutManager = LinearLayoutManager(this)
        // TODO: Implement adapter for blocked apps
        // rvBlockedApps.adapter = BlockedAppsAdapter(getBlockedApps())
    }

    private fun setupFirestoreListeners() {
        val userId = auth.currentUser?.uid ?: return

        // Listener para uso diario
        usageListener = db.collection("uso_diario")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener datos de uso: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val usedTime = doc.getLong("tiempo_usado") ?: 0L
                    updateUsageTime(usedTime)
                }
            }

        // Listener para límites
        limitsListener = db.collection("limites_apps")
            .document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener límites: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val dailyLimit = doc.getLong("limite_diario") ?: 0L
                    val usedTime = doc.getLong("tiempo_usado") ?: 0L
                    startCountdown(dailyLimit - usedTime)
                }
            }
    }

    private fun updateUsageTime(usedTimeMillis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(usedTimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(usedTimeMillis) % 60

        tvUsedTime.text = "Usado: ${hours}h ${minutes}m"

        // Obtener el límite diario
        val userId = auth.currentUser?.uid ?: return
        db.collection("limites_apps")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val dailyLimit = doc.getLong("limite_diario") ?: 0L
                val remainingTime = dailyLimit - usedTimeMillis
                val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingTime)
                val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60

                tvRemainingTime.text = "Restante: ${remainingHours}h ${remainingMinutes}m"

                // Actualizar progreso
                val progress = if (dailyLimit > 0) {
                    ((usedTimeMillis.toFloat() / dailyLimit.toFloat()) * 100).toInt()
                } else {
                    0
                }
                progressBar.progress = progress
            }
    }

    private fun startCountdown(remainingTimeMillis: Long) {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                tvCountdown.text = String.format(
                    "Tiempo restante: %02d:%02d:%02d",
                    hours, minutes, seconds
                )
            }

            override fun onFinish() {
                tvCountdown.text = "Tiempo restante: 00:00:00"
                // Notificar al padre que se alcanzó el límite
                notifyParentLimitReached()
            }
        }.start()
    }

    private fun notifyParentLimitReached() {
        val userId = auth.currentUser?.uid ?: return
        val alerta = hashMapOf(
            "tipo" to "limite_alcanzado",
            "childId" to userId,
            "fecha" to FieldValue.serverTimestamp(),
            "mensaje" to "Se ha alcanzado el límite de tiempo diario"
        )

        db.collection("alertas")
            .add(alerta)
            .addOnSuccessListener {
                Toast.makeText(this, "Se ha notificado a tus padres", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al notificar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermission() -> {
                startLocationUpdates()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                requestLocationPermission()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de ubicación necesario")
            .setMessage("Esta aplicación necesita acceso a la ubicación para que tus padres puedan saber dónde estás.")
            .setPositiveButton("Solicitar permiso") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "La funcionalidad de ubicación no estará disponible", Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso denegado")
            .setMessage("Para usar la funcionalidad de ubicación, necesitas habilitar los permisos en la configuración.")
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

    private fun startLocationUpdates() {
        // Cancelar actualizaciones anteriores si existen
        stopLocationUpdates()

        // Crear nuevo runnable para actualizaciones periódicas
        locationUpdateRunnable = object : Runnable {
            override fun run() {
                getCurrentLocation()
                handler.postDelayed(this, LOCATION_UPDATE_INTERVAL)
            }
        }

        // Iniciar actualizaciones
        handler.post(locationUpdateRunnable!!)
    }

    private fun stopLocationUpdates() {
        locationUpdateRunnable?.let {
            handler.removeCallbacks(it)
            locationUpdateRunnable = null
        }
    }

    private fun getCurrentLocation() {
        if (!hasLocationPermission()) return

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, object : CancellationToken() {
                override fun onCanceledRequested(listener: OnTokenCanceledListener) = CancellationTokenSource().token

                override fun isCancellationRequested() = false
            }).addOnSuccessListener { location ->
                location?.let { updateLocationInFirestore(it) }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error al obtener ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLocationInFirestore(location: Location) {
        val userId = auth.currentUser?.uid ?: return
        val locationData = hashMapOf(
            "latitud" to location.latitude,
            "longitud" to location.longitude,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("hijos")
            .document(userId)
            .update("ubicacion", locationData)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al actualizar ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        stopLocationUpdates()
        usageListener?.remove()
        limitsListener?.remove()
    }
}