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
fun TemperatureScreen(viewModel: TemperatureViewModel) {
    val question by viewModel.question.collectAsStateWithLifecycle()
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val experimentStates by viewModel.experimentStates.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🌡️ Эксперимент с температурой") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Кнопка экспорта отчета
                    val hasResults = experimentStates.values.any { it is ExperimentState.Success }
                    IconButton(
                        onClick = {
                            val report = viewModel.generateReport()
                            val timestamp = getCurrentTimestamp()
                                .replace(":", "-")
                                .replace(" ", "_")
                            saveTextToFile(report, "temperature_experiment_$timestamp.txt")
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
                RunButtonsSection(
                    temperatures = viewModel.temperatures,
                    onRunSingle = { viewModel.runSingleExperiment(it) },
                    onRunAll = { viewModel.runAllExperiments() },
                    isLoading = isLoading,
                    questionEmpty = question.isBlank()
                )
            }

            // Сравнительная таблица метрик (если есть успешные результаты) - ПЕРВОЙ!
            item {
                val successResults = experimentStates.values
                    .filterIsInstance<ExperimentState.Success>()
                    .map { it.result }

                if (successResults.isNotEmpty()) {
                    ComparisonTable(results = successResults)
                }
            }

            // Карточки результатов - после таблицы
            items(viewModel.temperatures) { temperature ->
                val state = experimentStates[temperature] ?: ExperimentState.Idle
                TemperatureResultCard(
                    temperature = temperature,
                    state = state
                )
            }
        }
    }
}

@Composable
fun QuestionInputSection(
    question: String,
    onQuestionChange: (String) -> Unit,
    exampleQuestions: List<String>,
    onExampleClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Вопрос для эксперимента",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите ваш вопрос...") },
                minLines = 2,
                maxLines = 4
            )

            Text(
                "Примеры:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                exampleQuestions.forEachIndexed { index, _ ->
                    Button(
                        onClick = { onExampleClick(index) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(
                            when (index) {
                                0 -> "Факт"
                                1 -> "Креатив"
                                else -> "Техника"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    executionMode: ExecutionMode,
    onToggleMode: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Режим выполнения:",
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (executionMode) {
                        ExecutionMode.PARALLEL -> "⚡ Параллельно"
                        ExecutionMode.SEQUENTIAL -> "📝 Последовательно"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Switch(
                    checked = executionMode == ExecutionMode.SEQUENTIAL,
                    onCheckedChange = { if (!isLoading) onToggleMode() },
                    enabled = !isLoading
                )
            }
        }
    }
}

@Composable
fun RunButtonsSection(
    temperatures: List<Double>,
    onRunSingle: (Double) -> Unit,
    onRunAll: () -> Unit,
    isLoading: Boolean,
    questionEmpty: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Кнопка "Запустить все"
        Button(
            onClick = onRunAll,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !questionEmpty,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("▶️ Запустить все эксперименты")
        }

        // Индивидуальные кнопки для каждой температуры
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            temperatures.forEach { temp ->
                Button(
                    onClick = { onRunSingle(temp) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && !questionEmpty,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getTemperatureColor(temp).copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        "T=$temp",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TemperatureResultCard(
    temperature: Double,
    state: ExperimentState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = getTemperatureColor(temperature).copy(alpha = 0.1f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 2.dp,
            brush = androidx.compose.ui.graphics.SolidColor(getTemperatureColor(temperature))
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок с температурой
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Температура: $temperature",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = getTemperatureColor(temperature)
                )

                Text(
                    getTemperatureEmoji(temperature),
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            // Содержимое в зависимости от состояния
            when (state) {
                is ExperimentState.Idle -> {
                    Text(
                        "Нажмите кнопку запуска для начала эксперимента",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is ExperimentState.Loading -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Выполнение эксперимента... (${state.progress}/${state.total})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { state.progress.toFloat() / state.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is ExperimentState.Success -> {
                    SuccessContent(result = state.result)
                }

                is ExperimentState.Error -> {
                    Text(
                        "Ошибка: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun SuccessContent(result: TemperatureResult) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Метрики
        Text(
            "Метрики:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        MetricsGrid(metrics = result.metrics)

        // Первый ответ (пример)
        Text(
            "Пример ответа:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                result.responses.firstOrNull() ?: "",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Рекомендации
        result.recommendation?.let { recommendation ->
            RecommendationSection(recommendation = recommendation)
        }
    }
}

@Composable
fun MetricsGrid(metrics: TemperatureMetrics) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricChip(
                label = "Self-BLEU",
                value = "%.2f".format(metrics.selfBleu),
                modifier = Modifier.weight(1f)
            )
            MetricChip(
                label = "Семант. согласованность",
                value = "%.2f".format(metrics.semanticConsistency),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricChip(
                label = "Слов (сред.)",
                value = "%.0f".format(metrics.avgWordCount),
                modifier = Modifier.weight(1f)
            )
            MetricChip(
                label = "Уник. слов",
                value = "%.0f".format(metrics.avgUniqueWords),
                modifier = Modifier.weight(1f)
            )
        }

        MetricChip(
            label = "Структ. разнообразие",
            value = "%.2f".format(metrics.responseVariability.structuralDiversity),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun RecommendationSection(recommendation: TemperatureRecommendation) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "📊 Анализ и рекомендации:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    recommendation.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Text(
                    "✅ Подходит для:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                recommendation.bestFor.forEach { item ->
                    Text(
                        "• $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Text(
                    "❌ Избегать для:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                recommendation.avoidFor.forEach { item ->
                    Text(
                        "• $item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ComparisonTable(results: List<TemperatureResult>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "📈 Сравнительная таблица",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Заголовок таблицы
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Метрика",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                results.forEach { result ->
                    Text(
                        "T=${result.temperature}",
                        modifier = Modifier.weight(0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = getTemperatureColor(result.temperature)
                    )
                }
            }

            Divider()

            // Строки с метриками
            ComparisonRow("Self-BLEU", results) { it.metrics.selfBleu }
            ComparisonRow("Согласованность", results) { it.metrics.semanticConsistency }
            ComparisonRow("Слов (сред.)", results) { it.metrics.avgWordCount }
            ComparisonRow("Уник. слов", results) { it.metrics.avgUniqueWords }
            ComparisonRow("Разнообразие", results) { it.metrics.responseVariability.structuralDiversity }
        }
    }
}

@Composable
fun ComparisonRow(
    label: String,
    results: List<TemperatureResult>,
    getValue: (TemperatureResult) -> Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
        results.forEach { result ->
            Text(
                "%.2f".format(getValue(result)),
                modifier = Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Вспомогательные функции
fun getTemperatureColor(temperature: Double): Color {
    return when {
        temperature <= 0.1 -> Color(0xFF2196F3) // Синий
        temperature <= 0.8 -> Color(0xFFFFC107) // Желтый
        else -> Color(0xFFF44336) // Красный
    }
}

fun getTemperatureEmoji(temperature: Double): String {
    return when {
        temperature <= 0.1 -> "❄️"
        temperature <= 0.8 -> "🌤️"
        else -> "🔥"
    }
}
