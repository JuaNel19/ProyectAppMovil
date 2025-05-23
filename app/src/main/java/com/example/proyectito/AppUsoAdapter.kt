package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppUsoAdapter : ListAdapter<AppUsoInfo, AppUsoAdapter.AppViewHolder>(AppUsoDiffCallback()) {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvTiempoUso: TextView = view.findViewById(R.id.tvTiempoUso)
        val tvUltimoUso: TextView = view.findViewById(R.id.tvUltimoUso)

        fun bind(app: AppUsoInfo) {
            tvAppName.text = app.nombre

            // Formatear tiempo de uso
            val horas = app.tiempoUsado / 60
            val minutos = app.tiempoUsado % 60
            tvTiempoUso.text = when {
                horas > 0 -> "${horas}h ${minutos}min"
                else -> "${minutos}min"
            }

            // Formatear último uso
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            tvUltimoUso.text = "Último uso: ${dateFormat.format(Date(app.ultimoUso))}"

            // Cargar ícono usando Glide
            if (app.icono.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(app.icono)
                    .circleCrop()
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .error(android.R.drawable.sym_def_app_icon)
                    .into(ivAppIcon)
            } else {
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_uso, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

private class AppUsoDiffCallback : DiffUtil.ItemCallback<AppUsoInfo>() {
    override fun areItemsTheSame(oldItem: AppUsoInfo, newItem: AppUsoInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppUsoInfo, newItem: AppUsoInfo): Boolean {
        return oldItem == newItem
    }
}