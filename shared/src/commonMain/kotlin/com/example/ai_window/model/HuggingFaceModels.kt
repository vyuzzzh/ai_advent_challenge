package com.example.ai_window.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HuggingFace модель
 */
@Serializable
data class HFModel(
    val modelId: String,
    val displayName: String,
    val description: String = ""
)

/**
 * Список доступных моделей для сравнения
 * Используются модели через HuggingFace Inference Providers API
 */
object HuggingFaceModels {
    val AVAILABLE_MODELS = listOf(
        HFModel(
            modelId = "Sao10K/L3-8B-Stheno-v3.2:novita",
            displayName = "L3-8B Stheno v3.2",
            description = "Llama 3 8B fine-tune от Sao10K (провайдер: Novita)"
        ),
        HFModel(
            modelId = "MiniMaxAI/MiniMax-M2:fastest",
            displayName = "MiniMax-M2",
            description = "Продвинутая модель от MiniMax AI"
        ),
        HFModel(
            modelId = "Qwen/Qwen2.5-VL-7B-Instruct:fastest",
            displayName = "Qwen 2.5 VL 7B",
            description = "Мультимодальная модель (Vision-Language) от Alibaba"
        )
    )
}

/**
 * Запрос к HuggingFace Chat Completion API (OpenAI-совместимый формат)
 */
@Serializable
data class HuggingFaceRequest(
    @SerialName("model")
    val model: String,
    @SerialName("messages")
    val messages: List<HFChatMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int = 500,
    @SerialName("temperature")
    val temperature: Double = 0.7,
    @SerialName("stream")
    val stream: Boolean = false
)

/**
 * Сообщение чата для HuggingFace API
 */
@Serializable
data class HFChatMessage(
    @SerialName("role")
    val role: String,  // "user", "assistant", "system"
    @SerialName("content")
    val content: String
)

/**
 * Ответ от HuggingFace Chat Completion API
 */
@Serializable
data class HuggingFaceResponse(
    @SerialName("id")
    val id: String? = null,
    @SerialName("model")
    val model: String? = null,
    @SerialName("choices")
    val choices: List<ChatChoice>? = null,
    @SerialName("usage")
    val usage: TokenUsage? = null,
    @SerialName("error")
    val error: String? = null,
    @SerialName("execution_time")
    val executionTime: Long = 0  // milliseconds, добавляется прокси-сервером
)

/**
 * Выбор из ответа
 */
@Serializable
data class ChatChoice(
    @SerialName("index")
    val index: Int,
    @SerialName("message")
    val message: HFChatMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * Использование токенов
 */
@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

/**
 * Детальный ответ с метриками (добавляется клиентом)
 */
@Serializable
data class HFDetailedResponse(
    val modelId: String,
    val modelName: String,
    val generatedText: String,
    val executionTime: Long,  // milliseconds
    val tokenUsage: TokenUsage,  // Используем TokenUsage из API
    val error: String? = null
)
