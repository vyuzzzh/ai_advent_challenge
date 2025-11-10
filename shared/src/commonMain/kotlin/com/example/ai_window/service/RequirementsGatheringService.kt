package com.example.ai_window.service

import com.example.ai_window.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Service for Requirements Gathering mode (Day 3)
 *
 * This service wraps YandexGPT with a specialized system prompt
 * that collects project requirements and autonomously generates specifications.
 */
class RequirementsGatheringService(
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

    /**
     * Start requirements gathering session
     * This sends the initial system prompt to begin the conversation
     */
    suspend fun startSession(): kotlin.Result<ParseResult<AIResponse>> {
        return try {
            val systemPrompt = RequirementsPrompt.getSystemPrompt()
            val formatInstructions = RequirementsPrompt.getEnhancedFormatInstructions()

            // Initial message to start the conversation
            val initialMessage = "$formatInstructions\n\n$systemPrompt\n\nНачни диалог с пользователем."

            val request = YandexGptRequest(
                modelUri = modelUri,
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.7,  // Higher creativity for natural dialogue
                    maxTokens = 8000    // Longer responses for final specification
                ),
                messages = listOf(
                    Message(role = "user", text = initialMessage)
                )
            )

            println("📤 Starting requirements gathering session...")

            val response: YandexGptResponse = client.post(baseUrl) {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val rawText = response.result.alternatives.firstOrNull()?.message?.text
                ?: return kotlin.Result.failure(Exception("Пустой ответ от API"))

            println("📥 RAW INITIAL RESPONSE:")
            println(rawText)
            println("--- END RAW RESPONSE ---")

            val parseResult = ResponseParser.parse(rawText)

            when (parseResult) {
                is ParseResult.Success -> {
                    println("✅ Initial message parsed: ${parseResult.data.response.content.take(50)}...")
                }
                is ParseResult.Partial -> {
                    println("⚠️ Initial message warning: ${parseResult.warning}")
                }
                is ParseResult.Error -> {
                    println("❌ Parse error: ${parseResult.message}")
                }
            }

            kotlin.Result.success(parseResult)
        } catch (e: Exception) {
            println("❌ Service error: ${e.message}")
            kotlin.Result.failure(e)
        }
    }

    /**
     * Send user message in requirements gathering session
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): kotlin.Result<ParseResult<AIResponse>> {
        return try {
            val formatInstructions = RequirementsPrompt.getEnhancedFormatInstructions()

            // Build message history
            val messages = mutableListOf<Message>()

            // Add format instructions as first message (since Yandex doesn't support system role)
            if (conversationHistory.isEmpty()) {
                val systemPrompt = RequirementsPrompt.getSystemPrompt()
                messages.add(
                    Message(
                        role = "user",
                        text = "$formatInstructions\n\n$systemPrompt\n\nНачни диалог."
                    )
                )
            } else {
                // Add conversation history
                conversationHistory.forEach { msg ->
                    messages.add(
                        Message(
                            role = if (msg.isUser) "user" else "assistant",
                            text = msg.text
                        )
                    )
                }
            }

            // Add current user message
            messages.add(Message(role = "user", text = userMessage))

            val request = YandexGptRequest(
                modelUri = modelUri,
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.7,
                    maxTokens = 8000
                ),
                messages = messages
            )

            println("📤 Sending message to requirements gathering API...")
            println("  Messages count: ${messages.size}")

            val response: YandexGptResponse = client.post(baseUrl) {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val rawText = response.result.alternatives.firstOrNull()?.message?.text
                ?: return kotlin.Result.failure(Exception("Пустой ответ от API"))

            println("📥 RAW RESPONSE:")
            println(rawText)
            println("--- END RAW RESPONSE ---")

            val parseResult = ResponseParser.parse(rawText)

            // Log progress
            when (parseResult) {
                is ParseResult.Success -> {
                    val metadata = parseResult.data.response.metadata
                    println("✅ Parse success")
                    println("   Category: ${metadata.category}")
                    println("   Sections: ${metadata.sections_completed?.joinToString(", ") ?: "none"}")
                    println("   Questions: ${metadata.questions_asked ?: 0}")
                    println("   Complete: ${metadata.is_complete ?: false}")
                }
                is ParseResult.Partial -> {
                    println("⚠️ Parse partial: ${parseResult.warning}")
                }
                is ParseResult.Error -> {
                    println("❌ Parse error: ${parseResult.message}")
                }
            }

            kotlin.Result.success(parseResult)
        } catch (e: Exception) {
            println("❌ Service error: ${e.message}")
            e.printStackTrace()
            kotlin.Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
