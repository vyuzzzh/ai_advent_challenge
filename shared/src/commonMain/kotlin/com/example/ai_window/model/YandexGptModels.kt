package com.example.ai_window.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YandexGptRequest(
    @SerialName("modelUri")
    val modelUri: String,
    @SerialName("completionOptions")
    val completionOptions: CompletionOptions,
    @SerialName("messages")
    val messages: List<Message>
)

@Serializable
data class CompletionOptions(
    @SerialName("stream")
    val stream: Boolean = false,
    @SerialName("temperature")
    val temperature: Double = 0.6,
    @SerialName("maxTokens")
    val maxTokens: Int = 2000
)

@Serializable
data class Message(
    @SerialName("role")
    val role: String,
    @SerialName("text")
    val text: String
)

@Serializable
data class YandexGptResponse(
    @SerialName("result")
    val result: Result
)

@Serializable
data class Result(
    @SerialName("alternatives")
    val alternatives: List<Alternative>,
    @SerialName("usage")
    val usage: Usage,
    @SerialName("modelVersion")
    val modelVersion: String
)

@Serializable
data class Alternative(
    @SerialName("message")
    val message: Message,
    @SerialName("status")
    val status: String
)

@Serializable
data class Usage(
    @SerialName("inputTextTokens")
    val inputTextTokens: Int,
    @SerialName("completionTokens")
    val completionTokens: Int,
    @SerialName("totalTokens")
    val totalTokens: Int
)