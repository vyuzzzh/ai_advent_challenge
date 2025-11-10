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

### Compose Multiplatform UI

**Структура кода**:
- Общий UI: `composeApp/src/commonMain/kotlin`
- Платформо-специфичный: `composeApp/src/{androidMain,iosMain,jvmMain,jsMain,wasmJsMain}`
- Material Design 3 по умолчанию

**Screens и ViewModels**:
- `App.kt` - главный Composable с навигацией
- `ChatViewModel` - базовый чат
- `PlanningViewModel` - планирование задач с prompt engineering
- `ReasoningViewModel` - рассуждения с цепочкой мыслей

### Ktor Server

**Конфигурация**:
- Порт: 8080 (`shared/src/commonMain/kotlin/com/example/ai_window/Constants.kt`)
- CORS: разрешены все источники (для разработки)
- Логирование: `server/src/main/resources/logback.xml`

**API эндпоинты** (`server/src/main/kotlin/com/example/ai_window/Application.kt`):
- `GET /` - health check
- `POST /api/yandex-gpt` - прокси к Yandex API (headers: `X-API-Key`, `X-Folder-Id`)

### Добавление новых платформ

**При добавлении expect/actual реализаций**:
1. Объявите `expect` функцию/класс в `shared/src/commonMain`
2. Создайте `actual` реализацию в каждом sourceSet (`androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`)
3. Используйте платформо-специфичные API только в `actual` блоках

**Пример**: `Platform.kt` имеет 5 реализаций для каждой платформы

### Константы и конфигурация

- Все общие константы: `shared/src/commonMain/kotlin/com/example/ai_window/Constants.kt`
- BuildConfig (версии, debug флаги): `composeApp/src/commonMain/kotlin/com/example/ai_window/BuildConfig.kt`

### Desktop дистрибутивы

```bash
./gradlew :composeApp:createDistributable  # Создать пакет для текущей ОС
```

Поддерживаемые форматы (настройка в `composeApp/build.gradle.kts`):
- macOS: DMG
- Windows: MSI
- Linux: Deb