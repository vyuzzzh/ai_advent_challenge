package com.example.ai_window.util

import com.example.ai_window.model.ChatMessage

/**
 * Utility object for estimating token counts.
 * Day 8: Token estimation for history compression.
 */
object TokenUtils {

    /**
     * Estimate the number of tokens in a text string.
     *
     * This is a heuristic approximation based on word count:
     * - Russian text: ~1.3 tokens per word
     * - English text: ~1.0 tokens per word
     *
     * Since we primarily use Russian in this app, we use 1.3 as the multiplier.
     *
     * Note: This is not exact - actual tokenization depends on the model's tokenizer.
     * For production, consider using the actual tokenizer API if available.
     *
     * @param text The text to estimate tokens for
     * @return Estimated number of tokens
     */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0

        val wordCount = text.split(Regex("\\s+")).size
        // Russian text typically uses ~1.3 tokens per word
        return (wordCount * 1.3).toInt()
    }

    /**
     * Estimate tokens for a ChatMessage.
     * If the message already has estimatedTokens set, returns that value.
     * Otherwise, calculates based on text content.
     *
     * @param message The message to estimate tokens for
     * @return Estimated number of tokens
     */
    fun estimateTokensForMessage(message: ChatMessage): Int {
        // Use cached value if available
        message.estimatedTokens?.let { return it }

        // Otherwise calculate
        return estimateTokens(message.text)
    }

    /**
     * Calculate total estimated tokens for a list of messages.
     *
     * @param messages List of messages
     * @return Total estimated tokens
     */
    fun estimateTokensForHistory(messages: List<ChatMessage>): Int {
        return messages.sumOf { estimateTokensForMessage(it) }
    }

    /**
     * Format token count for display with thousands separator.
     *
     * @param tokens Token count
     * @return Formatted string (e.g., "1,234")
     */
    fun formatTokenCount(tokens: Int): String {
        return tokens.toString().reversed().chunked(3).joinToString(",").reversed()
    }
}
