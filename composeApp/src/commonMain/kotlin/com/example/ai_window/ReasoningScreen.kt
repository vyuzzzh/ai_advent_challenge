@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ai_window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai_window.service.ReasoningPrompts

@Composable
fun ReasoningScreen(viewModel: ReasoningViewModel) {
    // State
    val directResult by viewModel.directResult.collectAsStateWithLifecycle()
    val stepByStepResult by viewModel.stepByStepResult.collectAsStateWithLifecycle()
    val aiPromptResult by viewModel.aiPromptResult.collectAsStateWithLifecycle()
    val expertsPanelResult by viewModel.expertsPanelResult.collectAsStateWithLifecycle()
    val expertMessages by viewModel.expertMessages.collectAsStateWithLifecycle()

    val directLoading by viewModel.directLoading.collectAsStateWithLifecycle()
    val stepByStepLoading by viewModel.stepByStepLoading.collectAsStateWithLifecycle()
    val aiPromptLoading by viewModel.aiPromptLoading.collectAsStateWithLifecycle()
    val expertsPanelLoading by viewModel.expertsPanelLoading.collectAsStateWithLifecycle()

    // Tab state
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        TopAppBar(
            title = { Text("🧠 Сравнение подходов к рассуждению") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            actions = {
                // Run all button
                TextButton(
                    onClick = { viewModel.runAllApproaches() },
                    enabled = !directLoading && !stepByStepLoading && !aiPromptLoading && !expertsPanelLoading
                ) {
                    Text("Запустить все")
                }

                // Reset button
                IconButton(onClick = { viewModel.resetAllResults() }) {
                    Text("🔄")
                }
            }
        )

        // Business case card (always visible)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "📊 Бизнес-кейс:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ReasoningPrompts.BUSINESS_CASE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Прямой") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Пошаговый") }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                text = { Text("AI-промпт") }
            )
            Tab(
                selected = selectedTabIndex == 3,
                onClick = { selectedTabIndex = 3 },
                text = { Text("Эксперты") }
            )
        }

        // Tab content
        when (selectedTabIndex) {
            0 -> ReasoningTabContent(
                title = "Прямой подход",
                description = "Простой вопрос без дополнительных инструкций",
                result = directResult,
                isLoading = directLoading,
                onRun = { viewModel.runDirectApproach() }
            )
            1 -> ReasoningTabContent(
                title = "Пошаговое рассуждение",
                description = "Явные инструкции для пошагового решения",
                result = stepByStepResult,
                isLoading = stepByStepLoading,
                onRun = { viewModel.runStepByStepApproach() }
            )
            2 -> ReasoningTabContent(
                title = "AI-сгенерированный промпт",
                description = "AI сначала создает промпт, затем решает задачу",
                result = aiPromptResult,
                isLoading = aiPromptLoading,
                onRun = { viewModel.runAIPromptApproach() }
            )
            3 -> ExpertsPanelTabContent(
                title = "Панель экспертов",
                description = "Менеджер оркестрирует работу 3 экспертов",
                result = expertsPanelResult,
                expertMessages = expertMessages,
                isLoading = expertsPanelLoading,
                onRun = { viewModel.runExpertsPanelApproach() }
            )
        }
    }
}

@Composable
fun ReasoningTabContent(
    title: String,
    description: String,
    result: ReasoningResult?,
    isLoading: Boolean,
    onRun: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Approach description
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Run button
        Button(
            onClick = onRun,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Выполняется..." else "▶ Запустить")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Result display
        result?.let { reasoningResult ->
            when (reasoningResult) {
                is ReasoningResult.Success -> {
                    // Metrics card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            MetricBadge(
                                label = "Слов",
                                value = reasoningResult.wordCount.toString()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Warning if present
                    reasoningResult.warning?.let { warning ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "⚠️ $warning",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Response content
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Title
                            reasoningResult.response.response.title.takeIf { it.isNotBlank() }?.let { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Content
                            Text(
                                text = reasoningResult.response.response.content,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is ReasoningResult.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "❌ Ошибка: ${reasoningResult.message}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                is ReasoningResult.Streaming -> {
                    // Streaming is handled separately in ExpertsPanelTabContent
                    // This branch should not be reached for other tabs
                }
            }
        }
    }
}

@Composable
fun ExpertsPanelTabContent(
    title: String,
    description: String,
    result: ReasoningResult?,
    expertMessages: List<ExpertMessage>,
    isLoading: Boolean,
    onRun: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(expertMessages.size) {
        if (expertMessages.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Approach description
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Run button
        Button(
            onClick = onRun,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Выполняется..." else "▶ Запустить")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Streaming messages (shown during and after execution)
        if (expertMessages.isNotEmpty()) {
            expertMessages.forEach { message ->
                ExpertMessageBubble(message)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Loading indicator (only when no messages yet)
        if (isLoading && expertMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Final result metrics (after completion)
        if (!isLoading && result is ReasoningResult.Success) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricBadge(
                        label = "Слов",
                        value = result.wordCount.toString()
                    )
                    MetricBadge(
                        label = "Сообщений",
                        value = expertMessages.size.toString()
                    )
                }
            }
        }
    }
}

@Composable
fun ExpertMessageBubble(message: ExpertMessage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when (message.role) {
            ExpertRole.MANAGER -> MaterialTheme.colorScheme.primaryContainer
            ExpertRole.HR_EXPERT -> MaterialTheme.colorScheme.secondaryContainer
            ExpertRole.IT_EXPERT -> MaterialTheme.colorScheme.tertiaryContainer
            ExpertRole.BUSINESS_EXPERT -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Role header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.role.emoji,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.role.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MetricBadge(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
