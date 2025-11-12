# День 7: Анализ токенов и лимитов контекста

## Цель

Создать систему для анализа работы с токенами и демонстрации лимитов контекста моделей:
- **Подсчёт токенов** - реальные метрики от API (input/output/total)
- **Тестирование лимитов** - короткие, средние, длинные и превышающие лимит промпты
- **Визуализация использования** - прогресс-бары и цветовая индикация
- **Образовательная демонстрация** - показать что происходит при превышении лимита

## Выбор модели для анализа

### Требования к модели

Для демонстрации лимитов токенов нужна модель с **маленьким контекстным окном**:
- Контекст ~1024 токена (не 128K как у современных моделей)
- Поддержка Chat Completion API (OpenAI-совместимый формат)
- Доступность через HuggingFace Inference Providers API

### Путь выбора модели

**Попытка 1: DistilGPT2**
```
distilbert/distilgpt2
Контекст: 1024 токена
```
❌ **Проблема**: `400 Bad Request: model 'distilbert/distilgpt2' is not a chat model`
- DistilGPT2 - это text generation модель, не chat completion

**Попытка 2: TinyLlama**
```
TinyLlama/TinyLlama-1.1B-Chat-v1.0
Контекст: 2048 токенов
```
❌ **Проблема**: `400 Bad Request` - модель не доступна через Inference Providers API
- Не все модели HuggingFace доступны через роутер

**Попытка 3: Phi-3-mini**
```
microsoft/Phi-3-mini-4k-instruct:fastest
Контекст: 4096 токенов
```
✅ **Работает**, но контекст слишком большой для демонстрации лимитов
- Сложно создать промпт, превышающий 4096 токенов

**Попытка 4: DialoGPT-small**
```
microsoft/DialoGPT-small
Контекст: 1024 токена
```
❌ **Проблема**: `400 Bad Request` - не поддерживается Providers API

**Попытка 5: BlenderBot-400M-distill**
```
facebook/blenderbot-400M-distill
Контекст: 128 токенов
```
❌ **Проблема**: `400 Bad Request` - не поддерживается Providers API

### Финальное решение: Llama 3.2 1B с искусственным лимитом

```kotlin
HFModel(
    modelId = "meta-llama/Llama-3.2-1B-Instruct:fastest",
    displayName = "Llama 3.2 1B",
    description = "Легковесная chat-модель от Meta (1B параметров, контекст: 128K) - для анализа токенов"
)
```

**Характеристики:**
- ✅ Поддерживает Chat Completion API
- ✅ Доступна через `:fastest` роутинг HuggingFace
- ✅ Реальный контекст: 128K токенов
- ⚙️ **Искусственный лимит: 1024 токена** (для демонстрации)

**Почему искусственный лимит:**
```kotlin
companion object {
    const val LLAMA32_CONTEXT_LIMIT = 1024  // Искусственно ограничиваем для демонстрации
}
```

Это позволяет:
- Показать поведение при 100%+ использовании контекста
- Создать образовательный пример
- Использовать качественную современную модель

## Реализация

### Архитектура

```
ModelComparison.kt (models)
├─ PromptType - типы промптов (SHORT, MEDIUM, LONG, EXCEEDS_LIMIT)
├─ TokenTestPrompts - предустановленные промпты для тестирования
│   ├─ SHORT_PROMPT (~15-30 токенов)
│   ├─ MEDIUM_PROMPT (~150-220 токенов)
│   ├─ LONG_PROMPT (~750-900 токенов)
│   └─ generateExceedingPrompt() - автогенерация >1024 токенов
├─ TokenTestResult - результат теста с метриками
└─ TokenAnalysisState - состояние анализа (Idle, Loading, Success, Error)

HuggingFaceService.kt (service)
├─ generateText() - запрос к модели через прокси
├─ normalizeModelName() - нормализация имен моделей для сравнения
└─ TokenUsage из API (promptTokens, completionTokens, totalTokens)

ModelComparisonViewModel.kt (ViewModel)
├─ ComparisonMode.TOKEN_ANALYSIS - режим анализа токенов
├─ runTokenAnalysis() - запуск всех 4 тестов
├─ runSingleTokenTest(promptType) - тест одного промпта
├─ estimateTokens(text) - оценка токенов (~1.3 на слово для русского)
├─ generateTokenAnalysisReport() - генерация отчёта с полными текстами
└─ Константа LLAMA32_CONTEXT_LIMIT = 1024

ModelComparisonScreen.kt (UI)
├─ ModeSelectorSection - переключатель режимов
├─ TokenAnalysisDescription - описание режима
├─ TokenAnalysisRunButton - запуск анализа
├─ TokenAnalysisLoadingCard - прогресс с индикацией
├─ TokenAnalysisResultsTable - сравнительная таблица
├─ TokenTestResultCard - детальная карточка результата
└─ TokenLimitProgressBar - визуализация использования контекста
```

### Типы промптов

#### SHORT (~15-30 токенов)

**Назначение**: Базовый короткий запрос

```kotlin
val SHORT_PROMPT = "Что такое машинное обучение? Дай краткое определение в одном предложении."
```

**Ожидаемые метрики**:
- Input: ~15-30 токенов
- Output: ~50-100 токенов
- Использование контекста: <10%

#### MEDIUM (~150-220 токенов)

**Назначение**: Структурированный запрос средней длины

```kotlin
val MEDIUM_PROMPT = """
    Объясни концепцию машинного обучения.

    Включи в ответ:
    1. Определение машинного обучения
    2. Основные типы обучения (с учителем, без учителя, с подкреплением)
    3. Примеры практического применения
    4. Ключевые отличия от традиционного программирования

    Ответ должен быть структурированным, но не слишком детальным - примерно 100-150 слов.
""".trimIndent()
```

**Ожидаемые метрики**:
- Input: ~150-220 токенов
- Output: ~200-300 токенов
- Использование контекста: 35-50%

#### LONG (~750-900 токенов)

**Назначение**: Длинный детальный запрос, приближающийся к лимиту

```kotlin
val LONG_PROMPT = """
    Напиши подробную статью о машинном обучении и искусственном интеллекте.

    Структура статьи:

    1. ВВЕДЕНИЕ
    - Что такое искусственный интеллект и машинное обучение
    - История развития этих технологий
    - Почему они важны в современном мире

    2. ТИПЫ МАШИННОГО ОБУЧЕНИЯ
    - Обучение с учителем (supervised learning)
      * Классификация
      * Регрессия
      * Примеры алгоритмов и задач

    [... детальная структура статьи ...]

    Целевой объём: около 500 слов. Пиши информативно, но доступно.
""".trimIndent()
```

**Ожидаемые метрики**:
- Input: ~750-900 токенов
- Output: ~200-400 токенов
- Использование контекста: 90-110%

#### EXCEEDS_LIMIT (>1024 токенов)

**Назначение**: Демонстрация поведения при превышении лимита

**Алгоритм генерации**:
```kotlin
fun generateExceedingPrompt(targetTokens: Int = 1500): String {
    // Примерно 1.3 токена на слово для русского текста
    val targetWords = (targetTokens / 1.3).toInt()

    val baseText = """
        Напиши очень подробный, развёрнутый и детальный анализ следующих тем:

        1. История развития искусственного интеллекта с самого начала
        2. Все основные алгоритмы машинного обучения
        3. Детальное описание архитектур нейронных сетей
        4. Математические основы машинного обучения
        5. Практические применения в различных отраслях
    """.trimIndent()

    val repeatingText = """
        Дополнительно опиши следующие аспекты в деталях:
        - Линейная регрессия и её математические основы
        - Логистическая регрессия для классификации
        - Метод опорных векторов (SVM)
        - Деревья решений и случайный лес
        [... длинный список алгоритмов ...]
    """.trimIndent()

    // Добавляем повторяющийся текст до достижения целевого размера
    while (currentWords < targetWords) {
        result.append("\n\nИтерация $iteration: $repeatingText")
        iteration++
    }

    return result.toString()
}
```

**Ожидаемые метрики**:
- Input: ~1500-2000 токенов
- Output: ~100-300 токенов (усеченный)
- Использование контекста: 150-200% (превышение!)

### Оценка токенов

**Формула для русского текста:**
```kotlin
fun estimateTokens(text: String): Int {
    val wordCount = text.split(Regex("\\s+")).size
    return (wordCount * 1.3).toInt()  // ~1.3 токена на слово
}
```

**Почему 1.3:**
- Русские слова обычно длиннее английских
- Кириллица требует больше токенов
- Эмпирическое значение на основе тестов

**Для английского текста:** ~0.75 токена на слово

**Реальные токены от API:**
```kotlin
data class TokenUsage(
    val promptTokens: Int,      // Реальное количество input токенов
    val completionTokens: Int,  // Реальное количество output токенов
    val totalTokens: Int        // Сумма
)
```

### Нормализация имен моделей

**Проблема**: HuggingFace роутер нормализует имена моделей

**Пример**:
```
Запрошено:  meta-llama/Llama-3.2-1B-Instruct:fastest
Возвращено: meta-llama/llama-3.2-1b-instruct
```

Изменения:
- Удален суффикс `:fastest`
- Приведено к lowercase
- Это **не подмена модели**, а нормализация имени!

**Решение**:
```kotlin
private fun normalizeModelName(modelName: String): String {
    return modelName
        .split(":")  // Убираем суффикс провайдера (:fastest, :novita)
        .first()
        .lowercase()  // Приводим к нижнему регистру
        .trim()
}
```

**Применение в сервисе** (`HuggingFaceService.kt:80-95`):
```kotlin
val actualModel = response.model
if (actualModel != null) {
    val normalizedRequested = normalizeModelName(modelId)
    val normalizedActual = normalizeModelName(actualModel)

    if (normalizedActual != normalizedRequested) {
        println("⚠️  WARNING: Model substitution detected!")
        println("  Requested: $modelId (normalized: $normalizedRequested)")
        println("  Actually used: $actualModel (normalized: $normalizedActual)")
    } else if (actualModel != modelId) {
        println("ℹ️  Model name normalized by provider:")
        println("  Requested: $modelId")
        println("  Returned: $actualModel")
    }
}
```

**Применение в UI** (`ModelComparisonScreen.kt`):
```kotlin
// Warning о подмене модели (с нормализацией)
result.actualModelUsed?.let { actualModel ->
    if (normalizeModelName(actualModel) != normalizeModelName(result.requestedModel)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "⚠️ Модель подменена Router'ом!",
                    style = MaterialTheme.typography.titleSmall
                )
                Text("Запрошено: ${result.requestedModel}")
                Text("Использовано: $actualModel")
            }
        }
    }
}
```

**Важно**: Используем `.let { actualModel ->` для создания локальной переменной, чтобы избежать ошибки smart cast с public API property.

### Полные тексты в отчётах

**Проблема**: Изначально тексты обрезались `.take(500)` и `.take(300)`

**Решение**: Удалены все обрезки для полного анализа

**До**:
```kotlin
appendLine(response.take(500))  // Обрезка!
```

**После**:
```kotlin
appendLine(response)  // Полный текст
```

**Обновлено в функциях**:
1. `generateReport()` - отчёты сравнения моделей
2. `generateTokenAnalysisReport()` - отчёты анализа токенов

**Добавлено поле prompt в TokenTestResult**:
```kotlin
@Serializable
data class TokenTestResult(
    val promptType: String,
    val prompt: String,  // ← ДОБАВЛЕНО: полный текст промпта
    val promptLength: Int,
    val estimatedPromptTokens: Int,
    val actualInputTokens: Int,
    // ... остальные поля
)
```

**Теперь отчёты включают** (`ModelComparisonViewModel.kt:766-781`):
```kotlin
appendLine("Промпт:")
appendLine(repeatString("-", 80))
appendLine(result.prompt)  // Полный промпт
appendLine(repeatString("-", 80))
appendLine()

appendLine("Ответ модели:")
appendLine(repeatString("-", 80))
appendLine(result.response)  // Полный ответ, не обрезанный
appendLine(repeatString("-", 80))
```

### Динамические заголовки отчётов

**Проблема**: Хардкод `"ОТЧЕТ: АНАЛИЗ ТОКЕНОВ (DISTILGPT2)"`

**Решение**: Динамическая генерация из модели

**Было**:
```kotlin
appendLine("ОТЧЕТ: АНАЛИЗ ТОКЕНОВ (DISTILGPT2)")
```

**Стало** (`ModelComparisonViewModel.kt:715`):
```kotlin
appendLine("ОТЧЕТ: АНАЛИЗ ТОКЕНОВ (${llama32Model.displayName.uppercase()})")
// Результат: "ОТЧЕТ: АНАЛИЗ ТОКЕНОВ (LLAMA 3.2 1B)"
```

Это гарантирует корректность заголовка при смене модели.

## UI

### Структура экрана

```
TopAppBar
├─ Динамический заголовок (зависит от режима)
├─ 💾 - экспорт (токен-анализ или сравнение моделей)
└─ 🔄 - сброс результатов

ModeSelectorSection
├─ Текущий режим: "💎 Анализ токенов" или "🔀 Сравнение моделей"
└─ Кнопка переключения режимов

=== TOKEN ANALYSIS MODE ===

TokenAnalysisDescription
├─ "💎 Анализ токенов: проверка лимитов контекста"
├─ Модель: Llama 3.2 1B (демо-лимит: 1024 токена)
└─ Описание функционала

TokenAnalysisRunButton
├─ "▶ Запустить анализ всех типов промптов"
└─ Disabled при loading

TokenAnalysisLoadingCard (если loading)
├─ Текущий тест: MEDIUM
├─ Прогресс: 2 из 4
└─ CircularProgressIndicator

TokenAnalysisResultsTable
├─ Заголовок таблицы (Тип | Input | Output | Total | % лимита)
├─ Строка SHORT
├─ Строка MEDIUM
├─ Строка LONG
└─ Строка EXCEEDS_LIMIT (с цветовой индикацией)

TokenTestResultCard (для каждого результата)
├─ Header (тип промпта + статус)
├─ TokenLimitProgressBar (визуализация %)
├─ MetricChips (токены, время)
├─ Промпт (превью)
├─ Ответ (превью)
└─ Warning (если есть подмена модели)
```

### Визуализация лимитов контекста

**TokenLimitProgressBar** - ключевой компонент визуализации:

```kotlin
@Composable
fun TokenLimitProgressBar(percentage: Double) {
    val color = when {
        percentage < 70 -> Color(0xFF4CAF50)   // Зелёный: безопасно
        percentage < 90 -> Color(0xFFFFC107)   // Жёлтый: предупреждение
        else -> Color(0xFFF44336)               // Красный: критично
    }

    LinearProgressIndicator(
        progress = { (percentage / 100).coerceIn(0.0, 1.0).toFloat() },
        modifier = Modifier.fillMaxWidth().height(12.dp),
        color = color
    )

    // Легенда с отметками
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.weight(0.7f))
        Text("70%", fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.weight(0.2f))
        Text("90%", fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.weight(0.1f))
    }
}
```

**Цветовая индикация**:
- ✅ **0-70%** (Зелёный): Безопасный диапазон, можно увеличивать maxTokens
- 🟡 **70-90%** (Жёлтый): Приближение к лимиту, требуется внимание
- 🔴 **>90%** (Красный): Критическое использование, возможны ошибки

**Интерпретация >100%**:
```
Использование: 179.3%
Цвет: Красный
Значение: Промпт превысил контекстное окно
```

Это показывает, что:
- Модель получила запрос больше, чем может обработать
- Возможна обрезка контекста
- Ответ может быть неполным или с ошибками

### Сравнительная таблица

**Формат отображения**:
```
┌──────────────────┬──────────┬──────────┬──────────┬─────────────┐
│ Тип промпта      │ Input    │ Output   │ Total    │ % лимита    │
├──────────────────┼──────────┼──────────┼──────────┼─────────────┤
│ Короткий         │ 18       │ 95       │ 113      │ 11.0% 🟢    │
│ Средний          │ 165      │ 250      │ 415      │ 40.5% 🟢    │
│ Длинный          │ 820      │ 204      │ 1024     │ 100.0% 🟡   │
│ Превышает лимит  │ 1835     │ 0        │ 1835     │ 179.2% 🔴   │
└──────────────────┴──────────┴──────────┴──────────┴─────────────┘
```

**Реализация** (`ModelComparisonScreen.kt:1258-1302`):
```kotlin
@Composable
fun TokenAnalysisResultsTable(results: List<TokenTestResult>) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Сравнительная таблица", style = MaterialTheme.typography.titleMedium)

            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Тип промпта", modifier = Modifier.weight(2f))
                Text("Input", modifier = Modifier.weight(1f))
                Text("Output", modifier = Modifier.weight(1f))
                Text("Total", modifier = Modifier.weight(1f))
                Text("% лимита", modifier = Modifier.weight(1.5f))
            }

            HorizontalDivider()

            // Data rows
            results.forEach { result ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(result.promptType, modifier = Modifier.weight(2f))
                    Text("${result.actualInputTokens}", modifier = Modifier.weight(1f))
                    Text("${result.actualOutputTokens}", modifier = Modifier.weight(1f))
                    Text("${result.totalTokens}", modifier = Modifier.weight(1f))

                    // Цветовая индикация процента
                    val percentage = result.percentageUsed
                    val color = when {
                        percentage < 70 -> Color(0xFF4CAF50)
                        percentage < 90 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }

                    Text(
                        "${percentage.formatDecimals(1)}%",
                        modifier = Modifier.weight(1.5f),
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
```

### Детальные карточки результатов

**Компоненты карточки**:
1. **Header** - тип промпта + статус (✅/❌)
2. **ProgressBar** - визуализация процента использования
3. **Metrics Grid** - токены, время, символы
4. **Prompt Preview** - первые 200 символов промпта
5. **Response Preview** - первые 300 символов ответа
6. **Model Warning** - если была подмена модели

**Пример** (`ModelComparisonScreen.kt:1304-1430`):
```kotlin
@Composable
fun TokenTestResultCard(result: TokenTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row {
                Text(
                    result.promptType,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                Text(if (result.success) "✅" else "❌")
            }

            // Progress bar
            TokenLimitProgressBar(result.percentageUsed)

            // Metrics
            FlowRow {
                MetricChip("Input: ${result.actualInputTokens}")
                MetricChip("Output: ${result.actualOutputTokens}")
                MetricChip("Total: ${result.totalTokens}")
                MetricChip("${result.executionTime}ms")
            }

            // Prompt preview
            Text("Промпт:", fontWeight = FontWeight.Bold)
            Text(
                result.prompt.take(200) + if (result.prompt.length > 200) "..." else "",
                fontSize = 12.sp,
                color = Color.Gray
            )

            // Response preview
            Text("Ответ:", fontWeight = FontWeight.Bold)
            Text(
                result.response.take(300) + if (result.response.length > 300) "..." else "",
                fontSize = 12.sp
            )
        }
    }
}
```

## Экспорт результатов

### Формат отчёта

**Текстовый файл (.txt) с полным анализом**:

```
================================================================================
ОТЧЕТ: АНАЛИЗ ТОКЕНОВ (LLAMA 3.2 1B)
================================================================================

Модель: meta-llama/Llama-3.2-1B-Instruct:fastest
Демонстрационный лимит: 1024 токена (реальный лимит: 128K)
Дата: 2025-11-13 14:30:22
Количество тестов: 4

================================================================================
СВОДНАЯ ТАБЛИЦА МЕТРИК
================================================================================

Тип промпта          Input    Output   Total    % лимита   Статус
--------------------------------------------------------------------------------
SHORT                18       95       113      11.0%      ✅
MEDIUM               165      250      415      40.5%      ✅
LONG                 820      204      1024     100.0%     ✅
EXCEEDS_LIMIT        1835     0        1835     179.2%     ❌

================================================================================
ДЕТАЛЬНЫЕ РЕЗУЛЬТАТЫ: SHORT
================================================================================

Параметры теста:
  Тип: SHORT
  Описание: Короткий вопрос
  Оценка токенов: ~15-30 токенов
  Модель: meta-llama/Llama-3.2-1B-Instruct:fastest
  Контекстный лимит: 1024

Промпт (18 символов):
--------------------------------------------------------------------------------
Что такое машинное обучение? Дай краткое определение в одном предложении.
--------------------------------------------------------------------------------

Метрики токенов (реальные данные от API):
  Входные токены (prompt): 18
  Выходные токены (completion): 95
  Всего токенов: 113
  Использование контекста: 11.0%
  Время выполнения: 1,250 ms

Ответ модели:
--------------------------------------------------------------------------------
[ПОЛНЫЙ ТЕКСТ ОТВЕТА БЕЗ ОБРЕЗКИ]
--------------------------------------------------------------------------------

================================================================================
ДЕТАЛЬНЫЕ РЕЗУЛЬТАТЫ: EXCEEDS_LIMIT
================================================================================

Параметры теста:
  Тип: EXCEEDS_LIMIT
  Описание: Превышает лимит
  Оценка токенов: >1024 токенов
  Модель: meta-llama/Llama-3.2-1B-Instruct:fastest
  Контекстный лимит: 1024

Промпт (12,450 символов):
--------------------------------------------------------------------------------
[ПОЛНЫЙ ТЕКСТ ПРОМПТА - автогенерированный длинный текст]
--------------------------------------------------------------------------------

Метрики токенов:
  Входные токены: 1835
  Выходные токены: 0
  Всего токенов: 1835
  Использование контекста: 179.2% ⚠️ ПРЕВЫШЕНИЕ ЛИМИТА
  Время выполнения: 2,500 ms

Статус: ❌ ОШИБКА (превышен лимит контекста)

Ответ модели:
--------------------------------------------------------------------------------
[Ответ может быть пустым или усеченным из-за превышения лимита]
--------------------------------------------------------------------------------

⚠️ ВНИМАНИЕ: Промпт превысил лимит контекста модели в 1.79 раза.
Это демонстрирует поведение модели при перегрузке контекста.

================================================================================
ВЫВОДЫ И РЕКОМЕНДАЦИИ
================================================================================

✅ Короткий промпт (11.0%):
   - Безопасное использование контекста
   - Быстрое выполнение
   - Рекомендуется для интерактивных приложений

✅ Средний промпт (40.5%):
   - Оптимальный баланс детализации и эффективности
   - Достаточно места для развёрнутого ответа

⚠️ Длинный промпт (100.0%):
   - Полное использование доступного контекста
   - Мало места для ответа модели
   - Требуется осторожность

❌ Превышение лимита (179.2%):
   - Критическое превышение контекста
   - Модель не может обработать запрос полностью
   - Возможны ошибки или усечение ответа
   - Требуется разбиение на несколько запросов

================================================================================
КОНЕЦ ОТЧЕТА
================================================================================
```

### Реализация экспорта

**Генерация отчёта** (`ModelComparisonViewModel.kt:700-794`):
```kotlin
fun generateTokenAnalysisReport(): String {
    val state = _tokenAnalysisState.value
    if (state !is TokenAnalysisState.Success) {
        return "Нет доступных результатов для экспорта"
    }

    val results = state.results.sortedBy { it.promptType }

    return buildString {
        // Заголовок
        appendLine(repeatString("=", 80))
        appendLine("ОТЧЕТ: АНАЛИЗ ТОКЕНОВ (${llama32Model.displayName.uppercase()})")
        appendLine(repeatString("=", 80))
        appendLine()

        appendLine("Модель: ${llama32Model.modelId}")
        appendLine("Демонстрационный лимит: $LLAMA32_CONTEXT_LIMIT токена (реальный лимит: 128K)")
        appendLine("Дата: ${getCurrentTimestamp()}")
        appendLine("Количество тестов: ${results.size}")
        appendLine()

        // Сводная таблица
        appendLine(repeatString("=", 80))
        appendLine("СВОДНАЯ ТАБЛИЦА МЕТРИК")
        appendLine(repeatString("=", 80))
        appendLine()

        appendLine("%-20s %-8s %-8s %-8s %-12s %s".format(
            "Тип промпта", "Input", "Output", "Total", "% лимита", "Статус"
        ))
        appendLine(repeatString("-", 80))

        results.forEach { result ->
            appendLine("%-20s %-8d %-8d %-8d %-12s %s".format(
                result.promptType,
                result.actualInputTokens,
                result.actualOutputTokens,
                result.totalTokens,
                "${result.percentageUsed.formatDecimals(1)}%",
                if (result.success) "✅" else "❌"
            ))
        }

        // Детальные результаты для каждого теста
        results.forEach { result ->
            appendLine()
            appendLine(repeatString("=", 80))
            appendLine("ДЕТАЛЬНЫЕ РЕЗУЛЬТАТЫ: ${result.promptType}")
            appendLine(repeatString("=", 80))
            appendLine()

            appendLine("Параметры теста:")
            appendLine("  Тип: ${result.promptType}")
            appendLine("  Модель: ${result.requestedModel}")
            appendLine("  Контекстный лимит: ${result.modelContextLimit}")
            appendLine()

            appendLine("Промпт (${result.promptLength} символов):")
            appendLine(repeatString("-", 80))
            appendLine(result.prompt)  // ПОЛНЫЙ промпт, не обрезанный
            appendLine(repeatString("-", 80))
            appendLine()

            appendLine("Метрики токенов:")
            appendLine("  Входные токены: ${result.actualInputTokens}")
            appendLine("  Выходные токены: ${result.actualOutputTokens}")
            appendLine("  Всего токенов: ${result.totalTokens}")
            appendLine("  Использование контекста: ${result.percentageUsed.formatDecimals(1)}%")
            appendLine("  Время выполнения: ${result.executionTime} ms")
            appendLine()

            if (result.success) {
                appendLine("Ответ модели:")
                appendLine(repeatString("-", 80))
                appendLine(result.response)  // ПОЛНЫЙ ответ, не обрезанный
                appendLine(repeatString("-", 80))
            } else {
                appendLine("Статус: ❌ ОШИБКА")
                appendLine("Описание ошибки: ${result.error ?: "Неизвестная ошибка"}")
            }
        }

        // Выводы
        appendLine()
        appendLine(repeatString("=", 80))
        appendLine("ВЫВОДЫ И РЕКОМЕНДАЦИИ")
        appendLine(repeatString("=", 80))
        // ... выводы для каждого типа промпта ...
    }
}
```

**Сохранение файла**:
```kotlin
// В TopAppBar
IconButton(onClick = {
    val report = if (comparisonMode.value == ComparisonMode.TOKEN_ANALYSIS) {
        viewModel.generateTokenAnalysisReport()
    } else {
        viewModel.generateReport()
    }

    val filename = if (comparisonMode.value == ComparisonMode.TOKEN_ANALYSIS) {
        "token_analysis_${getCurrentTimestamp().replace(" ", "_").replace(":", "-")}.txt"
    } else {
        "model_comparison_${getCurrentTimestamp().replace(" ", "_").replace(":", "-")}.txt"
    }

    saveTextToFile(report, filename)
}) {
    Icon(imageVector = Icons.Default.Save, contentDescription = "Сохранить отчёт")
}
```

## Технические детали

### Multiplatform совместимость

**Использование expect/actual для getCurrentTimeMillis**:

```kotlin
// shared/src/commonMain/kotlin/.../util/TimeUtils.kt
expect fun getCurrentTimeMillis(): Long

// shared/src/jvmMain/kotlin/.../util/TimeUtils.jvm.kt
actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

// shared/src/wasmJsMain/kotlin/.../util/TimeUtils.wasmJs.kt
actual fun getCurrentTimeMillis(): Long {
    return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
```

**Форматирование чисел без String.format**:

```kotlin
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
```

### Обработка ошибок

**Try-catch в runSingleTokenTest**:
```kotlin
private suspend fun runSingleTokenTest(promptType: PromptType): TokenTestResult {
    return try {
        val prompt = TokenTestPrompts.getPromptByType(promptType)
        val estimatedTokens = estimateTokens(prompt)

        val result = hfService.generateText(
            modelId = llama32Model.modelId,
            prompt = prompt,
            maxTokens = 500,
            temperature = 0.7
        )

        when {
            result.isSuccess -> {
                val response = result.getOrThrow()
                TokenTestResult(
                    promptType = promptType.name,
                    prompt = prompt,  // Полный промпт
                    promptLength = prompt.length,
                    estimatedPromptTokens = estimatedTokens,
                    actualInputTokens = response.tokenUsage.promptTokens,
                    actualOutputTokens = response.tokenUsage.completionTokens,
                    totalTokens = response.tokenUsage.totalTokens,
                    modelContextLimit = LLAMA32_CONTEXT_LIMIT,
                    percentageUsed = (response.tokenUsage.totalTokens.toDouble() / LLAMA32_CONTEXT_LIMIT) * 100,
                    success = true,
                    response = response.generatedText,
                    executionTime = response.executionTime,
                    requestedModel = llama32Model.modelId,
                    actualModelUsed = response.actualModelUsed
                )
            }
            else -> {
                // Ошибка от API
                TokenTestResult(
                    promptType = promptType.name,
                    prompt = prompt,
                    promptLength = prompt.length,
                    estimatedPromptTokens = estimatedTokens,
                    actualInputTokens = 0,
                    actualOutputTokens = 0,
                    totalTokens = 0,
                    modelContextLimit = LLAMA32_CONTEXT_LIMIT,
                    percentageUsed = 0.0,
                    success = false,
                    error = result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    } catch (e: Exception) {
        // Критическая ошибка
        TokenTestResult(
            promptType = promptType.name,
            prompt = "",
            promptLength = 0,
            estimatedPromptTokens = 0,
            actualInputTokens = 0,
            actualOutputTokens = 0,
            totalTokens = 0,
            modelContextLimit = LLAMA32_CONTEXT_LIMIT,
            percentageUsed = 0.0,
            success = false,
            error = "Exception: ${e.message}"
        )
    }
}
```

### Progress tracking

**Отслеживание прогресса тестов**:
```kotlin
suspend fun runTokenAnalysis() {
    if (_tokenAnalysisState.value is TokenAnalysisState.Loading) return

    viewModelScope.launch {
        val promptTypes = PromptType.entries
        val results = mutableListOf<TokenTestResult>()

        promptTypes.forEachIndexed { index, promptType ->
            // Обновляем состояние: текущий тест и прогресс
            _tokenAnalysisState.value = TokenAnalysisState.Loading(
                currentTest = promptType,
                completedTests = index,
                totalTests = promptTypes.size
            )

            val result = runSingleTokenTest(promptType)
            results.add(result)
        }

        _tokenAnalysisState.value = TokenAnalysisState.Success(results)
    }
}
```

**Отображение в UI**:
```kotlin
@Composable
fun TokenAnalysisLoadingCard(state: TokenAnalysisState.Loading) {
    Card {
        Row(modifier = Modifier.padding(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "Выполняется анализ...",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Текущий тест: ${state.currentTest?.displayName ?: "..."}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Прогресс: ${state.completedTests} из ${state.totalTests}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
```

## Использование

### Быстрый старт

1. **Запустить прокси-сервер**:
   ```bash
   ./gradlew :server:run
   ```
   Дождаться: `INFO - Responding at http://0.0.0.0:8080`

2. **Запустить приложение**:
   ```bash
   ./gradlew :composeApp:run
   ```

3. **Перейти в режим анализа токенов**:
   - Открыть экран "Model Comparison"
   - Нажать кнопку "Переключить на: 💎 Анализ токенов"

4. **Запустить анализ**:
   - Нажать "▶ Запустить анализ всех типов промптов"
   - Дождаться завершения 4 тестов (~10-15 секунд)

5. **Изучить результаты**:
   - Сравнительная таблица показывает все метрики
   - Детальные карточки для каждого типа промпта
   - Прогресс-бары визуализируют использование контекста

6. **Экспортировать отчёт**:
   - Нажать 💾 в TopAppBar
   - Выбрать место сохранения
   - Файл: `token_analysis_2025-11-13_14-30-22.txt`

### Интерпретация результатов

#### SHORT (~11% контекста)
**Ожидаемое поведение**:
- ✅ Успешное выполнение
- ⚡ Быстрый ответ (<2 секунд)
- 📊 Input: ~15-30 токенов
- 📊 Output: ~50-100 токенов
- 🟢 Зелёный прогресс-бар

**Применение**: Интерактивные приложения, быстрые Q&A

#### MEDIUM (~40% контекста)
**Ожидаемое поведение**:
- ✅ Успешное выполнение
- ⚡ Нормальное время ответа (2-4 секунды)
- 📊 Input: ~150-220 токенов
- 📊 Output: ~200-300 токенов
- 🟢 Зелёный прогресс-бар

**Применение**: Структурированные запросы, детальные объяснения

#### LONG (~100% контекста)
**Ожидаемое поведение**:
- ✅ Успешное выполнение (на грани)
- ⏱️ Увеличенное время ответа (3-5 секунд)
- 📊 Input: ~750-900 токенов
- 📊 Output: ~200-400 токенов
- 🟡 Жёлтый прогресс-бар (приближение к лимиту)

**Применение**: Максимально детальные запросы, статьи

#### EXCEEDS_LIMIT (~179% контекста)
**Ожидаемое поведение**:
- ⚠️ Возможна ошибка или усечение
- ⏱️ Время ответа непредсказуемо
- 📊 Input: ~1500-2000 токенов
- 📊 Output: 0 или очень мало
- 🔴 Красный прогресс-бар (критическое превышение)

**Что происходит**:
```
Использование: 179.2%
Значение: Промпт на 79.2% больше лимита модели
```

Модель может:
1. **Вернуть ошибку** - "Context length exceeded"
2. **Усечь контекст** - обработать только первые N токенов
3. **Вернуть пустой ответ** - completionTokens = 0

**Применение**: Демонстрация ограничений, обучение

### Сценарий использования

**Задача**: Понять, какой размер промпта оптимален для вашей задачи

**Шаги**:
1. Запустить анализ всех типов
2. Изучить таблицу метрик
3. Найти sweet spot (40-70% использования)
4. Адаптировать свои промпты под найденный размер

**Пример выводов**:
- "SHORT слишком короткий - ответы недостаточно детальные"
- "MEDIUM оптимален - баланс детализации и эффективности"
- "LONG использует 100% - оставляет мало места для ответа"
- "EXCEEDS_LIMIT показывает лимит - нужно разбивать на части"

## Ожидаемые результаты

### Типичные метрики для Llama 3.2 1B

| Тип | Input | Output | Total | % лимита | Время | Статус |
|-----|-------|--------|-------|----------|-------|--------|
| SHORT | 15-30 | 50-100 | 65-130 | 6-13% | 1-2s | ✅ |
| MEDIUM | 150-220 | 200-300 | 350-520 | 34-51% | 2-4s | ✅ |
| LONG | 750-900 | 200-400 | 950-1300 | 93-127% | 3-5s | ⚠️ |
| EXCEEDS | 1500-2000 | 0-100 | 1500-2100 | 146-205% | 2-6s | ❌ |

**Примечания**:
- LONG может превысить 100% из-за округления токенизации
- EXCEEDS_LIMIT обычно возвращает пустой или очень короткий ответ
- Время зависит от загрузки API

### Сравнение с реальными лимитами

**Если бы использовался реальный лимит 128K**:
```
SHORT:    65 / 131,072 = 0.05%  (незаметно)
MEDIUM:   415 / 131,072 = 0.32% (незаметно)
LONG:     1,024 / 131,072 = 0.78% (незаметно)
EXCEEDS:  1,835 / 131,072 = 1.40% (незаметно)
```

Поэтому используется **искусственный лимит 1024** для демонстрации!

## Выводы

### Ключевые достижения

✅ **Реальные метрики токенов** - точные данные от HuggingFace API
✅ **4 типа промптов** - от короткого до превышающего лимит
✅ **Визуализация лимитов** - прогресс-бары с цветовой индикацией
✅ **Образовательная демонстрация** - показывает поведение при превышении
✅ **Полные отчёты** - экспорт с полными текстами промптов и ответов
✅ **Нормализация моделей** - корректная обработка имен от провайдеров
✅ **Dual-mode архитектура** - переключение между сравнением и анализом
✅ **Multiplatform** - работает на всех поддерживаемых платформах

### Технические уроки

**1. Token estimation (оценка токенов)**:
- Русский текст: ~1.3 токена на слово
- Английский текст: ~0.75 токена на слово
- Реальные токены от API точнее оценок

**2. Model name normalization (нормализация имен)**:
- Провайдеры нормализуют имена (lowercase, удаление суффиксов)
- Важно не путать нормализацию с подменой модели
- Используйте нормализованное сравнение для валидации

**3. Context limits (лимиты контекста)**:
- Современные модели имеют огромные контексты (128K+)
- Искусственные лимиты полезны для демонстрации
- >100% использования приводит к ошибкам или усечению

**4. Full text reporting (полные тексты в отчётах)**:
- Критично для анализа и воспроизводимости
- Обрезка `.take()` скрывает важные детали
- Динамические заголовки предотвращают устаревание документации

### Применение

Система полезна для:
- **Обучения** - понимание работы токенов и лимитов
- **Планирования промптов** - выбор оптимального размера
- **Тестирования моделей** - проверка поведения на разных промптах
- **Оптимизации затрат** - анализ использования токенов
- **Документирования** - экспорт полных результатов

### Основной урок

**Понимание лимитов токенов критично для эффективного использования LLM:**

- **0-70% контекста**: Оптимальная зона, достаточно места для ответа
- **70-90% контекста**: Приближение к лимиту, требуется внимание
- **90-100% контекста**: Критическая зона, мало места для ответа
- **>100% контекста**: Превышение лимита, возможны ошибки

Искусственный лимит 1024 токена позволяет наглядно продемонстрировать эти зоны.

## Файлы проекта

### Shared модуль
- `shared/src/commonMain/kotlin/com/example/ai_window/model/HuggingFaceModels.kt` - модели (+1 модель)
- `shared/src/commonMain/kotlin/com/example/ai_window/model/ModelComparison.kt` - структуры токен-анализа (+203 строки)
- `shared/src/commonMain/kotlin/com/example/ai_window/service/HuggingFaceService.kt` - нормализация моделей (+50 строк)

### ComposeApp модуль
- `composeApp/src/commonMain/kotlin/com/example/ai_window/ModelComparisonViewModel.kt` - логика токен-анализа (+268 строк)
- `composeApp/src/commonMain/kotlin/com/example/ai_window/ModelComparisonScreen.kt` - dual-mode UI (+528 строк)
- `composeApp/src/commonMain/kotlin/com/example/ai_window/App.kt` - обновлена вкладка

### Platform-specific
- `shared/src/{platform}Main/kotlin/.../util/TimeUtils.*.kt` - getCurrentTimeMillis() для всех платформ

### Server
- `server/src/main/kotlin/com/example/ai_window/Application.kt` - прокси /api/huggingface

## Дальнейшие улучшения

- [ ] Поддержка других моделей с разными лимитами (512, 2048, 4096)
- [ ] Realtime оценка токенов при вводе текста
- [ ] График зависимости времени от размера промпта
- [ ] Token-based pricing калькулятор
- [ ] Сравнение efficiency (токены/секунда) разных моделей
- [ ] Streaming режим с подсчётом токенов в реальном времени
- [ ] Визуализация распределения токенов (prompt vs completion)
- [ ] A/B тестирование разных стратегий использования контекста
- [ ] История анализов с возможностью сравнения
