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

class BlockedAppsAdapter(
    private val onUnblockRequest: (String, String) -> Unit
) : ListAdapter<BlockedApp, BlockedAppsAdapter.BlockedAppViewHolder>(BlockedAppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_app, parent, false)
        return BlockedAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlockedAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BlockedAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val requestButton: ImageButton = itemView.findViewById(R.id.requestButton)

        fun bind(app: BlockedApp) {
            appName.text = app.appName
            appIcon.setImageDrawable(app.icon)

            requestButton.setOnClickListener {
                onUnblockRequest(app.packageName, app.appName)
            }
        }
    }

    private class BlockedAppDiffCallback : DiffUtil.ItemCallback<BlockedApp>() {
        override fun areItemsTheSame(oldItem: BlockedApp, newItem: BlockedApp): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: BlockedApp, newItem: BlockedApp): Boolean {
            return oldItem == newItem
        }
    }
}

data class BlockedApp(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable
)