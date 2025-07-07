package com.example.proyectito

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var tvLocationInfo: TextView
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var childSelector: AutoCompleteTextView
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentMarker: com.google.android.gms.maps.model.Marker? = null
    private var isMapReady = false

    // Variables para el selector de hijos
    private var currentChildId: String? = null
    private var childrenList: List<ChildInfo> = emptyList()

    private lateinit var btnLocationHistory: View
    private lateinit var rvLocationHistory: RecyclerView
    private var isHistoryVisible = false
    private lateinit var locationHistoryAdapter: LocationHistoryAdapter
    private var locationHistoryList: List<UbicacionInfo> = emptyList()

    data class ChildInfo(
        val id: String,
        val name: String
    )

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
            tvLocationInfo = view.findViewById(R.id.tvLocationInfo)
            fabRefresh = view.findViewById(R.id.fabRefresh)
            childSelector = view.findViewById(R.id.childSelector)
            btnLocationHistory = view.findViewById(R.id.btnLocationHistory)
            rvLocationHistory = view.findViewById(R.id.rvLocationHistory)
            locationHistoryAdapter = LocationHistoryAdapter()
            rvLocationHistory.layoutManager = LinearLayoutManager(requireContext())
            rvLocationHistory.adapter = locationHistoryAdapter

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

            btnLocationHistory.setOnClickListener {
                isHistoryVisible = !isHistoryVisible
                rvLocationHistory.visibility = if (isHistoryVisible) View.VISIBLE else View.GONE
                val mapFragment = childFragmentManager.findFragmentById(R.id.map)
                if (isHistoryVisible) {
                    cargarHistorialUbicaciones()
                    btnLocationHistory.setBackgroundResource(R.color.colorAccent)
                    // Ocultar el mapa
                    if (mapFragment != null) {
                        childFragmentManager.beginTransaction().hide(mapFragment).commit()
                    }
                } else {
                    btnLocationHistory.setBackgroundResource(R.color.colorPrimaryDark)
                    // Mostrar el mapa
                    if (mapFragment != null) {
                        childFragmentManager.beginTransaction().show(mapFragment).commit()
                    }
                }
            }

            // Configurar selector de hijos
            setupChildSelector()

            // Cargar lista de hijos
            loadChildren()
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

        // Verificar que hay un hijo seleccionado
        if (currentChildId == null) {
            Toast.makeText(context, "Selecciona un hijo para ver su ubicación", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("UbicacionFragment", "Cargando ubicación para hijo: $currentChildId")

        // Consultar la ubicación específica del hijo seleccionado
        db.collection("ubicacion_actual")
            .whereEqualTo("parentId", userId)
            .whereEqualTo("childId", currentChildId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    val selectedChildName = childrenList.find { it.id == currentChildId }?.name ?: "Tu hijo"
                    updateLocationInfo("No hay ubicación disponible para $selectedChildName", selectedChildName)
                    Toast.makeText(context, "No hay ubicación disponible para este hijo", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val document = documents.documents[0]
                val latitude = document.getDouble("latitude")
                val longitude = document.getDouble("longitude")
                val timestamp = document.getLong("timestamp")
                val accuracy = document.getDouble("accuracy")

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
                    childId = currentChildId!!,
                    parentId = userId
                )

                val selectedChildName = childrenList.find { it.id == currentChildId }?.name ?: "Tu hijo"
                Log.d("UbicacionFragment", "Ubicación cargada para $selectedChildName: lat=$latitude, lon=$longitude, accuracy=$accuracy, age=${locationAge/1000}s")
                actualizarMapa(ubicacion, selectedChildName)
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

    private fun cargarHistorialUbicaciones() {
        val userId = auth.currentUser?.uid ?: return
        if (currentChildId == null) {
            Toast.makeText(context, "Selecciona un hijo para ver el historial", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("ubicacion_actual")
            .whereEqualTo("parentId", userId)
            .whereEqualTo("childId", currentChildId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100) // Traer más para filtrar
            .get()
            .addOnSuccessListener { documents ->
                val allList = documents.mapNotNull { doc ->
                    val lat = doc.getDouble("latitude")
                    val lon = doc.getDouble("longitude")
                    val ts = doc.getLong("timestamp")
                    val childId = doc.getString("childId") ?: ""
                    val parentId = doc.getString("parentId") ?: ""
                    if (lat != null && lon != null && ts != null) {
                        UbicacionInfo(lat, lon, ts, childId, parentId)
                    } else null
                }
                // Filtrar para dejar solo una ubicación cada 5 minutos
                val filteredList = mutableListOf<UbicacionInfo>()
                var lastTimestamp: Long? = null
                for (ubicacion in allList) {
                    if (lastTimestamp == null || (lastTimestamp - ubicacion.timestamp) >= 5 * 60 * 1000) {
                        filteredList.add(ubicacion)
                        lastTimestamp = ubicacion.timestamp
                    }
                }
                locationHistoryList = filteredList
                locationHistoryAdapter.submitList(filteredList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar historial: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarMapa(ubicacion: UbicacionInfo, selectedChildName: String) {
        try {
            val latLng = ubicacion.toLatLng()
            val map = map ?: return

            // Actualizar marcador con animación
            currentMarker?.remove()
            currentMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Ubicación actual de $selectedChildName")
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

            getPlaceInfo(latLng, selectedChildName)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al actualizar mapa: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPlaceInfo(latLng: LatLng, childName: String) {
        try {
            // Ejecutar geocoding en un hilo secundario para evitar bloqueos
            Thread {
                try {
                    Log.d("UbicacionFragment", "Usando geocoding inverso para obtener información de ubicación")
                    performReverseGeocoding(latLng, childName)
                } catch (e: Exception) {
                    Log.e("UbicacionFragment", "Error en geocoding: ${e.message}")
                    // Actualizar UI en el hilo principal
                    requireActivity().runOnUiThread {
                        updateLocationInfo("$childName se encuentra aquí", childName)
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e("UbicacionFragment", "Error al obtener información del lugar: ${e.message}")
            // En caso de error, mostrar mensaje genérico
            updateLocationInfo("$childName se encuentra aquí", childName)
        }
    }

    private fun performReverseGeocoding(latLng: LatLng, childName: String) {
        try {
            val geocoder = android.location.Geocoder(requireContext(), Locale.getDefault())

            // Usar el método síncrono que es compatible con API level 24
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)

            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]

                // Intentar obtener el nombre más específico y útil disponible
                val placeName = when {
                    // Si hay un nombre de establecimiento específico
                    !address.featureName.isNullOrEmpty() &&
                            address.featureName != address.thoroughfare &&
                            address.featureName != address.locality -> {
                        address.featureName
                    }
                    // Si hay una calle específica
                    !address.thoroughfare.isNullOrEmpty() -> {
                        "la calle ${address.thoroughfare}"
                    }
                    // Si hay un vecindario específico
                    !address.subLocality.isNullOrEmpty() -> {
                        address.subLocality
                    }
                    // Si hay una localidad específica
                    !address.locality.isNullOrEmpty() -> {
                        address.locality
                    }
                    // Si hay un área administrativa
                    !address.adminArea.isNullOrEmpty() -> {
                        address.adminArea
                    }
                    else -> null
                }

                if (placeName != null && placeName.isNotEmpty()) {
                    updateLocationInfo("$childName se encuentra en $placeName", childName)
                    Log.d("UbicacionFragment", "Ubicación obtenida por geocoding: $placeName")
                } else {
                    updateLocationInfo("$childName se encuentra aquí", childName)
                    Log.d("UbicacionFragment", "No se pudo obtener información específica de la ubicación")
                }
            } else {
                updateLocationInfo("$childName se encuentra aquí", childName)
                Log.d("UbicacionFragment", "No se encontraron direcciones para las coordenadas")
            }
        } catch (e: Exception) {
            Log.e("UbicacionFragment", "Error en geocoding inverso: ${e.message}")
            updateLocationInfo("$childName se encuentra aquí", childName)
        }
    }

    private fun updateLocationInfo(locationText: String, childName: String) {
        try {
            // Asegurar que la actualización de UI se haga en el hilo principal
            if (isAdded && activity != null) {
                requireActivity().runOnUiThread {
                    try {
                        tvLocationInfo.text = locationText
                        Log.d("UbicacionFragment", "Información de ubicación actualizada: $locationText")
                    } catch (e: Exception) {
                        Log.e("UbicacionFragment", "Error al actualizar TextView: ${e.message}")
                    }
                }
            } else {
                Log.w("UbicacionFragment", "Fragment no está adjunto o activity es null")
            }
        } catch (e: Exception) {
            Log.e("UbicacionFragment", "Error al actualizar información de ubicación: ${e.message}")
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private fun setupChildSelector() {
        childSelector.setOnItemClickListener { _, _, position, _ ->
            val selectedChild = childrenList[position]
            currentChildId = selectedChild.id
            // Ocultar historial al cambiar de hijo
            isHistoryVisible = false
            rvLocationHistory.visibility = View.GONE
            btnLocationHistory.setBackgroundResource(R.color.colorPrimaryDark)
            // Mostrar el mapa si estaba oculto
            val mapFragment = childFragmentManager.findFragmentById(R.id.map)
            if (mapFragment != null) {
                childFragmentManager.beginTransaction().show(mapFragment).commit()
            }
            Log.d("UbicacionFragment", "Hijo seleccionado: ${selectedChild.name} (${selectedChild.id})")
            updateLocationInfo("Cargando ubicación de ${selectedChild.name}...", selectedChild.name)
            cargarUbicacion()
        }
    }

    private fun updateChildSelector() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            childrenList.map { it.name }
        )
        childSelector.setAdapter(adapter)
        childSelector.isEnabled = true

        if (childrenList.isNotEmpty()) {
            // Buscar el hijo previamente seleccionado
            val selectedIndex = childrenList.indexOfFirst { it.id == currentChildId }
            val indexToSelect = if (selectedIndex != -1) selectedIndex else 0
            childSelector.setText(childrenList[indexToSelect].name, false)
            currentChildId = childrenList[indexToSelect].id
            updateLocationInfo("Cargando ubicación de ${childrenList[indexToSelect].name}...", childrenList[indexToSelect].name)
            cargarUbicacion()
        } else {
            Log.d("UbicacionFragment", "No hay hijos disponibles")
            childSelector.isEnabled = false
            updateLocationInfo("No hay hijos asociados", "")
        }
    }

    private fun loadChildren() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("parent_child_relations")
            .whereEqualTo("parent_id", userId)
            .get()
            .addOnSuccessListener { documents ->
                val childrenIds = documents.mapNotNull { it.getString("child_id") }
                if (childrenIds.isEmpty()) {
                    Log.d("UbicacionFragment", "No se encontraron hijos asociados")
                    return@addOnSuccessListener
                }

                loadChildrenInfo(childrenIds)
            }
            .addOnFailureListener { e ->
                Log.e("UbicacionFragment", "Error al cargar hijos: ${e.message}")
                Toast.makeText(context, "Error al cargar hijos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadChildrenInfo(childrenIds: List<String>) {
        var loadedCount = 0
        val tempList = mutableListOf<ChildInfo>()

        childrenIds.forEach { childId ->
            db.collection("hijos").document(childId)
                .get()
                .addOnSuccessListener { document ->
                    val name = document.getString("nombre") ?: "Hijo"
                    tempList.add(ChildInfo(childId, name))
                    loadedCount++

                    if (loadedCount == childrenIds.size) {
                        childrenList = tempList.sortedBy { it.name }
                        updateChildSelector()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("UbicacionFragment", "Error al cargar información del hijo $childId: ${e.message}")
                    loadedCount++
                    if (loadedCount == childrenIds.size) {
                        childrenList = tempList.sortedBy { it.name }
                        updateChildSelector()
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map = null
        currentMarker = null
    }
}