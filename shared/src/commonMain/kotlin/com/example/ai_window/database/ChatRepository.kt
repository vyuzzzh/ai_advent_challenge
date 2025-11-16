package com.example.ai_window.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ai_window.model.ChatMessage as DomainChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Day 9: Repository для работы с сообщениями чата.
 * Предоставляет suspend функции для сохранения/загрузки сообщений.
 */
class ChatRepository(database: ChatDatabase) {
    private val queries = database.chatMessageQueries

    /**
     * Сохраняет сообщение в БД.
     */
    suspend fun saveChatMessage(message: DomainChatMessage) = withContext(Dispatchers.Default) {
        val dbMessage = message.toDbModel()
        queries.insert(
            id = dbMessage.id,
            text = dbMessage.text,
            isUser = dbMessage.isUser,
            timestamp = dbMessage.timestamp,
            title = dbMessage.title,
            metadata_json = dbMessage.metadata_json,
            parseWarning = dbMessage.parseWarning,
            isSummary = dbMessage.isSummary,
            summarizedCount = dbMessage.summarizedCount,
            originalIds_json = dbMessage.originalIds_json,
            estimatedTokens = dbMessage.estimatedTokens
        )
    }

    /**
     * Получает все сообщения из БД.
     */
    suspend fun getAllMessages(): List<DomainChatMessage> = withContext(Dispatchers.Default) {
        queries.selectAll()
            .executeAsList()
            .map { it.toDomainModel() }
    }

    /**
     * Получает все сообщения как Flow для реактивного обновления.
     */
    fun getAllMessagesFlow(): Flow<List<DomainChatMessage>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomainModel() } }
    }

    /**
     * Получает последние N сообщений.
     */
    suspend fun getRecentMessages(limit: Int): List<DomainChatMessage> = withContext(Dispatchers.Default) {
        queries.selectRecent(limit.toLong())
            .executeAsList()
            .map { it.toDomainModel() }
            .reversed() // Развернуть, т.к. запрос сортирует по DESC
    }

    /**
     * Получает количество сообщений.
     */
    suspend fun getMessageCount(): Long = withContext(Dispatchers.Default) {
        queries.count().executeAsOne()
    }

    /**
     * Удаляет сообщение по ID.
     */
    suspend fun deleteMessage(id: String) = withContext(Dispatchers.Default) {
        queries.deleteById(id)
    }

    /**
     * Удаляет все сообщения.
     */
    suspend fun deleteAllMessages() = withContext(Dispatchers.Default) {
        queries.deleteAll()
    }

    /**
     * Удаляет старые сообщения, оставляя только последние N.
     */
    suspend fun keepOnlyRecentMessages(keepCount: Int) = withContext(Dispatchers.Default) {
        queries.deleteOldMessages(keepCount.toLong())
    }
}
