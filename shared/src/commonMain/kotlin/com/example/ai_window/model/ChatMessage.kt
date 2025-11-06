package com.example.ai_window.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = 0L,
    // NEW: Title and Metadata for AI responses
    val title: String? = null,  // null for user messages
    val metadata: ResponseMetadata? = null,  // null for user messages
    val parseWarning: String? = null  // Store any parsing warnings
)

enum class ChatState {
    IDLE,
    LOADING,
    ERROR
}