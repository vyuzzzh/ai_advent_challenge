# AI Window - Чат с Yandex GPT

Простое кроссплатформенное приложение для общения с AI агентом на базе Yandex GPT API.

## Настройка проекта

### 1. Получение API ключей Yandex Cloud

Для работы приложения вам нужно получить API ключ от Yandex Cloud:

1. Зарегистрируйтесь в [Yandex Cloud](https://cloud.yandex.ru/)
2. Создайте каталог (folder) или используйте существующий
3. Перейдите в раздел "Сервисные аккаунты"
4. Создайте сервисный аккаунт с ролью `ai.languageModels.user`
5. Создайте API ключ для сервисного аккаунта
6. Сохраните API ключ и ID каталога (folder ID)

### 2. Настройка приложения

#### Вариант 1: Используя BuildConfig (рекомендуется)

1. Скопируйте шаблон:
   ```bash
   cp composeApp/src/commonMain/kotlin/com/example/ai_window/BuildConfig.kt.template \
      composeApp/src/commonMain/kotlin/com/example/ai_window/BuildConfig.kt
   ```

2. Откройте `composeApp/src/commonMain/kotlin/com/example/ai_window/BuildConfig.kt` и замените:
   ```kotlin
   object BuildConfig {
       const val YANDEX_API_KEY = "YOUR_API_KEY_HERE"      // Ваш API ключ
       const val YANDEX_FOLDER_ID = "YOUR_FOLDER_ID_HERE"  // Ваш Folder ID
   }
   ```

**Важно:** Файл `BuildConfig.kt` добавлен в `.gitignore` и не будет закоммичен в Git!

#### Вариант 2: Используя local.properties

Добавьте в файл `local.properties`:
```properties
yandex.api.key=YOUR_API_KEY_HERE
yandex.folder.id=YOUR_FOLDER_ID_HERE
```

### 3. Запуск приложения

#### Android ✅ (работает)
```bash
./gradlew :composeApp:assembleDebug
```

Установите APK из `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

#### Desktop ✅ (должно работать)
```bash
./gradlew :composeApp:run
```

#### Web (WASM/JS) ⚠️ (требует прокси-сервер)

**Проблема:** Из-за политики CORS браузеры блокируют прямые запросы к Yandex GPT API.

**Решение 1 - Использовать Android или Desktop** (рекомендуется)

**Решение 2 - Backend прокси** (требует дополнительной настройки):
- Необходимо настроить backend сервер, который будет проксировать запросы
- Файл `server/src/main/kotlin/com/example/ai_window/Application.kt` содержит готовый прокси
- Требуется дополнительная конфигурация для production использования

## Структура проекта

```
ai_window/
├── composeApp/              # UI слой с Compose Multiplatform
│   └── src/commonMain/
│       └── kotlin/com/example/ai_window/
│           ├── App.kt       # Главный UI экран
│           └── ChatViewModel.kt  # ViewModel для управления состоянием
├── shared/                  # Общая бизнес-логика
│   └── src/commonMain/kotlin/com/example/ai_window/
│       ├── model/           # Модели данных
│       │   ├── ChatMessage.kt
│       │   └── YandexGptModels.kt
│       └── service/         # Сервисы
│           └── YandexGptService.kt  # Клиент для Yandex GPT API
```

## Особенности реализации

### Архитектура
- **MVVM паттерн**: Использует ViewModel для управления состоянием UI
- **Reactive UI**: StateFlow для реактивного обновления интерфейса
- **Multiplatform**: Код работает на Android, iOS, Desktop и Web

### Функциональность
- ✅ Отправка сообщений к Yandex GPT
- ✅ Сохранение истории диалога
- ✅ Автоматическая прокрутка к последнему сообщению
- ✅ Индикатор загрузки
- ✅ Обработка ошибок
- ✅ Очистка истории чата

### API Integration
Приложение использует Yandex Foundation Models API (YandexGPT) с моделью `yandexgpt-lite/latest`.

Параметры запроса:
- `temperature`: 0.6 (креативность ответов)
- `maxTokens`: 2000 (максимальная длина ответа)

## Безопасность

⚠️ **ВАЖНО**: Не коммитьте API ключи в систему контроля версий!

В production-версии рекомендуется:
1. Хранить API ключи в переменных окружения
2. Использовать backend-прокси для защиты ключей
3. Реализовать аутентификацию пользователей

## Требования

- JDK 11 или выше
- Kotlin 2.2.20
- Gradle 8.x
- Для Android: Android SDK 24+
- Для Desktop: Windows/macOS/Linux
- Для iOS: Xcode (только на macOS)

## Troubleshooting

### Ошибка "Unauthorized"
Проверьте правильность API ключа и убедитесь, что сервисный аккаунт имеет роль `ai.languageModels.user`.

### Ошибка сети
Убедитесь, что у вас есть доступ к интернету и API Yandex Cloud доступен в вашем регионе.

### Gradle sync failed
Запустите:
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

## Лицензия

MIT License
