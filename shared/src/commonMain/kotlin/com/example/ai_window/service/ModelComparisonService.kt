package com.example.ai_window.service

import com.example.ai_window.model.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Сервис для сравнения моделей HuggingFace
 * Переиспользует логику метрик из TemperatureExperimentService
 */
class ModelComparisonService(
    private val hfService: HuggingFaceService
) {
    /**
     * Запустить сравнение для одной модели
     */
    suspend fun compareModel(
        model: HFModel,
        question: String,
        runs: Int = 3,
        maxTokens: Int = 250,
        temperature: Double = 0.7,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): kotlin.Result<ModelComparisonResult> {
        return try {
            val responses = mutableListOf<String>()
            val executionTimes = mutableListOf<Long>()
            val errors = mutableListOf<String>()

            // Выполняем множественные запросы
            val result = hfService.generateMultiple(
                modelId = model.modelId,
                prompt = question,
                count = runs,
                maxTokens = maxTokens,
                temperature = temperature,
                onProgress = onProgress
            )

            // Сохраняем детальные ответы для доступа к tokenUsage
            val detailedResponses: List<HFDetailedResponse>

            when {
                result.isSuccess -> {
                    detailedResponses = result.getOrThrow()
                    detailedResponses.forEach { response ->
                        responses.add(response.generatedText)
                        executionTimes.add(response.executionTime)
                    }
                }
                result.isFailure -> {
                    val exception = result.exceptionOrNull()
                    errors.add(exception?.message ?: "Unknown error")
                    return kotlin.Result.failure(exception ?: Exception("Unknown error"))
                }
                else -> {
                    return kotlin.Result.failure(Exception("Unexpected result state"))
                }
            }

            if (responses.isEmpty()) {
                return kotlin.Result.failure(Exception("Все запросы вернули пустые ответы"))
            }

            // Вычисляем метрики, передавая детальные ответы с реальными tokenUsage
            val metrics = calculateMetrics(responses, executionTimes, detailedResponses)

            // Создаем результат
            val comparisonResult = ModelComparisonResult(
                model = model,
                question = question,
                responses = responses,
                metrics = metrics,
                executionTimes = executionTimes,
                errors = errors
            )

            kotlin.Result.success(comparisonResult)
        } catch (e: Exception) {
            println("❌ Model comparison error: ${e.message}")
            kotlin.Result.failure(e)
        }
    }

    /**
     * Вычислить метрики для сравнения модели
     */
    private fun calculateMetrics(
        responses: List<String>,
        executionTimes: List<Long>,
        detailedResponses: List<HFDetailedResponse>
    ): ModelComparisonMetrics {
        // Метрики производительности
        val avgResponseTime = executionTimes.average()
        val minResponseTime = executionTimes.minOrNull() ?: 0L
        val maxResponseTime = executionTimes.maxOrNull() ?: 0L

        // Реальные метрики токенов из API
        val avgInputTokens = detailedResponses.map { it.tokenUsage.promptTokens }.average()
        val avgOutputTokens = detailedResponses.map { it.tokenUsage.completionTokens }.average()
        val avgTotalTokens = detailedResponses.map { it.tokenUsage.totalTokens }.average()

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

        return ModelComparisonMetrics(
            avgResponseTime = avgResponseTime,
            minResponseTime = minResponseTime,
            maxResponseTime = maxResponseTime,
            avgInputTokens = avgInputTokens,
            avgOutputTokens = avgOutputTokens,
            avgTotalTokens = avgTotalTokens,
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
     * Определить победителей в каждой категории
     */
    fun determineWinners(results: List<ModelComparisonResult>): ModelWinner {
        val fastest = results.minByOrNull { it.metrics.avgResponseTime }?.model?.displayName ?: "N/A"
        val mostConsistent = results.maxByOrNull { it.metrics.semanticConsistency }?.model?.displayName ?: "N/A"
        val mostCreative = results.minByOrNull { it.metrics.selfBleu }?.model?.displayName ?: "N/A"
        val longestResponses = results.maxByOrNull { it.metrics.avgWordCount }?.model?.displayName ?: "N/A"

        // Эффективность = качество / время
        val mostEfficient = results.maxByOrNull { result ->
            val quality = result.metrics.avgWordCount * result.metrics.avgUniqueWords
            val time = result.metrics.avgResponseTime
            if (time > 0) quality / time else 0.0
        }?.model?.displayName ?: "N/A"

        return ModelWinner(
            fastest = fastest,
            mostConsistent = mostConsistent,
            mostCreative = mostCreative,
            longestResponses = longestResponses,
            mostEfficient = mostEfficient
        )
    }

    /**
     * Генерация рекомендаций для модели
     */
    fun generateRecommendation(result: ModelComparisonResult): ModelRecommendation {
        val metrics = result.metrics
        val strengths = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()
        val bestUseCases = mutableListOf<String>()

        // Анализ скорости
        if (metrics.avgResponseTime < 2000) {
            strengths.add("Быстрые ответы (${metrics.avgResponseTime.toInt()}ms)")
            bestUseCases.add("Интерактивные приложения")
        } else {
            weaknesses.add("Медленные ответы (${metrics.avgResponseTime.toInt()}ms)")
        }

        // Анализ консистентности
        if (metrics.semanticConsistency > 0.7) {
            val roundedConsistency = ((metrics.semanticConsistency * 100).toInt() / 100.0)
            strengths.add("Высокая консистентность ($roundedConsistency)")
            bestUseCases.add("Технические задачи")
        } else {
            strengths.add("Разнообразные ответы")
            bestUseCases.add("Креативные задачи")
        }

        // Анализ разнообразия
        if (metrics.selfBleu < 0.3) {
            strengths.add("Высокое разнообразие генераций")
            bestUseCases.add("Генерация вариантов контента")
        }

        // Анализ длины ответов
        if (metrics.avgWordCount > 50) {
            strengths.add("Подробные ответы (${metrics.avgWordCount.toInt()} слов)")
            bestUseCases.add("Объяснения и туториалы")
        } else {
            strengths.add("Краткие ответы (${metrics.avgWordCount.toInt()} слов)")
            bestUseCases.add("Быстрые ответы на вопросы")
        }

        fun formatDouble(value: Double): String {
            val rounded = (value * 100).toInt() / 100.0
            return rounded.toString()
        }

        val summary = "Модель ${result.model.displayName}: " +
                "средняя скорость ${metrics.avgResponseTime.toInt()}ms, " +
                "консистентность ${formatDouble(metrics.semanticConsistency)}, " +
                "разнообразие ${formatDouble(1.0 - metrics.selfBleu)}."

        return ModelRecommendation(
            model = result.model,
            strengths = strengths,
            weaknesses = weaknesses.ifEmpty { listOf("Нет явных недостатков") },
            bestUseCases = bestUseCases,
            summary = summary
        )
    }

    fun close() {
        hfService.close()
    }
}
