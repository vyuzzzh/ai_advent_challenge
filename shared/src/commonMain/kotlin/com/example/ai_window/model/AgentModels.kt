// Day 11: Agent Models для нативной MCP интеграции через чат
package com.example.ai_window.model

import kotlinx.serialization.Serializable

/**
 * Состояния агента для отображения в UI
 */
enum class AgentState {
    IDLE,               // Ожидание ввода
    THINKING,           // AI анализирует запрос
    EXECUTING_TOOL,     // Выполняется MCP tool
    FORMATTING_RESPONSE // Форматирование ответа
}

/**
 * Запрос на выполнение MCP tool
 */
@Serializable
data class ToolCall(
    val tool: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * Результат выполнения MCP tool
 */
@Serializable
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val executionTime: Long? = null
)

/**
 * Запрос на выполнение tool (для REST API)
 */
@Serializable
data class ToolExecutionRequest(
    val tool: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * Ответ агента после обработки сообщения пользователя
 */
@Serializable
data class AgentResponse(
    val thought: String,           // Рассуждения AI (опционально показывать в UI)
    val toolCall: ToolCall?,       // null если tool не нужен
    val response: String           // Финальный ответ пользователю
)

/**
 * Сообщение в агентском чате с поддержкой tool calls
 */
@Serializable
data class AgentMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val toolCall: ToolCall? = null,      // Если сообщение содержит вызов tool
    val toolResult: ToolResult? = null,  // Результат выполнения tool
    val agentState: AgentState? = null   // Состояние агента при генерации
)

/**
 * Описание доступного Git tool для агента
 */
@Serializable
data class GitToolDescription(
    val name: String,
    val description: String,
    val parameters: List<String>,
    val examples: List<String>  // Примеры фраз пользователя
)

/**
 * Список Git tools для агента
 */
object GitTools {
    val tools = listOf(
        GitToolDescription(
            name = "git-log",
            description = "Показать историю коммитов",
            parameters = listOf("count"),
            examples = listOf(
                "покажи последние коммиты",
                "история коммитов",
                "что было закоммичено",
                "последние изменения в git"
            )
        ),
        GitToolDescription(
            name = "git-status",
            description = "Показать статус репозитория (измененные файлы)",
            parameters = emptyList(),
            examples = listOf(
                "что изменилось",
                "статус репозитория",
                "какие файлы изменены",
                "git status"
            )
        ),
        GitToolDescription(
            name = "git-diff",
            description = "Показать различия в файлах",
            parameters = listOf("file"),
            examples = listOf(
                "покажи diff",
                "что изменилось в файле",
                "различия в коде",
                "изменения"
            )
        ),
        GitToolDescription(
            name = "git-branches",
            description = "Показать список веток",
            parameters = emptyList(),
            examples = listOf(
                "какие ветки есть",
                "список веток",
                "branches",
                "текущая ветка"
            )
        )
    )
}
