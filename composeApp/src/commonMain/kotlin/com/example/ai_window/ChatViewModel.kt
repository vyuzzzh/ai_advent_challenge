package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.database.ChatRepository
import com.example.ai_window.database.DatabaseHolder
import com.example.ai_window.model.ChatMessage
import com.example.ai_window.model.ChatState
import com.example.ai_window.model.ParseResult
import com.example.ai_window.service.AgentService
import com.example.ai_window.service.IntentDetector
import com.example.ai_window.service.YandexGptService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class ChatViewModel(
    private val apiKey: String,
    private val folderId: String
) : ViewModel() {

    private val yandexGptService = YandexGptService(apiKey, folderId)

    // Day 11: MCP tools integration
    private val intentDetector = IntentDetector()
    private val agentService = AgentService()

    // Day 9: ChatRepository для внешней памяти
    private val chatRepository: ChatRepository? = try {
        DatabaseHolder.getChatRepository()
    } catch (e: Exception) {
        null // БД опциональна, работаем без неё если не инициализирована
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chatState = MutableStateFlow(ChatState.IDLE)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Day 9: Загрузка сохраненных сообщений при инициализации
    init {
        loadSavedMessages()
    }

    /**
     * Day 9: Загружает сохраненные сообщения из БД.
     */
    private fun loadSavedMessages() {
        viewModelScope.launch {
            try {
                val savedMessages = chatRepository?.getAllMessages() ?: emptyList()
                if (savedMessages.isNotEmpty()) {
                    _messages.value = savedMessages
                    println("✅ Loaded ${savedMessages.size} messages from database")
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load messages: ${e.message}")
            }
        }
    }

    /**
     * Day 9: Сохраняет сообщение в БД.
     */
    private fun saveMessage(message: ChatMessage) {
        viewModelScope.launch {
            try {
                chatRepository?.saveChatMessage(message)
            } catch (e: Exception) {
                println("⚠️ Failed to save message: ${e.message}")
            }
        }
    }

    private fun generateId(): String {
        return "${Random.nextLong()}-${Random.nextLong()}"
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _chatState.value == ChatState.LOADING) return

        // Добавляем сообщение пользователя
        val userMessage = ChatMessage(
            id = generateId(),
            text = text,
            isUser = true,
            timestamp = System.currentTimeMillis() // Day 9: добавляем timestamp
        )
        _messages.value = _messages.value + userMessage
        saveMessage(userMessage) // Day 9: сохраняем в БД

        viewModelScope.launch {
            _chatState.value = ChatState.LOADING
            _errorMessage.value = null

            // Day 11: Проверяем, нужен ли MCP tool
            val detection = intentDetector.detect(text)

            when (detection) {
                is IntentDetector.DetectionResult.ToolDetected -> {
                    // Выполняем MCP tool
                    try {
                        val toolCall = detection.toolCall
                        println("[ChatViewModel] Executing MCP tool: ${toolCall.tool}")

                        val result = agentService.executeTool(toolCall)
                        val formattedResult = agentService.formatToolResult(toolCall, result)

                        val aiMessage = ChatMessage(
                            id = generateId(),
                            text = formattedResult,
                            title = null,
                            isUser = false,
                            metadata = null,
                            parseWarning = null,
                            timestamp = System.currentTimeMillis()
                        )
                        _messages.value = _messages.value + aiMessage
                        saveMessage(aiMessage)
                        _chatState.value = ChatState.IDLE

                    } catch (e: Exception) {
                        _errorMessage.value = "Ошибка MCP tool: ${e.message}"
                        _chatState.value = ChatState.ERROR
                    }
                }

                is IntentDetector.DetectionResult.NoToolNeeded -> {
                    // Отправляем запрос к Yandex GPT API
                    yandexGptService.sendMessage(text, _messages.value.dropLast(1))
                        .onSuccess { parseResult ->
                            when (parseResult) {
                                is ParseResult.Success -> {
                                    // Successfully parsed JSON
                                    val aiMessage = ChatMessage(
                                        id = generateId(),
                                        text = parseResult.data.response.content,
                                        title = parseResult.data.response.title,
                                        isUser = false,
                                        metadata = parseResult.data.response.metadata,
                                        parseWarning = null,
                                        timestamp = System.currentTimeMillis() // Day 9
                                    )
                                    _messages.value = _messages.value + aiMessage
                                    saveMessage(aiMessage) // Day 9: сохраняем в БД
                                    _chatState.value = ChatState.IDLE
                                }

                                is ParseResult.Partial -> {
                                    // Parsed with warnings (e.g., plain text fallback)
                                    val aiMessage = ChatMessage(
                                        id = generateId(),
                                        text = parseResult.data.response.content,
                                        title = parseResult.data.response.title,
                                        isUser = false,
                                        metadata = parseResult.data.response.metadata,
                                        parseWarning = parseResult.warning,
                                        timestamp = System.currentTimeMillis() // Day 9
                                    )
                                    _messages.value = _messages.value + aiMessage
                                    saveMessage(aiMessage) // Day 9: сохраняем в БД
                                    _chatState.value = ChatState.IDLE
                                }

                                is ParseResult.Error -> {
                                    // Failed to parse - show error message
                                    val errorMessage = ChatMessage(
                                        id = generateId(),
                                        text = "❌ Parse Error: ${parseResult.message}\n\nRaw response: ${parseResult.rawResponse}",
                                        isUser = false,
                                        metadata = null,
                                        parseWarning = "Parse failed",
                                        timestamp = System.currentTimeMillis() // Day 9
                                    )
                                    _messages.value = _messages.value + errorMessage
                                    saveMessage(errorMessage) // Day 9: сохраняем в БД
                                    _chatState.value = ChatState.IDLE
                                }
                            }
                        }
                        .onFailure { error ->
                            _errorMessage.value = error.message ?: "Неизвестная ошибка"
                            _chatState.value = ChatState.ERROR
                        }
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        if (_chatState.value == ChatState.ERROR) {
            _chatState.value = ChatState.IDLE
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _chatState.value = ChatState.IDLE
        _errorMessage.value = null

        // Day 9: Очищаем БД
        viewModelScope.launch {
            try {
                chatRepository?.deleteAllMessages()
                println("✅ Cleared database")
            } catch (e: Exception) {
                println("⚠️ Failed to clear database: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        yandexGptService.close()
        agentService.close() // Day 11
    }
}