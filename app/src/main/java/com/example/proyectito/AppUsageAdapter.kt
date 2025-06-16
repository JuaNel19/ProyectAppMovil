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

class AppUsageAdapter : ListAdapter<AppUsageInfo, AppUsageAdapter.AppUsageViewHolder>(AppUsageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return AppUsageViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppUsageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val usageTime: TextView = itemView.findViewById(R.id.usageTime)

        fun bind(app: AppUsageInfo) {
            appName.text = app.appName
            usageTime.text = app.getFormattedUsageTime()

            // Cargar el ícono de la app
            try {
                val packageManager = itemView.context.packageManager
                val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                Glide.with(itemView.context)
                    .load(packageManager.getApplicationIcon(appInfo))
                    .into(appIcon)
            } catch (e: Exception) {
                // Si no se puede cargar el ícono, usar uno por defecto
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }
    }
}

class AppUsageDiffCallback : DiffUtil.ItemCallback<AppUsageInfo>() {
    override fun areItemsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppUsageInfo, newItem: AppUsageInfo): Boolean {
        return oldItem == newItem
    }
}