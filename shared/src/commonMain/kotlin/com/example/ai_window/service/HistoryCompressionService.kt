package com.example.ai_window.service

import com.example.ai_window.model.*
import com.example.ai_window.util.TokenUtils
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Service for compressing conversation history using YandexGPT summarization.
 * Day 8: History compression mechanism.
 *
 * Strategy: Every N messages, summarize old messages into a single summary message.
 *
 * @param apiKey Yandex API key
 * @param folderId Yandex folder ID
 * @param compressionThreshold Number of messages after which compression is triggered (default: 10)
 */
class HistoryCompressionService(
    private val apiKey: String,
    private val folderId: String,
    private val compressionThreshold: Int = 10
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val baseUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
    private val modelUri = "gpt://$folderId/yandexgpt-lite/latest"

    /**
     * Check if the history should be compressed based on message count.
     *
     * @param history Current conversation history
     * @return true if compression should be applied
     */
    fun shouldCompress(history: List<ChatMessage>): Boolean {
        // Don't compress if history is too short
        if (history.size <= compressionThreshold) return false

        // Check if we have at least compressionThreshold messages since last summary
        val lastSummaryIndex = history.indexOfLast { it.isSummary }

        return if (lastSummaryIndex == -1) {
            // No summaries yet - compress if we have enough messages
            history.size >= compressionThreshold
        } else {
            // Compress if we have compressionThreshold new messages after last summary
            (history.size - lastSummaryIndex - 1) >= compressionThreshold
        }
    }

    /**
     * Compress conversation history by summarizing old messages.
     *
     * Takes the first N messages and replaces them with a summary message.
     * Keeps recent messages intact for context.
     *
     * @param history Current conversation history
     * @param keepRecentCount Number of recent messages to keep intact (default: 5)
     * @return Compressed history with summary
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun compressHistory(
        history: List<ChatMessage>,
        keepRecentCount: Int = 5
    ): kotlin.Result<List<ChatMessage>> {
        if (!shouldCompress(history)) {
            return kotlin.Result.success(history)
        }

        println("🗜️ Compressing history: ${history.size} messages")

        // Find existing summaries at the beginning of history
        val existingSummaries = history.takeWhile { it.isSummary }
        val nonSummaryMessages = history.drop(existingSummaries.size)

        println("  - Found ${existingSummaries.size} existing summaries")

        // Split non-summary messages into parts to summarize and parts to keep
        val toSummarize = nonSummaryMessages.dropLast(keepRecentCount)
        val toKeep = nonSummaryMessages.takeLast(keepRecentCount)

        println("  - Summarizing: ${toSummarize.size} new messages")
        println("  - Keeping: ${toKeep.size} recent messages")

        // If nothing to summarize (all messages are recent), return as is
        if (toSummarize.isEmpty()) {
            println("  - Nothing to summarize, keeping all messages")
            return kotlin.Result.success(history)
        }

        // Create summary of messages between last summary and recent messages
        return summarizeMessages(toSummarize).fold(
            onSuccess = { summaryText ->
                val summaryMessage = ChatMessage(
                    id = Uuid.random().toString(),
                    text = summaryText,
                    isUser = false,
                    timestamp = 0L, // Timestamp not critical for summary
                    title = "📝 Краткое содержание диалога (#${existingSummaries.size + 1})",
                    metadata = ResponseMetadata(
                        confidence = 0.8,
                        category = "general"
                    ),
                    isSummary = true,
                    summarizedCount = toSummarize.size,
                    originalIds = toSummarize.map { it.id },
                    estimatedTokens = TokenUtils.estimateTokens(summaryText)
                )

                // Preserve existing summaries + add new summary + keep recent messages
                val compressed = existingSummaries + listOf(summaryMessage) + toKeep

                println("✅ Compression complete: ${history.size} → ${compressed.size} messages")
                println("  Token estimate: ${TokenUtils.estimateTokensForHistory(history)} → ${TokenUtils.estimateTokensForHistory(compressed)}")

                kotlin.Result.success(compressed)
            },
            onFailure = { exception ->
                println("❌ Compression failed: ${exception.message}")
                // On failure, return original history
                kotlin.Result.failure(exception)
            }
        )
    }

    /**
     * Summarize a list of messages using YandexGPT.
     *
     * Creates a concise summary of the conversation that preserves key context.
     *
     * @param messages Messages to summarize
     * @return Summary text
     */
    private suspend fun summarizeMessages(messages: List<ChatMessage>): kotlin.Result<String> {
        if (messages.isEmpty()) {
            return kotlin.Result.success("")
        }

        return try {
            // Format conversation for summarization
            val conversationText = messages.joinToString(separator = "\n\n") { msg ->
                val role = if (msg.isUser) "Пользователь" else "Ассистент"
                "$role: ${msg.text}"
            }

            val summarizationPrompt = """
                Создай краткое содержание следующего диалога в 3-5 предложениях.
                Сохрани ключевые факты, темы обсуждения и важный контекст.
                Отвечай ТОЛЬКО текстом резюме, БЕЗ дополнительных пояснений.

                Диалог:
                $conversationText

                Краткое содержание:
            """.trimIndent()

            // Create request for summarization (simple text generation, no JSON schema)
            val request = YandexGptRequest(
                modelUri = modelUri,
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.3,  // Low temperature for consistent summaries
                    maxTokens = 500     // Limit summary length
                ),
                messages = listOf(
                    Message(role = "user", text = summarizationPrompt)
                )
            )

            println("📤 Sending summarization request to Yandex API")

            val response: YandexGptResponse = client.post(baseUrl) {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val summaryText = response.result.alternatives.firstOrNull()?.message?.text
                ?: return kotlin.Result.failure(Exception("Empty summary from API"))

            println("📥 Summary received: ${summaryText.take(100)}...")

            kotlin.Result.success(summaryText.trim())

        } catch (e: Exception) {
            println("❌ Summarization failed: ${e.message}")
            kotlin.Result.failure(e)
        }
    }

    /**
     * Calculate compression statistics for display.
     *
     * @param originalHistory Original history before compression
     * @param compressedHistory History after compression
     * @return Statistics about the compression
     */
    fun calculateCompressionStats(
        originalHistory: List<ChatMessage>,
        compressedHistory: List<ChatMessage>
    ): CompressionStatistics {
        val originalTokens = TokenUtils.estimateTokensForHistory(originalHistory)
        val compressedTokens = TokenUtils.estimateTokensForHistory(compressedHistory)
        val savedTokens = originalTokens - compressedTokens
        val compressionRatio = if (originalTokens > 0) {
            (savedTokens.toDouble() / originalTokens.toDouble()) * 100
        } else {
            0.0
        }

        return CompressionStatistics(
            originalMessageCount = originalHistory.size,
            compressedMessageCount = compressedHistory.size,
            messagesSaved = originalHistory.size - compressedHistory.size,
            originalTokens = originalTokens,
            compressedTokens = compressedTokens,
            tokensSaved = savedTokens,
            compressionRatio = compressionRatio
        )
    }
}

/**
 * Statistics about a compression operation.
 */
data class CompressionStatistics(
    val originalMessageCount: Int,
    val compressedMessageCount: Int,
    val messagesSaved: Int,
    val originalTokens: Int,
    val compressedTokens: Int,
    val tokensSaved: Int,
    val compressionRatio: Double  // Percentage of tokens saved
)
