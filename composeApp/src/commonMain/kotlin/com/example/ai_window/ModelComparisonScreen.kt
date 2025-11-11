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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelComparisonScreen(viewModel: ModelComparisonViewModel) {
    val question by viewModel.question.collectAsStateWithLifecycle()
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 Сравнение моделей HuggingFace") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Кнопка экспорта отчета
                    val hasResults = modelStates.values.any { it is ModelComparisonState.Success }
                    IconButton(
                        onClick = {
                            val report = viewModel.generateReport()
                            val timestamp = getCurrentTimestamp()
                                .replace(":", "-")
                                .replace(" ", "_")
                            saveTextToFile(report, "model_comparison_$timestamp.txt")
                        },
                        enabled = hasResults
                    ) {
                        Text("💾")
                    }

                    // Кнопка очистки результатов
                    IconButton(onClick = { viewModel.clearResults() }) {
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
