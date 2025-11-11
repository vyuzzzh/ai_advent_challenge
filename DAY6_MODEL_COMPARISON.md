# День 6: Сравнение моделей HuggingFace

## Цель

Создать систему для сравнения различных моделей HuggingFace по ключевым метрикам:
- **Время ответа** - скорость генерации
- **Количество токенов** - стоимость использования
- **Качество ответов** - разнообразие и консистентность
- **Автоматические рекомендации** - выбор оптимальной модели для задачи

## Модели для сравнения

```
1. L3-8B Stheno v3.2 (Sao10K/L3-8B-Stheno-v3.2:novita)
   - Llama 3 8B fine-tune
   - Провайдер: Novita
   - Специализированная версия для качественных ответов

2. MiniMax-M2 (MiniMaxAI/MiniMax-M2:fastest)
   - Продвинутая модель от MiniMax AI
   - Автовыбор самого быстрого провайдера
   - Оптимизирована для скорости

3. Qwen 2.5 VL 7B (Qwen/Qwen2.5-VL-7B-Instruct:fastest)
   - Мультимодальная модель (Vision-Language)
   - Поддержка изображений и текста
   - От Alibaba, 7B параметров
```

## Реализация

### Архитектура

```
HuggingFaceModels.kt
├─ HFModel (modelId, displayName, description)
├─ HuggingFaceRequest (Chat Completion API формат)
├─ HuggingFaceResponse (choices, usage, executionTime)
└─ TokenUsage (promptTokens, completionTokens, totalTokens)

HuggingFaceService.kt
├─ generateText() - отправка запроса через прокси
└─ generateMultiple() - N запросов для метрик

ModelComparisonService.kt
├─ compareModel() - сравнение одной модели (3 запроса)
├─ calculateMetrics() - расчет всех метрик
├─ determineWinners() - победители по категориям
└─ generateRecommendation() - рекомендации для модели

ModelComparisonViewModel.kt
├─ State для каждой модели (result, loading, error)
├─ runComparison() - запуск сравнения для модели
├─ runAllModels() - параллельный запуск всех моделей
└─ generateReport() - итоговый отчет

ModelComparisonScreen.kt
├─ QuestionInput - ввод вопроса
├─ Settings - настройки (runs, maxTokens, temperature)
├─ ComparisonTable - таблица результатов
└─ ResultCards - детальные карточки для каждой модели
```

### Переход на Inference Providers API

**Старый подход** (Serverless API):
```kotlin
// Проблемы:
// - Ограниченный выбор моделей
// - Только оценочные метрики токенов
// - Модели могут быть недоступны (loading state)
POST https://api-inference.huggingface.co/models/{modelId}
{
  "inputs": "prompt text",
  "parameters": {"max_new_tokens": 250}
}

// Ответ:
[{"generated_text": "prompt + response"}]
// Нет метрик токенов!
```

**Новый подход** (Inference Providers API):
```kotlin
// Преимущества:
// - OpenAI-совместимый формат
// - Автовыбор провайдера (:fastest, :cheapest)
// - Реальные метрики токенов от API
// - Больше доступных моделей
POST https://router.huggingface.co/v1/chat/completions
{
  "model": "Qwen/Qwen2.5-VL-7B-Instruct:fastest",
  "messages": [
    {"role": "user", "content": "prompt text"}
  ],
  "max_tokens": 500,
  "temperature": 0.7
}

// Ответ:
{
  "choices": [{
    "message": {"role": "assistant", "content": "response"}
  }],
  "usage": {
    "prompt_tokens": 42,
    "completion_tokens": 250,
    "total_tokens": 292
  }
}
```

### Метрики сравнения

#### 1. Производительность

```kotlin
data class ModelComparisonMetrics(
    val avgResponseTime: Double,  // Среднее время ответа (ms)
    val minResponseTime: Long,     // Минимальное время (ms)
    val maxResponseTime: Long      // Максимальное время (ms)
)
```

**Как измеряется:**
```kotlin
val startTime = getCurrentTimeMillis()
val response = client.post(url) { ... }
val endTime = getCurrentTimeMillis()
val executionTime = endTime - startTime
```

#### 2. Токены (реальные метрики от API)

```kotlin
data class ModelComparisonMetrics(
    val avgInputTokens: Double,   // Среднее кол-во входных токенов
    val avgOutputTokens: Double,  // Среднее кол-во выходных токенов
    val avgTotalTokens: Double    // Среднее общее кол-во токенов
)

// Расчет:
val avgInputTokens = responses.map { it.tokenUsage.promptTokens }.average()
val avgOutputTokens = responses.map { it.tokenUsage.completionTokens }.average()
```

**Важно**: Это не оценки, а реальные данные от HuggingFace API!

#### 3. Качество текста

```kotlin
data class ModelComparisonMetrics(
    val avgWordCount: Double,     // Среднее количество слов
    val avgCharCount: Double,     // Среднее количество символов
    val avgUniqueWords: Double    // Среднее количество уникальных слов
)
```

#### 4. Self-BLEU - Разнообразие ответов

**Что измеряет**: Насколько похожи ответы модели между собой

```kotlin
private fun calculateSelfBLEU(responses: List<String>): Double {
    val wordSets = responses.map { response ->
        response.split(Regex("\\s+"))
            .map { it.lowercase().trim() }
            .toSet()
    }

    var totalSimilarity = 0.0
    var comparisons = 0

    // Сравниваем каждую пару ответов
    for (i in wordSets.indices) {
        for (j in i + 1 until wordSets.size) {
            val intersection = wordSets[i].intersect(wordSets[j]).size.toDouble()
            val union = wordSets[i].union(wordSets[j]).size.toDouble()
            totalSimilarity += if (union > 0) intersection / union else 0.0
            comparisons++
        }
    }

    return if (comparisons > 0) totalSimilarity / comparisons else 0.0
}
```

**Интерпретация**:
- **Низкий Self-BLEU (< 0.3)**: Высокое разнообразие → хорошо для креативных задач
- **Высокий Self-BLEU (> 0.7)**: Низкое разнообразие → хорошо для консистентности

#### 5. Semantic Consistency - Семантическая согласованность

**Что измеряет**: Стабильность ключевых концепций в ответах

```kotlin
private fun calculateSemanticConsistency(responses: List<String>): Double {
    // Извлекаем слова длиннее 3 символов
    val allWords = responses.flatMap { response ->
        response.split(Regex("\\s+"))
            .map { it.lowercase().trim() }
            .filter { it.length > 3 }
    }

    val wordFrequency = allWords.groupingBy { it }.eachCount()

    // Слова, встречающиеся в большинстве ответов
    val commonWords = wordFrequency.filter { (_, count) ->
        count >= responses.size / 2
    }

    return if (wordFrequency.isNotEmpty()) {
        commonWords.size.toDouble() / wordFrequency.size.toDouble()
    } else {
        0.0
    }
}
```

**Интерпретация**:
- **Высокая консистентность (> 0.7)**: Модель стабильна → хорошо для технических задач
- **Низкая консистентность (< 0.3)**: Модель разнообразна → хорошо для креативности

#### 6. Response Variability - Вариативность

```kotlin
data class VariabilityMetrics(
    val lengthStdDev: Double,          // Станд. отклонение длины
    val uniqueWordsVariance: Double,   // Разброс уникальных слов
    val structuralDiversity: Double    // Структурное разнообразие (0-1)
)

private fun calculateVariability(
    responses: List<String>,
    wordCounts: List<Int>,
    uniqueWordsCounts: List<Int>
): VariabilityMetrics {
    // Стандартное отклонение длины
    val avgLength = wordCounts.average()
    val lengthVariance = wordCounts.map { (it - avgLength).pow(2) }.average()
    val lengthStdDev = sqrt(lengthVariance)

    // Разброс уникальных слов
    val avgUnique = uniqueWordsCounts.average()
    val uniqueVariance = uniqueWordsCounts.map { (it - avgUnique).pow(2) }.average()

    // Структурное разнообразие
    val sentenceCounts = responses.map { it.split(Regex("[.!?]+")).size }
    val avgSentences = sentenceCounts.average()
    val sentenceVariance = sentenceCounts.map { (it - avgSentences).pow(2) }.average()

    val normalizedVariance = sentenceVariance / 10.0
    val structuralDiversity = normalizedVariance.coerceIn(0.0, 1.0)

    return VariabilityMetrics(lengthStdDev, uniqueVariance, structuralDiversity)
}
```

### Определение победителей

```kotlin
fun determineWinners(results: List<ModelComparisonResult>): ModelWinner {
    return ModelWinner(
        fastest = results
            .minByOrNull { it.metrics.avgResponseTime }
            ?.model?.displayName ?: "N/A",

        mostConsistent = results
            .maxByOrNull { it.metrics.semanticConsistency }
            ?.model?.displayName ?: "N/A",

        mostCreative = results
            .minByOrNull { it.metrics.selfBleu }  // Меньше = больше разнообразия
            ?.model?.displayName ?: "N/A",

        longestResponses = results
            .maxByOrNull { it.metrics.avgWordCount }
            ?.model?.displayName ?: "N/A",

        mostEfficient = results
            .maxByOrNull { result ->
                // Эффективность = качество / время
                val quality = result.metrics.avgWordCount * result.metrics.avgUniqueWords
                val time = result.metrics.avgResponseTime
                if (time > 0) quality / time else 0.0
            }
            ?.model?.displayName ?: "N/A"
    )
}
```

### Генерация рекомендаций

```kotlin
fun generateRecommendation(result: ModelComparisonResult): ModelRecommendation {
    val metrics = result.metrics
    val strengths = mutableListOf<String>()
    val weaknesses = mutableListOf<String>()
    val bestUseCases = mutableListOf<String>()

    // Анализ скорости
    if (metrics.avgResponseTime < 2000) {
        strengths.add("Быстрые ответы (${metrics.avgResponseTime.toInt()}ms)")
        bestUseCases.add("Интерактивные приложения")
    } else {
        weaknesses.add("Медленные ответы (${metrics.avgResponseTime.toInt()}ms)")
    }

    // Анализ консистентности
    if (metrics.semanticConsistency > 0.7) {
        strengths.add("Высокая консистентность")
        bestUseCases.add("Технические задачи")
    } else {
        strengths.add("Разнообразные ответы")
        bestUseCases.add("Креативные задачи")
    }

    // Анализ разнообразия
    if (metrics.selfBleu < 0.3) {
        strengths.add("Высокое разнообразие генераций")
        bestUseCases.add("Генерация вариантов контента")
    }

    // Анализ длины ответов
    if (metrics.avgWordCount > 50) {
        strengths.add("Подробные ответы (${metrics.avgWordCount.toInt()} слов)")
        bestUseCases.add("Объяснения и туториалы")
    } else {
        strengths.add("Краткие ответы (${metrics.avgWordCount.toInt()} слов)")
        bestUseCases.add("Быстрые ответы на вопросы")
    }

    return ModelRecommendation(
        model = result.model,
        strengths = strengths,
        weaknesses = weaknesses.ifEmpty { listOf("Нет явных недостатков") },
        bestUseCases = bestUseCases,
        summary = "Модель ${result.model.displayName}: ..."
    )
}
```

## Прокси-сервер

### Зачем нужен прокси

**Проблема**: Нельзя хранить API токены в клиентском приложении
**Решение**: Прокси-сервер на Ktor

```kotlin
// server/src/main/kotlin/com/example/ai_window/Application.kt
post("/api/huggingface") {
    // 1. Получить токен из заголовка
    val hfToken = call.request.header("X-HF-Token")
        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing token")

    // 2. Получить запрос от клиента
    val request = call.receive<HuggingFaceRequest>()

    println("📤 HuggingFace Request:")
    println("  Model: ${request.model}")
    println("  Messages: ${request.messages.size}")
    println("  Max tokens: ${request.maxTokens}")

    val startTime = System.currentTimeMillis()

    // 3. Проксировать к HuggingFace
    val hfResponse = httpClient.post("https://router.huggingface.co/v1/chat/completions") {
        header("Authorization", "Bearer $hfToken")
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    val endTime = System.currentTimeMillis()
    val executionTime = endTime - startTime

    // 4. Обработать ответ
    when (hfResponse.status) {
        HttpStatusCode.OK -> {
            val responseText = hfResponse.body<String>()
            println("📥 HF Response: $responseText")

            val apiResponse = json.decodeFromString<HuggingFaceResponse>(responseText)
            val enrichedResponse = apiResponse.copy(executionTime = executionTime)

            call.respond(enrichedResponse)
        }
        HttpStatusCode.ServiceUnavailable -> {
            call.respond(HuggingFaceResponse(
                error = "Модель временно недоступна. Попробуйте другую модель."
            ))
        }
        else -> {
            val errorBody = hfResponse.body<String>()
            call.respond(HttpStatusCode.InternalServerError,
                HuggingFaceResponse(error = "API error: $errorBody")
            )
        }
    }
}
```

### Преимущества прокси

1. **Безопасность**: API токен не попадает в клиентский код
2. **Измерение времени**: Сервер добавляет `executionTime` к ответу
3. **Логирование**: Все запросы логируются на сервере
4. **Обработка ошибок**: Централизованная обработка ошибок API
5. **CORS**: Настроен для работы с веб-клиентами

## UI

### Структура экрана

1. **Header**
   - Заголовок "Сравнение моделей HuggingFace"
   - Кнопка "🔄 Сбросить" - очистка всех результатов

2. **QuestionInput Card**
   - TextField для ввода вопроса
   - Плейсхолдер: "Напишите короткую историю о роботе..."

3. **Settings Card**
   - Slider "Количество запросов" (1-5, default: 3)
   - Slider "Max tokens" (100-1000, default: 500)
   - Slider "Temperature" (0.0-2.0, default: 0.7)

4. **Actions**
   - Кнопка "▶ Запустить сравнение" для каждой модели
   - Индикатор загрузки (CircularProgressIndicator)

5. **ComparisonTable**
   - Таблица с результатами всех моделей
   - Столбцы: Модель, Время (ms), Токены, Слова, Разнообразие
   - Цветовая индикация лучших значений

6. **ResultCards**
   - Детальная карточка для каждой модели
   - Метрики, рекомендации, примеры ответов
   - Значки победителей (🥇 Fastest, 🎨 Creative, etc.)

7. **WinnersSection**
   - Итоговый отчет с победителями по категориям
   - Автоматически сгенерированные выводы

### Пример отображения метрик

```
┌─────────────────────────────────────────────────┐
│ Qwen 2.5 VL 7B                            🎨     │
│─────────────────────────────────────────────────│
│ ⚡ Производительность                           │
│   • Среднее время: 2,500 ms                     │
│   • Диапазон: 2,300 - 2,700 ms                  │
│                                                  │
│ 💬 Токены (реальные данные от API)              │
│   • Вход: 42 токена                             │
│   • Выход: 250 токенов                          │
│   • Всего: 292 токена                           │
│                                                  │
│ 📊 Качество                                     │
│   • Слов: 220                                   │
│   • Уникальных слов: 150                        │
│   • Self-BLEU: 0.25 (высокое разнообразие)      │
│   • Консистентность: 0.65                       │
│                                                  │
│ 💡 Рекомендации                                 │
│   ✓ Разнообразные ответы                        │
│   ✓ Мультимодальная поддержка                   │
│   • Лучше для: Креативные задачи, генерация     │
│     вариантов контента                          │
└─────────────────────────────────────────────────┘
```

## Технические детали

### Multiplatform совместимость

**Проблема**: `System.currentTimeMillis()` и `String.format()` не работают в commonMain

**Решение**: expect/actual паттерн

```kotlin
// commonMain/kotlin/.../Utils.kt
expect fun getCurrentTimeMillis(): Long

fun Double.formatDecimals(decimals: Int = 2): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 100.0
    }
    val rounded = (this * multiplier).toInt() / multiplier
    return rounded.toString()
}

// jvmMain/kotlin/.../TimeUtils.jvm.kt
actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

// wasmJsMain/kotlin/.../TimeUtils.wasmJs.kt
actual fun getCurrentTimeMillis(): Long {
    return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
```

### Избежание конфликтов имён

**Проблема**: `ChatMessage` уже существует в проекте (для другого функционала)

**Решение**: Переименование в `HFChatMessage`

```kotlin
// HuggingFace Chat Message
data class HFChatMessage(
    val role: String,
    val content: String
)

// Существующий ChatMessage
data class ChatMessage(
    val id: Int,
    val text: String,
    val isUser: Boolean
)
```

### Обработка загрузки моделей

```kotlin
when (hfResponse.status) {
    HttpStatusCode.ServiceUnavailable -> {
        val errorBody = hfResponse.body<String>()

        // Модель может быть временно недоступна
        call.respond(HuggingFaceResponse(
            error = "Модель временно недоступна. Попробуйте другую модель или повторите позже.",
            executionTime = executionTime
        ))
    }
}
```

## Использование

### Запуск

**1. Запуск сервера (обязательно первым):**
```bash
./gradlew :server:run
```

Дождитесь:
```
INFO - Responding at http://0.0.0.0:8080
```

**2. Запуск клиента:**
```bash
./gradlew :composeApp:run
```

**3. Использование:**
1. Перейти на экран "Model Comparison"
2. Ввести вопрос: "Напишите короткую историю о роботе"
3. Настроить параметры (по умолчанию подходят)
4. Нажать "▶ Запустить сравнение" для каждой модели
5. Дождаться результатов (по 3 запроса на модель)
6. Изучить таблицу сравнения и рекомендации

### Пример сценария

**Вопрос**: "Объясни что такое квантовая физика"

**Ожидаемые результаты**:

```
L3-8B Stheno v3.2 (Novita):
- Время: ~2,000 ms
- Токены: ~300
- Self-BLEU: 0.28 (креативная)
- Консистентность: 0.62
→ Рекомендация: Креативные объяснения, примеры

MiniMax-M2:
- Время: ~1,500 ms (🥇 FASTEST)
- Токены: ~250
- Self-BLEU: 0.45
- Консистентность: 0.75 (🥇 MOST CONSISTENT)
→ Рекомендация: Быстрые технические ответы

Qwen 2.5 VL 7B:
- Время: ~2,500 ms
- Токены: ~350
- Self-BLEU: 0.22 (🎨 MOST CREATIVE)
- Консистентность: 0.58
→ Рекомендация: Подробные креативные объяснения
```

## Выводы

### Ключевые достижения

✅ **Интеграция с Inference Providers API** - OpenAI-совместимый формат
✅ **Реальные метрики токенов** - точные данные от API, не оценки
✅ **Автоматический выбор провайдера** - модификаторы :fastest, :novita
✅ **Комплексная оценка качества** - Self-BLEU, консистентность, вариативность
✅ **Автоматические рекомендации** - система выбора оптимальной модели
✅ **Прокси-сервер** - безопасное хранение API токенов
✅ **Multiplatform** - работает на Desktop/JVM

### Преимущества подхода

1. **OpenAI-совместимость**: Легко портировать код на другие API
2. **Автоматизация**: Победители определяются автоматически
3. **Объективность**: Реальные метрики вместо субъективных оценок
4. **Безопасность**: API токены на сервере, не в клиенте
5. **Расширяемость**: Легко добавить новые модели и метрики

### Применение

Система полезна для:
- **Выбора модели** под конкретную задачу
- **A/B тестирования** разных моделей
- **Оптимизации затрат** (анализ токенов)
- **Исследования** характеристик моделей
- **Обучения** пониманию поведения LLM

### Основной урок

**Разные модели подходят для разных задач:**
- Быстрые модели (MiniMax-M2) → интерактивные приложения
- Креативные модели (Qwen 2.5 VL) → генерация контента
- Консистентные модели → технические задачи

**Важно измерять объективные метрики**, а не полагаться на субъективные впечатления.

## Файлы проекта

### Shared модуль
- `shared/src/commonMain/kotlin/com/example/ai_window/model/HuggingFaceModels.kt` - модели и структуры данных
- `shared/src/commonMain/kotlin/com/example/ai_window/model/ModelComparison.kt` - результаты сравнения
- `shared/src/commonMain/kotlin/com/example/ai_window/service/HuggingFaceService.kt` - API клиент
- `shared/src/commonMain/kotlin/com/example/ai_window/service/ModelComparisonService.kt` - логика сравнения

### ComposeApp модуль
- `composeApp/src/commonMain/kotlin/com/example/ai_window/ModelComparisonViewModel.kt` - управление состоянием
- `composeApp/src/commonMain/kotlin/com/example/ai_window/ModelComparisonScreen.kt` - UI
- `composeApp/src/commonMain/kotlin/com/example/ai_window/Utils.kt` - форматирование чисел
- `composeApp/src/commonMain/kotlin/com/example/ai_window/App.kt` - добавлена вкладка Model Comparison

### Platform-specific
- `composeApp/src/{platform}Main/kotlin/.../TimeUtils.*.kt` - getCurrentTimeMillis() для каждой платформы
- `composeApp/src/{platform}Main/kotlin/.../FileExport.*.kt` - saveTextToFile() для каждой платформы

### Server
- `server/src/main/kotlin/com/example/ai_window/Application.kt` - прокси-сервер с эндпоинтом /api/huggingface

## Дальнейшие улучшения

- [ ] Добавить визуализацию метрик (графики, radar charts)
- [ ] Экспорт результатов в CSV/JSON/PDF
- [ ] Поддержка пользовательских моделей
- [ ] Batch сравнение (>3 моделей одновременно)
- [ ] История сравнений с возможностью повторного просмотра
- [ ] Расчет стоимости на основе токенов и прайсинга провайдеров
- [ ] Дополнительные метрики (ROUGE, Perplexity, Sentiment)
- [ ] A/B тестирование с сохранением результатов
