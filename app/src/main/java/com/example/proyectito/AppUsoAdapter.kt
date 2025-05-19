package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AppUsoAdapter(
    private var apps: List<AppUsoInfo>
) : RecyclerView.Adapter<AppUsoAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvTiempoUso: TextView = view.findViewById(R.id.tvTiempoUso)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_uso, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]

        holder.tvAppName.text = app.nombre

        // Formatear tiempo de uso
        val horas = app.tiempoUsado / 60
        val minutos = app.tiempoUsado % 60
        holder.tvTiempoUso.text = when {
            horas > 0 -> "${horas}h ${minutos}min"
            else -> "${minutos}min"
        }

        // Cargar ícono usando Glide
        if (app.icono.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(app.icono)
                .circleCrop()
                .into(holder.ivAppIcon)
        } else {
            // Usar ícono por defecto
            holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
    }

    override fun getItemCount() = apps.size

    fun updateApps(newApps: List<AppUsoInfo>) {
        apps = newApps.sortedByDescending { it.tiempoUsado }
        notifyDataSetChanged()
    }
}