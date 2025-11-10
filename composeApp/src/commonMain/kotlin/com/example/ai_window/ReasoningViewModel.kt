package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.model.AIResponse
import com.example.ai_window.model.ParseResult
import com.example.ai_window.service.ReasoningComparisonService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Day 4: Reasoning Comparison Screen
 *
 * Manages state for 4 different reasoning approaches:
 * 1. Direct
 * 2. Step-by-step
 * 3. AI-generated prompt
 * 4. Experts panel
 */
class ReasoningViewModel(
    apiKey: String,
    folderId: String
) : ViewModel() {

    private val service = ReasoningComparisonService(apiKey, folderId)

    // State for each approach
    private val _directResult = MutableStateFlow<ReasoningResult?>(null)
    val directResult: StateFlow<ReasoningResult?> = _directResult.asStateFlow()

    private val _stepByStepResult = MutableStateFlow<ReasoningResult?>(null)
    val stepByStepResult: StateFlow<ReasoningResult?> = _stepByStepResult.asStateFlow()

    private val _aiPromptResult = MutableStateFlow<ReasoningResult?>(null)
    val aiPromptResult: StateFlow<ReasoningResult?> = _aiPromptResult.asStateFlow()

    private val _expertsPanelResult = MutableStateFlow<ReasoningResult?>(null)
    val expertsPanelResult: StateFlow<ReasoningResult?> = _expertsPanelResult.asStateFlow()

    // Loading states for each approach
    private val _directLoading = MutableStateFlow(false)
    val directLoading: StateFlow<Boolean> = _directLoading.asStateFlow()

    private val _stepByStepLoading = MutableStateFlow(false)
    val stepByStepLoading: StateFlow<Boolean> = _stepByStepLoading.asStateFlow()

    private val _aiPromptLoading = MutableStateFlow(false)
    val aiPromptLoading: StateFlow<Boolean> = _aiPromptLoading.asStateFlow()

    private val _expertsPanelLoading = MutableStateFlow(false)
    val expertsPanelLoading: StateFlow<Boolean> = _expertsPanelLoading.asStateFlow()

    // Error states
    private val _directError = MutableStateFlow<String?>(null)
    val directError: StateFlow<String?> = _directError.asStateFlow()

    private val _stepByStepError = MutableStateFlow<String?>(null)
    val stepByStepError: StateFlow<String?> = _stepByStepError.asStateFlow()

    private val _aiPromptError = MutableStateFlow<String?>(null)
    val aiPromptError: StateFlow<String?> = _aiPromptError.asStateFlow()

    private val _expertsPanelError = MutableStateFlow<String?>(null)
    val expertsPanelError: StateFlow<String?> = _expertsPanelError.asStateFlow()

    /**
     * Run Direct approach
     */
    fun runDirectApproach() {
        viewModelScope.launch {
            _directLoading.value = true
            _directError.value = null

            val parseResult = service.runDirectApproach()
            _directResult.value = parseResultToReasoningResult(parseResult)

            _directLoading.value = false
        }
    }

    /**
     * Run Step-by-step approach
     */
    fun runStepByStepApproach() {
        viewModelScope.launch {
            _stepByStepLoading.value = true
            _stepByStepError.value = null

            val parseResult = service.runStepByStepApproach()
            _stepByStepResult.value = parseResultToReasoningResult(parseResult)

            _stepByStepLoading.value = false
        }
    }

    /**
     * Run AI-generated prompt approach (2 API calls)
     */
    fun runAIPromptApproach() {
        viewModelScope.launch {
            _aiPromptLoading.value = true
            _aiPromptError.value = null

            val parseResult = service.runAIPromptApproach()
            _aiPromptResult.value = parseResultToReasoningResult(parseResult)

            _aiPromptLoading.value = false
        }
    }

    // Stream of messages for experts panel
    private val _expertMessages = MutableStateFlow<List<ExpertMessage>>(emptyList())
    val expertMessages: StateFlow<List<ExpertMessage>> = _expertMessages.asStateFlow()

    /**
     * Run Experts panel approach with streaming messages
     */
    fun runExpertsPanelApproach() {
        viewModelScope.launch {
            _expertsPanelLoading.value = true
            _expertsPanelError.value = null
            _expertMessages.value = emptyList()  // Clear previous messages

            val parseResult = service.runExpertsPanelApproach { role, content ->
                // Add message to stream
                val expertRole = when (role) {
                    "MANAGER" -> ExpertRole.MANAGER
                    "HR_EXPERT" -> ExpertRole.HR_EXPERT
                    "IT_EXPERT" -> ExpertRole.IT_EXPERT
                    "BUSINESS_EXPERT" -> ExpertRole.BUSINESS_EXPERT
                    else -> ExpertRole.MANAGER
                }

                _expertMessages.value = _expertMessages.value + ExpertMessage(
                    role = expertRole,
                    content = content
                )
            }

            _expertsPanelResult.value = parseResultToReasoningResult(parseResult)
            _expertsPanelLoading.value = false
        }
    }

    /**
     * Run all approaches in parallel
     */
    fun runAllApproaches() {
        runDirectApproach()
        runStepByStepApproach()
        runAIPromptApproach()
        runExpertsPanelApproach()
    }

    /**
     * Reset all results
     */
    fun resetAllResults() {
        _directResult.value = null
        _stepByStepResult.value = null
        _aiPromptResult.value = null
        _expertsPanelResult.value = null

        _directError.value = null
        _stepByStepError.value = null
        _aiPromptError.value = null
        _expertsPanelError.value = null
    }

    /**
     * Convert ParseResult to ReasoningResult
     */
    private fun parseResultToReasoningResult(parseResult: ParseResult<AIResponse>): ReasoningResult {
        return when (parseResult) {
            is ParseResult.Success -> {
                ReasoningResult.Success(
                    response = parseResult.data,
                    wordCount = parseResult.data.response.metadata.wordCount ?: 0,
                    hasSteps = parseResult.data.response.metadata.hasSteps ?: false
                )
            }
            is ParseResult.Partial -> {
                ReasoningResult.Success(
                    response = parseResult.data,
                    wordCount = parseResult.data.response.metadata.wordCount ?: 0,
                    hasSteps = parseResult.data.response.metadata.hasSteps ?: false,
                    warning = parseResult.warning
                )
            }
            is ParseResult.Error -> {
                ReasoningResult.Error(parseResult.message)
            }
        }
    }

    // Clear individual errors
    fun clearDirectError() { _directError.value = null }
    fun clearStepByStepError() { _stepByStepError.value = null }
    fun clearAIPromptError() { _aiPromptError.value = null }
    fun clearExpertsPanelError() { _expertsPanelError.value = null }
}

/**
 * Result wrapper for reasoning approaches
 */
sealed class ReasoningResult {
    data class Success(
        val response: AIResponse,
        val wordCount: Int,
        val hasSteps: Boolean,
        val warning: String? = null
    ) : ReasoningResult()

    data class Error(val message: String) : ReasoningResult()

    data class Streaming(
        val messages: List<ExpertMessage>
    ) : ReasoningResult()
}

/**
 * Message in the expert panel chain
 */
data class ExpertMessage(
    val role: ExpertRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Roles in the expert panel
 */
enum class ExpertRole(val displayName: String, val emoji: String) {
    MANAGER("Менеджер", "👔"),
    HR_EXPERT("HR-специалист (Мария)", "👤"),
    IT_EXPERT("IT-аналитик (Дмитрий)", "💻"),
    BUSINESS_EXPERT("Бизнес-консультант (Елена)", "📊")
}
