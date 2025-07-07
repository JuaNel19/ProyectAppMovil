package com.example.proyectito

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val senderRole: String = "",
    val isRead: Boolean = false,
    val chatId: String = ""
) 