package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.model.*
import com.example.ai_window.service.TemperatureExperimentService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Платформо-зависимая функция для получения текущей даты и времени
 */
expect fun getCurrentTimestamp(): String

class TemperatureViewModel(
    apiKey: String,
    folderId: String
) : ViewModel() {

    private val service = TemperatureExperimentService(apiKey, folderId)

    // Список температур для экспериментов
    // Yandex GPT API поддерживает температуру от 0.0 до 1.0
    val temperatures = listOf(0.1, 0.6, 0.9)

    // Вопрос для эксперимента
    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    // Режим выполнения
    private val _executionMode = MutableStateFlow(ExecutionMode.PARALLEL)
    val executionMode: StateFlow<ExecutionMode> = _executionMode.asStateFlow()

    // Состояния для каждой температуры
    private val _experimentStates = MutableStateFlow<Map<Double, ExperimentState>>(
        temperatures.associateWith { ExperimentState.Idle }
    )
    val experimentStates: StateFlow<Map<Double, ExperimentState>> = _experimentStates.asStateFlow()

    // Общее состояние загрузки
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Примеры вопросов для разных типов задач
    val exampleQuestions = listOf(
        "Кто написал роман 'Война и мир'?",
        "Напиши короткую историю про робота-художника",
        "Объясни принцип работы async/await в Kotlin"
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
     * Запустить эксперимент для одной температуры
     */
    fun runSingleExperiment(temperature: Double) {
        if (_question.value.isBlank()) {
            updateState(temperature, ExperimentState.Error("Введите вопрос"))
            return
        }

        viewModelScope.launch {
            try {
                updateState(temperature, ExperimentState.Loading())

                val result = service.runExperiment(
                    question = _question.value,
                    temperature = temperature,
                    runs = 3,
                    onProgress = { current, total ->
                        updateState(temperature, ExperimentState.Loading(current, total))
                    }
                )

                result.fold(
                    onSuccess = { tempResult ->
                        // Добавляем рекомендации
                        val withRecommendation = tempResult.copy(
                            recommendation = service.generateRecommendation(tempResult)
                        )
                        updateState(temperature, ExperimentState.Success(withRecommendation))
                    },
                    onFailure = { error ->
                        updateState(temperature, ExperimentState.Error(error.message ?: "Ошибка"))
                    }
                )
            } catch (e: Exception) {
                updateState(temperature, ExperimentState.Error(e.message ?: "Неизвестная ошибка"))
            }
        }
    }

    /**
     * Запустить все эксперименты
     */
    fun runAllExperiments() {
        if (_question.value.isBlank()) {
            temperatures.forEach { temp ->
                updateState(temp, ExperimentState.Error("Введите вопрос"))
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
                println("Error running all experiments: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Параллельный запуск всех экспериментов
     */
    private suspend fun runParallel() {
        // Устанавливаем состояние Loading для всех
        temperatures.forEach { temp ->
            updateState(temp, ExperimentState.Loading())
        }

        // Запускаем все эксперименты параллельно
        val jobs = temperatures.map { temp ->
            viewModelScope.async {
                val result = service.runExperiment(
                    question = _question.value,
                    temperature = temp,
                    runs = 3,
                    onProgress = { current, total ->
                        updateState(temp, ExperimentState.Loading(current, total))
                    }
                )

                result.fold(
                    onSuccess = { tempResult ->
                        val withRecommendation = tempResult.copy(
                            recommendation = service.generateRecommendation(tempResult)
                        )
                        updateState(temp, ExperimentState.Success(withRecommendation))
                    },
                    onFailure = { error ->
                        updateState(temp, ExperimentState.Error(error.message ?: "Ошибка"))
                    }
                )
            }
        }

        jobs.awaitAll()
    }

    /**
     * Последовательный запуск всех экспериментов
     */
    private suspend fun runSequential() {
        for (temp in temperatures) {
            updateState(temp, ExperimentState.Loading())

            val result = service.runExperiment(
                question = _question.value,
                temperature = temp,
                runs = 3,
                onProgress = { current, total ->
                    updateState(temp, ExperimentState.Loading(current, total))
                }
            )

            result.fold(
                onSuccess = { tempResult ->
                    val withRecommendation = tempResult.copy(
                        recommendation = service.generateRecommendation(tempResult)
                    )
                    updateState(temp, ExperimentState.Success(withRecommendation))
                },
                onFailure = { error ->
                    updateState(temp, ExperimentState.Error(error.message ?: "Ошибка"))
                }
            )
        }
    }

    /**
     * Очистить все результаты
     */
    fun clearResults() {
        _experimentStates.value = temperatures.associateWith { ExperimentState.Idle }
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
        val results = _experimentStates.value.values
            .filterIsInstance<ExperimentState.Success>()
            .map { it.result }
            .sortedBy { it.temperature }

        if (results.isEmpty()) {
            return "Нет данных для экспорта. Запустите эксперименты."
        }

        val report = buildString {
            appendLine(repeatString("=", 80))
            appendLine("ОТЧЕТ: ЭКСПЕРИМЕНТЫ С ТЕМПЕРАТУРОЙ YANDEX GPT API")
            appendLine(repeatString("=", 80))
            appendLine()
            appendLine("Вопрос: ${_question.value}")
            appendLine("Дата: ${getCurrentTimestamp()}")
            appendLine("Количество генераций на температуру: 3")
            appendLine()
            appendLine(repeatString("=", 80))
            appendLine("СРАВНИТЕЛЬНАЯ ТАБЛИЦА МЕТРИК")
            appendLine(repeatString("=", 80))
            appendLine()

            // Заголовок таблицы
            append("Метрика".padEnd(25))
            results.forEach { result ->
                append("T=${result.temperature}".padStart(15))
            }
            appendLine()
            appendLine(repeatString("-", 80))

            // Строки метрик
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

            append("Разнообразие".padEnd(25))
            results.forEach { result ->
                append(formatDouble(result.metrics.responseVariability.structuralDiversity, 2).padStart(15))
            }
            appendLine()
            appendLine()

            // Детальные результаты для каждой температуры
            results.forEach { result ->
                appendLine()
                appendLine(repeatString("=", 80))
                appendLine("ТЕМПЕРАТУРА: ${result.temperature}")
                appendLine(repeatString("=", 80))
                appendLine()

                // Метрики
                appendLine("МЕТРИКИ:")
                appendLine("  Self-BLEU: ${formatDouble(result.metrics.selfBleu, 2)}")
                appendLine("  Семантическая согласованность: ${formatDouble(result.metrics.semanticConsistency, 2)}")
                appendLine("  Слов (среднее): ${formatDouble(result.metrics.avgWordCount, 0)}")
                appendLine("  Символов (среднее): ${formatDouble(result.metrics.avgCharCount, 0)}")
                appendLine("  Уникальных слов: ${formatDouble(result.metrics.avgUniqueWords, 0)}")
                appendLine("  Структурное разнообразие: ${formatDouble(result.metrics.responseVariability.structuralDiversity, 2)}")
                appendLine("  Станд. откл. длины: ${formatDouble(result.metrics.responseVariability.lengthStdDev, 2)}")
                appendLine()

                // Рекомендации
                result.recommendation?.let { rec ->
                    appendLine("РЕКОМЕНДАЦИИ:")
                    appendLine(rec.summary)
                    appendLine()
                    appendLine("Подходит для:")
                    rec.bestFor.forEach { appendLine("  • $it") }
                    appendLine()
                    appendLine("Избегать для:")
                    rec.avoidFor.forEach { appendLine("  • $it") }
                    appendLine()
                }

                // Примеры ответов
                appendLine("ПРИМЕРЫ ОТВЕТОВ:")
                result.responses.forEachIndexed { index, response ->
                    appendLine()
                    appendLine("--- Ответ ${index + 1} ---")
                    appendLine(response)
                    appendLine()
                }

                // Предупреждения (если есть)
                if (result.parseWarnings.isNotEmpty()) {
                    appendLine("ПРЕДУПРЕЖДЕНИЯ:")
                    result.parseWarnings.forEach { appendLine("  ⚠ $it") }
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
     * Обновить состояние для конкретной температуры
     */
    private fun updateState(temperature: Double, state: ExperimentState) {
        _experimentStates.value = _experimentStates.value.toMutableMap().apply {
            put(temperature, state)
        }
    }

    override fun onCleared() {
        super.onCleared()
        service.close()
    }
}
