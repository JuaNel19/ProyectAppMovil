package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import android.location.Geocoder

class LocationHistoryAdapter : ListAdapter<UbicacionInfo, LocationHistoryAdapter.LocationViewHolder>(LocationDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(android.R.id.text1)
        private val tvSubtitle: TextView = itemView.findViewById(android.R.id.text2)
        fun bind(ubicacion: UbicacionInfo) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            tvTitle.text = dateFormat.format(Date(ubicacion.timestamp))
            // Mostrar primero lat/lon
            val latLonText = "Lat: %.5f, Lon: %.5f".format(ubicacion.latitud, ubicacion.longitud)
            tvSubtitle.text = latLonText
            // Intentar obtener dirección de forma asíncrona
            val context = itemView.context
            Thread {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(ubicacion.latitud, ubicacion.longitud, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val placeName = when {
                            !address.featureName.isNullOrEmpty() &&
                                    address.featureName != address.thoroughfare &&
                                    address.featureName != address.locality -> {
                                address.featureName
                            }
                            !address.thoroughfare.isNullOrEmpty() -> {
                                "la calle ${address.thoroughfare}"
                            }
                            !address.subLocality.isNullOrEmpty() -> {
                                address.subLocality
                            }
                            !address.locality.isNullOrEmpty() -> {
                                address.locality
                            }
                            !address.adminArea.isNullOrEmpty() -> {
                                address.adminArea
                            }
                            else -> null
                        }
                        if (!placeName.isNullOrEmpty()) {
                            itemView.post {
                                tvSubtitle.text = "$placeName ($latLonText)"
                            }
                        }
                    }
                } catch (_: Exception) {}
            }.start()
            // Abrir Google Maps al hacer click
            itemView.setOnClickListener {
                val uri = "geo:${ubicacion.latitud},${ubicacion.longitud}?q=${ubicacion.latitud},${ubicacion.longitud}(Ubicación)"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                intent.setPackage("com.google.android.apps.maps")
                context.startActivity(intent)
            }
        }
    }

    class LocationDiffCallback : DiffUtil.ItemCallback<UbicacionInfo>() {
        override fun areItemsTheSame(oldItem: UbicacionInfo, newItem: UbicacionInfo): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.latitud == newItem.latitud && oldItem.longitud == newItem.longitud
        }
        override fun areContentsTheSame(oldItem: UbicacionInfo, newItem: UbicacionInfo): Boolean {
            return oldItem == newItem
        }
    }
} 