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

/**
 * Тип тестового промпта для анализа токенов
 */
enum class PromptType(val displayName: String, val estimatedTokens: String) {
    SHORT("Короткий", "~15-30 токенов"),
    MEDIUM("Средний", "~150-220 токенов"),
    LONG("Длинный", "~750-900 токенов"),
    EXCEEDS_LIMIT("Превышает лимит", ">1024 токенов")
}

/**
 * Предустановленные промпты для тестирования токенов
 */
object TokenTestPrompts {
    /**
     * Короткий промпт (~10-20 слов, ~15-30 токенов)
     */
    val SHORT_PROMPT = "Что такое машинное обучение? Дай краткое определение в одном предложении."

    /**
     * Средний промпт (~100-150 слов, ~150-220 токенов)
     */
    val MEDIUM_PROMPT = """
        Объясни концепцию машинного обучения.

        Включи в ответ:
        1. Определение машинного обучения
        2. Основные типы обучения (с учителем, без учителя, с подкреплением)
        3. Примеры практического применения
        4. Ключевые отличия от традиционного программирования

        Ответ должен быть структурированным, но не слишком детальным - примерно 100-150 слов.
    """.trimIndent()

    /**
     * Длинный промпт (~500 слов, ~750-900 токенов)
     */
    val LONG_PROMPT = """
        Напиши подробную статью о машинном обучении и искусственном интеллекте.

        Структура статьи:

        1. ВВЕДЕНИЕ
        - Что такое искусственный интеллект и машинное обучение
        - История развития этих технологий
        - Почему они важны в современном мире

        2. ТИПЫ МАШИННОГО ОБУЧЕНИЯ
        - Обучение с учителем (supervised learning)
          * Классификация
          * Регрессия
          * Примеры алгоритмов и задач
        - Обучение без учителя (unsupervised learning)
          * Кластеризация
          * Снижение размерности
          * Примеры применения
        - Обучение с подкреплением (reinforcement learning)
          * Концепция агента и среды
          * Системы вознаграждений
          * Примеры: игры, робототехника

        3. АРХИТЕКТУРЫ НЕЙРОННЫХ СЕТЕЙ
        - Полносвязные сети (Dense Networks)
        - Свёрточные сети (CNN) для обработки изображений
        - Рекуррентные сети (RNN, LSTM) для последовательностей
        - Трансформеры для обработки языка

        4. ПРАКТИЧЕСКОЕ ПРИМЕНЕНИЕ
        - Компьютерное зрение (распознавание объектов, лиц)
        - Обработка естественного языка (чат-боты, переводчики)
        - Рекомендательные системы
        - Предсказательная аналитика в бизнесе
        - Медицинская диагностика
        - Автономные транспортные средства

        5. ВЫЗОВЫ И ОГРАНИЧЕНИЯ
        - Необходимость больших объёмов данных
        - Вычислительные ресурсы
        - Интерпретируемость моделей ("чёрный ящик")
        - Этические вопросы и предвзятость
        - Безопасность и конфиденциальность

        6. БУДУЩЕЕ ТЕХНОЛОГИЙ
        - Развитие больших языковых моделей
        - Мультимодальные системы
        - Автоматизация и её влияние на рынок труда
        - Регулирование AI

        ЗАКЛЮЧЕНИЕ
        - Резюме ключевых идей
        - Важность ответственного развития технологий

        Целевой объём: около 500 слов. Пиши информативно, но доступно.
    """.trimIndent()

    /**
     * Генерирует промпт, превышающий лимит модели
     * @param targetTokens целевое количество токенов (по умолчанию 1500 для превышения лимита 1024)
     */
    fun generateExceedingPrompt(targetTokens: Int = 1500): String {
        // Примерно 1.3 токена на слово для русского текста
        val targetWords = (targetTokens / 1.3).toInt()

        val baseText = """
            Напиши очень подробный, развёрнутый и детальный анализ следующих тем:

            1. История развития искусственного интеллекта с самого начала
            2. Все основные алгоритмы машинного обучения
            3. Детальное описание архитектур нейронных сетей
            4. Математические основы машинного обучения
            5. Практические применения в различных отраслях

        """.trimIndent()

        // Добавляем повторяющийся текст для достижения нужного размера
        val repeatingText = """
            Дополнительно опиши следующие аспекты в деталях:
            - Линейная регрессия и её математические основы
            - Логистическая регрессия для классификации
            - Метод опорных векторов (SVM)
            - Деревья решений и случайный лес
            - Градиентный бустинг (XGBoost, LightGBM)
            - K-ближайших соседей (K-NN)
            - Наивный байесовский классификатор
            - Кластеризация K-средних
            - Иерархическая кластеризация
            - DBSCAN для выявления аномалий
            - Метод главных компонент (PCA)
            - t-SNE для визуализации
            - Автокодировщики (Autoencoders)
            - Вариационные автокодировщики (VAE)
            - Генеративно-состязательные сети (GAN)
            - Сверточные нейронные сети (CNN)
            - Рекуррентные сети (RNN)
            - LSTM и GRU архитектуры
            - Механизм внимания (Attention)
            - Трансформеры и BERT
            - GPT модели и их эволюция
            - Обучение с подкреплением
            - Q-learning и Deep Q-Networks
            - Policy Gradient методы
            - Actor-Critic архитектуры

        """.trimIndent()

        val result = StringBuilder(baseText)

        // Добавляем повторяющийся текст до достижения целевого размера
        var currentWords = baseText.split("\\s+".toRegex()).size
        var iteration = 1

        while (currentWords < targetWords) {
            result.append("\n\nИтерация $iteration: $repeatingText")
            currentWords = result.toString().split("\\s+".toRegex()).size
            iteration++
        }

        result.append("\n\nВключи все детали, формулы, примеры кода и подробные объяснения.")

        return result.toString()
    }

    /**
     * Получить промпт по типу
     */
    fun getPromptByType(type: PromptType): String {
        return when (type) {
            PromptType.SHORT -> SHORT_PROMPT
            PromptType.MEDIUM -> MEDIUM_PROMPT
            PromptType.LONG -> LONG_PROMPT
            PromptType.EXCEEDS_LIMIT -> generateExceedingPrompt()
        }
    }
}

/**
 * Результат тестирования токенов
 */
@Serializable
data class TokenTestResult(
    val promptType: String,           // Тип промпта (SHORT, MEDIUM, LONG, EXCEEDS_LIMIT)
    val prompt: String,               // Полный текст промпта
    val promptLength: Int,            // Длина промпта в символах
    val estimatedPromptTokens: Int,   // Примерная оценка токенов промпта
    val actualInputTokens: Int,       // Реальные токены из API
    val actualOutputTokens: Int,      // Реальные токены вывода
    val totalTokens: Int,             // Общее количество токенов
    val modelContextLimit: Int,       // Лимит контекста модели (например, 1024)
    val percentageUsed: Double,       // Процент использования контекста
    val success: Boolean,             // Успешно ли выполнен запрос
    val error: String? = null,        // Ошибка, если есть
    val response: String = "",        // Ответ модели
    val executionTime: Long = 0,      // Время выполнения в мс
    val requestedModel: String = "",  // Запрошенная модель
    val actualModelUsed: String? = null  // Реально использованная модель (если отличается)
)

/**
 * Состояние анализа токенов
 */
sealed class TokenAnalysisState {
    data object Idle : TokenAnalysisState()
    data class Loading(val currentTest: PromptType?, val completedTests: Int, val totalTests: Int) : TokenAnalysisState()
    data class Success(val results: List<TokenTestResult>) : TokenAnalysisState()
    data class Error(val message: String) : TokenAnalysisState()
}
