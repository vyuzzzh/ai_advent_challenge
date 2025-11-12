package com.example.ai_window.service

import com.example.ai_window.SERVER_PORT
import com.example.ai_window.model.*
import com.example.ai_window.util.getCurrentTimeMillis
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.Result

/**
 * Сервис для взаимодействия с HuggingFace Inference API через прокси-сервер
 */
class HuggingFaceService(
    private val hfToken: String,
    private val serverUrl: String = "http://localhost:$SERVER_PORT"
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

    /**
     * Отправить запрос к модели через прокси
     * Использует Chat Completion API (OpenAI-совместимый формат)
     */
    suspend fun generateText(
        modelId: String,
        prompt: String,
        maxTokens: Int = 500,
        temperature: Double = 0.7
    ): Result<HFDetailedResponse> {
        return try {
            val startTime = getCurrentTimeMillis()

            val request = HuggingFaceRequest(
                model = modelId,
                messages = listOf(
                    HFChatMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                maxTokens = maxTokens,
                temperature = temperature,
                stream = false
            )

            println("📤 HuggingFace REQUEST:")
            println("  Model: $modelId")
            println("  Prompt: ${prompt.take(100)}...")
            println("  Max tokens: $maxTokens")
            println("  Temperature: $temperature")

            val response: HuggingFaceResponse = client.post("$serverUrl/api/huggingface") {
                header("X-HF-Token", hfToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val endTime = getCurrentTimeMillis()
            val executionTime = endTime - startTime

            println("📥 HuggingFace RESPONSE:")
            println("  Execution time: ${executionTime}ms")
            println("  Model: ${response.model}")
            println("  Choices: ${response.choices?.size}")
            println("  Usage: ${response.usage}")
            println("  Error: ${response.error}")

            // Проверка подмены модели (с нормализацией)
            val actualModel = response.model
            if (actualModel != null) {
                val normalizedRequested = normalizeModelName(modelId)
                val normalizedActual = normalizeModelName(actualModel)

                if (normalizedActual != normalizedRequested) {
                    println("⚠️  WARNING: Model substitution detected!")
                    println("  Requested: $modelId (normalized: $normalizedRequested)")
                    println("  Actually used: $actualModel (normalized: $normalizedActual)")
                } else if (actualModel != modelId) {
                    println("ℹ️  Model name normalized by provider:")
                    println("  Requested: $modelId")
                    println("  Returned: $actualModel")
                }
            }

            if (response.error != null) {
                return Result.failure(Exception("HuggingFace API error: ${response.error}"))
            }

            val generatedText = response.choices?.firstOrNull()?.message?.content
            if (generatedText == null) {
                return Result.failure(Exception("Empty response from HuggingFace API"))
            }

            // Используем реальные метрики токенов из API
            val tokenUsage = response.usage ?: TokenUsage(
                promptTokens = 0,
                completionTokens = 0,
                totalTokens = 0
            )

            val detailedResponse = HFDetailedResponse(
                modelId = modelId,
                modelName = modelId.split("/").firstOrNull()?.split(":")?.firstOrNull() ?: modelId,
                generatedText = generatedText,
                executionTime = executionTime,
                tokenUsage = tokenUsage,
                actualModelUsed = actualModel  // Сохраняем реально использованную модель
            )

            Result.success(detailedResponse)
        } catch (e: Exception) {
            println("❌ HuggingFace Service error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Генерировать множественные ответы для одного промпта
     * (для расчета метрик разнообразия)
     */
    suspend fun generateMultiple(
        modelId: String,
        prompt: String,
        count: Int = 3,
        maxTokens: Int = 500,
        temperature: Double = 0.7,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<HFDetailedResponse>> {
        val responses = mutableListOf<HFDetailedResponse>()

        for (i in 1..count) {
            onProgress(i, count)

            val result = generateText(
                modelId = modelId,
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature
            )

            when {
                result.isSuccess -> {
                    responses.add(result.getOrThrow())
                }
                result.isFailure -> {
                    return Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
                }
            }
        }

        return Result.success(responses)
    }

    /**
     * Нормализация имени модели для корректного сравнения
     * - Убирает суффиксы провайдеров (:fastest, :novita и т.д.)
     * - Приводит к нижнему регистру
     */
    private fun normalizeModelName(modelName: String): String {
        return modelName
            .split(":")  // Убираем суффикс провайдера
            .first()
            .lowercase()  // Приводим к нижнему регистру
            .trim()
    }

    fun close() {
        client.close()
    }
}
