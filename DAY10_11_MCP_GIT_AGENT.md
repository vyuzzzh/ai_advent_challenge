# Day 10-11: MCP Integration & Native Git Agent

## Обзор

В рамках Day 10-11 реализована **нативная интеграция MCP tools через основной чат**:
- Day 10: SimpleMcpServer, REST API, McpScreen для отображения tools
- Day 11: GitToolExecutor, IntentDetector, интеграция в ChatViewModel

Пользователь пишет естественный запрос → система определяет намерение → вызывает MCP tool → отображает результат в чате.

## Как работает

```
Пользователь: "покажи последние коммиты"
    ↓
IntentDetector (regex matching)
    ↓
ToolCall { tool: "git-log", params: {count: "5"} }
    ↓
POST /api/tools/execute
    ↓
GitToolExecutor → ProcessBuilder → git log
    ↓
ToolResult { success: true, output: "..." }
    ↓
ChatScreen (форматированный вывод)
```

## Примеры команд

В основном чате (вкладка "💬 Чат"):

| Запрос пользователя | MCP Tool | Результат |
|---------------------|----------|-----------|
| "покажи последние коммиты" | git-log | История коммитов |
| "что изменилось" | git-status | Измененные файлы |
| "какие ветки есть" | git-branches | Список веток |
| "покажи diff" | git-diff | Различия в файлах |
| любой другой вопрос | - | Yandex GPT |

## Архитектура

### Server-side (Ktor)

**SimpleMcpServer** (`server/src/main/kotlin/com/example/ai_window/mcp/SimpleMcpServer.kt`):
- Регистрирует 4 Git MCP tools
- Предоставляет информацию через REST API

**GitToolExecutor** (`server/src/main/kotlin/com/example/ai_window/tools/GitToolExecutor.kt`):
- Выполняет реальные git команды через ProcessBuilder
- Поддерживает: git-log, git-status, git-diff, git-branches
- Timeout 30 секунд на команду

**REST API endpoints**:
```kotlin
GET  /api/mcp/info         // Полная информация о MCP сервере
GET  /api/mcp/tools        // Список tools (4 штуки)
POST /api/tools/execute    // Выполнение tool
```

### Shared (Kotlin Multiplatform)

**AgentModels.kt** (`shared/src/commonMain/kotlin/com/example/ai_window/model/AgentModels.kt`):
```kotlin
enum class AgentState { IDLE, THINKING, EXECUTING_TOOL, FORMATTING_RESPONSE }

data class ToolCall(val tool: String, val params: Map<String, String>)
data class ToolResult(val success: Boolean, val output: String, val error: String?, val executionTime: Long?)
data class ToolExecutionRequest(val tool: String, val params: Map<String, String>)
```

**IntentDetector.kt** (`shared/src/commonMain/kotlin/com/example/ai_window/service/IntentDetector.kt`):
- Распознает намерения по regex паттернам
- Извлекает параметры (count, file) из текста
- Поддерживает русские числительные ("пять" → 5)

**AgentService.kt** (`shared/src/commonMain/kotlin/com/example/ai_window/service/AgentService.kt`):
- HTTP клиент для вызова tools
- Форматирование результатов с emoji

### Client (Compose Multiplatform)

**ChatViewModel.kt** - интегрирован MCP:
```kotlin
fun sendMessage(text: String) {
    val detection = intentDetector.detect(text)

    when (detection) {
        is ToolDetected -> {
            // Выполняем MCP tool
            val result = agentService.executeTool(detection.toolCall)
            // Отображаем результат
        }
        is NoToolNeeded -> {
            // Отправляем в Yandex GPT
            yandexGptService.sendMessage(text, ...)
        }
    }
}
```

**McpScreen.kt** - UI для просмотра доступных tools и resources

## Запуск и тестирование

### 1. Запуск сервера

```bash
./gradlew :server:run
```

Логи:
```
[MCP Server] Registered 4 tools:
  - git-log: Show commit history...
  - git-status: Show repository status...
  - git-diff: Show file differences...
  - git-branches: List all branches...
[GitToolExecutor] Initialized with repo path: /path/to/repo
```

### 2. Запуск приложения

```bash
./gradlew :composeApp:run
```

### 3. Тестирование через curl

```bash
# Git log
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"git-log","params":{"count":"5"}}'

# Git status
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"git-status","params":{}}'

# Git branches
curl -X POST http://localhost:8080/api/tools/execute \
  -H "Content-Type: application/json" \
  -d '{"tool":"git-branches","params":{}}'
```

### 4. Тестирование в UI

1. Открыть вкладку "💬 Чат"
2. Ввести: "покажи последние коммиты"
3. Увидеть результат git-log с форматированием

## Структура файлов

```
server/
  src/main/kotlin/com/example/ai_window/
    Application.kt                    # REST endpoints
    mcp/
      SimpleMcpServer.kt              # MCP tools registry
    tools/
      GitToolExecutor.kt              # Git command executor

shared/
  src/commonMain/kotlin/com/example/ai_window/
    model/
      AgentModels.kt                  # ToolCall, ToolResult, AgentState
      McpModels.kt                    # McpTool, McpResource, McpServerInfo
    service/
      AgentService.kt                 # HTTP client for tools
      IntentDetector.kt               # Intent recognition
      McpService.kt                   # MCP info client

composeApp/
  src/commonMain/kotlin/com/example/ai_window/
    ChatViewModel.kt                  # Integrated MCP tools
    McpViewModel.kt                   # MCP screen state
    screens/
      McpScreen.kt                    # Tools/resources display
```

## Ключевые особенности

### Intent Detection

Паттерны для распознавания:
```kotlin
// git-log
"покажи.*коммит", "история.*коммит", "git log"

// git-status
"что.*изменил", "статус.*репозитор", "git status"

// git-diff
"покажи.*diff", "различия.*код"

// git-branches
"какие.*ветк", "список.*веток", "branch"
```

### Форматирование результатов

```kotlin
fun formatToolResult(toolCall: ToolCall, result: ToolResult): String {
    val header = when (toolCall.tool) {
        "git-log" -> "📜 История коммитов"
        "git-status" -> "📊 Статус репозитория"
        "git-diff" -> "📝 Различия в файлах"
        "git-branches" -> "🌿 Список веток"
    }
    // ...
}
```

### Безопасность

- Git команды выполняются в указанном репозитории (GIT_REPO_PATH или user.dir)
- Timeout 30 секунд на команду
- Только read-only операции (log, status, diff, branch)

## Дальнейшее развитие

### Расширение tools

1. **git-show** - детали конкретного коммита
2. **git-blame** - авторство строк файла
3. **git-stash** - управление stash

### AI-based Intent Detection

Текущий подход использует regex. Для улучшения:
- Fallback на Yandex GPT для неопределенных запросов
- Извлечение параметров через AI

### Интеграция с другими API

- Yandex.Tracker (задачи, issues)
- GitHub API (PRs, issues)
- CI/CD системы

## Зависимости

В `gradle/libs.versions.toml`:
```toml
[versions]
mcp-kotlin-sdk = "0.6.0"

[libraries]
mcp-kotlin-sdk = { module = "io.modelcontextprotocol:kotlin-sdk", version.ref = "mcp-kotlin-sdk" }
```

---

**Дата**: 2025-11-19
**Статус**: ✅ Completed
**Ветка**: day_10

### Day 10
- SimpleMcpServer с регистрацией tools
- REST API для MCP info
- McpScreen для отображения tools

### Day 11
- GitToolExecutor для выполнения git команд
- IntentDetector для распознавания намерений
- Интеграция в основной ChatViewModel
- Нативный UX: запрос → tool → результат в чате
