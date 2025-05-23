package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class AlertaAdapter(
    private val onAlertaClick: (Alerta) -> Unit
) : ListAdapter<Alerta, AlertaAdapter.AlertaViewHolder>(AlertaDiffCallback()) {

    class AlertaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivTipoAlerta: ImageView = view.findViewById(R.id.ivTipoAlerta)
        val tvTipoAlerta: TextView = view.findViewById(R.id.tvTipoAlerta)
        val tvMensaje: TextView = view.findViewById(R.id.tvMensaje)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val ivEstado: ImageView = view.findViewById(R.id.ivEstado)

        fun bind(alerta: Alerta, onAlertaClick: (Alerta) -> Unit) {
            // Configurar tipo de alerta
            when (alerta.tipo) {
                Alerta.TIPO_INSTALACION -> {
                    ivTipoAlerta.setImageResource(R.drawable.ic_install)
                    tvTipoAlerta.text = "Instalación de app"
                }
                Alerta.TIPO_SOLICITUD_TIEMPO -> {
                    ivTipoAlerta.setImageResource(R.drawable.ic_time)
                    tvTipoAlerta.text = "Solicitud de tiempo"
                }
                Alerta.TIPO_DESBLOQUEO -> {
                    ivTipoAlerta.setImageResource(android.R.drawable.ic_lock_lock)
                    tvTipoAlerta.text = "Solicitud de desbloqueo"
                }
                Alerta.TIPO_USO_BLOQUEADO -> {
                    ivTipoAlerta.setImageResource(R.drawable.ic_block)
                    tvTipoAlerta.text = "Intento de uso bloqueado"
                }
            }

            // Configurar estado
            when (alerta.estado) {
                Alerta.ESTADO_PENDIENTE -> {
                    ivEstado.setImageResource(R.drawable.ic_pending)
                }
                Alerta.ESTADO_APROBADO -> {
                    ivEstado.setImageResource(R.drawable.ic_approved)
                }
                Alerta.ESTADO_DENEGADO -> {
                    ivEstado.setImageResource(R.drawable.ic_denied)
                }
            }

            tvMensaje.text = alerta.mensaje
            tvFecha.text = formatDate(alerta.fecha)

            itemView.setOnClickListener { onAlertaClick(alerta) }
        }

        private fun formatDate(timestamp: Timestamp): String {
            return try {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                sdf.format(timestamp.toDate())
            } catch (e: Exception) {
                "Fecha no disponible"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alerta, parent, false)
        return AlertaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertaViewHolder, position: Int) {
        holder.bind(getItem(position), onAlertaClick)
    }
}

private class AlertaDiffCallback : DiffUtil.ItemCallback<Alerta>() {
    override fun areItemsTheSame(oldItem: Alerta, newItem: Alerta): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Alerta, newItem: Alerta): Boolean {
        return oldItem == newItem
    }
}