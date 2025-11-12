package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.model.*
import com.example.ai_window.service.HuggingFaceService
import com.example.ai_window.service.ModelComparisonService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelComparisonViewModel(
    hfToken: String
) : ViewModel() {

    private val hfService = HuggingFaceService(hfToken)
    private val comparisonService = ModelComparisonService(hfService)

    // Список моделей для сравнения
    val models = HuggingFaceModels.AVAILABLE_MODELS

    // Вопрос для сравнения
    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    // Режим выполнения
    private val _executionMode = MutableStateFlow(ExecutionMode.PARALLEL)
    val executionMode: StateFlow<ExecutionMode> = _executionMode.asStateFlow()

    // Состояния для каждой модели
    private val _modelStates = MutableStateFlow<Map<String, ModelComparisonState>>(
        models.associate { it.modelId to ModelComparisonState.Idle }
    )
    val modelStates: StateFlow<Map<String, ModelComparisonState>> = _modelStates.asStateFlow()

    // Общее состояние загрузки
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Примеры вопросов
    val exampleQuestions = listOf(
        "Кто написал роман 'Война и мир'?",
        "Напиши короткую историю про робота",
        "Объясни что такое квантовая физика",
        "Как работает машинное обучение?"
    )

    /**
     * Установить вопрос
     */
    fun setQuestion(text: String) {
        _question.value = text
    }

    /**
     * Переключить режим выполнения
     */
    fun toggleExecutionMode() {
        _executionMode.value = when (_executionMode.value) {
            ExecutionMode.PARALLEL -> ExecutionMode.SEQUENTIAL
            ExecutionMode.SEQUENTIAL -> ExecutionMode.PARALLEL
        }
    }

    /**
     * Запустить сравнение для одной модели
     */
    fun runSingleComparison(model: HFModel) {
        if (_question.value.isBlank()) {
            updateState(model.modelId, ModelComparisonState.Error("Введите вопрос"))
            return
        }

        viewModelScope.launch {
            try {
                updateState(model.modelId, ModelComparisonState.Loading())

                val result = comparisonService.compareModel(
                    model = model,
                    question = _question.value,
                    runs = 3,
                    onProgress = { current, total ->
                        updateState(model.modelId, ModelComparisonState.Loading(current, total))
                    }
                )

                result.fold(
                    onSuccess = { comparisonResult ->
                        updateState(model.modelId, ModelComparisonState.Success(comparisonResult))
                    },
                    onFailure = { error ->
                        updateState(model.modelId, ModelComparisonState.Error(error.message ?: "Ошибка"))
                    }
                )
            } catch (e: Exception) {
                updateState(model.modelId, ModelComparisonState.Error(e.message ?: "Неизвестная ошибка"))
            }
        }
    }

    /**
     * Запустить сравнение всех моделей
     */
    fun runAllComparisons() {
        if (_question.value.isBlank()) {
            models.forEach { model ->
                updateState(model.modelId, ModelComparisonState.Error("Введите вопрос"))
            }
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                when (_executionMode.value) {
                    ExecutionMode.PARALLEL -> runParallel()
                    ExecutionMode.SEQUENTIAL -> runSequential()
                }
            } catch (e: Exception) {
                println("Error running all comparisons: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Параллельный запуск всех сравнений
     */
    private suspend fun runParallel() {
        // Устанавливаем состояние Loading для всех
        models.forEach { model ->
            updateState(model.modelId, ModelComparisonState.Loading())
        }

        // Запускаем все сравнения параллельно
        val jobs = models.map { model ->
            viewModelScope.async {
                val result = comparisonService.compareModel(
                    model = model,
                    question = _question.value,
                    runs = 3,
                    onProgress = { current, total ->
                        updateState(model.modelId, ModelComparisonState.Loading(current, total))
                    }
                )

                result.fold(
                    onSuccess = { comparisonResult ->
                        updateState(model.modelId, ModelComparisonState.Success(comparisonResult))
                    },
                    onFailure = { error ->
                        updateState(model.modelId, ModelComparisonState.Error(error.message ?: "Ошибка"))
                    }
                )
            }
        }

        jobs.awaitAll()
    }

    /**
     * Последовательный запуск всех сравнений
     */
    private suspend fun runSequential() {
        for (model in models) {
            updateState(model.modelId, ModelComparisonState.Loading())

            val result = comparisonService.compareModel(
                model = model,
                question = _question.value,
                runs = 3,
                onProgress = { current, total ->
                    updateState(model.modelId, ModelComparisonState.Loading(current, total))
                }
            )

            result.fold(
                onSuccess = { comparisonResult ->
                    updateState(model.modelId, ModelComparisonState.Success(comparisonResult))
                },
                onFailure = { error ->
                    updateState(model.modelId, ModelComparisonState.Error(error.message ?: "Ошибка"))
                }
            )
        }
    }

    /**
     * Очистить все результаты
     */
    fun clearResults() {
        _modelStates.value = models.associate { it.modelId to ModelComparisonState.Idle }
        _isLoading.value = false
    }

    /**
     * Установить пример вопроса
     */
    fun setExampleQuestion(index: Int) {
        if (index in exampleQuestions.indices) {
            _question.value = exampleQuestions[index]
        }
    }

    /**
     * Форматирование чисел для отчета
     */
    private fun formatDouble(value: Double, decimals: Int = 2): String {
        val multiplier = when (decimals) {
            0 -> 1.0
            1 -> 10.0
            2 -> 100.0
            else -> 100.0
        }
        val rounded = (value * multiplier).toInt() / multiplier
        return rounded.toString()
    }

    /**
     * Повторить строку N раз
     */
    private fun repeatString(str: String, count: Int): String {
        return buildString {
            repeat(count) {
                append(str)
            }
        }
    }

    /**
     * Сгенерировать текстовый отчет для экспорта
     */
    fun generateReport(): String {
        val results = _modelStates.value.values
            .filterIsInstance<ModelComparisonState.Success>()
            .map { it.result }
            .sortedBy { it.model.displayName }

        if (results.isEmpty()) {
            return "Нет данных для экспорта. Запустите сравнение моделей."
        }

        val winners = comparisonService.determineWinners(results)

        val report = buildString {
            appendLine(repeatString("=", 80))
            appendLine("ОТЧЕТ: СРАВНЕНИЕ МОДЕЛЕЙ HUGGINGFACE")
            appendLine(repeatString("=", 80))
            appendLine()
            appendLine("Вопрос: ${_question.value}")
            appendLine("Дата: ${getCurrentTimestamp()}")
            appendLine("Количество генераций на модель: 3")
            appendLine("Режим выполнения: ${_executionMode.value}")
            appendLine()
            appendLine(repeatString("=", 80))
            appendLine("ПОБЕДИТЕЛИ В КАТЕГОРИЯХ")
            appendLine(repeatString("=", 80))
            appendLine("  🏃 Самая быстрая: ${winners.fastest}")
            appendLine("  🎯 Самая консистентная: ${winners.mostConsistent}")
            appendLine("  🎨 Самая креативная: ${winners.mostCreative}")
            appendLine("  📝 Самые длинные ответы: ${winners.longestResponses}")
            appendLine("  ⚡ Самая эффективная: ${winners.mostEfficient}")
            appendLine()
            appendLine(repeatString("=", 80))
            appendLine("СРАВНИТЕЛЬНАЯ ТАБЛИЦА МЕТРИК")
            appendLine(repeatString("=", 80))
            appendLine()

            // Заголовок таблицы
            append("Метрика".padEnd(25))
            results.forEach { result ->
                append(result.model.displayName.take(15).padStart(15))
            }
            appendLine()
            appendLine(repeatString("-", 80))

            // Строки метрик
            append("Время (мс)".padEnd(25))
            results.forEach { result ->
                append(formatDouble(result.metrics.avgResponseTime, 0).padStart(15))
            }
            appendLine()

            append("Токены (всего)".padEnd(25))
            results.forEach { result ->
                append(formatDouble(result.metrics.avgTotalTokens, 0).padStart(15))
            }
            appendLine()

            append("Self-BLEU".padEnd(25))
            results.forEach { result ->
                append(formatDouble(result.metrics.selfBleu, 2).padStart(15))
            }
            appendLine()

            append("Согласованность".padEnd(25))
            results.forEach { result ->
                append(formatDouble(result.metrics.semanticConsistency, 2).padStart(15))
            }
            appendLine()

            append("Слов (среднее)".padEnd(25))
            results.forEach { result ->
                append(formatDouble(result.metrics.avgWordCount, 0).padStart(15))
            }
            appendLine()

            append("Уник. слов".padEnd(25))
            results.forEach { result ->
                append(formatDouble(result.metrics.avgUniqueWords, 0).padStart(15))
            }
            appendLine()
            appendLine()

            // Детальные результаты для каждой модели
            results.forEach { result ->
                appendLine()
                appendLine(repeatString("=", 80))
                appendLine("МОДЕЛЬ: ${result.model.displayName}")
                appendLine("ID: ${result.model.modelId}")
                appendLine(repeatString("=", 80))
                appendLine()

                // Метрики производительности
                appendLine("ПРОИЗВОДИТЕЛЬНОСТЬ:")
                appendLine("  Среднее время: ${formatDouble(result.metrics.avgResponseTime, 0)} мс")
                appendLine("  Мин время: ${result.metrics.minResponseTime} мс")
                appendLine("  Макс время: ${result.metrics.maxResponseTime} мс")
                appendLine()

                // Метрики токенов
                appendLine("ТОКЕНЫ:")
                appendLine("  Вход (среднее): ${formatDouble(result.metrics.avgInputTokens, 0)}")
                appendLine("  Выход (среднее): ${formatDouble(result.metrics.avgOutputTokens, 0)}")
                appendLine("  Всего (среднее): ${formatDouble(result.metrics.avgTotalTokens, 0)}")
                appendLine()

                // Метрики качества
                appendLine("КАЧЕСТВО:")
                appendLine("  Self-BLEU: ${formatDouble(result.metrics.selfBleu, 2)}")
                appendLine("  Семантическая согласованность: ${formatDouble(result.metrics.semanticConsistency, 2)}")
                appendLine("  Слов (среднее): ${formatDouble(result.metrics.avgWordCount, 0)}")
                appendLine("  Символов (среднее): ${formatDouble(result.metrics.avgCharCount, 0)}")
                appendLine("  Уникальных слов: ${formatDouble(result.metrics.avgUniqueWords, 0)}")
                appendLine("  Структурное разнообразие: ${formatDouble(result.metrics.responseVariability.structuralDiversity, 2)}")
                appendLine()

                // Рекомендации
                val recommendation = comparisonService.generateRecommendation(result)
                appendLine("РЕКОМЕНДАЦИИ:")
                appendLine(recommendation.summary)
                appendLine()
                appendLine("Сильные стороны:")
                recommendation.strengths.forEach { appendLine("  ✓ $it") }
                appendLine()
                appendLine("Слабые стороны:")
                recommendation.weaknesses.forEach { appendLine("  ✗ $it") }
                appendLine()
                appendLine("Лучшие сценарии:")
                recommendation.bestUseCases.forEach { appendLine("  • $it") }
                appendLine()

                // Примеры ответов
                appendLine("ПРИМЕРЫ ОТВЕТОВ:")
                result.responses.forEachIndexed { index, response ->
                    appendLine()
                    appendLine("--- Ответ ${index + 1} (${result.executionTimes.getOrNull(index) ?: 0}мс) ---")
                    appendLine(response.take(500)) // Ограничиваем длину
                    if (response.length > 500) appendLine("... (обрезано)")
                    appendLine()
                }

                // Ошибки (если есть)
                if (result.errors.isNotEmpty()) {
                    appendLine("ОШИБКИ:")
                    result.errors.forEach { appendLine("  ❌ $it") }
                    appendLine()
                }
            }

            appendLine()
            appendLine(repeatString("=", 80))
            appendLine("КОНЕЦ ОТЧЕТА")
            appendLine(repeatString("=", 80))
        }

        return report
    }

    /**
     * Обновить состояние для конкретной модели
     */
    private fun updateState(modelId: String, state: ModelComparisonState) {
        _modelStates.value = _modelStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }

    override fun onCleared() {
        super.onCleared()
        comparisonService.close()
    }
}
