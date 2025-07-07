package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AppTiempoAdapter(
    private val onEditClick: (AppTiempoInfo) -> Unit
) : ListAdapter<AppTiempoInfo, AppTiempoAdapter.AppViewHolder>(AppTiempoDiffCallback()) {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvLimite: TextView = view.findViewById(R.id.tvLimite)
        val btnEditar: ImageButton = view.findViewById(R.id.btnEditar)

        fun bind(app: AppTiempoInfo, onEditClick: (AppTiempoInfo) -> Unit) {
            tvAppName.text = app.nombre

            // Mostrar límite de tiempo
            tvLimite.text = if (app.limiteDiario > 0) {
                "Límite: ${app.limiteDiario} minutos"
            } else {
                "Sin límite"
            }

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

            // Configurar botón de edición
            btnEditar.setOnClickListener {
                onEditClick(app)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_tiempo, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), onEditClick)
    }

    fun updateAppTimeLimit(packageName: String, newLimit: Int) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = currentList[index]
            currentList[index] = app.copy(limiteDiario = newLimit)
            submitList(currentList)
        }
    }
}

private class AppTiempoDiffCallback : DiffUtil.ItemCallback<AppTiempoInfo>() {
    override fun areItemsTheSame(oldItem: AppTiempoInfo, newItem: AppTiempoInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppTiempoInfo, newItem: AppTiempoInfo): Boolean {
        return oldItem == newItem
    }
}