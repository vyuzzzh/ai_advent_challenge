package com.example.ai_window.service

import com.example.ai_window.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Сервис для проведения экспериментов с разными температурами
 */
class TemperatureExperimentService(
    private val apiKey: String,
    private val folderId: String
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

    private val baseUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
    private val modelUri = "gpt://$folderId/yandexgpt-lite/latest"

    /**
     * Запустить множественные генерации с одной температурой
     * @param question вопрос для отправки
     * @param temperature значение температуры
     * @param runs количество генераций (для расчета метрик)
     * @param onProgress callback для отслеживания прогресса
     */
    suspend fun runExperiment(
        question: String,
        temperature: Double,
        runs: Int = 3,
        onProgress: ((Int, Int) -> Unit)? = null
    ): kotlin.Result<TemperatureResult> {
        return try {
            val responses = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            // Выполняем несколько запросов для сбора данных
            repeat(runs) { runIndex ->
                onProgress?.invoke(runIndex + 1, runs)

                val request = YandexGptRequest(
                    modelUri = modelUri,
                    completionOptions = CompletionOptions(
                        stream = false,
                        temperature = temperature,
                        maxTokens = 2000
                    ),
                    messages = listOf(
                        Message(role = "user", text = question)
                    )
                )

                val response: YandexGptResponse = client.post(baseUrl) {
                    header("Authorization", "Api-Key $apiKey")
                    header("x-folder-id", folderId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

                val text = response.result.alternatives.firstOrNull()?.message?.text
                if (text != null) {
                    responses.add(text)
                } else {
                    warnings.add("Run ${runIndex + 1}: Empty response")
                }
            }

            if (responses.isEmpty()) {
                return kotlin.Result.failure(Exception("Все запросы вернули пустые ответы"))
            }

            // Вычисляем метрики
            val metrics = calculateMetrics(responses)

            // Создаем результат
            val result = TemperatureResult(
                temperature = temperature,
                question = question,
                responses = responses,
                metrics = metrics,
                recommendation = null,  // Будет добавлено позже
                parseWarnings = warnings
            )

            kotlin.Result.success(result)
        } catch (e: Exception) {
            println("❌ Temperature experiment error: ${e.message}")
            kotlin.Result.failure(e)
        }
    }

    /**
     * Вычислить метрики для набора ответов
     */
    private fun calculateMetrics(responses: List<String>): TemperatureMetrics {
        // Базовые метрики
        val wordCounts = responses.map { it.split(Regex("\\s+")).size }
        val charCounts = responses.map { it.length }
        val uniqueWordsCounts = responses.map { response ->
            response.split(Regex("\\s+"))
                .map { it.lowercase().trim() }
                .distinct()
                .size
        }

        val avgWordCount = wordCounts.average()
        val avgCharCount = charCounts.average()
        val avgUniqueWords = uniqueWordsCounts.average()

        // Self-BLEU - разнообразие между ответами
        val selfBleu = calculateSelfBLEU(responses)

        // Semantic Consistency - семантическая схожесть
        val semanticConsistency = calculateSemanticConsistency(responses)

        // Response Variability - вариативность
        val variability = calculateVariability(responses, wordCounts, uniqueWordsCounts)

        return TemperatureMetrics(
            avgWordCount = avgWordCount,
            avgCharCount = avgCharCount,
            avgUniqueWords = avgUniqueWords,
            selfBleu = selfBleu,
            semanticConsistency = semanticConsistency,
            responseVariability = variability
        )
    }

    /**
     * Рассчитать Self-BLEU метрику
     * Упрощенная версия: сравниваем пересечение уникальных слов между ответами
     */
    private fun calculateSelfBLEU(responses: List<String>): Double {
        if (responses.size < 2) return 0.0

        val wordSets = responses.map { response ->
            response.split(Regex("\\s+"))
                .map { it.lowercase().trim() }
                .toSet()
        }

        var totalSimilarity = 0.0
        var comparisons = 0

        for (i in wordSets.indices) {
            for (j in i + 1 until wordSets.size) {
                val intersection = wordSets[i].intersect(wordSets[j]).size.toDouble()
                val union = wordSets[i].union(wordSets[j]).size.toDouble()
                totalSimilarity += if (union > 0) intersection / union else 0.0
                comparisons++
            }
        }

        return if (comparisons > 0) totalSimilarity / comparisons else 0.0
    }

    /**
     * Рассчитать семантическую согласованность
     * Упрощенная версия: анализируем стабильность ключевых слов
     */
    private fun calculateSemanticConsistency(responses: List<String>): Double {
        if (responses.size < 2) return 1.0

        // Извлекаем все слова из ответов
        val allWords = responses.flatMap { response ->
            response.split(Regex("\\s+"))
                .map { it.lowercase().trim() }
                .filter { it.length > 3 }  // Фильтруем короткие слова
        }

        // Частота встречаемости слов
        val wordFrequency = allWords.groupingBy { it }.eachCount()

        // Находим слова, которые встречаются в большинстве ответов
        val commonWords = wordFrequency.filter { (_, count) ->
            count >= responses.size / 2
        }

        // Чем больше общих слов, тем выше согласованность
        return if (wordFrequency.isNotEmpty()) {
            commonWords.size.toDouble() / wordFrequency.size.toDouble()
        } else {
            0.0
        }
    }

    /**
     * Рассчитать метрики вариативности
     */
    private fun calculateVariability(
        responses: List<String>,
        wordCounts: List<Int>,
        uniqueWordsCounts: List<Int>
    ): VariabilityMetrics {
        // Стандартное отклонение длины
        val avgLength = wordCounts.average()
        val lengthVariance = wordCounts.map { (it - avgLength).pow(2) }.average()
        val lengthStdDev = sqrt(lengthVariance)

        // Разброс уникальных слов
        val avgUnique = uniqueWordsCounts.average()
        val uniqueVariance = uniqueWordsCounts.map { (it - avgUnique).pow(2) }.average()

        // Структурное разнообразие (на основе длины предложений и абзацев)
        val structuralDiversity = calculateStructuralDiversity(responses)

        return VariabilityMetrics(
            lengthStdDev = lengthStdDev,
            uniqueWordsVariance = uniqueVariance,
            structuralDiversity = structuralDiversity
        )
    }

    /**
     * Рассчитать структурное разнообразие
     */
    private fun calculateStructuralDiversity(responses: List<String>): Double {
        val sentenceCounts = responses.map { it.split(Regex("[.!?]+")).size }
        val paragraphCounts = responses.map { it.split(Regex("\n\n+")).size }

        val avgSentences = sentenceCounts.average()
        val avgParagraphs = paragraphCounts.average()

        val sentenceVariance = sentenceCounts.map { (it - avgSentences).pow(2) }.average()
        val paragraphVariance = paragraphCounts.map { (it - avgParagraphs).pow(2) }.average()

        // Нормализуем на 0-1 диапазон
        val normalizedVariance = (sentenceVariance + paragraphVariance) / 10.0
        return normalizedVariance.coerceIn(0.0, 1.0)
    }

    /**
     * Генерация рекомендаций на основе метрик
     */
    fun generateRecommendation(result: TemperatureResult): TemperatureRecommendation {
        val temp = result.temperature
        val metrics = result.metrics

        return when {
            temp <= 0.1 -> TemperatureRecommendation(
                temperature = temp,
                bestFor = listOf(
                    "Фактические вопросы",
                    "Документация и техническое письмо",
                    "Задачи требующие точности",
                    "Математические расчеты"
                ),
                avoidFor = listOf(
                    "Креативное письмо",
                    "Генерация идей",
                    "Брейнсторминг"
                ),
                useCases = listOf(
                    "Ответы на вопросы из базы знаний",
                    "Перевод технических текстов",
                    "Генерация кода по четкому ТЗ"
                ),
                summary = "Детерминированный режим. Максимальная точность и воспроизводимость. " +
                        "Self-BLEU: ${"%.2f".format(metrics.selfBleu)} - " +
                        "${if (metrics.selfBleu > 0.8) "очень высокая повторяемость" else "высокая стабильность"}."
            )

            temp in 0.5..0.8 -> TemperatureRecommendation(
                temperature = temp,
                bestFor = listOf(
                    "Общие чат-боты",
                    "Объяснения и туториалы",
                    "Сбалансированный контент",
                    "Маркетинговые тексты"
                ),
                avoidFor = listOf(
                    "Задачи требующие 100% точности",
                    "Очень креативные задачи"
                ),
                useCases = listOf(
                    "Диалоговые ассистенты",
                    "Генерация описаний продуктов",
                    "Ответы на пользовательские запросы"
                ),
                summary = "Сбалансированный режим. Компромисс между точностью и креативностью. " +
                        "Self-BLEU: ${"%.2f".format(metrics.selfBleu)}, " +
                        "Вариативность: ${"%.2f".format(metrics.responseVariability.structuralDiversity)}."
            )

            else -> TemperatureRecommendation(
                temperature = temp,
                bestFor = listOf(
                    "Креативное письмо",
                    "Генерация идей",
                    "Истории и сценарии",
                    "Брейнсторминг"
                ),
                avoidFor = listOf(
                    "Фактические вопросы",
                    "Технические задачи",
                    "Задачи требующие точности"
                ),
                useCases = listOf(
                    "Написание рассказов",
                    "Генерация маркетинговых слоганов",
                    "Создание вариантов контента"
                ),
                summary = "Высококреативный режим. Максимальное разнообразие ответов. " +
                        "Self-BLEU: ${"%.2f".format(metrics.selfBleu)} - " +
                        "${if (metrics.selfBleu < 0.3) "очень высокое разнообразие" else "хорошее разнообразие"}."
            )
        }
    }

    fun close() {
        client.close()
    }
}
