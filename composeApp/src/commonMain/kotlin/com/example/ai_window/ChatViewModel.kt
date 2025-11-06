package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.model.ChatMessage
import com.example.ai_window.model.ChatState
import com.example.ai_window.model.ParseResult
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

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chatState = MutableStateFlow(ChatState.IDLE)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private fun generateId(): String {
        return "${Random.nextLong()}-${Random.nextLong()}"
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _chatState.value == ChatState.LOADING) return

        // Добавляем сообщение пользователя
        val userMessage = ChatMessage(
            id = generateId(),
            text = text,
            isUser = true
        )
        _messages.value = _messages.value + userMessage

        // Отправляем запрос к API
        viewModelScope.launch {
            _chatState.value = ChatState.LOADING
            _errorMessage.value = null

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
                                parseWarning = null
                            )
                            _messages.value = _messages.value + aiMessage
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
                                parseWarning = parseResult.warning
                            )
                            _messages.value = _messages.value + aiMessage
                            _chatState.value = ChatState.IDLE
                        }

                        is ParseResult.Error -> {
                            // Failed to parse - show error message
                            val errorMessage = ChatMessage(
                                id = generateId(),
                                text = "❌ Parse Error: ${parseResult.message}\n\nRaw response: ${parseResult.rawResponse}",
                                isUser = false,
                                metadata = null,
                                parseWarning = "Parse failed"
                            )
                            _messages.value = _messages.value + errorMessage
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
    }

    override fun onCleared() {
        super.onCleared()
        yandexGptService.close()
    }
}