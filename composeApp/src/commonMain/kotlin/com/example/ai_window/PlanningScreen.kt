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
import com.example.ai_window.model.ChatMessage
import com.example.ai_window.model.ChatState

@Composable
fun PlanningScreen(viewModel: PlanningViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val sectionsCompleted by viewModel.sectionsCompleted.collectAsStateWithLifecycle()
    val questionsAsked by viewModel.questionsAsked.collectAsStateWithLifecycle()
    val isSpecificationComplete by viewModel.isSpecificationComplete.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-start session on first composition
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            viewModel.startSession()
        }
    }

    // Auto-scroll to last message
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
        // Header with progress
        TopAppBar(
            title = {
                if (!isSpecificationComplete) {
                    Text("📋 Сбор требований")
                } else {
                    Text("✅ Техническое задание готово")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            actions = {
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { viewModel.resetSession() }) {
                        Text("🔄")
                    }
                }
            }
        )

        // Progress indicator - indeterminate while gathering requirements
        if (!isSpecificationComplete && chatState == ChatState.LOADING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Messages area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            state = listState
        ) {
            items(messages) { message ->
                PlanningMessageBubble(message)
            }

            // Loading indicator
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

        // Error message
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

        // Input field (disabled when spec is complete)
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
                    placeholder = {
                        Text(
                            if (isSpecificationComplete)
                                "ТЗ готово. Нажмите 🔄 для нового проекта"
                            else
                                "Ответьте на вопрос..."
                        )
                    },
                    enabled = chatState != ChatState.LOADING && !isSpecificationComplete,
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
                    enabled = inputText.isNotBlank() && chatState != ChatState.LOADING && !isSpecificationComplete
                ) {
                    Text("➤")
                }
            }
        }
    }
}

// Planning-specific message bubble without metadata display
@Composable
fun PlanningMessageBubble(message: ChatMessage) {
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

            // Show parse warning if present (but no metadata badges)
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