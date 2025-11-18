// Day 10: MCP REST client service
package com.example.ai_window.service

import com.example.ai_window.SERVER_PORT
import com.example.ai_window.model.McpServerInfo
import com.example.ai_window.model.McpTool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * REST клиент для взаимодействия с MCP сервером
 * Получает информацию о доступных MCP tools и resources через HTTP API
 */
class McpService(
    private val serverUrl: String = "http://localhost:$SERVER_PORT"
) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    /**
     * Получить полную информацию о MCP сервере
     * @return McpServerInfo с инструментами, ресурсами и промптами
     */
    suspend fun getMcpServerInfo(): Result<McpServerInfo> {
        return try {
            val response = client.get("$serverUrl/api/mcp/info")

            if (response.status == HttpStatusCode.OK) {
                val mcpInfo = response.body<McpServerInfo>()
                Result.success(mcpInfo)
            } else {
                Result.failure(Exception("Failed to fetch MCP info: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить список MCP tools
     * @return Список инструментов с их описаниями
     */
    suspend fun getMcpTools(): Result<McpToolsResponse> {
        return try {
            val response = client.get("$serverUrl/api/mcp/tools")

            if (response.status == HttpStatusCode.OK) {
                val toolsResponse = response.body<McpToolsResponse>()
                Result.success(toolsResponse)
            } else {
                Result.failure(Exception("Failed to fetch MCP tools: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Response модель для /api/mcp/tools endpoint
 */
@Serializable
data class McpToolsResponse(
    val count: Int,
    val tools: List<McpToolItem>
)

/**
 * Упрощенная модель MCP tool для списка
 */
@Serializable
data class McpToolItem(
    val name: String,
    val description: String,
    val parameters: List<String>,
    val category: String
)
