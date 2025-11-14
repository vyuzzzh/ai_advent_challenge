package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.model.*
import com.example.ai_window.service.HistoryCompressionService
import com.example.ai_window.service.YandexGptService
import com.example.ai_window.util.TokenUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel for comparing chat with and without history compression.
 * Day 8: Compression comparison experiment.
 *
 * Features:
 * - Two parallel chats: one with compression, one without
 * - Same messages sent to both
 * - Metrics collection: tokens, response times, compression stats
 */
class CompressionComparisonViewModel(
    private val apiKey: String,
    private val folderId: String
) : ViewModel() {

    // Services
    private val yandexGptService = YandexGptService(apiKey, folderId, useNativeJsonSchema = false)
    private val compressionService = HistoryCompressionService(apiKey, folderId, compressionThreshold = 10)

    // Messages for both chats
    private val _messagesWithCompression = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messagesWithCompression: StateFlow<List<ChatMessage>> = _messagesWithCompression.asStateFlow()

    private val _messagesWithoutCompression = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messagesWithoutCompression: StateFlow<List<ChatMessage>> = _messagesWithoutCompression.asStateFlow()

    // State
    private val _chatState = MutableStateFlow(ChatState.IDLE)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Statistics
    private val _stats = MutableStateFlow(CompressionStats())
    val stats: StateFlow<CompressionStats> = _stats.asStateFlow()

    private fun generateId(): String {
        return "${Random.nextLong()}-${Random.nextLong()}"
    }

    /**
     * Send the same message to both chats in parallel.
     * Collect metrics for comparison.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || _chatState.value == ChatState.LOADING) return

        viewModelScope.launch {
            _chatState.value = ChatState.LOADING
            _errorMessage.value = null

            // Add user message to both chats
            val userMessageWithCompression = ChatMessage(
                id = generateId(),
                text = text,
                isUser = true,
                timestamp = 0L,
                estimatedTokens = TokenUtils.estimateTokens(text)
            )
            val userMessageWithoutCompression = userMessageWithCompression.copy(id = generateId())

            _messagesWithCompression.value = _messagesWithCompression.value + userMessageWithCompression
            _messagesWithoutCompression.value = _messagesWithoutCompression.value + userMessageWithoutCompression

            // Get histories for both chats (excluding the just-added user message)
            var historyWithCompression = _messagesWithCompression.value.dropLast(1)
            val historyWithoutCompression = _messagesWithoutCompression.value.dropLast(1)

            // Apply compression if needed
            if (compressionService.shouldCompress(historyWithCompression)) {
                println("🗜️ Applying compression to history...")

                compressionService.compressHistory(historyWithCompression, keepRecentCount = 5)
                    .onSuccess { compressed ->
                        historyWithCompression = compressed
                        println("✅ Compression successful")

                        // Update messages with compression to show the summary
                        val lastUserMessage = _messagesWithCompression.value.last()
                        _messagesWithCompression.value = compressed + lastUserMessage

                        // Update stats
                        val currentStats = _stats.value
                        _stats.value = currentStats.copy(
                            messagesWithCompression = compressed.size,
                            summariesCreated = currentStats.summariesCreated + 1,
                            summarizationCount = currentStats.summarizationCount + 1
                        )
                    }
                    .onFailure { error ->
                        println("❌ Compression failed: ${error.message}")
                        // Continue with uncompressed history on failure
                    }
            }

            // Send requests to both chats IN PARALLEL
            try {
                val withCompressionDeferred = async {
                    yandexGptService.sendMessage(text, historyWithCompression)
                }

                val withoutCompressionDeferred = async {
                    yandexGptService.sendMessage(text, historyWithoutCompression)
                }

                // Wait for both to complete
                val resultWithCompression = withCompressionDeferred.await()
                val resultWithoutCompression = withoutCompressionDeferred.await()

                // Process result WITH compression
                resultWithCompression.onSuccess { parseResult ->
                    val aiMessage = handleParseResult(parseResult, withCompression = true)
                    _messagesWithCompression.value = _messagesWithCompression.value + aiMessage
                }

                // Process result WITHOUT compression
                resultWithoutCompression.onSuccess { parseResult ->
                    val aiMessage = handleParseResult(parseResult, withCompression = false)
                    _messagesWithoutCompression.value = _messagesWithoutCompression.value + aiMessage
                }

                // Update statistics
                updateStats(
                    resultWithCompression = resultWithCompression,
                    resultWithoutCompression = resultWithoutCompression,
                    userMessageTokens = TokenUtils.estimateTokens(text)
                )

                _chatState.value = ChatState.IDLE

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Неизвестная ошибка"
                _chatState.value = ChatState.ERROR
            }
        }
    }

    private fun handleParseResult(parseResult: ParseResult<AIResponse>, withCompression: Boolean): ChatMessage {
        return when (parseResult) {
            is ParseResult.Success -> {
                ChatMessage(
                    id = generateId(),
                    text = parseResult.data.response.content,
                    title = parseResult.data.response.title,
                    isUser = false,
                    timestamp = 0L,
                    metadata = parseResult.data.response.metadata,
                    parseWarning = null,
                    estimatedTokens = TokenUtils.estimateTokens(parseResult.data.response.content)
                )
            }
            is ParseResult.Partial -> {
                ChatMessage(
                    id = generateId(),
                    text = parseResult.data.response.content,
                    title = parseResult.data.response.title,
                    isUser = false,
                    timestamp = 0L,
                    metadata = parseResult.data.response.metadata,
                    parseWarning = parseResult.warning,
                    estimatedTokens = TokenUtils.estimateTokens(parseResult.data.response.content)
                )
            }
            is ParseResult.Error -> {
                ChatMessage(
                    id = generateId(),
                    text = "❌ Parse Error: ${parseResult.message}",
                    isUser = false,
                    timestamp = 0L,
                    metadata = null,
                    parseWarning = "Parse failed"
                )
            }
        }
    }

    private fun updateStats(
        resultWithCompression: kotlin.Result<ParseResult<AIResponse>>,
        resultWithoutCompression: kotlin.Result<ParseResult<AIResponse>>,
        userMessageTokens: Int
    ) {
        val currentStats = _stats.value

        // Extract token usage from successful results
        // Note: .dropLast(1) removes the AI response, but keeps user message
        // User message tokens are already included in the history, so we don't add them separately
        var inputTokensWithCompression = TokenUtils.estimateTokensForHistory(_messagesWithCompression.value.dropLast(1))
        var outputTokensWithCompression = 0
        var inputTokensWithoutCompression = TokenUtils.estimateTokensForHistory(_messagesWithoutCompression.value.dropLast(1))
        var outputTokensWithoutCompression = 0

        resultWithCompression.onSuccess { parseResult ->
            if (parseResult is ParseResult.Success || parseResult is ParseResult.Partial) {
                val data = when (parseResult) {
                    is ParseResult.Success -> parseResult.data
                    is ParseResult.Partial -> parseResult.data
                    else -> null
                }
                data?.response?.metadata?.tokenUsage?.let { usage ->
                    inputTokensWithCompression = usage.inputTextTokens
                    outputTokensWithCompression = usage.completionTokens
                }
            }
        }

        resultWithoutCompression.onSuccess { parseResult ->
            if (parseResult is ParseResult.Success || parseResult is ParseResult.Partial) {
                val data = when (parseResult) {
                    is ParseResult.Success -> parseResult.data
                    is ParseResult.Partial -> parseResult.data
                    else -> null
                }
                data?.response?.metadata?.tokenUsage?.let { usage ->
                    inputTokensWithoutCompression = usage.inputTextTokens
                    outputTokensWithoutCompression = usage.completionTokens
                }
            }
        }

        // Update request counts
        val newRequestCountWithCompression = currentStats.requestCountWithCompression + 1
        val newRequestCountWithoutCompression = currentStats.requestCountWithoutCompression + 1

        _stats.value = currentStats.copy(
            messagesWithCompression = _messagesWithCompression.value.size,
            messagesWithoutCompression = _messagesWithoutCompression.value.size,
            latestInputTokensWithCompression = inputTokensWithCompression,
            latestInputTokensWithoutCompression = inputTokensWithoutCompression,
            latestOutputTokensWithCompression = outputTokensWithCompression,
            latestOutputTokensWithoutCompression = outputTokensWithoutCompression,
            totalInputTokensWithCompression = currentStats.totalInputTokensWithCompression + inputTokensWithCompression,
            totalInputTokensWithoutCompression = currentStats.totalInputTokensWithoutCompression + inputTokensWithoutCompression,
            totalOutputTokensWithCompression = currentStats.totalOutputTokensWithCompression + outputTokensWithCompression,
            totalOutputTokensWithoutCompression = currentStats.totalOutputTokensWithoutCompression + outputTokensWithoutCompression,
            requestCountWithCompression = newRequestCountWithCompression,
            requestCountWithoutCompression = newRequestCountWithoutCompression
        )

        println("📊 Stats updated:")
        println("  With compression: ${inputTokensWithCompression} input tokens")
        println("  Without compression: ${inputTokensWithoutCompression} input tokens")
        println("  Token savings: ${_stats.value.formatTokenSavings()}")
    }

    fun clearError() {
        _errorMessage.value = null
        if (_chatState.value == ChatState.ERROR) {
            _chatState.value = ChatState.IDLE
        }
    }

    fun clearChat() {
        _messagesWithCompression.value = emptyList()
        _messagesWithoutCompression.value = emptyList()
        _stats.value = CompressionStats()
        _chatState.value = ChatState.IDLE
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        yandexGptService.close()
    }
}
