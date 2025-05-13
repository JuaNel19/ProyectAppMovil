package com.example.proyectito

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_hijo)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()
        startCountdown()
        updateUsageTime()
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

    private fun startCountdown() {
        // Example: 1 hour 30 minutes countdown
        val totalTimeInMillis = (1 * 60 * 60 + 30 * 60) * 1000L

        countDownTimer = object : CountDownTimer(totalTimeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (1000 * 60 * 60)
                val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                val seconds = (millisUntilFinished % (1000 * 60)) / 1000

                tvCountdown.text = String.format(
                    "Tiempo restante: %02d:%02d:%02d",
                    hours, minutes, seconds
                )
            }

            override fun onFinish() {
                tvCountdown.text = "Tiempo restante: 00:00:00"
            }
        }.start()
    }

    private fun updateUsageTime() {
        // TODO: Implement actual usage time tracking
        // For now, using example values
        tvUsedTime.text = "Usado: 2h 30m"
        tvRemainingTime.text = "Restante: 1h 30m"
        progressBar.progress = 65 // Example: 65% used
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}