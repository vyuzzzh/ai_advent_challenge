package com.example.ai_window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai_window.model.*

/**
 * Нормализация имени модели для корректного сравнения
 * - Убирает суффиксы провайдеров (:fastest, :novita и т.д.)
 * - Приводит к нижнему регистру
 */
private fun normalizeModelName(modelName: String): String {
    return modelName
        .split(":")
        .first()
        .lowercase()
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelComparisonScreen(viewModel: ModelComparisonViewModel) {
    val question by viewModel.question.collectAsStateWithLifecycle()
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Token Analysis Mode states
    val comparisonMode by viewModel.comparisonMode.collectAsStateWithLifecycle()
    val tokenAnalysisState by viewModel.tokenAnalysisState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (comparisonMode) {
                            ComparisonMode.MODEL_COMPARISON -> "🤖 Сравнение моделей HuggingFace"
                            ComparisonMode.TOKEN_ANALYSIS -> "💎 Анализ токенов (Llama 3.2 1B)"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Кнопка экспорта отчета
                    val hasResults = when (comparisonMode) {
                        ComparisonMode.MODEL_COMPARISON -> modelStates.values.any { it is ModelComparisonState.Success }
                        ComparisonMode.TOKEN_ANALYSIS -> tokenAnalysisState is TokenAnalysisState.Success
                    }

                    IconButton(
                        onClick = {
                            val report = when (comparisonMode) {
                                ComparisonMode.MODEL_COMPARISON -> viewModel.generateReport()
                                ComparisonMode.TOKEN_ANALYSIS -> viewModel.generateTokenAnalysisReport()
                            }
                            val timestamp = getCurrentTimestamp()
                                .replace(":", "-")
                                .replace(" ", "_")
                            val filename = when (comparisonMode) {
                                ComparisonMode.MODEL_COMPARISON -> "model_comparison_$timestamp.txt"
                                ComparisonMode.TOKEN_ANALYSIS -> "token_analysis_$timestamp.txt"
                            }
                            saveTextToFile(report, filename)
                        },
                        enabled = hasResults
                    ) {
                        Text("💾")
                    }

                    // Кнопка очистки результатов
                    IconButton(
                        onClick = {
                            when (comparisonMode) {
                                ComparisonMode.MODEL_COMPARISON -> viewModel.clearResults()
                                ComparisonMode.TOKEN_ANALYSIS -> viewModel.clearTokenAnalysis()
                            }
                        }
                    ) {
                        Text("🗑️")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Переключатель режимов
            item {
                ModeSelectorSection(
                    currentMode = comparisonMode,
                    onToggleMode = { viewModel.toggleComparisonMode() }
                )
            }

            // ========== MODEL COMPARISON MODE ==========
            if (comparisonMode == ComparisonMode.MODEL_COMPARISON) {
                // Секция с вводом вопроса
                item {
                    QuestionInputSection(
                        question = question,
                        onQuestionChange = { viewModel.setQuestion(it) },
                        exampleQuestions = viewModel.exampleQuestions,
                        onExampleClick = { viewModel.setExampleQuestion(it) }
                    )
                }

                // Секция с настройками
                item {
                    SettingsSection(
                        executionMode = executionMode,
                        onToggleMode = { viewModel.toggleExecutionMode() },
                        isLoading = isLoading
                    )
                }

                // Кнопки запуска
                item {
                    ModelRunButtonsSection(
                        models = viewModel.models,
                        onRunSingle = { viewModel.runSingleComparison(it) },
                        onRunAll = { viewModel.runAllComparisons() },
                        isLoading = isLoading,
                        questionEmpty = question.isBlank()
                    )
                }

                // Сравнительная таблица метрик
                item {
                    val successResults = modelStates.values
                        .filterIsInstance<ModelComparisonState.Success>()
                        .map { it.result }
                        .sortedBy { it.model.displayName }

                    if (successResults.isNotEmpty()) {
                        ModelComparisonTable(results = successResults)
                    }
                }

                // Результаты для каждой модели
                items(viewModel.models) { model ->
                    val state = modelStates[model.modelId] ?: ModelComparisonState.Idle

                    ModelResultCard(
                        model = model,
                        state = state
                    )
                }
            }

            // ========== TOKEN ANALYSIS MODE ==========
            if (comparisonMode == ComparisonMode.TOKEN_ANALYSIS) {
                // Описание режима
                item {
                    TokenAnalysisDescription()
                }

                // Кнопка запуска анализа
                item {
                    TokenAnalysisRunButton(
                        onRun = { viewModel.runTokenAnalysis() },
                        state = tokenAnalysisState
                    )
                }

                // Статус загрузки
                item {
                    when (tokenAnalysisState) {
                        is TokenAnalysisState.Loading -> {
                            TokenAnalysisLoadingCard(state = tokenAnalysisState as TokenAnalysisState.Loading)
                        }
                        else -> {}
                    }
                }

                // Таблица результатов
                item {
                    when (tokenAnalysisState) {
                        is TokenAnalysisState.Success -> {
                            TokenAnalysisResultsTable(
                                results = (tokenAnalysisState as TokenAnalysisState.Success).results
                            )
                        }
                        is TokenAnalysisState.Error -> {
                            ErrorCard(message = (tokenAnalysisState as TokenAnalysisState.Error).message)
                        }
                        else -> {}
                    }
                }

                // Детальные карточки результатов
                when (tokenAnalysisState) {
                    is TokenAnalysisState.Success -> {
                        items((tokenAnalysisState as TokenAnalysisState.Success).results) { result ->
                            TokenTestResultCard(result = result)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun ModelRunButtonsSection(
    models: List<HFModel>,
    onRunSingle: (HFModel) -> Unit,
    onRunAll: () -> Unit,
    isLoading: Boolean,
    questionEmpty: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Запуск сравнения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Кнопка "Сравнить все модели"
            Button(
                onClick = onRunAll,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !questionEmpty
            ) {
                Text("▶️ Сравнить все модели")
            }

            Divider()

            Text(
                "Или запустить отдельно:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Кнопки для каждой модели
            models.forEach { model ->
                OutlinedButton(
                    onClick = { onRunSingle(model) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && !questionEmpty
                ) {
                    Text(model.displayName)
                }
            }
        }
    }
}

@Composable
fun ModelComparisonTable(results: List<ModelComparisonResult>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📊 Сравнительная таблица метрик",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Таблица метрик
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Заголовок
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Метрика",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    results.forEach { result ->
                        Text(
                            result.model.displayName.take(10),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Divider()

                // Время ответа
                MetricRow("Время (мс)", results) { it.metrics.avgResponseTime.toInt().toString() }

                // Токены
                MetricRow("Токены", results) { it.metrics.avgTotalTokens.toInt().toString() }

                // Слова
                MetricRow("Слов", results) { it.metrics.avgWordCount.toInt().toString() }

                // Self-BLEU
                MetricRow("Self-BLEU", results) { ((it.metrics.selfBleu * 100).toInt() / 100.0).toString() }

                // Консистентность
                MetricRow("Консист.", results) { ((it.metrics.semanticConsistency * 100).toInt() / 100.0).toString() }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, results: List<ModelComparisonResult>, valueExtractor: (ModelComparisonResult) -> String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
        results.forEach { result ->
            Text(
                valueExtractor(result),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ModelResultCard(
    model: HFModel,
    state: ModelComparisonState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ModelComparisonState.Success -> MaterialTheme.colorScheme.primaryContainer
                is ModelComparisonState.Error -> MaterialTheme.colorScheme.errorContainer
                is ModelComparisonState.Loading -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        model.modelId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Статус индикатор
                when (state) {
                    is ModelComparisonState.Idle -> Text("⏸️", style = MaterialTheme.typography.headlineMedium)
                    is ModelComparisonState.Loading -> {
                        Column(horizontalAlignment = Alignment.End) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                "${state.progress}/${state.total}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is ModelComparisonState.Success -> Text("✅", style = MaterialTheme.typography.headlineMedium)
                    is ModelComparisonState.Error -> Text("❌", style = MaterialTheme.typography.headlineMedium)
                }
            }

            // Детали результата
            when (state) {
                is ModelComparisonState.Success -> {
                    val result = state.result
                    Divider()

                    // Основные метрики в компактном виде
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MetricChip(
                            "⏱️ Среднее время",
                            "${result.metrics.avgResponseTime.toInt()} мс"
                        )
                        MetricChip(
                            "🎯 Токены",
                            result.metrics.avgTotalTokens.toInt().toString()
                        )
                        MetricChip(
                            "📝 Слов",
                            result.metrics.avgWordCount.toInt().toString()
                        )
                        MetricChip(
                            "🎲 Self-BLEU",
                            ((result.metrics.selfBleu * 100).toInt() / 100.0).toString()
                        )
                    }

                    // Первый ответ (превью)
                    if (result.responses.isNotEmpty()) {
                        Divider()
                        Text(
                            "Пример ответа:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            result.responses[0].take(200) + if (result.responses[0].length > 200) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                        )
                    }
                }
                is ModelComparisonState.Error -> {
                    Divider()
                    Text(
                        "Ошибка: ${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MetricChip(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ========== TOKEN ANALYSIS MODE COMPOSABLES ==========

@Composable
fun ModeSelectorSection(
    currentMode: ComparisonMode,
    onToggleMode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Режим работы:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(onClick = onToggleMode) {
                Text(
                    when (currentMode) {
                        ComparisonMode.MODEL_COMPARISON -> "🤖 Сравнение моделей → 💎 Анализ токенов"
                        ComparisonMode.TOKEN_ANALYSIS -> "💎 Анализ токенов → 🤖 Сравнение моделей"
                    }
                )
            }
        }
    }
}

@Composable
fun TokenAnalysisDescription() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "💎 Анализ токенов",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Модель: Llama 3.2 1B (демо-лимит: 1024 токена)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                "Этот режим тестирует 4 типа промптов разной длины, чтобы продемонстрировать работу с токенами:",
                style = MaterialTheme.typography.bodySmall
            )

            Column(modifier = Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("• Короткий (~15-30 токенов)", style = MaterialTheme.typography.bodySmall)
                Text("• Средний (~150-220 токенов)", style = MaterialTheme.typography.bodySmall)
                Text("• Длинный (~750-900 токенов)", style = MaterialTheme.typography.bodySmall)
                Text("• Превышающий лимит (>1024 токенов)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TokenAnalysisRunButton(
    onRun: () -> Unit,
    state: TokenAnalysisState
) {
    val isLoading = state is TokenAnalysisState.Loading

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Запуск анализа",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onRun,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "⏳ Выполняется анализ..." else "▶️ Запустить анализ токенов")
            }
        }
    }
}

@Composable
fun TokenAnalysisLoadingCard(state: TokenAnalysisState.Loading) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()

            Text(
                "Выполняется тест: ${state.currentTest?.displayName ?: "..."}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Завершено: ${state.completedTests} из ${state.totalTests}",
                style = MaterialTheme.typography.bodyMedium
            )

            LinearProgressIndicator(
                progress = { state.completedTests.toFloat() / state.totalTests.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("❌", style = MaterialTheme.typography.headlineMedium)
            Column {
                Text(
                    "Ошибка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun TokenAnalysisResultsTable(results: List<TokenTestResult>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📊 Результаты анализа токенов",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Таблица результатов
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Заголовок
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Тип",
                        modifier = Modifier.weight(1.5f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Input",
                        modifier = Modifier.weight(0.8f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Output",
                        modifier = Modifier.weight(0.8f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Total",
                        modifier = Modifier.weight(0.8f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "%",
                        modifier = Modifier.weight(0.6f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Divider()

                // Строки данных
                results.forEach { result ->
                    val promptTypeDisplay = when (result.promptType) {
                        "SHORT" -> "Короткий"
                        "MEDIUM" -> "Средний"
                        "LONG" -> "Длинный"
                        "EXCEEDS_LIMIT" -> "Превышает"
                        else -> result.promptType
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            promptTypeDisplay,
                            modifier = Modifier.weight(1.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            result.actualInputTokens.toString(),
                            modifier = Modifier.weight(0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            result.actualOutputTokens.toString(),
                            modifier = Modifier.weight(0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            result.totalTokens.toString(),
                            modifier = Modifier.weight(0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${result.percentageUsed.toInt()}%",
                            modifier = Modifier.weight(0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            color = getTokenPercentageColor(result.percentageUsed)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TokenTestResultCard(result: TokenTestResult) {
    val statusColor = when {
        !result.success -> MaterialTheme.colorScheme.errorContainer
        result.percentageUsed > 90 -> Color(0xFFFFCDD2) // Light Red
        result.percentageUsed > 70 -> Color(0xFFFFF9C4) // Light Yellow
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (result.promptType) {
                        "SHORT" -> "📝 Короткий промпт"
                        "MEDIUM" -> "📄 Средний промпт"
                        "LONG" -> "📃 Длинный промпт"
                        "EXCEEDS_LIMIT" -> "📜 Превышающий лимит"
                        else -> result.promptType
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (result.success) {
                        when {
                            result.percentageUsed > 90 -> "🔴"
                            result.percentageUsed > 70 -> "🟡"
                            else -> "✅"
                        }
                    } else "❌",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Divider()

            // Warning о подмене модели (с нормализацией)
            result.actualModelUsed?.let { actualModel ->
                if (normalizeModelName(actualModel) != normalizeModelName(result.requestedModel)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)) // Light Orange
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠️", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Модель подменена Router'ом!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100) // Dark Orange
                            )
                        }
                        Text(
                            "Запрошено: ${result.requestedModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6D4C41) // Brown
                        )
                        Text(
                            "Использовано: $actualModel",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D4C41)
                        )
                        Text(
                            "Поэтому метрики токенов могут не соответствовать ожиданиям!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
                }
            }

            // Метрики
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip("Длина промпта", "${result.promptLength} символов")
                MetricChip("Оценка токенов", "~${result.estimatedPromptTokens} токенов")
                MetricChip("Реальные токены (вход)", result.actualInputTokens.toString())
                MetricChip("Реальные токены (выход)", result.actualOutputTokens.toString())
                MetricChip("Всего токенов", result.totalTokens.toString())
                MetricChip("Лимит модели", "${result.modelContextLimit} токенов")
            }

            // Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Использование контекста:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${result.percentageUsed.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = getTokenPercentageColor(result.percentageUsed)
                    )
                }

                TokenLimitProgressBar(
                    percentage = result.percentageUsed,
                    limit = result.modelContextLimit
                )
            }

            // Результат/Ошибка
            Divider()

            if (result.success) {
                Text(
                    "Ответ модели:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    result.response.take(200) + if (result.response.length > 200) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                )

                Text(
                    "Время выполнения: ${result.executionTime} мс",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Ошибка: ${result.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TokenLimitProgressBar(percentage: Double, limit: Int) {
    val progress = (percentage / 100.0).toFloat().coerceIn(0f, 1f)

    val progressColor = when {
        percentage > 90 -> Color(0xFFE53935) // Red
        percentage > 70 -> Color(0xFFFDD835) // Yellow
        else -> Color(0xFF43A047) // Green
    }

    Column {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Легенда
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "70%",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFDD835),
                fontWeight = FontWeight.Bold
            )
            Text(
                "90%",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE53935),
                fontWeight = FontWeight.Bold
            )
            Text("$limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun getTokenPercentageColor(percentage: Double): Color {
    return when {
        percentage > 90 -> Color(0xFFE53935) // Red
        percentage > 70 -> Color(0xFFFDD835) // Yellow
        else -> Color(0xFF43A047) // Green
    }
}
