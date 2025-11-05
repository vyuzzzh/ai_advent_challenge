package com.example.ai_window.service

import com.example.ai_window.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.Result

class YandexGptService(
    private val apiKey: String,
    private val folderId: String
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

    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<String> {
        return try {
            // Формируем историю диалога для API
            val messages = mutableListOf<Message>()

            // Добавляем историю
            conversationHistory.forEach { msg ->
                messages.add(
                    Message(
                        role = if (msg.isUser) "user" else "assistant",
                        text = msg.text
                    )
                )
            }

            // Добавляем текущее сообщение пользователя
            messages.add(Message(role = "user", text = userMessage))

            val request = YandexGptRequest(
                modelUri = modelUri,
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.6,
                    maxTokens = 2000
                ),
                messages = messages
            )

            val response: YandexGptResponse = client.post(baseUrl) {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val assistantMessage = response.result.alternatives.firstOrNull()?.message?.text
                ?: return Result.failure(Exception("Пустой ответ от API"))

            Result.success(assistantMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}