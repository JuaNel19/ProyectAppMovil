package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class AlertaAdapter(
    private var alertas: List<Alerta>,
    private val onAlertaClick: (Alerta) -> Unit
) : RecyclerView.Adapter<AlertaAdapter.AlertaViewHolder>() {

    class AlertaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivTipoAlerta: ImageView = view.findViewById(R.id.ivTipoAlerta)
        val tvTipoAlerta: TextView = view.findViewById(R.id.tvTipoAlerta)
        val tvMensaje: TextView = view.findViewById(R.id.tvMensaje)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val ivEstado: ImageView = view.findViewById(R.id.ivEstado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alerta, parent, false)
        return AlertaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertaViewHolder, position: Int) {
        val alerta = alertas[position]

        // Configurar tipo de alerta
        when (alerta.tipo) {
            Alerta.TIPO_INSTALACION -> {
                holder.ivTipoAlerta.setImageResource(android.R.drawable.ic_menu_upload)
                holder.tvTipoAlerta.text = "Instalación de app"
            }
            Alerta.TIPO_SOLICITUD_TIEMPO -> {
                holder.ivTipoAlerta.setImageResource(android.R.drawable.ic_menu_recent_history)
                holder.tvTipoAlerta.text = "Solicitud de tiempo"
            }
            Alerta.TIPO_DESBLOQUEO -> {
                holder.ivTipoAlerta.setImageResource(android.R.drawable.ic_menu_manage)
                holder.tvTipoAlerta.text = "Solicitud de desbloqueo"
            }
            Alerta.TIPO_USO_BLOQUEADO -> {
                holder.ivTipoAlerta.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                holder.tvTipoAlerta.text = "Intento de uso bloqueado"
            }
        }

        // Configurar estado
        when (alerta.estado) {
            Alerta.ESTADO_PENDIENTE -> {
                holder.ivEstado.setImageResource(android.R.drawable.ic_popup_sync)
            }
            Alerta.ESTADO_APROBADO -> {
                holder.ivEstado.setImageResource(android.R.drawable.ic_menu_send)
            }
            Alerta.ESTADO_DENEGADO -> {
                holder.ivEstado.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            }
        }

        holder.tvMensaje.text = alerta.mensaje
        holder.tvFecha.text = formatDate(alerta.fecha)

        holder.itemView.setOnClickListener { onAlertaClick(alerta) }
    }

    override fun getItemCount() = alertas.size

    fun updateAlertas(newAlertas: List<Alerta>) {
        alertas = newAlertas
        notifyDataSetChanged()
    }

    private fun formatDate(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }
}