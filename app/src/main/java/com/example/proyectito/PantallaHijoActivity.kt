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
import android.os.Build
import java.util.Calendar

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
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1003
    private val LOCATION_UPDATE_INTERVAL = 3 * 60 * 1000L // 3 minutos
    private val TAG = "PantallaHijo"

    private lateinit var localBroadcastManager: LocalBroadcastManager
    private var timeUpdateReceiver: BroadcastReceiver? = null
    private lateinit var childDeviceManager: ChildDeviceManager
    private lateinit var blockedAppsAdapter: BlockedAppsAdapter

    // Variables para controlar el flujo de permisos
    private var permissionsChecked = false
    private var appsSynced = false

    // Variables para controlar alertas de tiempo agotado
    private var timeLimitAlertSent = false
    private var lastAlertDay = -1
    private var lastAlertTimeUsed = 0L

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                Log.d(TAG, "Permisos de ubicación concedidos")
                startLocationUpdates()
            }
            else -> {
                Log.e(TAG, "Permisos de ubicación denegados")
                showPermissionDeniedDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_hijo)

        Log.d("PantallaHijoActivity", "onCreate iniciado")

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childDeviceManager = ChildDeviceManager(this)

        // Cargar estado de alertas
        loadTimeLimitAlertState()

        // Iniciar servicios de monitoreo
        DeviceMonitorService.startService(this)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()

        // Inicializar LocationManager
        locationManager = CustomLocationManager(this)
        Log.d("PantallaHijoActivity", "LocationManager inicializado")

        // Verificar y solicitar permisos PRIMERO
        checkRequiredPermissions()

        // Iniciar listener de límites
        setupFirestoreListener()

        // Configurar receptor de broadcasts
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        setupBroadcastReceiver()

        // Iniciar el servicio de bloqueo de apps
        startService(Intent(this, AppBlockerService::class.java))

        // Actualizar token FCM
        FCMUtils.updateFCMToken()

    }

    override fun onResume() {
        super.onResume()
        Log.d("PantallaHijoActivity", "onResume")

        // Verificar permisos nuevamente cuando la actividad se reanuda
        //checkRequiredPermissions()

        // Verificar permisos de ubicación específicamente
        checkLocationPermission()

        // Si ya se verificaron los permisos y no se han sincronizado las apps, sincronizarlas
        if (permissionsChecked && !appsSynced && childDeviceManager.checkUsageStatsPermission()) {
            Log.d(TAG, "Sincronizando apps después de verificar permisos")
            syncAppsIfNeeded()
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
        // Asegurarse de que el servicio se detenga si la actividad se destruye
        stopService(Intent(this, AppBlockerService::class.java))
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
        blockedAppsAdapter = BlockedAppsAdapter { packageName, appName ->
            requestAppUnblock(packageName, appName)
        }
        rvBlockedApps.adapter = blockedAppsAdapter

        // Cargar apps bloqueadas
        loadBlockedApps()
    }

    private fun loadBlockedApps() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "Cargando apps bloqueadas para usuario: $userId")

        db.collection("children")
            .document(userId)
            .collection("installedApps")
            .document("apps")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error al cargar apps bloqueadas", e)
                    return@addSnapshotListener
                }

                val appsList = snapshot?.get("apps") as? List<Map<String, Any>> ?: emptyList()
                Log.d(TAG, "Apps encontradas: ${appsList.size}")

                val blockedApps = appsList
                    .filter { appMap -> appMap["bloqueado"] == true }
                    .mapNotNull { appMap ->
                        val packageName = appMap["packageName"] as? String
                        val appName = appMap["nombre"] as? String

                        if (packageName != null && appName != null) {
                            try {
                                val icon = packageManager.getApplicationIcon(packageName)
                                BlockedApp(packageName, appName, icon)
                            } catch (e: PackageManager.NameNotFoundException) {
                                Log.e(TAG, "Error al obtener icono para $packageName", e)
                                null
                            }
                        } else null
                    }

                Log.d(TAG, "Apps bloqueadas encontradas: ${blockedApps.size}")
                blockedAppsAdapter.submitList(blockedApps)
            }
    }

    private fun requestAppUnblock(packageName: String, appName: String) {
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "Iniciando solicitud de desbloqueo para app: $appName")

        // Obtener el parentId desde la relación padre-hijo
        db.collection("parent_child_relations")
            .whereEqualTo("child_id", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "No se encontró relación padre-hijo")
                    Toast.makeText(this, "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val parentId = documents.documents[0].getString("parent_id")
                if (parentId == null) {
                    Log.e(TAG, "ParentId es null")
                    Toast.makeText(this, "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                Log.d(TAG, "ParentId encontrado: $parentId")

                val request = hashMapOf(
                    "childId" to userId,
                    "childName" to (auth.currentUser?.displayName ?: "Hijo"),
                    "appName" to appName,
                    "packageName" to packageName,
                    "parentId" to parentId,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis()
                )

                Log.d(TAG, "Guardando solicitud en Firestore: $request")

                db.collection("unblockRequests")
                    .add(request)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "Solicitud guardada exitosamente con ID: ${documentReference.id}")
                        Toast.makeText(this, "Solicitud enviada", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al enviar solicitud", e)
                        Toast.makeText(this, "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener parentId", e)
                Toast.makeText(this, "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
            }
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

            // Verificar si se debe enviar la alerta
            if (shouldSendTimeLimitAlert(timeUsed)) {
                createTimeLimitAlert()
                timeLimitAlertSent = true
                lastAlertTimeUsed = timeUsed
                saveTimeLimitAlertState()
            }
        } else {
            tvRemainingTime.setTextColor(getColor(android.R.color.black))
            // Resetear flag cuando el límite ya no está alcanzado
            if (timeLimitAlertSent) {
                timeLimitAlertSent = false
                saveTimeLimitAlertState()
            }
        }
    }

    private fun shouldSendTimeLimitAlert(currentTimeUsed: Long): Boolean {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        // Opción A: Reset diario
        if (currentDay != lastAlertDay) {
            Log.d(TAG, "Nuevo día detectado, reseteando flag de alerta")
            return true
        }

        // Opción B: Reset por nuevo tiempo (cuando el tiempo usado es menor al último alertado)
        if (currentTimeUsed < lastAlertTimeUsed) {
            Log.d(TAG, "Tiempo reiniciado detectado, reseteando flag de alerta")
            return true
        }

        // Si ya se envió alerta para este tiempo, no enviar otra
        if (timeLimitAlertSent) {
            Log.d(TAG, "Alerta ya enviada para este tiempo, saltando")
            return false
        }

        return true
    }

    private fun createTimeLimitAlert() {
        val userId = auth.currentUser?.uid ?: return

        // Obtener el parentId desde la relación padre-hijo
        db.collection("parent_child_relations")
            .whereEqualTo("child_id", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "No se encontró relación padre-hijo")
                    return@addOnSuccessListener
                }

                val parentId = documents.documents[0].getString("parent_id") ?: return@addOnSuccessListener

                val alert = hashMapOf(
                    "parentId" to parentId,
                    "childId" to userId,
                    "childName" to (auth.currentUser?.displayName ?: "Hijo"),
                    "tipo" to "TIEMPO_AGOTADO",
                    "mensaje" to "El tiempo de uso diario ha sido agotado",
                    "timestamp" to System.currentTimeMillis(),
                    "leida" to false
                )

                db.collection("alertas")
                    .add(alert)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al crear alerta de tiempo", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener parentId", e)
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

    private fun checkRequiredPermissions() {
        Log.d(TAG, "Verificando permisos requeridos")

        // Solicitar permisos en cadena, uno tras otro
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Permiso de notificaciones necesario")
                    .setMessage("Esta aplicación necesita permiso de notificaciones para recibir alertas importantes.")
                    .setPositiveButton("Solicitar permiso") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            NOTIFICATION_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Cancelar") { dialog, _ ->
                        dialog.dismiss()
                        // Continuar con el siguiente permiso aunque se cancele
                        checkUsageStatsPermission()
                    }
                    .setOnDismissListener {
                        // Continuar con el siguiente permiso después del diálogo
                        checkUsageStatsPermission()
                    }
                    .show()
                return
            }
        }
        // Si ya está concedido o no es necesario, continuar
        checkUsageStatsPermission()
    }

    private fun checkUsageStatsPermission() {
        if (!childDeviceManager.checkUsageStatsPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Permiso de uso de apps necesario")
                .setMessage("Esta aplicación necesita permiso de uso de apps para el control parental.")
                .setPositiveButton("Solicitar permiso") { _, _ ->
                    childDeviceManager.requestUsageStatsPermission()
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.dismiss()
                    checkOverlayPermission()
                }
                .setOnDismissListener {
                    checkOverlayPermission()
                }
                .show()
            return
        }
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permiso de superposición necesario")
                .setMessage("Esta aplicación necesita permiso de superposición para el control parental.")
                .setPositiveButton("Solicitar permiso") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.dismiss()
                    onAllRequiredPermissionsChecked()
                }
                .setOnDismissListener {
                    onAllRequiredPermissionsChecked()
                }
                .show()
            return
        }
        onAllRequiredPermissionsChecked()
    }

    private fun onAllRequiredPermissionsChecked() {
        permissionsChecked = true
        if (areAllRequiredPermissionsGranted() && !appsSynced) {
            Log.d(TAG, "Todos los permisos concedidos, sincronizando apps")
            syncAppsIfNeeded()
        }
    }

    private fun areAllRequiredPermissionsGranted(): Boolean {
        val usageStatsPermission = childDeviceManager.checkUsageStatsPermission()
        val overlayPermission = Settings.canDrawOverlays(this)
        val locationPermission = locationManager.hasLocationPermission()
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // En versiones anteriores no se requiere
        }

        Log.d(TAG, "Estado de permisos - Uso de apps: $usageStatsPermission, Superposición: $overlayPermission, Ubicación: $locationPermission, Notificaciones: $notificationPermission")

        return usageStatsPermission && overlayPermission && locationPermission && notificationPermission
    }

    private fun showPermissionStatusDialog() {
        val usageStatsPermission = childDeviceManager.checkUsageStatsPermission()
        val overlayPermission = Settings.canDrawOverlays(this)
        val locationPermission = locationManager.hasLocationPermission()
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // En versiones anteriores no se requiere
        }

        val message = StringBuilder("Estado de permisos:\n\n")
        message.append("• Uso de apps: ${if (usageStatsPermission) "✅" else "❌"}\n")
        message.append("• Superposición: ${if (overlayPermission) "✅" else "❌"}\n")
        message.append("• Ubicación: ${if (locationPermission) "✅" else "❌"}\n")
        message.append("• Notificaciones: ${if (notificationPermission) "✅" else "❌"}\n\n")

        if (!usageStatsPermission || !overlayPermission || !locationPermission || !notificationPermission) {
            message.append("Algunos permisos son necesarios para el funcionamiento completo de la app.")
        } else {
            message.append("Todos los permisos están concedidos. La app funcionará correctamente.")
        }

        AlertDialog.Builder(this)
            .setTitle("Estado de Permisos")
            .setMessage(message.toString())
            .setPositiveButton("Verificar Permisos") { _, _ ->
                resetPermissionState()
                checkRequiredPermissions()
                checkLocationPermission()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun resetPermissionState() {
        Log.d(TAG, "Reiniciando estado de permisos")
        permissionsChecked = false
        appsSynced = false
    }

    private fun loadTimeLimitAlertState() {
        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        timeLimitAlertSent = prefs.getBoolean("time_limit_alert_sent", false)
        lastAlertDay = prefs.getInt("last_alert_day", -1)
        lastAlertTimeUsed = prefs.getLong("last_alert_time_used", 0L)

        // Verificar reset diario al cargar
        checkDailyReset()
    }

    private fun checkDailyReset() {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (currentDay != lastAlertDay) {
            Log.d(TAG, "Reset de alerta de tiempo agotado detectado")
            timeLimitAlertSent = false
            lastAlertDay = currentDay
            saveTimeLimitAlertState()
        }
    }

    private fun saveTimeLimitAlertState() {
        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean("time_limit_alert_sent", timeLimitAlertSent)
        editor.putInt("last_alert_day", lastAlertDay)
        editor.putLong("last_alert_time_used", lastAlertTimeUsed)
        editor.apply()
    }

    private fun syncAppsIfNeeded() {
        if (appsSynced) {
            Log.d(TAG, "Apps ya sincronizadas, saltando")
            return
        }

        if (childDeviceManager.checkUsageStatsPermission()) {
            Log.d(TAG, "Sincronizando apps instaladas")
            childDeviceManager.syncInstalledApps()
            appsSynced = true
        } else {
            Log.w(TAG, "No se pueden sincronizar apps sin permisos")
        }
    }
}