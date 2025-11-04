package com.example.ai_window.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = 0L
)

enum class ChatState {
    IDLE,
    LOADING,
    ERROR
}