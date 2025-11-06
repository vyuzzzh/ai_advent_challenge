package com.example.ai_window.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YandexGptRequest(
    @SerialName("modelUri")
    val modelUri: String,
    @SerialName("completionOptions")
    val completionOptions: CompletionOptions,
    @SerialName("messages")
    val messages: List<Message>,
    @SerialName("json_schema")
    val jsonSchema: JsonSchemaParam? = null,
    @SerialName("json_object")
    val jsonObject: Boolean? = null
)

@Serializable
data class CompletionOptions(
    @SerialName("stream")
    val stream: Boolean = false,
    @SerialName("temperature")
    val temperature: Double = 0.6,
    @SerialName("maxTokens")
    val maxTokens: Int = 2000
)

@Serializable
data class Message(
    @SerialName("role")
    val role: String,
    @SerialName("text")
    val text: String
)

@Serializable
data class YandexGptResponse(
    @SerialName("result")
    val result: Result
)

@Serializable
data class Result(
    @SerialName("alternatives")
    val alternatives: List<Alternative>,
    @SerialName("usage")
    val usage: Usage,
    @SerialName("modelVersion")
    val modelVersion: String
)

@Serializable
data class Alternative(
    @SerialName("message")
    val message: Message,
    @SerialName("status")
    val status: String
)

@Serializable
data class Usage(
    @SerialName("inputTextTokens")
    val inputTextTokens: Int,
    @SerialName("completionTokens")
    val completionTokens: Int,
    @SerialName("totalTokens")
    val totalTokens: Int
)

// JSON Schema parameter for structured output
// Note: Using JsonElement instead of Map<String, Any> for proper serialization
@Serializable
data class JsonSchemaParam(
    @SerialName("schema")
    val schema: kotlinx.serialization.json.JsonElement
)

// Structured AI Response Models
@Serializable
data class AIResponse(
    val response: ResponseContent,
    val version: String = "1.0"
)

@Serializable
data class ResponseContent(
    val title: String = "",
    val content: String,
    val metadata: ResponseMetadata
)

@Serializable
data class ResponseMetadata(
    val confidence: Double = 0.5,
    val category: String = "general"
) {
    val categoryEnum: ResponseCategory
        get() = ResponseCategory.fromString(category)
}

// Response category enum
enum class ResponseCategory(val displayName: String) {
    FACTUAL("Factual"),
    OPINION("Opinion"),
    SUGGESTION("Suggestion"),
    ERROR("Error"),
    GENERAL("General"),
    PLAINTEXT_FALLBACK("Plain Text"),
    MANUAL_EXTRACTION("Manual Extract"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String): ResponseCategory {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}

// Parse result types
sealed class ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Partial<T>(val data: T, val warning: String) : ParseResult<T>()
    data class Error(val message: String, val rawResponse: String) : ParseResult<Nothing>()
}

// Validation result types
sealed class ValidationResult {
    data class Valid(val response: AIResponse) : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

// Response Parser
object ResponseParser {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Strict parsing for JSON Schema responses (minimal fallback)
     */
    fun parseStrict(rawResponse: String): ParseResult<AIResponse> {
        if (rawResponse.isBlank()) {
            return ParseResult.Error("Empty response from AI", rawResponse)
        }

        return try {
            val parsed = json.decodeFromString<AIResponse>(rawResponse.trim())

            // Validate even with schema (belt and suspenders)
            when (val validation = validate(parsed)) {
                is ValidationResult.Valid -> ParseResult.Success(parsed)
                is ValidationResult.Warning -> ParseResult.Partial(parsed, validation.message)
                is ValidationResult.Invalid -> ParseResult.Error(validation.message, rawResponse)
            }
        } catch (e: Exception) {
            // With JSON Schema this should rarely happen
            ParseResult.Error(
                "JSON Schema validation failed: ${e.message}",
                rawResponse
            )
        }
    }

    /**
     * Robust parsing with fallbacks for prompt-based responses
     */
    fun parse(rawResponse: String): ParseResult<AIResponse> {
        // Handle empty response
        if (rawResponse.isBlank()) {
            return ParseResult.Error("Empty response from AI", rawResponse)
        }

        val trimmed = rawResponse.trim()

        // FIRST: Try to extract JSON from markdown code blocks (before checking if it starts with {)
        val jsonString = extractJsonFromMarkdown(trimmed)

        // Check if response looks like JSON (after markdown extraction)
        if (!jsonString.startsWith("{") && !jsonString.startsWith("[")) {
            // Fallback: Model returned plain text instead of JSON
            return ParseResult.Partial(
                data = AIResponse(
                    response = ResponseContent(
                        title = "Response",
                        content = rawResponse,
                        metadata = ResponseMetadata(
                            confidence = 0.0,
                            category = "plaintext_fallback"
                        )
                    )
                ),
                warning = "Model returned plain text instead of JSON"
            )
        }

        // Attempt to parse JSON
        return try {
            val parsed = json.decodeFromString<AIResponse>(jsonString)

            // Validate parsed response
            when (val validation = validate(parsed)) {
                is ValidationResult.Valid -> ParseResult.Success(parsed)
                is ValidationResult.Warning -> ParseResult.Partial(parsed, validation.message)
                is ValidationResult.Invalid -> ParseResult.Error(validation.message, rawResponse)
            }
        } catch (e: Exception) {
            // JSON parsing failed - try to extract content manually
            tryManualExtraction(jsonString, rawResponse, e)
        }
    }

    private fun extractJsonFromMarkdown(text: String): String {
        // Remove markdown code blocks: ```json ... ``` or ``` ... ```
        // This handles cases like: "Response\n\n```\n{...}\n```"
        val codeBlockPattern = "```(?:json)?\\s*([\\s\\S]*?)```".toRegex()
        val match = codeBlockPattern.find(text)

        if (match != null) {
            // Found markdown block - extract JSON from it
            return match.groupValues[1].trim()
        }

        // No markdown block - return original text
        return text
    }

    private fun tryManualExtraction(
        jsonString: String,
        rawResponse: String,
        originalError: Exception
    ): ParseResult<AIResponse> {
        // Try to extract title and content fields even if JSON is malformed
        val titlePattern = "\"title\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val contentPattern = "\"content\"\\s*:\\s*\"([^\"]+)\"".toRegex()

        val titleMatch = titlePattern.find(jsonString)
        val contentMatch = contentPattern.find(jsonString)

        return if (contentMatch != null) {
            ParseResult.Partial(
                data = AIResponse(
                    response = ResponseContent(
                        title = titleMatch?.groupValues?.get(1) ?: "Response",
                        content = contentMatch.groupValues[1],
                        metadata = ResponseMetadata(
                            confidence = 0.0,
                            category = "manual_extraction"
                        )
                    )
                ),
                warning = "Malformed JSON, extracted content manually: ${originalError.message}"
            )
        } else {
            ParseResult.Error(
                "Failed to parse JSON: ${originalError.message}",
                rawResponse
            )
        }
    }

    private fun validate(response: AIResponse): ValidationResult {
        val content = response.response.content
        val metadata = response.response.metadata

        return when {
            content.isEmpty() ->
                ValidationResult.Invalid("Content is empty")

            content.length < 3 ->
                ValidationResult.Warning("Content is very short (${content.length} chars)")

            metadata.confidence !in 0.0..1.0 ->
                ValidationResult.Invalid("Invalid confidence: ${metadata.confidence} (must be 0.0-1.0)")

            metadata.confidence < 0.3 ->
                ValidationResult.Warning("Low confidence: ${metadata.confidence}")

            else ->
                ValidationResult.Valid(response)
        }
    }
}