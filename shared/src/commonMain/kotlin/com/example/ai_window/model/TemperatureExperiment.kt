package com.example.ai_window.model

import kotlinx.serialization.Serializable

/**
 * Результат одного эксперимента с определенной температурой
 */
@Serializable
data class TemperatureResult(
    val temperature: Double,
    val question: String,
    val responses: List<String>,  // Множественные генерации для метрик
    val metrics: TemperatureMetrics,
    val recommendation: TemperatureRecommendation? = null,
    val parseWarnings: List<String> = emptyList()
)

/**
 * Метрики для анализа результатов
 */
@Serializable
data class TemperatureMetrics(
    // Базовые метрики
    val avgWordCount: Double,
    val avgCharCount: Double,
    val avgUniqueWords: Double,

    // Self-BLEU - разнообразие между генерациями
    val selfBleu: Double,  // 0.0 (полностью разные) до 1.0 (идентичные)

    // Semantic Consistency - семантическая схожесть
    val semanticConsistency: Double,  // 0.0 (разная семантика) до 1.0 (одинаковая)

    // Response Variability - вариативность ответов
    val responseVariability: VariabilityMetrics
)

/**
 * Детальные метрики вариативности
 */
@Serializable
data class VariabilityMetrics(
    val lengthStdDev: Double,  // Стандартное отклонение длины
    val uniqueWordsVariance: Double,  // Разброс уникальных слов
    val structuralDiversity: Double  // Разнообразие структуры (0.0-1.0)
)

/**
 * Рекомендации по использованию температуры
 */
@Serializable
data class TemperatureRecommendation(
    val temperature: Double,
    val bestFor: List<String>,  // Для каких задач подходит
    val avoidFor: List<String>,  // Для каких задач не подходит
    val useCases: List<String>,  // Конкретные примеры использования
    val summary: String  // Краткое резюме
)

/**
 * Состояние эксперимента для одной температуры
 */
sealed class ExperimentState {
    data object Idle : ExperimentState()
    data class Loading(val progress: Int = 0, val total: Int = 3) : ExperimentState()
    data class Success(val result: TemperatureResult) : ExperimentState()
    data class Error(val message: String) : ExperimentState()
}

/**
 * Режим запуска экспериментов
 */
enum class ExecutionMode {
    PARALLEL,      // Все запросы одновременно
    SEQUENTIAL     // По очереди
}
