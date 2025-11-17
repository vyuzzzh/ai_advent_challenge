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
- **Android SDK**: min 24 (Android 7.0), target 36
- **JVM Target**: 11

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

**DatabaseDriverFactory** (expect/actual pattern):
- Android: `AndroidSqliteDriver` - хранит в app-specific storage
- iOS: `NativeSqliteDriver` - хранит в app sandbox
- JVM/Desktop: `JdbcSqliteDriver` - хранит в `~/.ai_window/chat.db`
- JS/WASM: Не поддерживается (база отключена)

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

**Принципы разработки**:
- Каждый "день" = отдельная функциональность/эксперимент
- Комментарии в коде помечены day_X для отслеживания
- Новые поля в моделях помечаются как `// Day X: описание`
- Бранчи именуются `day_X` для изоляции экспериментов
- Каждая функциональность мержится через Pull Request после завершения
- Перед началом нового day_X убедитесь, что предыдущий день смержен в main

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