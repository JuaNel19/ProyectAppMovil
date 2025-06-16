package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.example.proyectito.Alerta
import com.example.proyectito.TipoAlerta

class AlertasAdapter(
    private val onAlertaClick: (Alerta) -> Unit
) : ListAdapter<Alerta, AlertasAdapter.AlertaViewHolder>(AlertaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alerta, parent, false)
        return AlertaViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImageView: ImageView = itemView.findViewById(R.id.iconImageView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        private val actionButton: MaterialButton = itemView.findViewById(R.id.actionButton)

        fun bind(alerta: Alerta) {
            // Configurar el ícono según el tipo de alerta
            iconImageView.setImageResource(
                when (alerta.tipo) {
                    TipoAlerta.DESBLOQUEO_APP -> R.drawable.ic_lock_open
                    TipoAlerta.TIEMPO_AGOTADO -> R.drawable.ic_time
                }
            )

            // Configurar el título según el tipo de alerta
            titleTextView.text = when (alerta.tipo) {
                TipoAlerta.DESBLOQUEO_APP -> "Solicitud de desbloqueo"
                TipoAlerta.TIEMPO_AGOTADO -> "Tiempo agotado"
            }

            // Configurar la descripción
            descriptionTextView.text = alerta.mensaje

            // Configurar la hora
            timeTextView.text = alerta.getFormattedTime()

            // Configurar el botón de acción
            if (alerta.tipo == TipoAlerta.DESBLOQUEO_APP && !alerta.leida) {
                actionButton.visibility = View.VISIBLE
                actionButton.text = "Responder"
                actionButton.setOnClickListener {
                    onAlertaClick(alerta)
                }
            } else {
                actionButton.visibility = View.GONE
            }

            // Configurar el fondo según si está leída o no
            itemView.alpha = if (alerta.leida) 0.7f else 1.0f
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
}