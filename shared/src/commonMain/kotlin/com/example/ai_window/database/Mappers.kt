package com.example.ai_window.database

import com.example.ai_window.model.ChatMessage as DomainChatMessage
import com.example.ai_window.model.ResponseMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.aiwindow.ChatMessage as DbChatMessage

/**
 * Day 9: Mappers для конвертации между database моделями и domain моделями.
 */

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    isLenient = true
}

/**
 * Конвертирует domain ChatMessage в database ChatMessage.
 */
fun DomainChatMessage.toDbModel(): DbChatMessage {
    return DbChatMessage(
        id = id,
        text = text,
        isUser = if (isUser) 1L else 0L, // Boolean → Long
        timestamp = timestamp,
        title = title,
        metadata_json = metadata?.let { json.encodeToString(it) },
        parseWarning = parseWarning,
        isSummary = if (isSummary) 1L else 0L, // Boolean → Long
        summarizedCount = summarizedCount?.toLong(),
        originalIds_json = originalIds?.let { json.encodeToString(it) },
        estimatedTokens = estimatedTokens?.toLong()
    )
}

/**
 * Конвертирует database ChatMessage в domain ChatMessage.
 */
fun DbChatMessage.toDomainModel(): DomainChatMessage {
    return DomainChatMessage(
        id = id,
        text = text,
        isUser = isUser == 1L, // Long → Boolean
        timestamp = timestamp,
        title = title,
        metadata = metadata_json?.let {
            try {
                json.decodeFromString<ResponseMetadata>(it)
            } catch (e: Exception) {
                null
            }
        },
        parseWarning = parseWarning,
        isSummary = isSummary == 1L, // Long → Boolean
        summarizedCount = summarizedCount?.toInt(),
        originalIds = originalIds_json?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                null
            }
        },
        estimatedTokens = estimatedTokens?.toInt()
    )
}

/**
 * Сериализует объект в JSON string.
 */
fun <T> toJsonString(value: T, serializer: kotlinx.serialization.KSerializer<T>): String {
    return json.encodeToString(serializer, value)
}

/**
 * Десериализует JSON string в объект.
 */
fun <T> fromJsonString(jsonString: String, deserializer: kotlinx.serialization.KSerializer<T>): T? {
    return try {
        json.decodeFromString(deserializer, jsonString)
    } catch (e: Exception) {
        null
    }
}
