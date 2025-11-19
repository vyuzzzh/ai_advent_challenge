package com.example.ai_window

import com.example.ai_window.model.YandexGptRequest
import com.example.ai_window.model.YandexGptResponse
import com.example.ai_window.model.McpServerInfo
import com.example.ai_window.model.McpTool
import com.example.ai_window.model.McpResource
import com.example.ai_window.model.McpPrompt
import com.example.ai_window.model.ToolExecutionRequest
import com.example.ai_window.mcp.SimpleMcpServer
import com.example.ai_window.tools.GitToolExecutor
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Настройка CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // В продакшене указать конкретные хосты
    }

    // Настройка сериализации
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    // HTTP клиент для запросов к Yandex API
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    // Day 10: Инициализация упрощенного MCP сервера
    SimpleMcpServer.initialize()

    // Day 11: Инициализация Git Tool Executor
    val gitToolExecutor = GitToolExecutor()

    routing {
        get("/") {
            call.respondText("AI Window Proxy Server")
        }

        // Прокси-эндпоинт для Yandex GPT API
        post("/api/yandex-gpt") {
            try {
                val apiKey = call.request.header("X-API-Key")
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing API key")

                val folderId = call.request.header("X-Folder-Id")
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing Folder ID")

                val request = call.receive<YandexGptRequest>()

                val response: YandexGptResponse = httpClient.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                    header("Authorization", "Api-Key $apiKey")
                    header("x-folder-id", folderId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

                call.respond(response)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }

        // Прокси-эндпоинт для HuggingFace Inference Providers API (Chat Completion)
        post("/api/huggingface") {
            try {
                val hfToken = call.request.header("X-HF-Token")
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing HuggingFace token")

                val request = call.receive<com.example.ai_window.model.HuggingFaceRequest>()

                println("📤 HuggingFace Request:")
                println("  Model: ${request.model}")
                println("  Messages: ${request.messages.size}")
                println("  Max tokens: ${request.maxTokens}")

                val startTime = System.currentTimeMillis()

                // Запрос к HuggingFace Inference Providers API (Chat Completion)
                val hfResponse = try {
                    httpClient.post("https://router.huggingface.co/v1/chat/completions") {
                        header("Authorization", "Bearer $hfToken")
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        com.example.ai_window.model.HuggingFaceResponse(
                            error = "Failed to connect to HuggingFace API: ${e.message}"
                        )
                    )
                }

                val endTime = System.currentTimeMillis()
                val executionTime = endTime - startTime

                // Парсим ответ
                when (hfResponse.status) {
                    HttpStatusCode.OK -> {
                        // Chat Completion API возвращает OpenAI-совместимый формат
                        val responseText = hfResponse.body<String>()
                        println("📥 HF Response: $responseText")

                        try {
                            val json = Json { ignoreUnknownKeys = true }
                            val apiResponse = json.decodeFromString<com.example.ai_window.model.HuggingFaceResponse>(responseText)

                            // Добавляем время выполнения
                            val enrichedResponse = apiResponse.copy(executionTime = executionTime)

                            call.respond(enrichedResponse)
                        } catch (e: Exception) {
                            println("Failed to parse response: ${e.message}")
                            e.printStackTrace()
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                com.example.ai_window.model.HuggingFaceResponse(
                                    error = "Failed to parse HuggingFace response: ${e.message}"
                                )
                            )
                        }
                    }
                    HttpStatusCode.ServiceUnavailable -> {
                        // Модель загружается или недоступна
                        val errorBody = hfResponse.body<String>()
                        println("Service unavailable: $errorBody")

                        call.respond(
                            com.example.ai_window.model.HuggingFaceResponse(
                                error = "Модель временно недоступна. Попробуйте другую модель или повторите позже.",
                                executionTime = executionTime
                            )
                        )
                    }
                    else -> {
                        val errorBody = hfResponse.body<String>()
                        println("❌ HF API error (${hfResponse.status}): $errorBody")

                        call.respond(
                            HttpStatusCode.InternalServerError,
                            com.example.ai_window.model.HuggingFaceResponse(
                                error = "HuggingFace API error (${hfResponse.status}): $errorBody"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    com.example.ai_window.model.HuggingFaceResponse(
                        error = "Server error: ${e.message}"
                    )
                )
            }
        }

        // Day 10: MCP REST API endpoints для UI
        get("/api/mcp/info") {
            try {
                val mcpInfo = McpServerInfo(
                    serverName = "ai-window-mcp-server",
                    version = "1.0.0",
                    tools = SimpleMcpServer.tools.map { tool ->
                        McpTool(
                            name = tool.name,
                            description = tool.description,
                            parameters = tool.parameters,
                            category = tool.category,
                            inputSchema = buildJsonObject {
                                put("type", "object")
                                put("parameters", tool.parameters.joinToString(", "))
                                put("category", tool.category)
                            }
                        )
                    },
                    resources = SimpleMcpServer.resources.map { resource ->
                        McpResource(
                            uri = resource.uri,
                            name = resource.name,
                            description = resource.description,
                            mimeType = "text/plain"
                        )
                    },
                    prompts = emptyList()
                )

                call.respond(mcpInfo)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        get("/api/mcp/tools") {
            try {
                call.respond(
                    mapOf(
                        "count" to SimpleMcpServer.tools.size.toString(),
                        "tools" to SimpleMcpServer.tools.map { tool ->
                            mapOf(
                                "name" to tool.name,
                                "description" to tool.description,
                                "parameters" to tool.parameters.joinToString(", "),
                                "category" to tool.category
                            )
                        }.toString()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // Day 11: Endpoint для выполнения MCP tools
        post("/api/tools/execute") {
            try {
                val request = call.receive<ToolExecutionRequest>()

                println("[API] Executing tool: ${request.tool} with params: ${request.params}")

                val result = gitToolExecutor.execute(request.tool, request.params)

                call.respond(result)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}