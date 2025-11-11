@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ai_window

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai_window.model.ChatMessage
import com.example.ai_window.model.ChatState
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        // State for navigation between modes
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }

        // ViewModels
        val chatViewModel: ChatViewModel = viewModel {
            ChatViewModel(
                apiKey = BuildConfig.YANDEX_API_KEY,
                folderId = BuildConfig.YANDEX_FOLDER_ID
            )
        }

        val planningViewModel: PlanningViewModel = viewModel {
            PlanningViewModel(
                apiKey = BuildConfig.YANDEX_API_KEY,
                folderId = BuildConfig.YANDEX_FOLDER_ID
            )
        }

        val reasoningViewModel: ReasoningViewModel = viewModel {
            ReasoningViewModel(
                apiKey = BuildConfig.YANDEX_API_KEY,
                folderId = BuildConfig.YANDEX_FOLDER_ID
            )
        }

        val temperatureViewModel: TemperatureViewModel = viewModel {
            TemperatureViewModel(
                apiKey = BuildConfig.YANDEX_API_KEY,
                folderId = BuildConfig.YANDEX_FOLDER_ID
            )
        }

        val modelComparisonViewModel: ModelComparisonViewModel = viewModel {
            ModelComparisonViewModel(
                hfToken = BuildConfig.HUGGINGFACE_API_TOKEN
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Navigation tabs
            NavigationBar(
                selectedScreen = currentScreen,
                onScreenSelected = { currentScreen = it }
            )

            // Screen content
            when (currentScreen) {
                Screen.Chat -> ChatScreen(chatViewModel)
                Screen.Planning -> PlanningScreen(planningViewModel)
                Screen.Reasoning -> ReasoningScreen(reasoningViewModel)
                Screen.Temperature -> TemperatureScreen(temperatureViewModel)
                Screen.ModelComparison -> ModelComparisonScreen(modelComparisonViewModel)
            }
        }
    }
}

// Screen enum
enum class Screen(val displayName: String, val icon: String) {
    Chat("Чат", "💬"),
    Planning("ТЗ", "📋"),
    Reasoning("Рассуждение", "🧠"),
    Temperature("Температура", "🌡️"),
    ModelComparison("Модели HF", "🤖")
}

@Composable
fun NavigationBar(
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Screen.entries.forEach { screen ->
                NavigationTab(
                    screen = screen,
                    isSelected = screen == selectedScreen,
                    onClick = { onScreenSelected(screen) }
                )
            }
        }
    }
}

@Composable
fun RowScope.NavigationTab(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text("${screen.icon} ${screen.displayName}")
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Автоматическая прокрутка к последнему сообщению
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Заголовок
        TopAppBar(
            title = { Text("AI Чат (Yandex GPT)") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            actions = {
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Text("🗑️")
                    }
                }
            }
        )

        // Область сообщений
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            state = listState
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }

            // Индикатор загрузки
            if (chatState == ChatState.LOADING) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // Сообщение об ошибке
        errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            }
        }

        // Поле ввода
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Введите сообщение...") },
                    enabled = chatState != ChatState.LOADING,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && chatState != ChatState.LOADING
                ) {
                    Text("➤")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Message content
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Title (жирным шрифтом)
                    message.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (message.isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Content
                    Text(
                        text = message.text,
                        color = if (message.isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }

            // NEW: Display metadata for AI messages
            message.metadata?.let { metadata ->
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, start = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Confidence badge
                    ConfidenceBadge(metadata.confidence)

                    // Category chip
                    CategoryChip(metadata.category)
                }
            }

            // NEW: Show parse warning if present
            if (message.parseWarning != null) {
                Text(
                    text = "⚠️ ${message.parseWarning}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ConfidenceBadge(confidence: Double) {
    val color = when {
        confidence >= 0.7 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)  // Green
        confidence >= 0.4 -> androidx.compose.ui.graphics.Color(0xFFFFA500)  // Orange
        else -> androidx.compose.ui.graphics.Color(0xFFF44336)  // Red
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "Confidence: ${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CategoryChip(category: String) {
    val categoryIcon = when (category.lowercase()) {
        "factual" -> "📚"
        "opinion" -> "💭"
        "suggestion" -> "💡"
        "error" -> "❌"
        "general" -> "💬"
        "plaintext_fallback" -> "📝"
        "manual_extraction" -> "🔧"
        else -> "❓"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "$categoryIcon $category",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}