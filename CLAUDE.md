# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Обзор проекта

Это Kotlin Multiplatform проект для создания кроссплатформенного приложения с поддержкой Android, iOS, Desktop (JVM), Web (JS/WASM) и сервера на Ktor.

## Архитектура проекта

### Модульная структура
- **composeApp** - UI слой с Compose Multiplatform для всех клиентских платформ
- **shared** - общая бизнес-логика для всех платформ
- **server** - Ktor сервер на порту 8080
- **iosApp** - iOS приложение с интеграцией Kotlin framework

### Платформо-специфичные точки входа
- Android: `composeApp/src/androidMain/kotlin/com/example/ai_window/MainActivity.kt`
- Desktop: `composeApp/src/jvmMain/kotlin/com/example/ai_window/main.kt`
- Web: `composeApp/src/webMain/kotlin/com/example/ai_window/main.kt`
- Server: `server/src/main/kotlin/com/example/ai_window/Application.kt`

### Паттерн Platform абстракции
Проект использует интерфейс `Platform` в shared модуле с платформо-специфичными реализациями через expect/actual механизм Kotlin Multiplatform.

## Команды разработки

### Сборка и запуск
```bash
# Android APK
./gradlew :composeApp:assembleDebug

# Desktop приложение
./gradlew :composeApp:run

# Web версия (WASM - рекомендуется для разработки)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web версия (JS - для старых браузеров)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Сервер
./gradlew :server:run

# iOS - требует Xcode
open iosApp/iosApp.xcodeproj
```

### Тестирование
```bash
# Запуск всех тестов
./gradlew test

# Тесты конкретного модуля
./gradlew :shared:test
./gradlew :server:test
```

### Полезные команды для разработки
```bash
# Очистка проекта
./gradlew clean

# Обновление зависимостей
./gradlew --refresh-dependencies

# Проверка доступных задач для модуля
./gradlew :composeApp:tasks
./gradlew :shared:tasks
./gradlew :server:tasks
```

## Конфигурация и версии

Все версии зависимостей управляются через `gradle/libs.versions.toml`:
- Kotlin: 2.2.20
- Compose Multiplatform: 1.9.1
- Ktor: 3.3.1
- Android Target SDK: 36
- Android Min SDK: 24

## Особенности разработки

### Compose Multiplatform
- Общий UI код находится в `composeApp/src/commonMain`
- Платформо-специфичные UI компоненты в соответствующих sourceSet
- Material Design 3 используется по умолчанию

### Ktor Server
- Запускается на порту 8080 (константа в `shared/src/commonMain/kotlin/com/example/ai_window/Constants.kt`)
- Конфигурация логирования в `server/src/main/resources/logback.xml`
- Роутинг определен в `Application.kt`

### Android особенности
- Edge-to-edge UI включен в MainActivity
- Минимальная версия Android 7.0 (API 24)
- Использует AndroidX и Jetpack Compose

### Desktop дистрибутивы
Проект настроен для создания нативных пакетов:
- macOS: DMG
- Windows: MSI
- Linux: Deb

## Важные замечания

- При добавлении новых платформо-специфичных реализаций используйте expect/actual механизм
- Все общие константы должны быть в `shared/src/commonMain/kotlin/com/example/ai_window/Constants.kt`
- UI компоненты должны быть в `composeApp` модуле, бизнес-логика в `shared`
- При работе с iOS требуется macOS с установленным Xcode