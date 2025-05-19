package com.example.proyectito

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UbicacionFragment : Fragment() {

    private lateinit var map: GoogleMap
    private lateinit var tvLastUpdate: TextView
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentMarker: com.google.android.gms.maps.model.Marker? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ubicacion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Inicializar vistas
        tvLastUpdate = view.findViewById(R.id.tvLastUpdate)
        fabRefresh = view.findViewById(R.id.fabRefresh)

        // Configurar mapa
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            map = googleMap
            map.uiSettings.apply {
                isZoomControlsEnabled = true
                isMyLocationButtonEnabled = true
                isCompassEnabled = true
            }
            cargarUbicacion()
        }

        // Configurar FAB
        fabRefresh.setOnClickListener {
            cargarUbicacion()
        }
    }

    private fun cargarUbicacion() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("ubicacion_actual")
            .whereEqualTo("parentId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                val ubicacion = snapshot?.documents?.firstOrNull()?.toObject(UbicacionInfo::class.java)
                ubicacion?.let { actualizarMapa(it) }
            }
    }

    private fun actualizarMapa(ubicacion: UbicacionInfo) {
        val latLng = ubicacion.toLatLng()

        // Actualizar marcador con animación
        currentMarker?.remove()
        currentMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Ubicación actual de tu hijo")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        // Mover cámara con animación
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, 15f),
            1000,
            null
        )

        // Actualizar timestamp
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val fecha = Date(ubicacion.timestamp)
        tvLastUpdate.text = "Última actualización: ${dateFormat.format(fecha)}"
    }
}