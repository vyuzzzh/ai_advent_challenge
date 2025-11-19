// Day 11: Agent Service - сервис для выполнения MCP tools через REST API
package com.example.ai_window.service

import com.example.ai_window.SERVER_PORT
import com.example.ai_window.model.ToolCall
import com.example.ai_window.model.ToolResult
import com.example.ai_window.model.ToolExecutionRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Сервис агента для выполнения MCP tools
 * Взаимодействует с сервером через REST API
 */
class AgentService {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val baseUrl = "http://localhost:$SERVER_PORT"

    /**
     * Выполнить MCP tool
     */
    suspend fun executeTool(toolCall: ToolCall): ToolResult {
        return try {
            println("[AgentService] Executing tool: ${toolCall.tool}")

            val request = ToolExecutionRequest(
                tool = toolCall.tool,
                params = toolCall.params
            )

            val response = httpClient.post("$baseUrl/api/tools/execute") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status == HttpStatusCode.OK) {
                val result: ToolResult = response.body()
                println("[AgentService] Tool result: success=${result.success}, output length=${result.output.length}")
                result
            } else {
                val errorBody = response.body<String>()
                println("[AgentService] Error: ${response.status} - $errorBody")
                ToolResult(
                    success = false,
                    output = "",
                    error = "HTTP ${response.status}: $errorBody"
                )
            }
        } catch (e: Exception) {
            println("[AgentService] Exception: ${e.message}")
            e.printStackTrace()
            ToolResult(
                success = false,
                output = "",
                error = "Network error: ${e.message}"
            )
        }
    }

    /**
     * Форматировать результат tool для отображения пользователю
     */
    fun formatToolResult(toolCall: ToolCall, result: ToolResult): String {
        return if (result.success) {
            val header = when (toolCall.tool) {
                "git-log" -> "📜 История коммитов"
                "git-status" -> "📊 Статус репозитория"
                "git-diff" -> "📝 Различия в файлах"
                "git-branches" -> "🌿 Список веток"
                else -> "🔧 Результат ${toolCall.tool}"
            }

            val output = result.output.ifEmpty { "Нет данных" }
            val time = result.executionTime?.let { " (${it}ms)" } ?: ""

            """$header$time

```
$output
```"""
        } else {
            """❌ Ошибка выполнения ${toolCall.tool}

${result.error ?: "Неизвестная ошибка"}"""
        }
    }

    /**
     * Закрыть HTTP клиент
     */
    fun close() {
        httpClient.close()
    }
}
