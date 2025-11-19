# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Обзор проекта

Kotlin Multiplatform приложение для взаимодействия с Yandex GPT API через прокси-сервер. Поддерживает Android, iOS, Desktop (JVM), Web (JS/WASM) с единой кодовой базой для UI и бизнес-логики.

## Архитектура проекта

### Модульная структура
- **composeApp** - UI слой (Compose Multiplatform): screens, ViewModels, UI компоненты
- **shared** - бизнес-логика: модели данных, сервисы, expect/actual реализации для платформ
- **server** - Ktor прокси-сервер для безопасной работы с Yandex GPT API (скрывает API ключи от клиентов)
- **iosApp** - iOS точка входа с интеграцией Kotlin framework

### Ключевые архитектурные решения

**Прокси-паттерн для API**:
- Клиентские приложения не содержат API ключи
- Запросы идут через Ktor сервер (`server/src/main/kotlin/com/example/ai_window/Application.kt`)
- Сервер добавляет авторизацию и проксирует запросы к Yandex API

**Платформо-специфичный код (expect/actual)**:
- Общий интерфейс: `shared/src/commonMain/kotlin/com/example/ai_window/Platform.kt`
- Реализации: `shared/src/{androidMain,iosMain,jvmMain,jsMain,wasmJsMain}/kotlin/com/example/ai_window/Platform.*.kt`

**Два подхода к структурированным ответам**:
1. Native JSON Schema (`useNativeJsonSchema = true`): использует встроенную поддержку JSON Schema в Yandex API
2. Prompt-based (`useNativeJsonSchema = false`): инструкции формата в промпте

Настраивается в `shared/src/commonMain/kotlin/com/example/ai_window/service/YandexGptService.kt`

**ViewModel архитектура**:
- `ChatViewModel`, `PlanningViewModel`, `ReasoningViewModel` в `composeApp/src/commonMain/kotlin`
- Управление состоянием через Compose State
- Lifecycle-aware компоненты через androidx.lifecycle

## Команды разработки

### Запуск приложения
```bash
# Desktop (рекомендуется для быстрой разработки)
./gradlew :composeApp:run

# Web WASM (современные браузеры, быстрее чем JS)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web JS (поддержка старых браузеров)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Android APK (debug сборка)
./gradlew :composeApp:assembleDebug

# iOS (требует Xcode на macOS)
open iosApp/iosApp.xcodeproj

# Ktor сервер (необходим для работы приложений с API)
./gradlew :server:run
```

### Тестирование
```bash
# Все тесты всех модулей
./gradlew test

# Тесты конкретного модуля
./gradlew :shared:test
./gradlew :composeApp:test
./gradlew :server:test

# Тесты с подробным выводом
./gradlew test --info

# Тесты для конкретной платформы
./gradlew :shared:jvmTest        # JVM тесты
./gradlew :shared:androidUnitTest # Android unit тесты
./gradlew :shared:iosSimulatorArm64Test # iOS симулятор тесты
```

### Сборка и очистка
```bash
# Полная очистка (удаляет build/, .gradle кеш)
./gradlew clean

# Пересборка зависимостей
./gradlew --refresh-dependencies

# Список доступных задач
./gradlew tasks
./gradlew :composeApp:tasks
```

## Конфигурация и версии

Зависимости управляются через `gradle/libs.versions.toml`:
- **Kotlin**: 2.2.20
- **Compose Multiplatform**: 1.9.1
- **Ktor**: 3.3.1
- **Kotlinx Coroutines**: 1.10.2
- **Kotlinx Serialization**: 1.8.0
- **Kotlinx DateTime**: 0.6.1
- **SQLDelight**: 2.0.2
- **AndroidX Lifecycle**: 2.9.5
- **Android SDK**: min 24 (Android 7.0), target 36
- **JVM Target**: 11

**Настройки производительности Gradle** (`gradle.properties`):
- Configuration cache включен для ускорения сборки
- Build cache активен
- JVM heap для Gradle: 4GB (можно увеличить при необходимости)
- Kotlin daemon heap: 3GB

## Особенности разработки

### Работа с Yandex GPT API

**Архитектура запросов**:
1. UI (ChatViewModel/PlanningViewModel/ReasoningViewModel) → YandexGptService
2. YandexGptService → Ktor Server (localhost:8080)
3. Ktor Server → Yandex API (с авторизацией)

**Парсинг ответов** (`shared/src/commonMain/kotlin/com/example/ai_window/model/ResponseSchema.kt`):
- `ParseResult.Success` - валидный JSON ответ
- `ParseResult.Partial` - частично валидный (с fallback значениями)
- `ParseResult.Error` - невалидный ответ с сырым текстом

### Ключевые сервисы

**YandexGptService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/YandexGptService.kt`):
- Взаимодействие с Yandex GPT API через прокси-сервер
- Два режима: с native JSON Schema (`useNativeJsonSchema = true`) и с промпт-инструкциями
- Модель: `yandexgpt-lite/latest`
- Поддержка структурированных ответов с полями `response.title`, `response.content`, `response.metadata`

**HuggingFaceService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/HuggingFaceService.kt`):
- Работа с HuggingFace Inference API через прокси
- Поддержка Chat Completion формата (OpenAI-совместимый)
- Используется в ModelComparisonViewModel для сравнения моделей

**HistoryCompressionService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/HistoryCompressionService.kt`):
- Автоматическое сжатие длинных диалогов для экономии токенов
- Стратегия: каждые N сообщений (по умолчанию `compressionThreshold = 10`), старые сообщения заменяются кратким резюме
- Сохраняет последние `keepRecentCount` сообщений (по умолчанию 5) для контекста

**Алгоритм сжатия**:
1. Проверка: `shouldCompress()` - нужно ли сжатие (>= threshold сообщений)
2. Разделение истории: старые сообщения → суммаризация, свежие → сохранение
3. YandexGPT создает краткое резюме (3-5 предложений) с `temperature = 0.3`
4. Замена: старые сообщения заменяются одним `ChatMessage` с `isSummary = true`

**TemperatureExperimentService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/TemperatureExperimentService.kt`):
- Сервис для экспериментов с параметром temperature (0.1, 0.6, 0.9)
- Поддержка параллельных и последовательных запросов
- Метрики: время выполнения, длина ответа, разнообразие

**ReasoningComparisonService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/ReasoningComparisonService.kt`):
- Сравнение режимов рассуждения (с/без chain-of-thought)
- Использует специализированные промпты из `ReasoningPrompts.kt`

**ModelComparisonService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/ModelComparisonService.kt`):
- Сравнение моделей HuggingFace (Llama 3.2, FLAN-T5)
- Метрики: время ответа, качество, длина ответа

**RequirementsGatheringService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/RequirementsGatheringService.kt`):
- Сбор требований через структурированные промпты
- Использует шаблоны из `RequirementsPrompt.kt`

### Внешняя память (External Memory) - Day 9

**SQLDelight persistence** (`shared/src/commonMain/sqldelight/com/example/ai_window/`):
- Multiplatform SQLite база данных для сохранения между запусками
- SQL схема: `ChatMessage.sq`, `ExperimentResult.sq`, `ExpertMessage.sq`
- Автоматическое сохранение всех сообщений чата
- **Важно**: При изменении .sq файлов SQLDelight автоматически регенерирует Kotlin код
- Запустите `./gradlew :shared:generateCommonMainChatDatabaseInterface` для явной регенерации

**DatabaseDriverFactory** (expect/actual pattern):
- Android: `AndroidSqliteDriver` - хранит в app-specific storage
- iOS: `NativeSqliteDriver` - хранит в app sandbox
- JVM/Desktop: `JdbcSqliteDriver` - хранит в `~/.ai_window/chat.db`
- JS/WASM: Не поддерживается (база отключена)
- **Примечание**: wasmJs временно отключен в shared модуле (Day 9) из-за проблем с SQLDelight

**Repository pattern** (`shared/src/commonMain/kotlin/com/example/ai_window/database/`):
- `ChatRepository` - CRUD операции для сообщений чата
- `ExperimentRepository` - сохранение результатов экспериментов
- `Mappers.kt` - конвертация DB ↔ Domain моделей

**Автосохранение в ChatViewModel**:
- `init` блок загружает сохраненные сообщения при старте
- После каждого добавления сообщения автоматически сохраняет в БД
- `clearChat()` также очищает базу данных

**Расположение БД файлов**:
- Android: `/data/data/com.example.ai_window/databases/chat.db`
- iOS: App Sandbox Documents
- Desktop: `~/.ai_window/chat.db`

**Миграции базы данных**:
- При изменении схемы создавайте миграции в `shared/src/commonMain/sqldelight/migrations/`
- Формат файла: `1.sqm`, `2.sqm`, и т.д.
- SQLDelight автоматически применяет миграции при обновлении версии схемы
- Тестируйте миграции на всех платформах перед релизом

### Сжатие истории диалогов (History Compression)

**Поля ChatMessage для сжатия** (`shared/src/commonMain/kotlin/com/example/ai_window/model/ChatMessage.kt`):
- `isSummary: Boolean` - флаг суммарного сообщения
- `summarizedCount: Int?` - количество замененных сообщений
- `originalIds: List<String>?` - ID оригинальных сообщений
- `estimatedTokens: Int?` - оценка токенов (кеш)

**TokenUtils** (`shared/src/commonMain/kotlin/com/example/ai_window/util/TokenUtils.kt`):
- Эвристическая оценка токенов: ~1.3 токена на слово для русского текста
- `estimateTokens(text)` - оценка для одного текста
- `estimateTokensForHistory(messages)` - суммарная оценка для истории
- Используется для метрик и принятия решений о сжатии

**CompressionStats** (`shared/src/commonMain/kotlin/com/example/ai_window/model/CompressionStats.kt`):
- Метрики сравнения чата с/без сжатия: количество сообщений, input/output токены, время ответов
- `tokenSavingsPercent` - процент экономии токенов
- `compressionOverhead` - время на суммаризацию
- `getQualityAssessment()` - оценка эффективности (>50% - отлично, >30% - хорошо, и т.д.)

### Compose Multiplatform UI

**Структура кода**:
- Общий UI: `composeApp/src/commonMain/kotlin`
- Платформо-специфичный: `composeApp/src/{androidMain,iosMain,jvmMain,jsMain,wasmJsMain}`
- Material Design 3 по умолчанию

**Screens и ViewModels** (`composeApp/src/commonMain/kotlin/com/example/ai_window/`):
- `App.kt` - главный Composable с навигацией (tabs для всех режимов)
- `screens/` - отдельная папка для UI экранов (CompressionComparisonScreen и др.)
- `ChatViewModel.kt` - базовый чат
- `PlanningViewModel.kt` - планирование задач с prompt engineering
- `ReasoningViewModel.kt` - рассуждения с цепочкой мыслей
- `TemperatureViewModel.kt` - эксперименты с параметром temperature (параллельные/последовательные запросы с разными значениями 0.1, 0.6, 0.9)
- `ModelComparisonViewModel.kt` - сравнение моделей HuggingFace (day_6: Llama 3.2, FLAN-T5 с метриками производительности и качества)
- `CompressionComparisonViewModel.kt` - сравнение чата с/без сжатия истории (day_8: параллельные запросы, метрики экономии токенов)
- `McpViewModel.kt` - управление состоянием MCP интеграции (day_10: отображение MCP tools и resources)

### Ktor Server

**Конфигурация**:
- Порт: 8080 (`shared/src/commonMain/kotlin/com/example/ai_window/Constants.kt`)
- CORS: разрешены все источники (для разработки)
- Логирование: `server/src/main/resources/logback.xml`

**API эндпоинты** (`server/src/main/kotlin/com/example/ai_window/Application.kt`):
- `GET /` - health check
- `POST /api/yandex-gpt` - прокси к Yandex API (headers: `X-API-Key`, `X-Folder-Id`)
- `POST /api/huggingface` - прокси к HuggingFace Inference API (headers: `X-HF-Token`)
  - Использует Chat Completion API: `https://router.huggingface.co/v1/chat/completions`
  - Поддерживает модели: Llama 3.2, FLAN-T5 и другие
  - Возвращает OpenAI-совместимый формат с дополнительным полем `executionTime`
- `GET /api/mcp/info` - MCP server info (day_10: список всех tools, resources, prompts)
- `GET /api/mcp/tools` - упрощенный список MCP tools (day_10: без детального inputSchema)

**Тестирование API через curl**:
```bash
# Health check
curl http://localhost:8080/

# MCP server info
curl http://localhost:8080/api/mcp/info

# MCP tools list
curl http://localhost:8080/api/mcp/tools
```

### MCP Integration (Model Context Protocol)

**Day 10** реализует упрощенную демонстрацию концепции MCP интеграции:

**SimpleMcpServer** (`server/src/main/kotlin/com/example/ai_window/mcp/SimpleMcpServer.kt`):
- Статическая регистрация 4 MCP tools:
  - `analyze-tokens` - анализ токенов (~1.3 токена на слово для русского)
  - `get-chat-history` - получение истории из SQLDelight БД
  - `compress-history` - сжатие истории через суммаризацию
  - `run-temperature-experiment` - эксперименты с temperature (0.1, 0.6, 0.9)
- Регистрация 2 MCP resources:
  - `server://status` - статус и версия MCP сервера
  - `chat://history` - полная история диалогов
- Инициализация в `Application.kt` при старте сервера
- Логи показывают количество зарегистрированных tools и resources

**McpService** (`shared/src/commonMain/kotlin/com/example/ai_window/service/McpService.kt`):
- REST клиент для запросов к `/api/mcp/*` endpoints
- Методы: `getMcpServerInfo()`, `getMcpTools()`
- Использует Ktor HttpClient с JSON serialization

**McpViewModel** (`composeApp/src/commonMain/kotlin/com/example/ai_window/McpViewModel.kt`):
- Управление состоянием: `isLoading`, `mcpServerInfo`, `mcpTools`, `errorMessage`
- Автозагрузка данных при инициализации
- Обработка ошибок и retry логика

**McpScreen** (`composeApp/src/commonMain/kotlin/com/example/ai_window/screens/McpScreen.kt`):
- UI для отображения MCP tools и resources
- ServerInfoCard - информация о сервере
- ToolCard - карточка tool с категорией и параметрами
- ResourceCard - карточка resource с URI и MIME-типом

**Ограничения текущей реализации**:
- ❌ Нет полной интеграции MCP SDK
- ❌ Нет выполнения tools (только отображение)
- ❌ Нет поддержки MCP transport protocols (stdio, SSE)
- ❌ Нет интеграции с Claude Desktop

**Дальнейшее развитие (Day 11+)**:
- Полная интеграция Kotlin MCP SDK
- Реальное выполнение tools с обработчиками
- Stdio transport для Claude Desktop
- Конфигурация `claude_desktop_config.json`

**См. также**: `DAY10_MCP_INTEGRATION.md` для подробной документации

### Добавление новых платформ

**При добавлении expect/actual реализаций**:
1. Объявите `expect` функцию/класс в `shared/src/commonMain`
2. Создайте `actual` реализацию в каждом sourceSet (`androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`)
3. Используйте платформо-специфичные API только в `actual` блоках

**Пример**: `Platform.kt` имеет 5 реализаций для каждой платформы

### Константы и конфигурация

- Все общие константы: `shared/src/commonMain/kotlin/com/example/ai_window/Constants.kt`
- **BuildConfig** (`composeApp/src/commonMain/kotlin/com/example/ai_window/BuildConfig.kt`):
  - `YANDEX_API_KEY` - ключ API для Yandex GPT
  - `YANDEX_FOLDER_ID` - ID каталога Yandex Cloud
  - `HUGGINGFACE_API_TOKEN` - токен для HuggingFace Inference API
  - Передаются в ViewModels при инициализации в `App.kt`
  - **ВАЖНО**: `BuildConfig.kt` находится в `.gitignore`, используйте `BuildConfig.kt.template` как шаблон
  - При клонировании репозитория скопируйте template и заполните своими ключами

### Desktop дистрибутивы

```bash
./gradlew :composeApp:createDistributable  # Создать пакет для текущей ОС
```

Поддерживаемые форматы (настройка в `composeApp/build.gradle.kts`):
- macOS: DMG
- Windows: MSI
- Linux: Deb

## Паттерн инкрементальной разработки (day_X)

Проект развивается по паттерну day-by-day экспериментов:
- **day_1**: Первая реализация AI чата с Yandex GPT
- **day_2**: Структурированные ответы (JSON Schema)
- **day_3**: Prompt engineering для сбора требований
- **day_4**: Режим рассуждений (reasoning/chain-of-thought)
- **day_5**: Эксперименты с temperature
- **day_6**: Model comparison (HuggingFace: Llama 3.2, FLAN-T5), refactor time utils
- **day_7**: Token analysis
- **day_8**: History compression - CompressionComparisonViewModel, HistoryCompressionService, TokenUtils, CompressionStats
- **day_9**: External memory - SQLDelight persistence, ChatRepository, DatabaseDriverFactory, автосохранение истории чатов
- **day_10**: MCP Integration - SimpleMcpServer, REST API endpoints (/api/mcp/*), McpService, McpViewModel, McpScreen для отображения MCP tools/resources

**Принципы разработки**:
- Каждый "день" = отдельная функциональность/эксперимент
- Комментарии в коде помечены day_X для отслеживания
- Новые поля в моделях помечаются как `// Day X: описание`
- Бранчи именуются `day_X` для изоляции экспериментов
- Каждая функциональность мержится через Pull Request после завершения
- Перед началом нового day_X убедитесь, что предыдущий день смержен в main

**Документация**:
Каждый day имеет свой markdown файл с подробным описанием:
- `DAY3_IMPLEMENTATION.md` - Prompt engineering
- `DAY4_REASONING.md` - Chain-of-thought reasoning
- `DAY5_TEMPERATURE.md` - Temperature experiments
- `DAY6_MODEL_COMPARISON.md` - HuggingFace models comparison
- `DAY7_TOKEN_ANALYSIS.md` - Token estimation
- `DAY9_EXTERNAL_MEMORY.md` - SQLDelight persistence
- `DAY10_MCP_INTEGRATION.md` - Model Context Protocol integration (упрощенная демонстрация концепции)

## Важные детали реализации

### Парсинг JSON ответов от LLM

**ResponseParser** (`shared/src/commonMain/kotlin/com/example/ai_window/model/ResponseSchema.kt`):
- Два режима парсинга: строгий (`parseStrict`) для native JSON Schema и толерантный (`parse`) для prompt-based
- Fallback стратегия: пытается извлечь JSON из markdown блоков, текста между фигурными скобками
- Возвращает `ParseResult.Partial` при частичной валидности (отсутствующие поля заполняются значениями по умолчанию)

### Платформо-специфичные утилиты

**TimeUtils** (`shared/src/commonMain/kotlin/com/example/ai_window/util/TimeUtils.kt`):
- `expect fun formatTimestamp(timestamp: Long): String` - форматирование времени для каждой платформы
- Android: `SimpleDateFormat`, iOS: `NSDateFormatter`, JVM: `SimpleDateFormat`, JS/WASM: `Date.toLocaleString()`

**FileExport** (`composeApp/src/commonMain/kotlin/com/example/ai_window/FileExport.kt`):
- `expect fun exportToFile(content: String, filename: String)` - экспорт в файл для каждой платформы
- Android: `ContentResolver` + `MediaStore`, iOS: `UIActivityViewController`, JVM: `JFileChooser`, Web: download через blob URL

### expect/actual паттерн

При добавлении новых платформо-специфичных функций:
1. Объявите `expect` в `commonMain`
2. Реализуйте `actual` в **каждом** sourceSet: `androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`
3. Проверьте компиляцию всех платформ: `./gradlew build`

### Управление зависимостями

- Версии в `gradle/libs.versions.toml` (Catalog approach)
- Обновление версий: редактировать `[versions]` секцию
- Синхронизация Gradle: `./gradlew --refresh-dependencies`

### Работа с CORS и прокси-сервером

При разработке frontend/backend:
1. Сначала запустите сервер: `./gradlew :server:run` (порт 8080)
2. Затем запустите клиентское приложение
3. В продакшене настройте конкретные CORS хосты в `server/src/main/kotlin/com/example/ai_window/Application.kt:34`

### Отладка платформо-специфичного кода

**Android**:
- Используйте Android Studio с подключенным устройством/эмулятором
- Логи: `adb logcat | grep "ai_window"`
- БД находится: `/data/data/com.example.ai_window/databases/chat.db`
- Доступ к БД через `adb shell` или Device File Explorer

**iOS**:
- Открыть `iosApp/iosApp.xcodeproj` в Xcode
- Запустить на симуляторе или реальном устройстве
- БД находится в App Sandbox Documents

**Desktop (JVM)**:
- Самый быстрый способ для разработки и отладки
- БД находится: `~/.ai_window/chat.db`
- Можно открыть БД через SQLite Browser для инспекции

**Web (WASM/JS)**:
- БД не поддерживается (persistence отключен)
- Все данные хранятся в памяти (теряются при перезагрузке)
- Используйте Browser DevTools для отладки

## Troubleshooting

### Проблемы с сервером (localhost:8080)

**Симптом**: Приложение не может подключиться к серверу
- Убедитесь, что сервер запущен: `./gradlew :server:run`
- Проверьте, что порт 8080 свободен: `lsof -i :8080` (macOS/Linux) или `netstat -ano | findstr :8080` (Windows)
- Проверьте логи сервера в консоли

**Симптом**: 401/403 ошибки от Yandex API
- Проверьте, что `YANDEX_API_KEY` и `YANDEX_FOLDER_ID` правильно указаны в `BuildConfig.kt`
- Убедитесь, что API ключ активен в Yandex Cloud Console
- Проверьте квоты и лимиты вашего Yandex Cloud аккаунта

### Проблемы с SQLDelight

**Симптом**: Ошибки компиляции после изменения .sq файлов
- Запустите `./gradlew clean` и пересоберите проект
- Явно регенерируйте интерфейсы: `./gradlew :shared:generateCommonMainChatDatabaseInterface`
- Проверьте синтаксис SQL в .sq файлах

**Симптом**: База данных не сохраняется/не загружается
- **Android**: Проверьте права доступа к storage
- **Desktop**: Убедитесь, что директория `~/.ai_window` существует и доступна для записи
- **iOS**: Проверьте, что app sandbox настроен правильно
- **Web**: Напоминание - БД не поддерживается на Web платформах

### Проблемы с expect/actual

**Симптом**: "Expected declaration not found" при компиляции
- Убедитесь, что `actual` реализация существует во **всех** sourceSet: androidMain, iosMain, jvmMain, jsMain, wasmJsMain
- Проверьте сигнатуру: `expect` и `actual` должны полностью совпадать
- Запустите `./gradlew :shared:build` для проверки всех платформ

### Проблемы с Gradle

**Симптом**: Out of memory при сборке
- Увеличьте heap в `gradle.properties`: `org.gradle.jvmargs=-Xmx6144M`
- Для Kotlin daemon: `kotlin.daemon.jvmargs=-Xmx4096M`
- Закройте ненужные приложения для освобождения памяти

**Симптом**: Медленная сборка
- Включите parallel execution: добавьте `org.gradle.parallel=true` в `gradle.properties` (если еще нет)
- Используйте `--no-daemon` если daemon процессы зависают
- Очистите Gradle cache: `./gradlew clean cleanBuildCache`