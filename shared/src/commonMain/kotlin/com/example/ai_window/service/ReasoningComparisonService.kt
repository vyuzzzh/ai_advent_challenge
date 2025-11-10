package com.example.ai_window.service

import com.example.ai_window.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Service for Day 4: Comparing different reasoning approaches
 *
 * Provides 4 methods to test different prompting strategies on the same business case.
 */
class ReasoningComparisonService(
    private val apiKey: String,
    private val folderId: String
) {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val apiUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

    /**
     * Approach 1: Direct answer (no special instructions)
     */
    suspend fun runDirectApproach(): ParseResult<AIResponse> {
        val prompt = ReasoningPrompts.wrapInJsonFormat(ReasoningPrompts.getDirectPrompt())
        return sendRequest(prompt)
    }

    /**
     * Approach 2: Step-by-step reasoning
     */
    suspend fun runStepByStepApproach(): ParseResult<AIResponse> {
        val prompt = ReasoningPrompts.wrapInJsonFormat(ReasoningPrompts.getStepByStepPrompt())
        return sendRequest(prompt)
    }

    /**
     * Approach 3: AI-generated prompt (meta-prompting)
     * This requires TWO API calls:
     * 1. First call: Ask AI to generate optimal prompt
     * 2. Second call: Use that prompt to solve the problem
     */
    suspend fun runAIPromptApproach(): ParseResult<AIResponse> {
        // Step 1: Generate the prompt
        val promptGenerationRequest = ReasoningPrompts.getAIPromptGenerationRequest()
        val generatedPromptResult = sendRequestForPromptGeneration(promptGenerationRequest)

        if (generatedPromptResult is ParseResult.Error) {
            return ParseResult.Error(
                "Failed to generate AI prompt: ${generatedPromptResult.message}",
                generatedPromptResult.rawResponse
            )
        }

        // Extract the generated prompt from the response
        val generatedPrompt = when (generatedPromptResult) {
            is ParseResult.Success -> generatedPromptResult.data.response.content
            is ParseResult.Partial -> generatedPromptResult.data.response.content
            is ParseResult.Error -> return generatedPromptResult
        }

        // Step 2: Use the generated prompt to solve the problem
        val finalPrompt = ReasoningPrompts.wrapInJsonFormat(generatedPrompt)
        return sendRequest(finalPrompt)
    }

    /**
     * Approach 4: Experts panel with Manager orchestration
     * Manager delegates tasks and can ask follow-up questions
     */
    suspend fun runExpertsPanelApproach(
        onMessage: (role: String, content: String) -> Unit = { _, _ -> }
    ): ParseResult<AIResponse> {
        val conversation = mutableListOf<Pair<String, String>>()

        // MANAGER: Start
        val managerStart = "Задача получена. Анализирую бизнес-кейс и формирую план работы.\n\n" +
                "Буду последовательно консультироваться с экспертами:\n" +
                "1. HR-специалист - для анализа влияния на персонал\n" +
                "2. IT-аналитик - для технических решений\n" +
                "3. Бизнес-консультант - для оценки эффективности\n\n" +
                "Отдаю задачу на проработку HR-специалисту..."
        onMessage("MANAGER", managerStart)
        conversation.add("MANAGER" to managerStart)

        // HR EXPERT
        val hrPrompt = ReasoningPrompts.getHRExpertPrompt()
        val hrResult = sendExpertRequest(hrPrompt)
        if (hrResult is ParseResult.Error) {
            return ParseResult.Error("HR Expert failed: ${hrResult.message}", hrResult.rawResponse)
        }
        val hrResponse = extractContent(hrResult)
        onMessage("HR_EXPERT", hrResponse)
        conversation.add("HR_EXPERT" to hrResponse)

        // MANAGER: Received HR response
        val managerAfterHR = "Получил анализ от HR-специалиста. Мария выделила ключевые риски для персонала и качества найма.\n\n" +
                "Передаю задачу IT-аналитику для проработки технических решений..."
        onMessage("MANAGER", managerAfterHR)
        conversation.add("MANAGER" to managerAfterHR)

        // IT EXPERT
        val itPrompt = ReasoningPrompts.getITExpertPrompt()
        val itResult = sendExpertRequest(itPrompt)
        if (itResult is ParseResult.Error) {
            return ParseResult.Error("IT Expert failed: ${itResult.message}", itResult.rawResponse)
        }
        val itResponse = extractContent(itResult)
        onMessage("IT_EXPERT", itResponse)
        conversation.add("IT_EXPERT" to itResponse)

        // MANAGER: Decide if clarification needed
        val managerDecision = askManagerForDecision(hrResponse, itResponse)
        onMessage("MANAGER", managerDecision.thinking)
        conversation.add("MANAGER" to managerDecision.thinking)

        // If manager wants clarification from IT
        var finalItResponse = itResponse
        if (managerDecision.needsClarification && managerDecision.clarifyWith == "IT") {
            val clarificationPrompt = ReasoningPrompts.getITClarificationPrompt(
                originalResponse = itResponse,
                question = managerDecision.question
            )
            val clarificationResult = sendExpertRequest(clarificationPrompt)
            if (clarificationResult !is ParseResult.Error) {
                val clarification = extractContent(clarificationResult)
                onMessage("IT_EXPERT", clarification)
                conversation.add("IT_EXPERT" to clarification)
                finalItResponse = "$itResponse\n\nУточнение:\n$clarification"

                val managerAck = "Спасибо за уточнение. Теперь картина яснее.\n\n" +
                        "Передаю задачу бизнес-консультанту для оценки эффективности..."
                onMessage("MANAGER", managerAck)
                conversation.add("MANAGER" to managerAck)
            }
        } else {
            val managerNext = "Получил технические рекомендации от IT-аналитика.\n\n" +
                    "Передаю задачу бизнес-консультанту для финансового анализа..."
            onMessage("MANAGER", managerNext)
            conversation.add("MANAGER" to managerNext)
        }

        // BUSINESS EXPERT
        val businessPrompt = ReasoningPrompts.getBusinessExpertPrompt()
        val businessResult = sendExpertRequest(businessPrompt)
        if (businessResult is ParseResult.Error) {
            return ParseResult.Error("Business Expert failed: ${businessResult.message}", businessResult.rawResponse)
        }
        val businessResponse = extractContent(businessResult)
        onMessage("BUSINESS_EXPERT", businessResponse)
        conversation.add("BUSINESS_EXPERT" to businessResponse)

        // MANAGER: Final synthesis
        val managerSynthesis = "Получил все экспертные мнения. Формирую итоговое решение, объединяя:\n" +
                "- HR аспекты от Марии\n" +
                "- Технические решения от Дмитрия\n" +
                "- Бизнес-обоснование от Елены\n\n" +
                "Анализирую и синтезирую рекомендации..."
        onMessage("MANAGER", managerSynthesis)
        conversation.add("MANAGER" to managerSynthesis)

        // SYNTHESIS
        val synthesisPrompt = ReasoningPrompts.getManagerSynthesisPrompt(
            hrOpinion = hrResponse,
            itOpinion = finalItResponse,
            businessOpinion = businessResponse
        )
        val synthesisResult = sendRequest(synthesisPrompt)
        val finalSynthesis = when (synthesisResult) {
            is ParseResult.Success -> synthesisResult.data.response.content
            is ParseResult.Partial -> synthesisResult.data.response.content
            is ParseResult.Error -> return synthesisResult
        }
        onMessage("MANAGER", finalSynthesis)
        conversation.add("MANAGER" to finalSynthesis)

        // Combine all messages into final response
        val combinedContent = conversation.joinToString("\n\n---\n\n") { (role, content) ->
            val emoji = when (role) {
                "MANAGER" -> "👔"
                "HR_EXPERT" -> "👤"
                "IT_EXPERT" -> "💻"
                "BUSINESS_EXPERT" -> "📊"
                else -> "💬"
            }
            val name = when (role) {
                "MANAGER" -> "Менеджер"
                "HR_EXPERT" -> "HR-специалист (Мария)"
                "IT_EXPERT" -> "IT-аналитик (Дмитрий)"
                "BUSINESS_EXPERT" -> "Бизнес-консультант (Елена)"
                else -> role
            }
            "$emoji $name:\n$content"
        }

        val finalResponse = AIResponse(
            response = ResponseContent(
                title = "Панель экспертов с оркестрацией",
                content = combinedContent,
                metadata = ResponseMetadata(
                    confidence = 0.9,
                    category = "reasoning"
                )
            )
        )

        return ParseResult.Success(enrichWithMetrics(finalResponse))
    }

    /**
     * Extract content from ParseResult
     */
    private fun extractContent(result: ParseResult<AIResponse>): String {
        return when (result) {
            is ParseResult.Success -> result.data.response.content
            is ParseResult.Partial -> result.data.response.content
            is ParseResult.Error -> "Ошибка: ${result.message}"
        }
    }

    /**
     * Manager decision-making (simple rule-based for now)
     */
    private fun askManagerForDecision(hrOpinion: String, itOpinion: String): ManagerDecision {
        // Simple rule: if IT response is too short, ask for clarification
        return if (itOpinion.length < 300) {
            ManagerDecision(
                thinking = "Анализирую ответ IT-аналитика... Ответ слишком краткий, нужны технические детали.\n\n" +
                        "Запрашиваю у Дмитрия уточнение по техническим решениям...",
                needsClarification = true,
                clarifyWith = "IT",
                question = "Дмитрий, можешь детальнее расписать техническую архитектуру решения и конкретные инструменты?"
            )
        } else {
            ManagerDecision(
                thinking = "Анализирую ответ IT-аналитика... Получил детальные технические рекомендации.",
                needsClarification = false
            )
        }
    }

    /**
     * Send request for individual expert
     */
    private suspend fun sendExpertRequest(promptText: String): ParseResult<AIResponse> {
        val wrappedPrompt = ReasoningPrompts.wrapInJsonFormat(promptText)
        return sendRequest(wrappedPrompt)
    }

    /**
     * Manager decision data class
     */
    private data class ManagerDecision(
        val thinking: String,
        val needsClarification: Boolean,
        val clarifyWith: String = "",
        val question: String = ""
    )

    /**
     * Send request to Yandex GPT API with higher temperature for creative reasoning
     */
    private suspend fun sendRequest(promptText: String): ParseResult<AIResponse> {
        val request = YandexGptRequest(
            modelUri = "gpt://$folderId/yandexgpt/latest",  // Full model, not lite
            completionOptions = CompletionOptions(
                stream = false,
                temperature = 0.6,  // Moderate temperature for reasoning
                maxTokens = 8000    // Full model supports up to 8000 tokens
            ),
            messages = listOf(
                Message(role = "user", text = promptText)
            )
        )

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header("Authorization", "Api-Key $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val yandexResponse = response.body<YandexGptResponse>()
                val aiResponseText = yandexResponse.result.alternatives.firstOrNull()?.message?.text
                    ?: return ParseResult.Error("No response from AI", "")

                // Parse the structured response with metrics calculation
                // Use robust parser that handles markdown blocks
                val parseResult = ResponseParser.parse(aiResponseText)

                // Add reasoning metrics to the response
                return when (parseResult) {
                    is ParseResult.Success -> {
                        val enrichedResponse = enrichWithMetrics(parseResult.data)
                        ParseResult.Success(enrichedResponse)
                    }
                    is ParseResult.Partial -> {
                        val enrichedResponse = enrichWithMetrics(parseResult.data)
                        ParseResult.Partial(enrichedResponse, parseResult.warning)
                    }
                    is ParseResult.Error -> parseResult
                }
            } else {
                ParseResult.Error(
                    "API Error: ${response.status}",
                    response.bodyAsText()
                )
            }
        } catch (e: Exception) {
            ParseResult.Error(
                "Network error: ${e.message}",
                e.toString()
            )
        }
    }

    /**
     * Special request for prompt generation (returns plain text, not JSON)
     */
    private suspend fun sendRequestForPromptGeneration(promptText: String): ParseResult<AIResponse> {
        val request = YandexGptRequest(
            modelUri = "gpt://$folderId/yandexgpt-lite/latest",
            completionOptions = CompletionOptions(
                stream = false,
                temperature = 0.8,  // Higher creativity for prompt generation
                maxTokens = 2000
            ),
            messages = listOf(
                Message(role = "user", text = promptText)
            )
        )

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header("Authorization", "Api-Key $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val yandexResponse = response.body<YandexGptResponse>()
                val generatedPromptText = yandexResponse.result.alternatives.firstOrNull()?.message?.text
                    ?: return ParseResult.Error("No prompt generated", "")

                // Wrap the generated prompt in AIResponse structure (it's plain text)
                ParseResult.Success(
                    AIResponse(
                        response = ResponseContent(
                            title = "Generated Prompt",
                            content = generatedPromptText,
                            metadata = ResponseMetadata(
                                confidence = 0.9,
                                category = "reasoning"
                            )
                        )
                    )
                )
            } else {
                ParseResult.Error(
                    "API Error: ${response.status}",
                    response.bodyAsText()
                )
            }
        } catch (e: Exception) {
            ParseResult.Error(
                "Network error: ${e.message}",
                e.toString()
            )
        }
    }

    /**
     * Calculate and add reasoning metrics to the response
     */
    private fun enrichWithMetrics(response: AIResponse): AIResponse {
        val content = response.response.content

        // Calculate word count
        val wordCount = content.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        // Detect if response has step-by-step structure
        val hasSteps = detectStepByStepStructure(content)

        // Create enriched metadata
        val enrichedMetadata = response.response.metadata.copy(
            wordCount = wordCount,
            hasSteps = hasSteps
        )

        return response.copy(
            response = response.response.copy(
                metadata = enrichedMetadata
            )
        )
    }

    /**
     * Detect if content has step-by-step reasoning structure
     */
    private fun detectStepByStepStructure(content: String): Boolean {
        // Check for numbered lists (1., 2., 3., etc.)
        val numberedSteps = Regex("""(?:^|\n)\s*\d+\.\s+""").findAll(content).count()
        if (numberedSteps >= 3) return true

        // Check for explicit step markers
        val stepKeywords = listOf("Шаг", "шаг", "Step", "step", "Этап", "этап")
        val stepMatches = stepKeywords.sumOf { keyword ->
            Regex("$keyword\\s*\\d+").findAll(content).count()
        }
        if (stepMatches >= 3) return true

        // Check for markdown headers suggesting steps
        val headers = Regex("""(?:^|\n)#{1,3}\s+""").findAll(content).count()
        if (headers >= 3) return true

        return false
    }
}
