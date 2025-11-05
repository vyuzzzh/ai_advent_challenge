package com.example.ai_window

import com.example.ai_window.model.YandexGptRequest
import com.example.ai_window.model.YandexGptResponse
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
    }
}