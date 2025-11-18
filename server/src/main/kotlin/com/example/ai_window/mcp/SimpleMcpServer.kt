// Day 10: Simplified MCP Server для демонстрации концепции
package com.example.ai_window.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Упрощенный MCP сервер для демонстрации концепции
 * Регистрирует список доступных MCP tools без полной интеграции SDK
 *
 * TODO: Полная интеграция с Kotlin MCP SDK требует дополнительного изучения API
 */
object SimpleMcpServer {

    /**
     * Список зарегистрированных MCP tools
     * Day 11: Только работающие Git tools
     */
    val tools = listOf(
        McpToolInfo(
            name = "git-log",
            description = "Show commit history. Use when user asks about commits, history, or recent changes.",
            parameters = listOf("count"),
            category = "Git"
        ),
        McpToolInfo(
            name = "git-status",
            description = "Show repository status (modified, staged files). Use when user asks what changed or current status.",
            parameters = emptyList(),
            category = "Git"
        ),
        McpToolInfo(
            name = "git-diff",
            description = "Show file differences. Use when user asks about specific file changes or diff.",
            parameters = listOf("file"),
            category = "Git"
        ),
        McpToolInfo(
            name = "git-branches",
            description = "List all branches. Use when user asks about branches or current branch.",
            parameters = emptyList(),
            category = "Git"
        )
    )

    /**
     * Список зарегистрированных MCP resources
     */
    val resources = listOf(
        McpResourceInfo(
            uri = "server://status",
            name = "MCP Server Status",
            description = "Current MCP server status, version, and capabilities information"
        ),
        McpResourceInfo(
            uri = "chat://history",
            name = "Chat History",
            description = "Access to complete chat conversation history stored in database"
        )
    )

    /**
     * Инициализация MCP сервера
     */
    fun initialize() {
        println("[MCP Server] Simplified MCP Server initialized")
        println("[MCP Server] Registered ${tools.size} tools:")
        tools.forEach { tool ->
            println("  - ${tool.name}: ${tool.description}")
        }
        println("[MCP Server] Registered ${resources.size} resources")
    }
}

/**
 * Информация о MCP Tool
 */
data class McpToolInfo(
    val name: String,
    val description: String,
    val parameters: List<String>,
    val category: String
)

/**
 * Информация о MCP Resource
 */
data class McpResourceInfo(
    val uri: String,
    val name: String,
    val description: String
)
