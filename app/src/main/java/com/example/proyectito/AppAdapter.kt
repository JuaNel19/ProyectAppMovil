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
import com.google.android.material.switchmaterial.SwitchMaterial

class AppAdapter(
    private val onSwitchChanged: (AppInfo, Boolean) -> Unit
) : ListAdapter<AppInfo, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val switchBloqueo: SwitchMaterial = view.findViewById(R.id.switchBloqueo)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)

        fun bind(app: AppInfo, onSwitchChanged: (AppInfo, Boolean) -> Unit) {
            tvAppName.text = app.nombre
            tvPackageName.text = app.packageName

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

            // Configurar switch
            switchBloqueo.setOnCheckedChangeListener(null) // Remover listener anterior
            switchBloqueo.isChecked = app.bloqueado
            switchBloqueo.setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(app, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), onSwitchChanged)
    }

    fun updateAppState(packageName: String, isBlocked: Boolean) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = currentList[index]
            currentList[index] = app.copy(bloqueado = isBlocked)
            submitList(currentList)
        }
    }
}

private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
    override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem == newItem
    }
} 