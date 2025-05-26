package com.example.proyectito

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UbicacionFragment : Fragment() {

    private var map: GoogleMap? = null
    private lateinit var tvLastUpdate: TextView
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentMarker: com.google.android.gms.maps.model.Marker? = null
    private var isMapReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ubicacion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Inicializar Firebase
            db = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()

            // Inicializar vistas
            tvLastUpdate = view.findViewById(R.id.tvLastUpdate)
            fabRefresh = view.findViewById(R.id.fabRefresh)

            // Configurar mapa
            val mapFragment = childFragmentManager
                .findFragmentById(R.id.map) as? SupportMapFragment

            if (mapFragment == null) {
                Toast.makeText(context, "Error al cargar el mapa", Toast.LENGTH_SHORT).show()
                return
            }

            mapFragment.getMapAsync { googleMap ->
                map = googleMap
                isMapReady = true
                googleMap.uiSettings.apply {
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
        } catch (e: Exception) {
            Toast.makeText(context, "Error al inicializar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarUbicacion() {
        if (!isMapReady) {
            Toast.makeText(context, "El mapa aún no está listo", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        // Consultar directamente en ubicacion_actual usando el parentId
        db.collection("ubicacion_actual")
            .whereEqualTo("parentId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, "No hay ubicaciones disponibles para tus hijos", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Obtener el documento más reciente manualmente
                val document = documents.documents.maxByOrNull { it.getLong("timestamp") ?: 0L }
                if (document == null) {
                    Toast.makeText(context, "Error al procesar la ubicación", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val latitude = document.getDouble("latitude")
                val longitude = document.getDouble("longitude")
                val timestamp = document.getLong("timestamp")
                val accuracy = document.getDouble("accuracy")
                val childId = document.getString("childId")

                if (latitude == null || longitude == null || timestamp == null) {
                    Toast.makeText(context, "Error: datos de ubicación incompletos", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Verificar si la ubicación es reciente (menos de 5 minutos)
                val locationAge = System.currentTimeMillis() - timestamp
                if (locationAge > 5 * 60 * 1000) { // 5 minutos
                    Toast.makeText(context, "La ubicación mostrada puede no ser la más reciente", Toast.LENGTH_LONG).show()
                }

                val ubicacion = UbicacionInfo(
                    latitud = latitude,
                    longitud = longitude,
                    timestamp = timestamp,
                    childId = childId ?: "",
                    parentId = userId
                )

                Log.d("UbicacionFragment", "Ubicación cargada: lat=$latitude, lon=$longitude, accuracy=$accuracy, age=${locationAge/1000}s")
                actualizarMapa(ubicacion)
            }
            .addOnFailureListener { e ->
                Log.e("UbicacionFragment", "Error al cargar ubicación: ${e.message}")
                if (e.message?.contains("requires an index") == true) {
                    Toast.makeText(context, "Error: Se requiere configurar un índice en Firestore. Por favor, contacta al administrador.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Error al cargar ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun actualizarMapa(ubicacion: UbicacionInfo) {
        try {
            val latLng = ubicacion.toLatLng()
            val map = map ?: return

            // Actualizar marcador con animación
            currentMarker?.remove()
            currentMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Ubicación actual de tu hijo")
                    .snippet("Última actualización: ${formatTimestamp(ubicacion.timestamp)}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )

            // Mover cámara con animación y zoom más cercano
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLng, 17f),
                1000,
                null
            )

            // Actualizar timestamp
            tvLastUpdate.text = "Última actualización: ${formatTimestamp(ubicacion.timestamp)}"
        } catch (e: Exception) {
            Toast.makeText(context, "Error al actualizar mapa: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map = null
        currentMarker = null
    }
}