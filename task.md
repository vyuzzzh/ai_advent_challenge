# AI Advent Challenge
### day_1
- create simple window for ai agent conversation ✅
- ai agent must use yandex gpt api ✅

## Реализовано:

### Структура проекта
1. **Модели данных** (`shared/src/commonMain/kotlin/com/example/ai_window/model/`)
   - `ChatMessage.kt` - модель сообщения чата
   - `YandexGptModels.kt` - модели для работы с Yandex GPT API

2. **Сервисы** (`shared/src/commonMain/kotlin/com/example/ai_window/service/`)
   - `YandexGptService.kt` - клиент для работы с Yandex GPT API

3. **UI слой** (`composeApp/src/commonMain/kotlin/com/example/ai_window/`)
   - `ChatViewModel.kt` - ViewModel для управления состоянием чата
   - `App.kt` - интерфейс чата с Material Design 3

### Функциональность
- ✅ Отправка сообщений к Yandex GPT API
- ✅ Сохранение истории диалога
- ✅ Автоматическая прокрутка к последнему сообщению
- ✅ Индикатор загрузки во время ожидания ответа
- ✅ Обработка и отображение ошибок
- ✅ Кнопка очистки истории чата
- ✅ Адаптивный UI с Material Design 3

### Настройка
1. Получите API ключ и Folder ID от Yandex Cloud
2. Откройте `composeApp/src/commonMain/kotlin/com/example/ai_window/App.kt`
3. Замените `YOUR_YANDEX_API_KEY_HERE` и `YOUR_FOLDER_ID_HERE` на ваши данные
4. Запустите приложение: `./gradlew :composeApp:run`

Подробные инструкции в файле `README_SETUP.md`

### day_2

## Objective
Learn how to specify and enforce a structured response format from the AI agent. Configure the agent to return responses in a predefined format that can be easily parsed by the application.

## Yandex GPT API Specifics

**🎯 CRITICAL DISCOVERY:** Yandex Cloud AI Studio has **NATIVE support for structured output**!

### Two Approaches for Structured Responses

Yandex provides **two methods** for structured output (choose based on your needs):

#### ✅ Approach 1: Native JSON Schema (RECOMMENDED)
**Best for:** Type-safe, strictly validated responses with guaranteed structure.

Yandex API supports `json_schema` parameter for enforcing response format:

```kotlin
// Add to CompletionOptions:
@Serializable
data class CompletionOptions(
    @SerialName("stream")
    val stream: Boolean = false,
    @SerialName("temperature")
    val temperature: Double = 0.6,
    @SerialName("maxTokens")
    val maxTokens: Int = 2000,
    @SerialName("json_schema")  // NEW!
    val jsonSchema: JsonSchema? = null
)

@Serializable
data class JsonSchema(
    @SerialName("schema")
    val schema: Map<String, Any>  // JSON Schema definition
)
```

**Benefits:**
- ✅ API guarantees valid JSON structure
- ✅ No need for fallback parsing
- ✅ Better token efficiency
- ✅ No extra prompting required

**Limitations:**
- Requires JSON Schema format
- Less flexible than prompt-based approach

#### ⚠️ Approach 2: Prompt-Based (FALLBACK)
**Best for:** Simple cases, testing, or when JSON Schema is unavailable.

If native JSON Schema is not yet available in your Yandex Cloud SDK version:

### System Prompt Limitation
- Yandex GPT does **NOT support** system role in messages
- Format instructions must be included in the **first user message** or prepended to each query
- Use this approach:

```kotlin
// ❌ WRONG - This won't work with Yandex GPT:
messages.add(Message(role = "system", text = "Return JSON format"))

// ✅ CORRECT - Include instructions in user message:
val formattedPrompt = """
IMPORTANT: You must respond in the following JSON format:
{
  "response": {
    "content": "your answer here",
    "metadata": {
      "confidence": 0.95,
      "category": "factual"
    }
  }
}

User question: $userMessage
""".trimIndent()

messages.add(Message(role = "user", text = formattedPrompt))
```

### Recommended API Parameters for Structured Responses
```kotlin
CompletionOptions(
    temperature = 0.2,  // Lower = more consistent structure (vs default 0.6)
    maxTokens = 2500    // JSON responses are longer than plain text
)
```

### Expected Behavior
- **Approach 1 (JSON Schema):** API guarantees valid JSON, minimal parsing needed
- **Approach 2 (Prompt-based):** Model may occasionally return plain text - robust fallback parsing is essential
- Test both approaches to determine which works best for your use case

---

## Implementation Decision

**For Day 2, we'll implement BOTH approaches:**

1. **Primary:** Native JSON Schema (if supported by API)
2. **Fallback:** Prompt-based with robust parsing

This ensures maximum reliability and compatibility.

---

## Tasks

### 1. Define Response Format & JSON Schema

#### 1.1 Response Structure
Define the desired JSON structure:

```json
{
  "response": {
    "content": "Main response text",
    "metadata": {
      "confidence": 0.95,
      "category": "general"
    }
  }
}
```

#### 1.2 Create JSON Schema (for Native Approach)

**File:** `shared/src/commonMain/kotlin/com/example/ai_window/model/ResponseSchema.kt` (NEW FILE)

```kotlin
package com.example.ai_window.model

import kotlinx.serialization.json.*

object ResponseSchema {
    /**
     * JSON Schema for AI response format
     * Based on: https://json-schema.org/
     */
    fun getSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("response") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "The main response text")
                    }
                    putJsonObject("metadata") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("confidence") {
                                put("type", "number")
                                put("minimum", 0.0)
                                put("maximum", 1.0)
                                put("description", "Confidence level from 0.0 to 1.0")
                            }
                            putJsonObject("category") {
                                put("type", "string")
                                put("enum", JsonArray(listOf(
                                    JsonPrimitive("factual"),
                                    JsonPrimitive("opinion"),
                                    JsonPrimitive("suggestion"),
                                    JsonPrimitive("error"),
                                    JsonPrimitive("general")
                                )))
                                put("description", "Response category")
                            }
                        }
                        put("required", JsonArray(listOf(
                            JsonPrimitive("confidence"),
                            JsonPrimitive("category")
                        )))
                    }
                }
                put("required", JsonArray(listOf(
                    JsonPrimitive("content"),
                    JsonPrimitive("metadata")
                )))
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("response"))))
    }

    /**
     * Convert JsonObject to Map<String, Any> for API compatibility
     */
    fun getSchemaAsMap(): Map<String, Any> {
        return Json.decodeFromJsonElement(getSchema())
    }
}
```

**Benefits of JSON Schema:**
- Enforces type constraints (confidence: 0.0-1.0)
- Validates enum values for category
- Guarantees required fields are present
- Self-documenting format

### 2. Configure Agent Prompt
- Modify the system prompt to include format specifications
- Add clear instructions about the expected output structure
- Include examples of correctly formatted responses in the prompt

**Example prompt additions:**
```
You must respond in the following JSON format:
{
  "response": {
    "content": "<your answer here>",
    "metadata": {
      "confidence": <0.0-1.0>,
      "category": "<category_name>"
    }
  }
}

Always wrap your response in this JSON structure.
```

### 3. Provide Format Examples

Include these examples in your prompt to guide the model:

#### Example 1: Standard factual question
**User:** "What is the capital of France?"

**Expected AI Response:**
```json
{
  "response": {
    "content": "The capital of France is Paris. It is located in the north-central part of the country.",
    "metadata": {
      "confidence": 0.95,
      "category": "factual"
    }
  }
}
```

#### Example 2: Opinion or uncertain answer
**User:** "Which programming language is best?"

**Expected AI Response:**
```json
{
  "response": {
    "content": "There is no single 'best' programming language - it depends on your specific needs. Python is great for beginners and data science, JavaScript for web development, and Rust for systems programming.",
    "metadata": {
      "confidence": 0.6,
      "category": "opinion"
    }
  }
}
```

#### Example 3: Suggestion or recommendation
**User:** "How can I improve my code quality?"

**Expected AI Response:**
```json
{
  "response": {
    "content": "Here are some recommendations: 1) Write unit tests, 2) Use code reviews, 3) Follow style guides, 4) Refactor regularly, 5) Use static analysis tools.",
    "metadata": {
      "confidence": 0.85,
      "category": "suggestion"
    }
  }
}
```

#### Example 4: Error or unclear query
**User:** "asdfgh jklqwer"

**Expected AI Response:**
```json
{
  "response": {
    "content": "I'm sorry, but I don't understand your question. Could you please rephrase it?",
    "metadata": {
      "confidence": 0.0,
      "category": "error"
    }
  }
}
```

#### Example 5: Plain text fallback (model ignores JSON instruction)
**Actual AI Response:** "Paris is the capital of France."

**Parser behavior:**
```kotlin
// Parser detects non-JSON and creates fallback:
ParseResult.Partial(
    data = AIResponse(
        response = ResponseContent(
            content = "Paris is the capital of France.",
            metadata = ResponseMetadata(confidence = 0.0, category = "plaintext_fallback")
        )
    ),
    warning = "Model returned plain text instead of JSON"
)
```

#### Example 6: Malformed JSON (incomplete response)
**Actual AI Response:** `{"response": {"content": "The answer is`

**Parser behavior:**
```kotlin
// Parser attempts manual extraction:
ParseResult.Error(
    message = "Failed to parse JSON: Unexpected end of JSON input",
    rawResponse = """{"response": {"content": "The answer is"""
)
```

#### Example 7: JSON wrapped in markdown
**Actual AI Response:**
````
```json
{
  "response": {
    "content": "Answer here",
    "metadata": {"confidence": 0.9, "category": "factual"}
  }
}
```
````

**Parser behavior:**
```kotlin
// Parser extracts JSON from markdown code blocks automatically
ParseResult.Success(AIResponse(...))
```

### 4. Implement Response Parsing with Robust Error Handling
- Create a parser function to extract data from AI responses
- Add validation to ensure responses match the expected format
- **Handle parsing errors gracefully with fallback strategies**
- Support both successful JSON and plain text responses

**Implementation steps:**

#### Step 4.1: Define data classes and result types
```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Response data classes
@Serializable
data class AIResponse(
    val response: ResponseContent,
    val version: String = "1.0"  // For future format changes
)

@Serializable
data class ResponseContent(
    val content: String,
    val metadata: ResponseMetadata
)

@Serializable
data class ResponseMetadata(
    val confidence: Double = 0.5,  // Default if not provided
    val category: String = "general"
)

// Sealed class for parse results
sealed class ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Partial<T>(val data: T, val warning: String) : ParseResult<T>()
    data class Error(val message: String, val rawResponse: String) : ParseResult<Nothing>()
}
```

#### Step 4.2: Implement robust JSON parser with fallbacks
```kotlin
object ResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Strict parsing for JSON Schema responses (no fallback needed)
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

        // Check if response looks like JSON
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            // Fallback: Model returned plain text instead of JSON
            return ParseResult.Partial(
                data = AIResponse(
                    response = ResponseContent(
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

        // Try to extract JSON if wrapped in markdown code blocks
        val jsonString = extractJsonFromMarkdown(trimmed)

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
        // Remove markdown code blocks: ```json ... ```
        val codeBlockPattern = "```(?:json)?\\s*([\\s\\S]*?)```".toRegex()
        val match = codeBlockPattern.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text
    }

    private fun tryManualExtraction(
        jsonString: String,
        rawResponse: String,
        originalError: Exception
    ): ParseResult<AIResponse> {
        // Try to extract content field even if JSON is malformed
        val contentPattern = "\"content\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val contentMatch = contentPattern.find(jsonString)

        return if (contentMatch != null) {
            ParseResult.Partial(
                data = AIResponse(
                    response = ResponseContent(
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
}
```

#### Step 4.3: Implement validation
```kotlin
sealed class ValidationResult {
    data class Valid(val response: AIResponse) : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
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
```

### 5. Architecture Integration Plan

To integrate structured responses into the existing codebase, follow this order:

#### 5.1 Extend ChatMessage model
**File:** `shared/src/commonMain/kotlin/com/example/ai_window/model/ChatMessage.kt`

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = 0L,
    // NEW: Add metadata for AI responses
    val metadata: ResponseMetadata? = null,  // null for user messages
    val parseWarning: String? = null  // Store any parsing warnings
)
```

#### 5.2 Modify YandexGptService
**File:** `shared/src/commonMain/kotlin/com/example/ai_window/service/YandexGptService.kt`

```kotlin
class YandexGptService(
    private val apiKey: String,
    private val folderId: String,
    private val useNativeJsonSchema: Boolean = true  // Toggle between approaches
) {

    // Fallback: Format instructions for prompt-based approach
    private val FORMAT_INSTRUCTIONS = """
        IMPORTANT: You must respond in the following JSON format:
        {
          "response": {
            "content": "your answer here",
            "metadata": {
              "confidence": 0.95,
              "category": "factual"
            }
          }
        }

        Categories: factual, opinion, suggestion, error, general
        Confidence: 0.0 (no confidence) to 1.0 (very confident)

        """.trimIndent()

    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<ParseResult<AIResponse>> {
        return try {
            val messages = buildList {
                // Add conversation history
                conversationHistory.forEach { msg ->
                    add(Message(
                        role = if (msg.isUser) "user" else "assistant",
                        text = msg.text
                    ))
                }

                // Add current message (with or without format instructions)
                if (useNativeJsonSchema) {
                    // Native approach: just send the question
                    add(Message(role = "user", text = userMessage))
                } else {
                    // Fallback: prepend format instructions
                    val formattedMessage = FORMAT_INSTRUCTIONS + "\nUser question: $userMessage"
                    add(Message(role = "user", text = formattedMessage))
                }
            }

            val request = YandexGptRequest(
                modelUri = "gpt://$folderId/yandexgpt-lite/latest",
                completionOptions = if (useNativeJsonSchema) {
                    // APPROACH 1: Native JSON Schema
                    CompletionOptions(
                        stream = false,
                        temperature = 0.6,  // Can use higher temp with schema validation
                        maxTokens = 2500,
                        jsonSchema = JsonSchema(
                            schema = ResponseSchema.getSchemaAsMap()
                        )
                    )
                } else {
                    // APPROACH 2: Prompt-based
                    CompletionOptions(
                        stream = false,
                        temperature = 0.2,  // Lower temp for consistency
                        maxTokens = 2500
                    )
                },
                messages = messages
            )

            val response = client.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<YandexGptResponse>()

            val rawText = response.result.alternatives.firstOrNull()?.message?.text
                ?: return Result.failure(Exception("Empty response"))

            // Parse the response
            val parseResult = if (useNativeJsonSchema) {
                // With JSON Schema, response should always be valid JSON
                ResponseParser.parseStrict(rawText)
            } else {
                // With prompts, need robust fallback parsing
                ResponseParser.parse(rawText)
            }

            Result.success(parseResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### 5.3 Update ChatViewModel
**File:** `composeApp/src/commonMain/kotlin/com/example/ai_window/ChatViewModel.kt`

```kotlin
class ChatViewModel(private val gptService: YandexGptService) : ViewModel() {

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        _messages.value += userMessage
        _isLoading.value = true

        viewModelScope.launch {
            gptService.sendMessage(text, _messages.value).fold(
                onSuccess = { parseResult ->
                    when (parseResult) {
                        is ParseResult.Success -> {
                            // Successfully parsed JSON
                            val aiMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = parseResult.data.response.content,
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                metadata = parseResult.data.response.metadata,
                                parseWarning = null
                            )
                            _messages.value += aiMessage
                        }

                        is ParseResult.Partial -> {
                            // Parsed with warnings (e.g., plain text fallback)
                            val aiMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = parseResult.data.response.content,
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                metadata = parseResult.data.response.metadata,
                                parseWarning = parseResult.warning
                            )
                            _messages.value += aiMessage
                            println("Parse warning: ${parseResult.warning}")
                        }

                        is ParseResult.Error -> {
                            // Failed to parse - show error message
                            val errorMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = "Error: ${parseResult.message}\n\nRaw response: ${parseResult.rawResponse}",
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                metadata = null,
                                parseWarning = "Parse failed"
                            )
                            _messages.value += errorMessage
                        }
                    }
                },
                onFailure = { exception ->
                    val errorMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "Error: ${exception.message}",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value += errorMessage
                }
            )
            _isLoading.value = false
        }
    }
}
```

#### 5.4 Update UI to display metadata
**File:** `composeApp/src/commonMain/kotlin/com/example/ai_window/App.kt`

```kotlin
@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column {
            // Message content
            Surface(
                color = if (message.isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    color = if (message.isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // NEW: Display metadata for AI messages
            if (!message.isUser && message.metadata != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Confidence badge
                    ConfidenceBadge(message.metadata.confidence)

                    // Category chip
                    CategoryChip(message.metadata.category)
                }
            }

            // NEW: Show parse warning if present
            if (message.parseWarning != null) {
                Text(
                    text = "⚠️ ${message.parseWarning}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ConfidenceBadge(confidence: Double) {
    val color = when {
        confidence >= 0.7 -> Color.Green
        confidence >= 0.4 -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "Confidence: ${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CategoryChip(category: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
```

### 6. Testing Strategy

#### 6.1 Unit Tests for Response Parser
**File:** `shared/src/commonTest/kotlin/com/example/ai_window/ResponseParserTest.kt`

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseParserTest {

    @Test
    fun `parse valid JSON response`() {
        val json = """
            {
              "response": {
                "content": "Test answer",
                "metadata": {
                  "confidence": 0.95,
                  "category": "factual"
                }
              }
            }
        """.trimIndent()

        val result = ResponseParser.parse(json)

        assertTrue(result is ParseResult.Success)
        val data = (result as ParseResult.Success).data
        assertEquals("Test answer", data.response.content)
        assertEquals(0.95, data.response.metadata.confidence)
        assertEquals("factual", data.response.metadata.category)
    }

    @Test
    fun `parse plain text fallback`() {
        val plainText = "This is just plain text"

        val result = ResponseParser.parse(plainText)

        assertTrue(result is ParseResult.Partial)
        val partial = result as ParseResult.Partial
        assertEquals(plainText, partial.data.response.content)
        assertEquals(0.0, partial.data.response.metadata.confidence)
        assertEquals("plaintext_fallback", partial.data.response.metadata.category)
    }

    @Test
    fun `parse JSON wrapped in markdown`() {
        val markdown = """
            ```json
            {
              "response": {
                "content": "Answer",
                "metadata": {"confidence": 0.9, "category": "general"}
              }
            }
            ```
        """.trimIndent()

        val result = ResponseParser.parse(markdown)

        assertTrue(result is ParseResult.Success)
        assertEquals("Answer", (result as ParseResult.Success).data.response.content)
    }

    @Test
    fun `handle empty response`() {
        val result = ResponseParser.parse("")

        assertTrue(result is ParseResult.Error)
        assertEquals("Empty response from AI", (result as ParseResult.Error).message)
    }

    @Test
    fun `handle malformed JSON`() {
        val malformed = """{"response": {"content": "incomplete"""

        val result = ResponseParser.parse(malformed)

        assertTrue(result is ParseResult.Error)
    }

    @Test
    fun `validate confidence out of range`() {
        val json = """
            {
              "response": {
                "content": "Test",
                "metadata": {
                  "confidence": 1.5,
                  "category": "factual"
                }
              }
            }
        """.trimIndent()

        val result = ResponseParser.parse(json)

        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("confidence"))
    }

    @Test
    fun `warn on low confidence`() {
        val json = """
            {
              "response": {
                "content": "Uncertain answer",
                "metadata": {
                  "confidence": 0.2,
                  "category": "opinion"
                }
              }
            }
        """.trimIndent()

        val result = ResponseParser.parse(json)

        assertTrue(result is ParseResult.Partial)
        val partial = result as ParseResult.Partial
        assertTrue(partial.warning.contains("Low confidence"))
    }
}
```

#### 6.2 Integration Tests with Mock Service
**File:** `shared/src/commonTest/kotlin/com/example/ai_window/YandexGptServiceTest.kt`

```kotlin
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class YandexGptServiceTest {

    @Test
    fun `sendMessage returns structured response`() = runTest {
        // Mock implementation
        val mockService = object : YandexGptService("test-key", "test-folder") {
            override suspend fun sendMessage(
                userMessage: String,
                conversationHistory: List<ChatMessage>
            ): Result<ParseResult<AIResponse>> {
                // Simulate successful API response
                val mockJson = """
                    {
                      "response": {
                        "content": "Mocked answer to: $userMessage",
                        "metadata": {"confidence": 0.9, "category": "factual"}
                      }
                    }
                """.trimIndent()

                return Result.success(ResponseParser.parse(mockJson))
            }
        }

        val result = mockService.sendMessage("Test question")

        assertTrue(result.isSuccess)
        val parseResult = result.getOrNull()
        assertTrue(parseResult is ParseResult.Success)
    }

    @Test
    fun `sendMessage handles plain text response`() = runTest {
        val mockService = object : YandexGptService("test-key", "test-folder") {
            override suspend fun sendMessage(
                userMessage: String,
                conversationHistory: List<ChatMessage>
            ): Result<ParseResult<AIResponse>> {
                // Simulate model returning plain text
                return Result.success(
                    ResponseParser.parse("This is plain text response")
                )
            }
        }

        val result = mockService.sendMessage("Question")

        assertTrue(result.isSuccess)
        val parseResult = result.getOrNull()
        assertTrue(parseResult is ParseResult.Partial)
    }
}
```

#### 6.3 Manual Testing Checklist

**Test scenarios to verify:**

1. **Standard queries:**
   - [ ] "What is 2+2?" → Factual response with high confidence
   - [ ] "Tell me a joke" → General response
   - [ ] "What do you think about AI?" → Opinion with moderate confidence

2. **Edge cases:**
   - [ ] Empty message → Error handling
   - [ ] Very long message (>1000 chars) → Response truncation
   - [ ] Special characters: `"quotes"`, `\n newlines`, `{json}`

3. **Format adherence:**
   - [ ] 10 consecutive queries all return valid JSON (or graceful fallback)
   - [ ] Confidence values are always between 0.0-1.0
   - [ ] Category values are consistent

4. **UI display:**
   - [ ] Confidence badge shows correct color (green/orange/red)
   - [ ] Category chip displays correctly
   - [ ] Parse warnings appear when appropriate
   - [ ] Fallback plain text displays without errors

5. **Error scenarios:**
   - [ ] Network timeout → Error message displayed
   - [ ] Invalid API key → Error message displayed
   - [ ] Malformed JSON → Fallback or error message

#### 6.4 Logging for Debugging

Add logging to track parse results:

```kotlin
// In YandexGptService or ChatViewModel
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
```

## Advanced Topics

### Response Category Validation

Define allowed categories as an enum for type safety:

```kotlin
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

// Update ResponseMetadata to use enum:
@Serializable
data class ResponseMetadata(
    val confidence: Double = 0.5,
    val category: String = "general"  // Keep as String for API compatibility
) {
    val categoryEnum: ResponseCategory
        get() = ResponseCategory.fromString(category)
}
```

### Format Versioning Strategy

Support multiple format versions for backward compatibility:

```kotlin
@Serializable
data class AIResponse(
    val response: ResponseContent,
    val version: String = "1.0"
) {
    companion object {
        const val CURRENT_VERSION = "1.0"
        val SUPPORTED_VERSIONS = setOf("1.0")

        fun isVersionSupported(version: String): Boolean {
            return version in SUPPORTED_VERSIONS
        }
    }
}

// In parser:
fun parse(rawResponse: String): ParseResult<AIResponse> {
    // ... parsing code ...

    val parsed = json.decodeFromString<AIResponse>(jsonString)

    // Check version compatibility
    if (!AIResponse.isVersionSupported(parsed.version)) {
        return ParseResult.Partial(
            data = parsed,
            warning = "Unsupported format version: ${parsed.version}"
        )
    }

    // ... validation ...
}
```

### Future Format Extensions

Plan for extensibility:

```kotlin
// Version 1.0 (current):
@Serializable
data class ResponseMetadata(
    val confidence: Double = 0.5,
    val category: String = "general"
)

// Version 2.0 (future):
@Serializable
data class ResponseMetadataV2(
    val confidence: Double = 0.5,
    val category: String = "general",
    val sources: List<String>? = null,  // New field
    val language: String? = null,        // New field
    val processingTime: Long? = null     // New field
)
```

### Prompt Engineering Tips

**For better structured responses from Yandex GPT:**

1. **Use specific examples in every request:**
```kotlin
val enhancedPrompt = """
IMPORTANT: Respond ONLY in this JSON format (no additional text):

EXAMPLE:
{"response": {"content": "Your answer", "metadata": {"confidence": 0.95, "category": "factual"}}}

Valid categories: factual, opinion, suggestion, error, general
Confidence range: 0.0 (no confidence) to 1.0 (very confident)

Question: $userMessage

JSON Response:
""".trimIndent()
```

2. **Request JSON in the last line:**
```kotlin
val prompt = """
$FORMAT_INSTRUCTIONS

Question: $userMessage

Now respond with ONLY the JSON (no markdown, no explanation):
""".trimIndent()
```

3. **Use lower temperature for consistency:**
```kotlin
temperature = 0.1  // Even lower for very structured responses
```

## Expected Result
The AI agent returns responses in a consistent, structured format that can be reliably parsed and processed by the application. The system gracefully handles both valid JSON responses and fallback scenarios.

## Success Criteria
- [x] Response format is clearly defined and documented
- [x] Yandex GPT API specifics are addressed (no system role support)
- [x] Agent prompt includes format specifications with examples
- [x] Parser successfully extracts data from AI responses with fallback strategies
- [x] Application handles valid JSON, plain text, and malformed responses
- [x] At least 7 different format examples are provided (including edge cases)
- [x] Unit tests cover all parsing scenarios
- [x] UI displays metadata (confidence badge, category chip)
- [x] Architecture integration plan is documented
- [x] Validation and versioning strategies are defined

## Notes

### Best Practices
- **Use Native JSON Schema (Approach 1) as primary method** - provides guaranteed structure
- **Keep Prompt-based (Approach 2) as fallback** - for compatibility and testing
- Log all parse warnings for continuous improvement
- Monitor confidence distribution and category usage
- Version your format schema for backward compatibility

### Performance Considerations
- **JSON Schema approach:**
  - ✅ Better token efficiency (no format instructions in prompt)
  - ✅ Higher reliability (API validation)
  - ✅ Can use higher temperature (0.6) for more creative responses
  - ⚠️ Requires API support (may not be available in all regions/accounts)

- **Prompt-based approach:**
  - ✅ Works with any Yandex GPT version
  - ✅ More flexible (can change format without API changes)
  - ⚠️ Lower reliability (model may ignore instructions)
  - ⚠️ Higher token usage (format instructions in every request)
  - ⚠️ Requires lower temperature (0.2) for consistency

### Debugging Tips
- Start with `useNativeJsonSchema = false` to test prompt-based approach
- Once confirmed working, switch to `useNativeJsonSchema = true`
- If JSON Schema fails, check Yandex Cloud console for feature availability
- Monitor logs to see which parsing path is used (strict vs fallback)
- Compare response quality between both approaches

---

## Implementation Checklist: Files to Modify

Follow this order for systematic implementation:

### Phase 1: Data Models (shared module)
- [ ] **shared/src/commonMain/kotlin/com/example/ai_window/model/ResponseSchema.kt** (NEW FILE)
  - Create `ResponseSchema` object with `getSchema()` method
  - Define JSON Schema structure for response format
  - Implement `getSchemaAsMap()` for API compatibility

- [ ] **shared/src/commonMain/kotlin/com/example/ai_window/model/YandexGptModels.kt**
  - Update `CompletionOptions` to include `jsonSchema: JsonSchema?` field
  - Add `JsonSchema` data class
  - Add `AIResponse`, `ResponseContent`, `ResponseMetadata` classes
  - Add `ParseResult` sealed class (Success/Partial/Error)
  - Add `ValidationResult` sealed class
  - Add `ResponseCategory` enum
  - Add `ResponseParser` object with:
    - `parseStrict()` method for JSON Schema responses
    - `parse()` method for prompt-based responses with fallbacks

- [ ] **shared/src/commonMain/kotlin/com/example/ai_window/model/ChatMessage.kt**
  - Add `metadata: ResponseMetadata?` field
  - Add `parseWarning: String?` field
  - Update serialization annotations if needed

### Phase 2: Service Layer (shared module)
- [ ] **shared/src/commonMain/kotlin/com/example/ai_window/service/YandexGptService.kt**
  - Add constructor parameter: `useNativeJsonSchema: Boolean = true`
  - Add `FORMAT_INSTRUCTIONS` constant (for fallback approach)
  - Modify `sendMessage()` return type to `Result<ParseResult<AIResponse>>`
  - Implement dual approach logic:
    - If `useNativeJsonSchema`: use `CompletionOptions` with `jsonSchema` parameter
    - If not: prepend `FORMAT_INSTRUCTIONS` to messages, use lower temperature (0.2)
  - Call `ResponseParser.parseStrict()` for JSON Schema responses
  - Call `ResponseParser.parse()` for prompt-based responses
  - Add logging for parse results and approach used

### Phase 3: ViewModel (composeApp module)
- [ ] **composeApp/src/commonMain/kotlin/com/example/ai_window/ChatViewModel.kt**
  - Update `sendMessage()` to handle `ParseResult` variants
  - Add logic for `ParseResult.Success` → create ChatMessage with metadata
  - Add logic for `ParseResult.Partial` → create ChatMessage with warning
  - Add logic for `ParseResult.Error` → display error message
  - Add debug logging for parse results

### Phase 4: UI Layer (composeApp module)
- [ ] **composeApp/src/commonMain/kotlin/com/example/ai_window/App.kt**
  - Update `MessageBubble` composable to display metadata
  - Add `ConfidenceBadge` composable (green/orange/red color coding)
  - Add `CategoryChip` composable (display category name)
  - Add warning indicator for `parseWarning` field
  - Test UI with mock data

### Phase 5: Testing (both modules)
- [ ] **shared/src/commonTest/kotlin/com/example/ai_window/ResponseParserTest.kt** (NEW FILE)
  - Test valid JSON parsing
  - Test plain text fallback
  - Test markdown-wrapped JSON
  - Test empty response handling
  - Test malformed JSON handling
  - Test confidence validation
  - Test low confidence warning

- [ ] **shared/src/commonTest/kotlin/com/example/ai_window/YandexGptServiceTest.kt** (NEW FILE)
  - Mock service tests for structured responses
  - Mock service tests for plain text fallback
  - Integration test scenarios

### Phase 6: Validation & Final Touches
- [ ] **Verify kotlinx.serialization is configured** in build.gradle.kts
- [ ] **Run all tests:** `./gradlew test`
- [ ] **Build project:** `./gradlew build`
- [ ] **Manual testing with real Yandex GPT API:**
  - [ ] Standard factual questions
  - [ ] Opinion questions
  - [ ] Edge cases (empty, special chars)
  - [ ] Verify UI metadata display
  - [ ] Check console logs for parse results
- [ ] **Document any Yandex GPT behavior patterns observed**

### Estimated Time per Phase:
- Phase 1 (Models): ~30 minutes
- Phase 2 (Service): ~20 minutes
- Phase 3 (ViewModel): ~20 minutes
- Phase 4 (UI): ~30 minutes
- Phase 5 (Testing): ~45 minutes
- Phase 6 (Validation): ~30 minutes
- **Total: ~2.5-3 hours**

### Debugging Tips:
- **If JSON Schema parameter not recognized:** Your Yandex Cloud account may not have this feature yet - use prompt-based approach
- If parsing always fails: Check `FORMAT_INSTRUCTIONS` format
- If UI doesn't show metadata: Verify ChatMessage serialization
- If tests fail: Check kotlinx.serialization version compatibility
- If Yandex GPT ignores JSON (prompt-based): Try temperature = 0.1, add more examples to prompt
- **Compare both approaches:** Run same queries with `useNativeJsonSchema = true/false` to see difference

### Reference Documentation:
- Yandex Cloud Structured Output: https://yandex.cloud/ru/docs/ai-studio/concepts/generation/structured-output
- JSON Schema specification: https://json-schema.org/
- Kotlin serialization: https://github.com/Kotlin/kotlinx.serialization

### day_3

# AI Requirements Gathering Assistant - Technical Specification

## Overview
An AI-powered assistant built on YandexGPT that conducts natural conversations to collect project requirements and automatically generates a structured technical specification document.

## Core Functionality

### Information Collection (6 Required Sections)
1. **Project Overview** - name, description, objectives
2. **Target Audience** - users, scale
3. **Features** - core functions with priorities (must/should/nice to have)
4. **Technical Requirements** - platforms, tech stack, integrations
5. **Non-Functional Requirements** - performance, security, availability
6. **Timeline & Budget** - deadlines, constraints

### Auto-Completion Logic
The assistant must **autonomously** stop and generate the final specification when:
- All 6 sections have sufficient information collected
- Maximum 10-15 questions asked
- User explicitly requests finalization

## Expected Behavior

**Conversation Flow:**
1. Natural dialogue (not questionnaire-style)
2. Clarifying questions when needed
3. Suggest reasonable defaults for unknown details
4. **Self-triggered completion** - no permission asking

**Completion Signal:**
```
📋 REQUIREMENTS GATHERING COMPLETED. GENERATING SPECIFICATION...
```
Followed by structured technical specification document.

## YandexGPT Configuration

```json
{
  "modelUri": "gpt://{folder_id}/yandexgpt-lite/latest",
  "completionOptions": {
    "temperature": 0.7,
    "maxTokens": 8000
  }
}
```

## Key Constraints
- ≤15 questions total
- Autonomous decision-making (no "is this enough?" questions)
- Complete all 6 sections before generating spec
- Natural conversational tone throughout

## Success Criteria
✅ Gathers all 6 required sections
✅ Natural dialogue flow
✅ Self-determines completion point
✅ Outputs structured, comprehensive specification
✅ Stays within question limit

---

### day_5

## Objective
Experiment with different temperature values (0.0, 0.7, 1.0) for Yandex GPT API to understand how temperature affects response quality, creativity, and consistency. Compare results using advanced metrics.

**Note:** Yandex GPT API supports temperature values from 0.0 to 1.0 (maximum).

## Implementation

### Created Files
1. **shared/src/commonMain/kotlin/com/example/ai_window/model/TemperatureExperiment.kt**
   - `TemperatureResult` - результат эксперимента
   - `TemperatureMetrics` - метрики анализа
   - `VariabilityMetrics` - метрики вариативности
   - `TemperatureRecommendation` - рекомендации по использованию
   - `ExperimentState` - состояния эксперимента
   - `ExecutionMode` - режимы выполнения (параллельный/последовательный)

2. **shared/src/commonMain/kotlin/com/example/ai_window/service/TemperatureExperimentService.kt**
   - `runExperiment()` - запуск эксперимента с заданной температурой
   - `calculateMetrics()` - вычисление всех метрик
   - `calculateSelfBLEU()` - метрика разнообразия между генерациями
   - `calculateSemanticConsistency()` - семантическая согласованность
   - `calculateVariability()` - вариативность ответов
   - `generateRecommendation()` - автоматические рекомендации

3. **composeApp/src/commonMain/kotlin/com/example/ai_window/TemperatureViewModel.kt**
   - Управление состоянием экспериментов для 3 температур
   - Поддержка параллельного и последовательного выполнения
   - Примеры вопросов для разных типов задач

4. **composeApp/src/commonMain/kotlin/com/example/ai_window/TemperatureScreen.kt**
   - UI с карточками для каждой температуры
   - Цветовая кодировка (синий, желтый, красный)
   - Отображение метрик и рекомендаций
   - Сравнительная таблица результатов

### Temperature Values
Актуальные значения температур: **0.1, 0.6, 0.9**
- **0.1** - Почти детерминированная (для фактов, документации, кода)
- **0.6** - Сбалансированная (для чат-ботов, общих задач)
- **0.9** - Высокая креативность (для историй, идей, брейнсторминга)

### Metrics Implemented
1. **Self-BLEU** - разнообразие между несколькими генерациями одного промпта
   - 0.0 = полностью разные ответы
   - 1.0 = идентичные ответы

2. **Semantic Consistency** - семантическая схожесть при повторных запросах
   - Анализ частоты общих ключевых слов
   - Стабильность основных тем

3. **Response Variability** - вариативность структуры ответов
   - Стандартное отклонение длины
   - Разброс уникальных слов
   - Структурное разнообразие (предложения, абзацы)

### Features
- ✅ Запуск отдельных экспериментов или всех сразу
- ✅ Два режима выполнения: параллельный (быстро) и последовательный (наглядно)
- ✅ Примеры вопросов для разных типов задач
- ✅ Прогресс-индикаторы для каждого эксперимента
- ✅ Автоматический анализ с рекомендациями
- ✅ **Сравнительная таблица метрик (отображается первой после запуска)**
  - Self-BLEU, семантическая согласованность
  - Среднее количество слов и уникальных слов
  - Структурное разнообразие
- ✅ **Экспорт результатов в текстовый файл (кнопка 💾)**
  - Сравнительная таблица метрик
  - Все 3 примера ответов для каждой температуры
  - Детальные метрики и рекомендации
  - Автоматическое именование файла с timestamp

### Example Usage
```bash
./gradlew :composeApp:run
```
1. Перейти на вкладку "🌡️ Температура"
2. Выбрать пример вопроса или ввести свой
3. Нажать "Запустить все эксперименты"
4. Сравнить результаты и изучить рекомендации

## Results Format
Каждый эксперимент выводит:
- Пример ответа
- Метрики (Self-BLEU, согласованность, количество слов)
- Автоматические рекомендации: для каких задач подходит данная температура

## Key Findings
**Temperature 0.1:**
- Максимальная повторяемость (Self-BLEU > 0.8)
- Идеально для фактических вопросов
- Подходит: документация, перевод, генерация кода

**Temperature 0.6:**
- Сбалансированный режим
- Компромисс между точностью и креативностью
- Подходит: чат-боты, объяснения, маркетинг

**Temperature 0.9:**
- Высокое разнообразие (Self-BLEU < 0.3)
- Высокая креативность
- Подходит: истории, идеи, брейнсторминг

## Success Criteria
✅ Реализованы эксперименты с 3 температурами
✅ Вычисляются продвинутые метрики (Self-BLEU, Semantic Consistency, Variability)
✅ Автоматический анализ и рекомендации
✅ UI с визуальным сравнением результатов
✅ Два режима выполнения (параллельный/последовательный)
✅ Проект успешно собирается