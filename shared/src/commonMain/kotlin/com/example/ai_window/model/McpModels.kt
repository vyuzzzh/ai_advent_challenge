// Day 10: MCP Integration - Data Models
package com.example.ai_window.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Модель MCP Tool для отображения в UI
 *
 * @property name уникальное имя tool
 * @property description описание функциональности
 * @property parameters список параметров tool
 * @property category категория tool (Analysis, Database, Optimization и т.д.)
 * @property inputSchema JSON Schema для параметров (опционально, для детальной информации)
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val parameters: List<String> = emptyList(),
    val category: String = "",
    val inputSchema: JsonObject? = null
)

/**
 * Модель MCP Resource для отображения в UI
 *
 * @property uri URI ресурса (например, "chat://history", "server://status")
 * @property name человеко-читаемое имя
 * @property description описание содержимого ресурса
 * @property mimeType MIME-тип контента (например, "text/plain", "application/json")
 */
@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String? = null
)

/**
 * Модель MCP Prompt для отображения в UI
 *
 * @property name уникальное имя промпта
 * @property description описание назначения промпта
 * @property argumentsSchema JSON Schema для аргументов промпта (опционально)
 */
@Serializable
data class McpPrompt(
    val name: String,
    val description: String,
    val argumentsSchema: JsonObject? = null
)

/**
 * Полная информация о MCP сервере
 * Возвращается REST API endpoint для отображения в UI
 *
 * @property serverName имя MCP сервера
 * @property version версия сервера
 * @property tools список доступных tools
 * @property resources список доступных resources
 * @property prompts список доступных prompts
 */
@Serializable
data class McpServerInfo(
    val serverName: String,
    val version: String,
    val tools: List<McpTool>,
    val resources: List<McpResource>,
    val prompts: List<McpPrompt>
)

/**
 * Упрощенная модель для быстрого отображения списка tools
 * Используется когда не нужна полная информация о server
 */
@Serializable
data class McpToolsListResponse(
    val count: Int,
    val tools: List<McpToolSummary>
)

/**
 * Краткая информация о tool (без детального inputSchema)
 */
@Serializable
data class McpToolSummary(
    val name: String,
    val description: String,
    val requiredParams: List<String> = emptyList()
)
