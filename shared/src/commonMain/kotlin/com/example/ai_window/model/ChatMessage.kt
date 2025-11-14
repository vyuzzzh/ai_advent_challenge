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
    val parseWarning: String? = null,  // Store any parsing warnings
    // Day 8: Compression fields
    val isSummary: Boolean = false,  // true if this is a summary message
    val summarizedCount: Int? = null,  // number of messages replaced by this summary
    val originalIds: List<String>? = null,  // IDs of original messages that were summarized
    val estimatedTokens: Int? = null  // estimated token count for this message
)

enum class ChatState {
    IDLE,
    LOADING,
    ERROR
}