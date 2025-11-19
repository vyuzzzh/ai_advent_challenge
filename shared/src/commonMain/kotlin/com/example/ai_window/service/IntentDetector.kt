// Day 11: Intent Detector - распознавание намерений пользователя для вызова MCP tools
package com.example.ai_window.service

import com.example.ai_window.model.GitTools
import com.example.ai_window.model.ToolCall

/**
 * Детектор намерений пользователя
 * Анализирует текст и определяет, нужно ли вызвать MCP tool
 */
class IntentDetector {

    /**
     * Результат детекции намерения
     */
    sealed class DetectionResult {
        data class ToolDetected(val toolCall: ToolCall) : DetectionResult()
        object NoToolNeeded : DetectionResult()
    }

    /**
     * Паттерны для распознавания Git команд
     */
    private val gitLogPatterns = listOf(
        Regex("покажи.*коммит", RegexOption.IGNORE_CASE),
        Regex("история.*коммит", RegexOption.IGNORE_CASE),
        Regex("последние.*коммит", RegexOption.IGNORE_CASE),
        Regex("что.*закоммич", RegexOption.IGNORE_CASE),
        Regex("git\\s*log", RegexOption.IGNORE_CASE),
        Regex("коммиты", RegexOption.IGNORE_CASE),
        Regex("история.*git", RegexOption.IGNORE_CASE),
        Regex("последние.*изменения.*git", RegexOption.IGNORE_CASE)
    )

    private val gitStatusPatterns = listOf(
        Regex("что.*изменил", RegexOption.IGNORE_CASE),
        Regex("статус.*репозитор", RegexOption.IGNORE_CASE),
        Regex("какие.*файл.*изменен", RegexOption.IGNORE_CASE),
        Regex("git\\s*status", RegexOption.IGNORE_CASE),
        Regex("текущ.*статус", RegexOption.IGNORE_CASE),
        Regex("измененные.*файл", RegexOption.IGNORE_CASE),
        Regex("что\\s+изменено", RegexOption.IGNORE_CASE)
    )

    private val gitDiffPatterns = listOf(
        Regex("покажи.*diff", RegexOption.IGNORE_CASE),
        Regex("различия.*код", RegexOption.IGNORE_CASE),
        Regex("что.*изменилось.*файл", RegexOption.IGNORE_CASE),
        Regex("git\\s*diff", RegexOption.IGNORE_CASE),
        Regex("diff\\s+", RegexOption.IGNORE_CASE),
        Regex("изменения.*в.*файл", RegexOption.IGNORE_CASE)
    )

    private val gitBranchPatterns = listOf(
        Regex("какие.*ветк", RegexOption.IGNORE_CASE),
        Regex("список.*веток", RegexOption.IGNORE_CASE),
        Regex("branch", RegexOption.IGNORE_CASE),
        Regex("текущая.*ветка", RegexOption.IGNORE_CASE),
        Regex("git\\s*branch", RegexOption.IGNORE_CASE),
        Regex("ветки", RegexOption.IGNORE_CASE),
        Regex("на\\s+какой.*ветке", RegexOption.IGNORE_CASE)
    )

    /**
     * Определить намерение пользователя по тексту
     */
    fun detect(userMessage: String): DetectionResult {
        val normalizedMessage = userMessage.trim().lowercase()

        // Проверяем git-log
        if (gitLogPatterns.any { it.containsMatchIn(normalizedMessage) }) {
            val count = extractNumber(normalizedMessage) ?: 5
            return DetectionResult.ToolDetected(
                ToolCall(
                    tool = "git-log",
                    params = mapOf("count" to count.toString())
                )
            )
        }

        // Проверяем git-status
        if (gitStatusPatterns.any { it.containsMatchIn(normalizedMessage) }) {
            return DetectionResult.ToolDetected(
                ToolCall(
                    tool = "git-status",
                    params = emptyMap()
                )
            )
        }

        // Проверяем git-diff
        if (gitDiffPatterns.any { it.containsMatchIn(normalizedMessage) }) {
            val file = extractFilePath(normalizedMessage)
            val params = if (file != null) mapOf("file" to file) else emptyMap()
            return DetectionResult.ToolDetected(
                ToolCall(
                    tool = "git-diff",
                    params = params
                )
            )
        }

        // Проверяем git-branches
        if (gitBranchPatterns.any { it.containsMatchIn(normalizedMessage) }) {
            return DetectionResult.ToolDetected(
                ToolCall(
                    tool = "git-branches",
                    params = emptyMap()
                )
            )
        }

        // Никакой tool не нужен
        return DetectionResult.NoToolNeeded
    }

    /**
     * Извлечь число из текста (для параметра count)
     */
    private fun extractNumber(text: String): Int? {
        // Ищем числа
        val numberRegex = Regex("(\\d+)")
        val match = numberRegex.find(text)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }

        // Ищем слова-числительные
        val wordNumbers = mapOf(
            "один" to 1, "одну" to 1,
            "два" to 2, "две" to 2,
            "три" to 3,
            "четыре" to 4,
            "пять" to 5,
            "шесть" to 6,
            "семь" to 7,
            "восемь" to 8,
            "девять" to 9,
            "десять" to 10
        )

        for ((word, number) in wordNumbers) {
            if (text.contains(word)) {
                return number
            }
        }

        return null
    }

    /**
     * Извлечь путь к файлу из текста
     */
    private fun extractFilePath(text: String): String? {
        // Паттерн для путей файлов
        val pathRegex = Regex("([\\w./\\-_]+\\.[\\w]+)")
        val match = pathRegex.find(text)
        return match?.groupValues?.get(1)
    }

    /**
     * Получить описание tool для отображения в UI
     */
    fun getToolDescription(toolName: String): String {
        return GitTools.tools.find { it.name == toolName }?.description
            ?: "Неизвестный инструмент"
    }

    /**
     * Получить все доступные tools
     */
    fun getAvailableTools() = GitTools.tools
}
