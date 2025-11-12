package com.example.ai_window.model

import kotlinx.serialization.Serializable

/**
 * Результат сравнения одной модели
 */
@Serializable
data class ModelComparisonResult(
    val model: HFModel,
    val question: String,
    val responses: List<String>,  // Множественные генерации для расчета метрик
    val metrics: ModelComparisonMetrics,
    val executionTimes: List<Long>,  // milliseconds для каждого запроса
    val errors: List<String> = emptyList()
)

/**
 * Метрики для сравнения моделей
 */
@Serializable
data class ModelComparisonMetrics(
    // Метрики производительности
    val avgResponseTime: Double,  // Среднее время ответа в мс
    val minResponseTime: Long,    // Минимальное время
    val maxResponseTime: Long,    // Максимальное время

    // Метрики токенов
    val avgInputTokens: Double,
    val avgOutputTokens: Double,
    val avgTotalTokens: Double,

    // Метрики качества (переиспользуем из TemperatureMetrics)
    val avgWordCount: Double,
    val avgCharCount: Double,
    val avgUniqueWords: Double,

    // Метрики разнообразия
    val selfBleu: Double,  // 0.0 (полностью разные) до 1.0 (идентичные)
    val semanticConsistency: Double,  // 0.0 (разная семантика) до 1.0 (одинаковая)

    // Метрики вариативности
    val responseVariability: VariabilityMetrics
)

/**
 * Состояние сравнения для одной модели
 */
sealed class ModelComparisonState {
    data object Idle : ModelComparisonState()
    data class Loading(val progress: Int = 0, val total: Int = 3) : ModelComparisonState()
    data class Success(val result: ModelComparisonResult) : ModelComparisonState()
    data class Error(val message: String) : ModelComparisonState()
}

/**
 * Общий отчет о сравнении моделей
 */
@Serializable
data class ModelsComparisonReport(
    val question: String,
    val results: List<ModelComparisonResult>,
    val winner: ModelWinner,
    val timestamp: String,
    val executionMode: ExecutionMode
)

/**
 * Победитель в каждой категории
 */
@Serializable
data class ModelWinner(
    val fastest: String,              // Самая быстрая модель
    val mostConsistent: String,       // Самая консистентная
    val mostCreative: String,         // Самая креативная (low self-BLEU)
    val longestResponses: String,     // Самые длинные ответы
    val mostEfficient: String         // Лучшее соотношение качество/скорость
)

/**
 * Рекомендация по использованию модели
 */
@Serializable
data class ModelRecommendation(
    val model: HFModel,
    val strengths: List<String>,      // Сильные стороны
    val weaknesses: List<String>,     // Слабые стороны
    val bestUseCases: List<String>,   // Лучшие сценарии использования
    val summary: String               // Краткое резюме
)
