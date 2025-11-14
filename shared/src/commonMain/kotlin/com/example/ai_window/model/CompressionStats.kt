package com.example.ai_window.model

/**
 * Statistics for comparing chat performance with and without compression.
 * Day 8: Compression comparison metrics.
 */
data class CompressionStats(
    // Message counts
    val messagesWithCompression: Int = 0,
    val messagesWithoutCompression: Int = 0,
    val summariesCreated: Int = 0,

    // Token usage (latest request)
    val latestInputTokensWithCompression: Int = 0,
    val latestInputTokensWithoutCompression: Int = 0,
    val latestOutputTokensWithCompression: Int = 0,
    val latestOutputTokensWithoutCompression: Int = 0,

    // Total tokens across all requests
    val totalInputTokensWithCompression: Int = 0,
    val totalInputTokensWithoutCompression: Int = 0,
    val totalOutputTokensWithCompression: Int = 0,
    val totalOutputTokensWithoutCompression: Int = 0,

    // Response times (milliseconds)
    val latestResponseTimeWithCompression: Long = 0,
    val latestResponseTimeWithoutCompression: Long = 0,
    val avgResponseTimeWithCompression: Long = 0,
    val avgResponseTimeWithoutCompression: Long = 0,

    // Request counts
    val requestCountWithCompression: Int = 0,
    val requestCountWithoutCompression: Int = 0,

    // Compression overhead (time spent on summarization)
    val totalSummarizationTime: Long = 0,
    val summarizationCount: Int = 0
) {
    /**
     * Calculate percentage of tokens saved by compression.
     * Based on total input tokens (context size).
     */
    val tokenSavingsPercent: Double
        get() = if (totalInputTokensWithoutCompression > 0) {
            val saved = totalInputTokensWithoutCompression - totalInputTokensWithCompression
            (saved.toDouble() / totalInputTokensWithoutCompression.toDouble()) * 100
        } else {
            0.0
        }

    /**
     * Calculate total tokens used (input + output) for compressed chat.
     */
    val totalTokensWithCompression: Int
        get() = totalInputTokensWithCompression + totalOutputTokensWithCompression

    /**
     * Calculate total tokens used (input + output) for uncompressed chat.
     */
    val totalTokensWithoutCompression: Int
        get() = totalInputTokensWithoutCompression + totalOutputTokensWithoutCompression

    /**
     * Average time per summarization operation.
     */
    val avgSummarizationTime: Long
        get() = if (summarizationCount > 0) {
            totalSummarizationTime / summarizationCount
        } else {
            0
        }

    /**
     * Total overhead added by compression (summarization time).
     */
    val compressionOverhead: Long
        get() = totalSummarizationTime

    /**
     * Net time saved/lost by using compression.
     * Negative value means compression added overhead, positive means it saved time.
     */
    val netTimeSavings: Long
        get() {
            val totalTimeWithCompression = (avgResponseTimeWithCompression * requestCountWithCompression) + compressionOverhead
            val totalTimeWithoutCompression = avgResponseTimeWithoutCompression * requestCountWithoutCompression
            return totalTimeWithoutCompression - totalTimeWithCompression
        }

    /**
     * Format token savings for display.
     */
    fun formatTokenSavings(): String {
        val saved = totalInputTokensWithoutCompression - totalInputTokensWithCompression
        val percent = (tokenSavingsPercent * 10).toInt() / 10.0 // Round to 1 decimal
        return if (saved > 0) {
            "↓ $saved токенов ($percent%)"
        } else if (saved < 0) {
            "↑ ${-saved} токенов (${-percent}%)"
        } else {
            "без изменений"
        }
    }

    /**
     * Quality assessment based on metrics.
     */
    fun getQualityAssessment(): String {
        return when {
            tokenSavingsPercent >= 50 -> "Отлично (>50% экономии)"
            tokenSavingsPercent >= 30 -> "Хорошо (>30% экономии)"
            tokenSavingsPercent >= 10 -> "Приемлемо (>10% экономии)"
            tokenSavingsPercent > 0 -> "Низкая эффективность (<10% экономии)"
            else -> "Без эффекта"
        }
    }

    /**
     * Average context size per request (with compression).
     */
    val avgContextSizeWithCompression: Int
        get() = if (requestCountWithCompression > 0) {
            totalInputTokensWithCompression / requestCountWithCompression
        } else {
            0
        }

    /**
     * Average context size per request (without compression).
     */
    val avgContextSizeWithoutCompression: Int
        get() = if (requestCountWithoutCompression > 0) {
            totalInputTokensWithoutCompression / requestCountWithoutCompression
        } else {
            0
        }
}
