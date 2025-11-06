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
    private val folderId: String,
    private val useNativeJsonSchema: Boolean = false  // false = использует FORMAT_INSTRUCTIONS
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

    // Format instructions for prompt-based approach (fallback)
    private val FORMAT_INSTRUCTIONS = """
        ОБЯЗАТЕЛЬНО отвечай ТОЛЬКО в JSON формате, БЕЗ дополнительного текста, БЕЗ markdown блоков ```!
        Формат:
        {
          "response": {
            "title": "Краткий заголовок",
            "content": "твой ответ",
            "metadata": {
              "confidence": 0.95,
              "category": "factual"
            }
          }
        }

        ВАЖНО про confidence: Это НЕ твоя уверенность в классификации!
        Это "насколько полезен и корректен МОЙ ОТВЕТ для пользователя":

        Categories и их confidence:

        1. "factual" (факты) → confidence 0.90-0.98
           Пример: "Столица России?" → Москва, confidence: 0.98

        2. "opinion" (мнения) → confidence 0.50-0.70
           Пример: "Какой язык лучше?" → confidence: 0.60 (субъективно)

        3. "suggestion" (советы) → confidence 0.70-0.85
           Пример: "Как улучшить код?" → confidence: 0.75

        4. "error" (невозможный вопрос) → confidence 0.05-0.25 !!!
           Пример: "Как делить на ноль?" → confidence: 0.15 (НЕ МОГУ дать полезный ответ!)

        5. "general" (общее) → confidence 0.75-0.85
           Пример: "Привет!" → confidence: 0.85

        Для error ВСЕГДА ставь НИЗКИЙ confidence (0.05-0.25), потому что ответ НЕ полезен!

        """.trimIndent()

    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<ParseResult<AIResponse>> {
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

            // Добавляем текущее сообщение (с или без инструкций формата)
            if (useNativeJsonSchema) {
                // Native approach: just send the question
                messages.add(Message(role = "user", text = userMessage))
                println("📊 Using NATIVE JSON Schema approach")
            } else {
                // Fallback: prepend format instructions
                val formattedMessage = FORMAT_INSTRUCTIONS + "\nUser question: $userMessage"
                messages.add(Message(role = "user", text = formattedMessage))
                println("📝 Using PROMPT-BASED approach")
            }

            val request = YandexGptRequest(
                modelUri = modelUri,
                completionOptions = if (useNativeJsonSchema) {
                    // APPROACH 1: Native JSON Schema
                    CompletionOptions(
                        stream = false,
                        temperature = 0.6,  // Can use higher temp with schema validation
                        maxTokens = 2500
                    )
                } else {
                    // APPROACH 2: Prompt-based
                    CompletionOptions(
                        stream = false,
                        temperature = 0.2,  // Lower temp for consistency
                        maxTokens = 2500
                    )
                },
                messages = messages,
                jsonSchema = if (useNativeJsonSchema) {
                    JsonSchemaParam(schema = ResponseSchema.getSchema())
                } else {
                    null
                }
            )

            // Log request
            println("📤 REQUEST TO API:")
            println("  modelUri: ${request.modelUri}")
            println("  temperature: ${request.completionOptions.temperature}")
            println("  jsonSchema: ${if (request.jsonSchema != null) "PRESENT" else "NULL"}")
            println("  jsonObject: ${request.jsonObject}")
            println("  messages count: ${request.messages.size}")

            // Serialize and log full JSON request
            val json = Json { prettyPrint = true }
            val requestJson = json.encodeToString(YandexGptRequest.serializer(), request)
            println("📄 FULL JSON REQUEST:")
            println(requestJson)
            println("--- END REQUEST ---")

            val response: YandexGptResponse = client.post(baseUrl) {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            val rawText = response.result.alternatives.firstOrNull()?.message?.text
                ?: return Result.failure(Exception("Пустой ответ от API"))

            // Log raw response
            println("📥 RAW RESPONSE FROM API:")
            println(rawText)
            println("--- END RAW RESPONSE ---")

            // Parse the response
            val parseResult = if (useNativeJsonSchema) {
                // With JSON Schema, response should always be valid JSON
                ResponseParser.parseStrict(rawText)
            } else {
                // With prompts, need robust fallback parsing
                ResponseParser.parse(rawText)
            }

            // Log parse result
            when (parseResult) {
                is ParseResult.Success -> {
                    println("✅ Parse success: ${parseResult.data.response.content.take(50)}...")
                }
                is ParseResult.Partial -> {
                    println("⚠️ Parse partial: ${parseResult.warning}")
                    println("   Content: ${parseResult.data.response.content.take(50)}...")
                }
                is ParseResult.Error -> {
                    println("❌ Parse error: ${parseResult.message}")
                    println("   Raw response: ${parseResult.rawResponse.take(100)}...")
                }
            }

            Result.success(parseResult)
        } catch (e: Exception) {
            println("❌ Service error: ${e.message}")
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}