package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.model.ChatMessage
import com.example.ai_window.model.ChatState
import com.example.ai_window.model.ParseResult
import com.example.ai_window.service.RequirementsGatheringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel for Requirements Gathering mode (Day 3)
 *
 * Manages state for AI-powered requirements collection and specification generation.
 */
class PlanningViewModel(
    private val apiKey: String,
    private val folderId: String
) : ViewModel() {

    private val requirementsService = RequirementsGatheringService(apiKey, folderId)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chatState = MutableStateFlow(ChatState.IDLE)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Progress tracking
    private val _sectionsCompleted = MutableStateFlow<List<String>>(emptyList())
    val sectionsCompleted: StateFlow<List<String>> = _sectionsCompleted.asStateFlow()

    private val _questionsAsked = MutableStateFlow(0)
    val questionsAsked: StateFlow<Int> = _questionsAsked.asStateFlow()

    private val _isSpecificationComplete = MutableStateFlow(false)
    val isSpecificationComplete: StateFlow<Boolean> = _isSpecificationComplete.asStateFlow()

    // Total sections required
    val totalSections = 6
    val requiredSections = listOf(
        "project_overview",
        "target_audience",
        "features",
        "technical_requirements",
        "non_functional",
        "timeline_budget"
    )

    private fun generateId(): String {
        return "${Random.nextLong()}-${Random.nextLong()}"
    }

    /**
     * Start the requirements gathering session
     */
    fun startSession() {
        if (_messages.value.isNotEmpty()) {
            // Session already started
            return
        }

        viewModelScope.launch {
            _chatState.value = ChatState.LOADING

            requirementsService.startSession()
                .onSuccess { parseResult ->
                    handleParseResult(parseResult)
                    _chatState.value = ChatState.IDLE
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Ошибка запуска сессии"
                    _chatState.value = ChatState.ERROR
                }
        }
    }

    /**
     * Send user message
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || _chatState.value == ChatState.LOADING) return

        // Don't allow new messages if specification is complete
        if (_isSpecificationComplete.value) {
            _errorMessage.value = "Сбор требований завершен. Создайте новую сессию для нового проекта."
            return
        }

        // Add user message
        val userMessage = ChatMessage(
            id = generateId(),
            text = text,
            isUser = true
        )
        _messages.value = _messages.value + userMessage

        // Send to API
        viewModelScope.launch {
            _chatState.value = ChatState.LOADING
            _errorMessage.value = null

            requirementsService.sendMessage(text, _messages.value.dropLast(1))
                .onSuccess { parseResult ->
                    handleParseResult(parseResult)
                    _chatState.value = ChatState.IDLE
                }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Неизвестная ошибка"
                    _chatState.value = ChatState.ERROR
                }
        }
    }

    /**
     * Handle parsed AI response and update state
     */
    private fun handleParseResult(parseResult: ParseResult<com.example.ai_window.model.AIResponse>) {
        when (parseResult) {
            is ParseResult.Success -> {
                val aiResponse = parseResult.data
                val aiMessage = ChatMessage(
                    id = generateId(),
                    text = aiResponse.response.content,
                    title = aiResponse.response.title.takeIf { it.isNotBlank() },
                    isUser = false,
                    metadata = aiResponse.response.metadata,
                    parseWarning = null
                )
                _messages.value = _messages.value + aiMessage

                // Update progress tracking
                updateProgress(aiResponse.response.metadata)
            }

            is ParseResult.Partial -> {
                val aiResponse = parseResult.data
                val aiMessage = ChatMessage(
                    id = generateId(),
                    text = aiResponse.response.content,
                    title = aiResponse.response.title.takeIf { it.isNotBlank() },
                    isUser = false,
                    metadata = aiResponse.response.metadata,
                    parseWarning = parseResult.warning
                )
                _messages.value = _messages.value + aiMessage

                // Update progress tracking
                updateProgress(aiResponse.response.metadata)
            }

            is ParseResult.Error -> {
                val errorMessage = ChatMessage(
                    id = generateId(),
                    text = "❌ Parse Error: ${parseResult.message}\n\nRaw response: ${parseResult.rawResponse}",
                    isUser = false,
                    metadata = null,
                    parseWarning = "Parse failed"
                )
                _messages.value = _messages.value + errorMessage
            }
        }
    }

    /**
     * Update progress tracking from metadata
     */
    private fun updateProgress(metadata: com.example.ai_window.model.ResponseMetadata) {
        // Update sections completed
        metadata.sections_completed?.let { sections ->
            _sectionsCompleted.value = sections
        }

        // Update questions asked
        metadata.questions_asked?.let { count ->
            _questionsAsked.value = count
        }

        // Check if specification is complete
        metadata.is_complete?.let { complete ->
            if (complete) {
                _isSpecificationComplete.value = true
                println("🎉 Specification completed!")
            }
        }
    }

    /**
     * Reset session and start over
     */
    fun resetSession() {
        _messages.value = emptyList()
        _chatState.value = ChatState.IDLE
        _errorMessage.value = null
        _sectionsCompleted.value = emptyList()
        _questionsAsked.value = 0
        _isSpecificationComplete.value = false

        // Auto-start new session
        startSession()
    }

    fun clearError() {
        _errorMessage.value = null
        if (_chatState.value == ChatState.ERROR) {
            _chatState.value = ChatState.IDLE
        }
    }

    override fun onCleared() {
        super.onCleared()
        requirementsService.close()
    }
}