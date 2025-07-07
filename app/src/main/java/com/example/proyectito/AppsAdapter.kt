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

class AppsAdapter(
    private val onAppBlocked: (AppInfo, Boolean) -> Unit
) : ListAdapter<AppInfo, AppsAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageViewIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val textViewName: TextView = itemView.findViewById(R.id.tvAppName)
        private val textViewPackage: TextView = itemView.findViewById(R.id.tvPackageName)
        private val switchBlock: SwitchMaterial = itemView.findViewById(R.id.switchBloqueo)

        fun bind(app: AppInfo) {
            textViewName.text = app.nombre
            textViewPackage.text = app.packageName
            switchBlock.isChecked = app.bloqueado

            // Mostrar el icono real
            imageViewIcon.setImageDrawable(app.icono)

            // Configurar listener del switch
            switchBlock.setOnCheckedChangeListener { _, isChecked ->
                onAppBlocked(app, isChecked)
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
} 