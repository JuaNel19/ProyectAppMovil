package com.example.proyectito

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class MessagesAdapter : ListAdapter<Message, MessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private var currentUserRole: String = "padre"

    fun setCurrentUserRole(role: String) {
        currentUserRole = role
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val tvMessageTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)

        fun bind(message: Message) {
            tvMessageContent.text = message.content
            tvSenderName.text = message.senderName
            
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvMessageTime.text = dateFormat.format(message.timestamp.toDate())

            // Ajustar el layout según el rol del usuario actual
            if (message.senderRole == currentUserRole) {
                // Mensaje del usuario actual (alineado a la derecha)
                messageContainer.setBackgroundResource(R.drawable.message_sent_background)
                tvMessageContent.setTextColor(itemView.context.getColor(android.R.color.white))
                tvSenderName.setTextColor(itemView.context.getColor(android.R.color.white))
                tvMessageTime.setTextColor(itemView.context.getColor(android.R.color.white))
                
                // Alinear a la derecha
                val layoutParams = messageContainer.layoutParams as LinearLayout.LayoutParams
                layoutParams.gravity = android.view.Gravity.END
                layoutParams.marginEnd = 0
                layoutParams.marginStart = 64
            } else {
                // Mensaje del otro usuario (alineado a la izquierda)
                messageContainer.setBackgroundResource(R.drawable.message_received_background)
                tvMessageContent.setTextColor(itemView.context.getColor(android.R.color.black))
                tvSenderName.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                tvMessageTime.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                
                // Alinear a la izquierda
                val layoutParams = messageContainer.layoutParams as LinearLayout.LayoutParams
                layoutParams.gravity = android.view.Gravity.START
                layoutParams.marginStart = 0
                layoutParams.marginEnd = 64
            }
        }
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
} 