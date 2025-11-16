package com.example.ai_window.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai_window.CompressionComparisonViewModel
import com.example.ai_window.model.ChatMessage
import com.example.ai_window.model.ChatState
import com.example.ai_window.model.CompressionStats

/**
 * Screen for comparing chat with and without history compression.
 * Day 8: Side-by-side comparison with metrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionComparisonScreen(viewModel: CompressionComparisonViewModel) {
    val messagesWithCompression by viewModel.messagesWithCompression.collectAsStateWithLifecycle()
    val messagesWithoutCompression by viewModel.messagesWithoutCompression.collectAsStateWithLifecycle()
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🗜️ Сжатие истории диалога") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Text("🗑️")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Metrics table
            MetricsTable(stats = stats)

            // Side-by-side chats
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Chat WITH compression
                ChatColumn(
                    title = "Со сжатием",
                    messages = messagesWithCompression,
                    isLoading = chatState == ChatState.LOADING,
                    modifier = Modifier.weight(1f)
                )

                // Chat WITHOUT compression
                ChatColumn(
                    title = "Без сжатия",
                    messages = messagesWithoutCompression,
                    isLoading = chatState == ChatState.LOADING,
                    modifier = Modifier.weight(1f)
                )
            }

            // Shared input field
            MessageInputField(
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                isLoading = chatState == ChatState.LOADING
            )

            // Error display
            errorMessage?.let { error ->
                Text(
                    text = "❌ Ошибка: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricsTable(stats: CompressionStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Метрики сравнения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Table header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Метрика", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Со сжатием", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Без сжатия", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Разница", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Messages count
            MetricRow(
                label = "Сообщений в истории",
                withCompression = stats.messagesWithCompression.toString(),
                withoutCompression = stats.messagesWithoutCompression.toString(),
                difference = "${stats.messagesWithCompression - stats.messagesWithoutCompression}"
            )

            // Summaries created
            MetricRow(
                label = "Создано summaries",
                withCompression = stats.summariesCreated.toString(),
                withoutCompression = "-",
                difference = "-"
            )

            // Latest input tokens
            if (stats.latestInputTokensWithCompression > 0) {
                MetricRow(
                    label = "Контекст последнего запроса",
                    withCompression = stats.latestInputTokensWithCompression.toString(),
                    withoutCompression = stats.latestInputTokensWithoutCompression.toString(),
                    difference = "${stats.latestInputTokensWithCompression - stats.latestInputTokensWithoutCompression}"
                )
            }

            // Total input tokens (cumulative API consumption)
            if (stats.totalInputTokensWithCompression > 0) {
                MetricRow(
                    label = "Суммарное потребление API",
                    withCompression = stats.totalInputTokensWithCompression.toString(),
                    withoutCompression = stats.totalInputTokensWithoutCompression.toString(),
                    difference = stats.formatTokenSavings()
                )

                // Average context size per request
                MetricRow(
                    label = "Средний размер запроса",
                    withCompression = stats.avgContextSizeWithCompression.toString(),
                    withoutCompression = stats.avgContextSizeWithoutCompression.toString(),
                    difference = "${stats.avgContextSizeWithCompression - stats.avgContextSizeWithoutCompression}"
                )
            }

            // Response times
            if (stats.latestResponseTimeWithCompression > 0) {
                MetricRow(
                    label = "Время ответа (последний, мс)",
                    withCompression = stats.latestResponseTimeWithCompression.toString(),
                    withoutCompression = stats.latestResponseTimeWithoutCompression.toString(),
                    difference = "${stats.latestResponseTimeWithCompression - stats.latestResponseTimeWithoutCompression}"
                )
            }

            // Explanations section
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ℹ️ Пояснения",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = "• Оценочные токены (~X под сообщениями) - размер только текста",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Text(
                        text = "• Контекст запроса - весь контекст отправленный в API (промпт + история + сообщение)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Text(
                        text = "• Суммарное потребление - сумма всех запросов к API (показывает реальную стоимость)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quality assessment
            if (stats.totalInputTokensWithCompression > 0) {
                Text(
                    text = "💡 Эффективность: ${stats.getQualityAssessment()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    withCompression: String,
    withoutCompression: String,
    difference: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(withCompression, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(withoutCompression, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(
            difference,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                difference.startsWith("↓") || difference.startsWith("-") -> Color(0xFF4CAF50) // Green
                difference.startsWith("↑") -> Color(0xFFF44336) // Red
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun ChatColumn(
    title: String,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )

            Divider()

            // Messages
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // Auto-scroll to bottom
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else if (message.isSummary) {
                    MaterialTheme.colorScheme.tertiaryContainer // Different color for summary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (message.isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else if (message.isSummary) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Summary indicator
                if (message.isSummary) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "📝 Summary (${message.summarizedCount} сообщений)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Title (if present)
                message.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Message text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Parse warning (if present)
                message.parseWarning?.let { warning ->
                    Text(
                        text = "⚠️ $warning",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Token estimate (if present)
                message.estimatedTokens?.let { tokens ->
                    // Show different info for user vs AI messages
                    val tokenText = if (message.isUser) {
                        "~$tokens токенов (оценка)"
                    } else {
                        // For AI messages, show metadata tokens if available
                        message.metadata?.tokenUsage?.let { usage ->
                            "Вход: ${usage.inputTextTokens} | Выход: ${usage.completionTokens}"
                        } ?: "~$tokens токенов (оценка)"
                    }

                    Text(
                        text = tokenText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageInputField(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Введите сообщение...") },
            enabled = !isLoading,
            singleLine = false,
            maxLines = 3
        )

        Button(
            onClick = onSend,
            enabled = !isLoading && messageText.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Отправить")
            }
        }
    }
}
