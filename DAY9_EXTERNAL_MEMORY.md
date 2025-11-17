# День 9: Внешняя память (SQLDelight Persistence)

## Цель

Создать систему долговременной памяти для приложения с автоматическим сохранением контекста и промежуточных результатов:
- **SQLite персистентность** - надежное хранение данных на устройстве
- **Автоматическое сохранение** - прозрачное для пользователя сохранение всех сообщений
- **Восстановление контекста** - загрузка истории при запуске приложения
- **Multiplatform поддержка** - работа на Android, iOS, Desktop (JVM)
- **Metadata сохранение** - полная информация о сообщениях (токены, compression, parsing)

## Выбор технологии персистентности

### Требования к решению

Для Kotlin Multiplatform проекта нужна библиотека с поддержкой:
- **SQLite** - стандарт для локального хранения
- **Type-safe queries** - безопасность типов на этапе компиляции
- **Multiplatform** - Android, iOS, JVM, JS (опционально)
- **Coroutines** - асинхронные операции
- **Migrations** - поддержка миграций схемы

### Сравнение вариантов

**Вариант 1: Room Persistence Library**
```kotlin
// Android-only
@Entity
data class ChatMessage(...)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM messages")
    suspend fun getAll(): List<ChatMessage>
}
```
❌ **Проблема**: Только Android, нет KMP поддержки

**Вариант 2: Realm Kotlin**
```kotlin
class ChatMessage : RealmObject {
    var id: String = ""
    var text: String = ""
}
```
⚠️ **Ограничения**:
- Тяжеловесная библиотека
- Специфичная объектная модель (наследование от RealmObject)
- Не все платформы поддерживаются

**Вариант 3: SQLDelight**
```sql
-- ChatMessage.sq
CREATE TABLE ChatMessage (
    id TEXT PRIMARY KEY NOT NULL,
    text TEXT NOT NULL,
    isUser INTEGER NOT NULL
);
```
✅ **Выбрано**:
- Нативная KMP поддержка
- Type-safe Kotlin API генерируется из SQL
- Легковесное решение
- Полный контроль над SQL
- Поддержка Android, iOS, JVM, JS

### Финальное решение: SQLDelight 2.0.2

```kotlin
// gradle/libs.versions.toml
[versions]
sqldelight = "2.0.2"

[libraries]
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutinesExtensions = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-androidDriver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-nativeDriver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-jvmDriver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
```

**Характеристики:**
- ✅ Официальная поддержка Kotlin Multiplatform
- ✅ Type-safe API из SQL схемы
- ✅ Coroutines-first подход
- ✅ Platform-specific драйверы для всех платформ
- ⚠️ WASM не поддерживается (временно отключен)

## Архитектура решения

### Общая структура

```
Database Layer
├─ SQL Schema (.sq файлы)
│   ├─ ChatMessage.sq - схема таблицы сообщений
│   ├─ ExperimentResult.sq - результаты экспериментов (будущее)
│   └─ ExpertMessage.sq - цепочки экспертов (будущее)
│
├─ Generated Code (SQLDelight)
│   ├─ ChatDatabase.kt - интерфейс БД
│   ├─ ChatDatabaseImpl.kt - реализация
│   ├─ ChatMessage.kt - data class
│   └─ ChatMessageQueries.kt - type-safe queries
│
├─ Drivers (expect/actual)
│   ├─ DatabaseDriverFactory.kt - expect class
│   ├─ DatabaseDriverFactory.android.kt - AndroidSqliteDriver
│   ├─ DatabaseDriverFactory.ios.kt - NativeSqliteDriver
│   ├─ DatabaseDriverFactory.jvm.kt - JdbcSqliteDriver
│   └─ DatabaseDriverFactory.js.kt - UnsupportedOperationException
│
├─ Repository Layer
│   ├─ ChatRepository.kt - CRUD операции
│   ├─ Mappers.kt - domain ↔ database конвертация
│   └─ DatabaseHolder.kt - singleton управление
│
└─ Integration
    ├─ DatabaseInit.kt - утилита инициализации
    └─ ChatViewModel.kt - автосохранение
```

### Схема базы данных

**ChatMessage.sq** - главная таблица для сообщений чата:

```sql
-- Day 9: Таблица для хранения сообщений чата
CREATE TABLE IF NOT EXISTS ChatMessage (
    id TEXT PRIMARY KEY NOT NULL,
    text TEXT NOT NULL,
    isUser INTEGER NOT NULL,              -- Boolean → INTEGER (0/1)
    timestamp INTEGER NOT NULL,
    title TEXT,
    metadata_json TEXT,                   -- ResponseMetadata (JSON)
    parseWarning TEXT,
    isSummary INTEGER NOT NULL DEFAULT 0, -- Boolean → INTEGER (0/1)
    summarizedCount INTEGER,
    originalIds_json TEXT,                -- List<String> (JSON)
    estimatedTokens INTEGER
);

-- Индекс для быстрой сортировки по времени
CREATE INDEX IF NOT EXISTS idx_timestamp ON ChatMessage(timestamp);

-- Индекс для поиска summaries
CREATE INDEX IF NOT EXISTS idx_isSummary ON ChatMessage(isSummary);
```

**Почему INTEGER для Boolean:**
- SQLite не имеет нативного Boolean типа
- Стандартная практика: 0 = false, 1 = true
- SQLDelight bug: `INTEGER AS Boolean` генерирует некорректный `import Boolean`
- Решение: Ручная конвертация в Mappers.kt

**JSON поля:**
```kotlin
metadata_json TEXT    // ResponseMetadata сериализуется в JSON
originalIds_json TEXT // List<String> сериализуется в JSON
```

Использование JSON для:
- Гибкости структуры (metadata может меняться)
- Избегания множественных таблиц
- Сохранения сложных типов

### Запросы

**selectAll** - все сообщения по времени:
```sql
SELECT *
FROM ChatMessage
ORDER BY timestamp ASC;
```

**selectRecent** - последние N сообщений:
```sql
SELECT *
FROM ChatMessage
ORDER BY timestamp DESC
LIMIT ?;
```

**selectWithPagination** - пагинация:
```sql
SELECT *
FROM ChatMessage
ORDER BY timestamp ASC
LIMIT ? OFFSET ?;
```

**insert** - вставка с заменой:
```sql
INSERT OR REPLACE INTO ChatMessage(
    id, text, isUser, timestamp, title,
    metadata_json, parseWarning, isSummary,
    summarizedCount, originalIds_json, estimatedTokens
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

**deleteOldMessages** - очистка старых:
```sql
DELETE FROM ChatMessage
WHERE id NOT IN (
    SELECT id FROM ChatMessage
    ORDER BY timestamp DESC
    LIMIT ?
);
```

## Platform-specific драйверы

### expect/actual паттерн

**Общий интерфейс** (`DatabaseDriverFactory.kt`):
```kotlin
package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Day 9: Platform-specific database driver factory.
 * Каждая платформа предоставляет свою реализацию.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

### Android реализация

**DatabaseDriverFactory.android.kt**:
```kotlin
package com.example.ai_window.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = ChatDatabase.Schema,
            context = context,
            name = "chat.db"
        )
    }
}
```

**Особенности:**
- Требует `Context` (передается из Activity/Application)
- Хранит БД в app-specific storage: `/data/data/com.example.ai_window/databases/chat.db`
- Автоматическое управление lifecycle

**Инициализация Android** (`PlatformDatabaseFactory.android.kt`):
```kotlin
object AndroidDatabaseInit {
    var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}

actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = AndroidDatabaseInit.context
        ?: throw IllegalStateException("AndroidDatabaseInit not initialized")
    return DatabaseDriverFactory(context)
}
```

### iOS реализация

**DatabaseDriverFactory.ios.kt**:
```kotlin
package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = ChatDatabase.Schema,
            name = "chat.db"
        )
    }
}
```

**Особенности:**
- Не требует Context
- Хранит БД в app documents directory
- Использует нативный SQLite framework iOS

### JVM/Desktop реализация

**DatabaseDriverFactory.jvm.kt**:
```kotlin
package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Создаем директорию для БД в user home
        val databasePath = File(System.getProperty("user.home"), ".ai_window")
        databasePath.mkdirs()

        val databaseFile = File(databasePath, "chat.db")
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"

        // Создаем или открываем БД
        val driver: SqlDriver = JdbcSqliteDriver(url)

        // Создаем схему (безопасно благодаря CREATE TABLE IF NOT EXISTS)
        ChatDatabase.Schema.create(driver)

        return driver
    }
}
```

**Особенности:**
- Хранит БД в `~/.ai_window/chat.db` (home directory пользователя)
- JDBC драйвер для SQLite
- Явное создание схемы при первом запуске
- Переносимость между запусками

**Проверка БД**:
```bash
ls -lh ~/.ai_window/
# -rw-r--r--  1 user  staff    20K Nov 16 23:34 chat.db

sqlite3 ~/.ai_window/chat.db ".schema ChatMessage"
# CREATE TABLE ChatMessage (...);
# CREATE INDEX idx_timestamp ON ChatMessage(timestamp);
```

### JS/WASM реализация

**DatabaseDriverFactory.js.kt**:
```kotlin
package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        throw UnsupportedOperationException(
            "Database is not supported on JS platform"
        )
    }
}
```

**Почему не поддерживается:**
- SQLDelight 2.0.2 не имеет JS/WASM драйвера
- Web приложения могут использовать IndexedDB напрямую
- Альтернатива: sql.js (SQLite в WebAssembly)

**WASM отключен**:
```kotlin
// composeApp/build.gradle.kts

// Day 9: wasmJs временно отключен из-за несовместимости с SQLDelight
// @OptIn(ExperimentalWasmDsl::class)
// wasmJs {
//     browser()
//     binaries.executable()
// }
```

## Repository слой

### ChatRepository

**Интерфейс для работы с сообщениями**:

```kotlin
package com.example.ai_window.database

import com.example.ai_window.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Day 9: Repository для CRUD операций с сообщениями чата.
 * Все операции асинхронные (suspend) и выполняются на Dispatchers.Default.
 */
class ChatRepository(private val database: ChatDatabase) {

    /**
     * Сохраняет сообщение в БД.
     */
    suspend fun saveChatMessage(message: ChatMessage) = withContext(Dispatchers.Default) {
        val dbMessage = message.toDbModel()
        database.chatMessageQueries.insert(
            id = dbMessage.id,
            text = dbMessage.text,
            isUser = dbMessage.isUser,
            timestamp = dbMessage.timestamp,
            title = dbMessage.title,
            metadata_json = dbMessage.metadata_json,
            parseWarning = dbMessage.parseWarning,
            isSummary = dbMessage.isSummary,
            summarizedCount = dbMessage.summarizedCount,
            originalIds_json = dbMessage.originalIds_json,
            estimatedTokens = dbMessage.estimatedTokens
        )
    }

    /**
     * Получает все сообщения, отсортированные по времени.
     */
    suspend fun getAllMessages(): List<ChatMessage> = withContext(Dispatchers.Default) {
        database.chatMessageQueries
            .selectAll()
            .executeAsList()
            .map { it.toDomainModel() }
    }

    /**
     * Получает последние N сообщений.
     */
    suspend fun getRecentMessages(limit: Int): List<ChatMessage> = withContext(Dispatchers.Default) {
        database.chatMessageQueries
            .selectRecent(limit.toLong())
            .executeAsList()
            .map { it.toDomainModel() }
    }

    /**
     * Удаляет все сообщения.
     */
    suspend fun deleteAllMessages() = withContext(Dispatchers.Default) {
        database.chatMessageQueries.deleteAll()
    }

    /**
     * Получает количество сообщений.
     */
    suspend fun getMessageCount(): Long = withContext(Dispatchers.Default) {
        database.chatMessageQueries.count().executeAsOne()
    }
}
```

**Ключевые особенности:**
- `withContext(Dispatchers.Default)` - все операции на background thread
- `executeAsList()` - синхронное выполнение (без async благодаря generateAsync = false)
- `map { it.toDomainModel() }` - конвертация database → domain моделей

### Mappers

**Конвертация между domain и database моделями**:

```kotlin
package com.example.ai_window.database

import com.example.ai_window.model.ChatMessage as DomainChatMessage
import com.example.ai_window.model.ResponseMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.aiwindow.ChatMessage as DbChatMessage

/**
 * Day 9: Mappers для конвертации между database моделями и domain моделями.
 */

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    isLenient = true
}

/**
 * Конвертирует domain ChatMessage в database ChatMessage.
 */
fun DomainChatMessage.toDbModel(): DbChatMessage {
    return DbChatMessage(
        id = id,
        text = text,
        isUser = if (isUser) 1L else 0L, // Boolean → Long
        timestamp = timestamp,
        title = title,
        metadata_json = metadata?.let { json.encodeToString(it) },
        parseWarning = parseWarning,
        isSummary = if (isSummary) 1L else 0L, // Boolean → Long
        summarizedCount = summarizedCount?.toLong(),
        originalIds_json = originalIds?.let { json.encodeToString(it) },
        estimatedTokens = estimatedTokens?.toLong()
    )
}

/**
 * Конвертирует database ChatMessage в domain ChatMessage.
 */
fun DbChatMessage.toDomainModel(): DomainChatMessage {
    return DomainChatMessage(
        id = id,
        text = text,
        isUser = isUser == 1L, // Long → Boolean
        timestamp = timestamp,
        title = title,
        metadata = metadata_json?.let {
            try {
                json.decodeFromString<ResponseMetadata>(it)
            } catch (e: Exception) {
                null
            }
        },
        parseWarning = parseWarning,
        isSummary = isSummary == 1L, // Long → Boolean
        summarizedCount = summarizedCount?.toInt(),
        originalIds = originalIds_json?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                null
            }
        },
        estimatedTokens = estimatedTokens?.toInt()
    )
}
```

**Конвертации:**
1. **Boolean ↔ Long**: SQLite ограничение
   ```kotlin
   // Domain → Database
   isUser = if (isUser) 1L else 0L

   // Database → Domain
   isUser = isUser == 1L
   ```

2. **Int ↔ Long**: SQLite использует INTEGER (64-bit)
   ```kotlin
   // Domain → Database
   summarizedCount = summarizedCount?.toLong()

   // Database → Domain
   summarizedCount = summarizedCount?.toInt()
   ```

3. **Complex types → JSON**: Сложные объекты
   ```kotlin
   // Domain → Database
   metadata_json = metadata?.let { json.encodeToString(it) }

   // Database → Domain
   metadata = metadata_json?.let {
       try {
           json.decodeFromString<ResponseMetadata>(it)
       } catch (e: Exception) {
           null  // Graceful degradation
       }
   }
   ```

**Обработка ошибок десериализации:**
- `try-catch` для JSON парсинга
- Возвращает `null` при ошибке
- Приложение продолжает работать с частичными данными

### DatabaseHolder

**Singleton управление БД**:

```kotlin
package com.example.ai_window.database

/**
 * Day 9: Singleton holder для Database и Repositories.
 * Обеспечивает единственный экземпляр БД на всё приложение.
 */
object DatabaseHolder {
    private var database: ChatDatabase? = null
    private var chatRepository: ChatRepository? = null

    /**
     * Инициализирует БД с заданным драйвером.
     * Должно быть вызвано один раз при старте приложения.
     */
    fun init(driverFactory: DatabaseDriverFactory) {
        if (database == null) {
            val driver = driverFactory.createDriver()

            // Day 9: Boolean конвертация выполняется в Mappers.kt (Long ↔ Boolean)
            database = ChatDatabase(driver = driver)

            chatRepository = ChatRepository(database!!)
        }
    }

    /**
     * Получает ChatRepository.
     * Throws IllegalStateException если БД не инициализирована.
     */
    fun getChatRepository(): ChatRepository {
        return chatRepository ?: throw IllegalStateException(
            "Database not initialized. Call DatabaseHolder.init() first."
        )
    }

    /**
     * Очищает все данные (для тестов).
     */
    suspend fun clearAll() {
        chatRepository?.deleteAllMessages()
    }
}
```

**Паттерн Singleton:**
- Один экземпляр БД на всё приложение
- Lazy инициализация
- Thread-safe (object в Kotlin)
- Централизованное управление

## Интеграция с приложением

### Инициализация в App.kt

**Startup инициализация**:

```kotlin
@Composable
fun App() {
    // Day 9: Инициализация БД при запуске
    remember {
        try {
            com.example.ai_window.util.initDatabase()
        } catch (e: Exception) {
            println("Failed to initialize database: ${e.message}")
        }
        true
    }

    // ... остальной код приложения
}
```

**DatabaseInit.kt утилита**:

```kotlin
package com.example.ai_window.util

import com.example.ai_window.database.DatabaseHolder
import com.example.ai_window.database.createDatabaseDriverFactory

fun initDatabase() {
    DatabaseHolder.init(createDatabaseDriverFactory())
}
```

**PlatformDatabaseFactory** (expect/actual):

```kotlin
// commonMain
expect fun createDatabaseDriverFactory(): DatabaseDriverFactory

// jvmMain
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
    return DatabaseDriverFactory()
}

// androidMain
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = AndroidDatabaseInit.context
        ?: throw IllegalStateException("Call AndroidDatabaseInit.init() first")
    return DatabaseDriverFactory(context)
}
```

### Автосохранение в ChatViewModel

**Загрузка сохраненных сообщений при старте**:

```kotlin
class ChatViewModel : ViewModel() {
    private val _messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val messages: State<List<ChatMessage>> = _messages

    // Day 9: Опциональный ChatRepository
    private val chatRepository: ChatRepository? = try {
        DatabaseHolder.getChatRepository()
    } catch (e: Exception) {
        null // БД опциональна - приложение работает без неё
    }

    init {
        loadSavedMessages()
    }

    /**
     * Day 9: Загружает сохраненные сообщения при старте.
     */
    private fun loadSavedMessages() {
        viewModelScope.launch {
            try {
                val savedMessages = chatRepository?.getAllMessages() ?: emptyList()
                if (savedMessages.isNotEmpty()) {
                    _messages.value = savedMessages
                    println("✅ Loaded ${savedMessages.size} messages from database")
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load messages: ${e.message}")
            }
        }
    }
}
```

**Автоматическое сохранение при отправке**:

```kotlin
suspend fun sendMessage(question: String) {
    // Создаем пользовательское сообщение
    val userMessage = ChatMessage(
        id = UUID.randomUUID().toString(),
        text = question,
        isUser = true,
        timestamp = Clock.System.now().toEpochMilliseconds(), // Day 9: timestamp
        title = null,
        metadata = null,
        parseWarning = null,
        isSummary = false,
        summarizedCount = null,
        originalIds = null,
        estimatedTokens = null
    )

    // Добавляем в UI
    _messages.value += userMessage

    // Day 9: Сохраняем в БД
    saveMessage(userMessage)

    // Отправляем запрос к API
    val result = yandexGptService.generateText(...)

    if (result.isSuccess) {
        val response = result.getOrThrow()

        // Создаем ответ AI с metadata
        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = response.generatedText,
            isUser = false,
            timestamp = Clock.System.now().toEpochMilliseconds(), // Day 9: timestamp
            title = response.title,
            metadata = ResponseMetadata(...), // Day 9: полная metadata
            parseWarning = response.parseWarning,
            isSummary = false,
            summarizedCount = null,
            originalIds = null,
            estimatedTokens = response.tokenUsage?.totalTokens // Day 9: токены
        )

        // Добавляем в UI
        _messages.value += aiMessage

        // Day 9: Сохраняем в БД
        saveMessage(aiMessage)
    }
}

/**
 * Day 9: Сохраняет сообщение в БД (background thread).
 */
private fun saveMessage(message: ChatMessage) {
    viewModelScope.launch {
        try {
            chatRepository?.saveChatMessage(message)
        } catch (e: Exception) {
            println("⚠️ Failed to save message: ${e.message}")
            // Не показываем ошибку пользователю - сохранение опционально
        }
    }
}
```

**Очистка чата**:

```kotlin
fun clearChat() {
    _messages.value = emptyList()
    _isLoading.value = false

    // Day 9: Очищаем БД
    viewModelScope.launch {
        try {
            chatRepository?.deleteAllMessages()
            println("✅ Database cleared")
        } catch (e: Exception) {
            println("⚠️ Failed to clear database: ${e.message}")
        }
    }
}
```

**Ключевые принципы:**
- ✅ **Опциональность**: БД может быть недоступна (JS/WASM платформы)
- ✅ **Silent failures**: Ошибки сохранения не мешают работе UI
- ✅ **Timestamps**: Все сообщения с точным временем создания
- ✅ **Metadata**: Сохраняется полная информация (токены, parsing, compression)

## Технические решения и проблемы

### 1. Boolean type mapping

**Проблема**: SQLDelight генерирует некорректный import

Изначальная схема с type mapping:
```sql
CREATE TABLE ChatMessage (
    isUser INTEGER AS Boolean NOT NULL
);
```

Сгенерированный код:
```kotlin
package com.example.aiwindow

import Boolean  // ❌ Ошибка компиляции: Unresolved reference 'Boolean'
import kotlin.Long
import kotlin.String

public data class ChatMessage(
  public val isUser: Boolean,  // Тип Boolean, но import неверный
  // ...
)
```

**Решение**: Убрать type mapping, использовать INTEGER

Исправленная схема:
```sql
CREATE TABLE ChatMessage (
    isUser INTEGER NOT NULL,  -- Без AS Boolean
);
```

Сгенерированный код:
```kotlin
package com.example.aiwindow

import kotlin.Long
import kotlin.String

public data class ChatMessage(
  public val isUser: Long,  // ✅ Компилируется
  // ...
)
```

Ручная конвертация в Mappers.kt:
```kotlin
// toDbModel
isUser = if (isUser) 1L else 0L

// toDomainModel
isUser = isUser == 1L
```

**Урок**: SQLDelight type mapping имеет баги, лучше использовать нативные SQLite типы

### 2. Async vs Sync API

**Проблема**: generateAsync создает сложный API

Конфигурация с async:
```kotlin
sqldelight {
    databases {
        create("ChatDatabase") {
            packageName.set("com.example.ai_window.database")
            generateAsync.set(true)  // Асинхронный API
        }
    }
}
```

Schema.create() возвращает `QueryResult.AsyncValue<Unit>`:
```kotlin
override fun create(driver: SqlDriver): QueryResult.AsyncValue<Unit> = QueryResult.AsyncValue {
    driver.execute(null, """
        CREATE TABLE IF NOT EXISTS ChatMessage (...)
    """.trimMargin(), 0).await()  // Требуется .await()
}
```

Вызов требует корутин:
```kotlin
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(url)

        // ❌ Ошибка: нужен suspend context
        ChatDatabase.Schema.create(driver)

        return driver
    }
}
```

**Решение**: Отключить generateAsync

```kotlin
sqldelight {
    databases {
        create("ChatDatabase") {
            packageName.set("com.example.ai_window.database")
            // generateAsync.set(false) - по умолчанию false
        }
    }
}
```

Синхронный API:
```kotlin
override fun create(driver: SqlDriver) {
    driver.execute(null, """
        CREATE TABLE IF NOT EXISTS ChatMessage (...)
    """.trimMargin(), 0)
}
```

Простой вызов:
```kotlin
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(url)

        ChatDatabase.Schema.create(driver)  // ✅ Работает

        return driver
    }
}
```

**Урок**: Async API полезен для большого количества миграций, но для простых случаев sync API проще

### 3. WASM platform incompatibility

**Проблема**: SQLDelight 2.0.2 не поддерживает WASM

```bash
> Could not resolve all files for configuration ':shared:wasmJsMainApi'.
   > Could not resolve app.cash.sqldelight:runtime:2.0.2.
     > No matching variant of app.cash.sqldelight:runtime:2.0.2 was found.
       Unresolved platforms: [wasmJs]
```

**Решение**: Временно отключить WASM

`shared/build.gradle.kts`:
```kotlin
kotlin {
    androidTarget { ... }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { ... }

    jvm()

    js {
        browser()
        binaries.executable()
    }

    // Day 9: wasmJs временно отключен из-за несовместимости с SQLDelight
    // @OptIn(ExperimentalWasmDsl::class)
    // wasmJs {
    //     browser()
    //     binaries.executable()
    // }
}
```

`composeApp/build.gradle.kts` - то же самое

**Будущее**: Ждем SQLDelight 2.1+ с WASM поддержкой или используем альтернативы:
- `sql.js` (SQLite в WebAssembly)
- IndexedDB API напрямую
- Realm Kotlin (если добавит WASM)

### 4. Package name normalization

**Проблема**: SQLDelight генерирует код в другом пакете

Конфигурация:
```kotlin
sqldelight {
    databases {
        create("ChatDatabase") {
            packageName.set("com.example.ai_window.database")
        }
    }
}
```

Сгенерированные классы:
```
shared/build/generated/sqldelight/code/ChatDatabase/commonMain/
└── com/example/
    ├── ai_window/database/
    │   ├── ChatDatabase.kt
    │   └── shared/ChatDatabaseImpl.kt
    └── aiwindow/  ← Другой пакет!
        ├── ChatMessage.kt
        └── ChatMessageQueries.kt
```

SQLDelight нормализует имена:
- `com.example.ai_window` (схема) → `com.example.aiwindow` (generated)
- Удаляет underscores из последнего сегмента

**Решение**: Использовать правильный import

```kotlin
// Mappers.kt
import com.example.ai_window.model.ChatMessage as DomainChatMessage
import com.example.aiwindow.ChatMessage as DbChatMessage  // Нормализованный пакет
```

**Урок**: SQLDelight нормализует package names, используйте type aliases для ясности

### 5. ExperimentRepository отложен

**Проблема**: Таблицы ExperimentResult и ExpertMessage не созданы

```kotlin
// ExperimentRepository.kt
class ExperimentRepository(private val database: ChatDatabase) {
    suspend fun saveExperimentResult(...) {
        database.experimentResultQueries.insert(...)  // ❌ Не существует
    }
}
```

**Решение**: Временно отключить

Переименовал файлы:
```bash
mv ExperimentRepository.kt ExperimentRepository.kt.disabled
mv ExperimentResult.sq ExperimentResult.sq.disabled
mv ExpertMessage.sq ExpertMessage.sq.disabled
```

Закомментировал в DatabaseHolder:
```kotlin
object DatabaseHolder {
    private var database: ChatDatabase? = null
    private var chatRepository: ChatRepository? = null
    // private var experimentRepository: ExperimentRepository? = null  // TODO Day 9

    fun init(driverFactory: DatabaseDriverFactory) {
        if (database == null) {
            val driver = driverFactory.createDriver()
            database = ChatDatabase(driver = driver)
            chatRepository = ChatRepository(database!!)
            // experimentRepository = ExperimentRepository(database!!)  // TODO Day 9
        }
    }

    // TODO Day 9: Добавить когда понадобится
    // fun getExperimentRepository(): ExperimentRepository { ... }
}
```

**Будущее**:
- Активировать когда понадобится сохранение экспериментов
- Добавить таблицы для TemperatureViewModel, CompressionComparisonViewModel
- Реализовать историю экспериментов

## Использование

### Быстрый старт

1. **Запустить приложение**:
   ```bash
   cd /Users/vyuzzzh/AndroidStudioProjects/ai_window
   ./gradlew :composeApp:run
   ```

2. **Открыть вкладку Chat**

3. **Отправить несколько сообщений**:
   - "Привет"
   - "Что такое машинное обучение?"
   - "Расскажи про нейронные сети"

4. **Закрыть приложение** (Ctrl+C или закрыть окно)

5. **Запустить снова**:
   ```bash
   ./gradlew :composeApp:run
   ```

6. **Проверить восстановление**:
   - Все сообщения должны появиться в чате
   - Консоль покажет: `✅ Loaded 6 messages from database`

### Тестирование персистентности

**Сценарий 1: Проверка автосохранения**

```bash
# Терминал 1: Запустить приложение
./gradlew :composeApp:run

# Отправить сообщения в UI...

# Терминал 2: Проверить БД во время работы
sqlite3 ~/.ai_window/chat.db "SELECT COUNT(*) FROM ChatMessage;"
# Вывод: 6

sqlite3 ~/.ai_window/chat.db "SELECT id, text, isUser, timestamp FROM ChatMessage;"
# uuid-1 | Привет | 1 | 1700000000000
# uuid-2 | Привет! Чем могу помочь? | 0 | 1700000001000
# ...
```

**Сценарий 2: Проверка восстановления**

```bash
# Закрыть приложение

# Проверить что БД сохранена
ls -lh ~/.ai_window/chat.db
# -rw-r--r--  1 user  staff   20K Nov 16 23:34 chat.db

# Запустить снова
./gradlew :composeApp:run

# Проверить консоль
# ✅ Loaded 6 messages from database

# UI должен показать все старые сообщения
```

**Сценарий 3: Очистка чата**

```bash
# В приложении нажать "Clear Chat"

# Проверить БД
sqlite3 ~/.ai_window/chat.db "SELECT COUNT(*) FROM ChatMessage;"
# Вывод: 0

# Консоль покажет:
# ✅ Database cleared
```

**Сценарий 4: Проверка metadata**

```sql
-- Проверить metadata JSON
SELECT id, metadata_json FROM ChatMessage WHERE isUser = 0 LIMIT 1;

-- Вывод:
-- uuid-2 | {"title":"Приветствие","thinking":"...","steps":[...]}

-- Проверить compression metadata
SELECT id, isSummary, summarizedCount, originalIds_json
FROM ChatMessage
WHERE isSummary = 1;

-- Вывод сводного сообщения:
-- uuid-sum | 1 | 10 | ["uuid-1","uuid-2",...,"uuid-10"]
```

### Проверка через sqlite3

**Установка sqlite3** (если нет):
```bash
# macOS
brew install sqlite3

# Linux
sudo apt-get install sqlite3

# Windows
# Скачать с https://sqlite.org/download.html
```

**Полезные команды**:

```bash
# Открыть БД
sqlite3 ~/.ai_window/chat.db

# Показать схему таблицы
.schema ChatMessage

# Показать индексы
.indexes

# Все сообщения
SELECT * FROM ChatMessage;

# Форматированный вывод
.mode column
.headers on
SELECT id, text, isUser, timestamp FROM ChatMessage;

# Последние 5 сообщений
SELECT text, datetime(timestamp/1000, 'unixepoch') as created
FROM ChatMessage
ORDER BY timestamp DESC
LIMIT 5;

# Статистика
SELECT
    COUNT(*) as total,
    SUM(CASE WHEN isUser = 1 THEN 1 ELSE 0 END) as user_messages,
    SUM(CASE WHEN isUser = 0 THEN 1 ELSE 0 END) as ai_messages,
    SUM(CASE WHEN isSummary = 1 THEN 1 ELSE 0 END) as summaries
FROM ChatMessage;

# Выход
.quit
```

**Экспорт БД**:
```bash
# Дамп схемы и данных
sqlite3 ~/.ai_window/chat.db .dump > chat_backup.sql

# Восстановление
sqlite3 new_chat.db < chat_backup.sql

# CSV экспорт
sqlite3 ~/.ai_window/chat.db <<EOF
.mode csv
.headers on
.output messages.csv
SELECT * FROM ChatMessage;
.quit
EOF
```

### Расположение БД по платформам

| Платформа | Путь | Комментарий |
|-----------|------|-------------|
| Desktop (JVM) | `~/.ai_window/chat.db` | User home directory |
| Android | `/data/data/com.example.ai_window/databases/chat.db` | App-specific storage |
| iOS | `~/Library/Application Support/chat.db` | App documents directory |
| JS/WASM | Не поддерживается | UnsupportedOperationException |

**Доступ к БД Android** (для разработки):
```bash
# Найти пакет
adb shell pm list packages | grep ai_window

# Скопировать БД с устройства
adb shell run-as com.example.ai_window cat databases/chat.db > android_chat.db

# Открыть
sqlite3 android_chat.db
```

**Доступ к БД iOS** (симулятор):
```bash
# Найти sandbox приложения
xcrun simctl get_app_container booted com.example.ai_window data

# Перейти в директорию
cd "$(xcrun simctl get_app_container booted com.example.ai_window data)"

# Открыть БД
sqlite3 chat.db
```

## Файлы проекта

### Gradle конфигурация

**gradle/libs.versions.toml** - зависимости:
```toml
[versions]
sqldelight = "2.0.2"

[libraries]
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutinesExtensions = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-androidDriver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-nativeDriver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-jvmDriver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }

[plugins]
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

**shared/build.gradle.kts** - конфигурация (+45 строк):
```kotlin
plugins {
    alias(libs.plugins.sqldelight)
}

kotlin {
    // wasmJs отключен
    sourceSets {
        androidMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
            implementation(libs.sqldelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
            implementation(libs.sqldelight.nativeDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
            implementation(libs.sqldelight.jvmDriver)
        }
    }
}

sqldelight {
    databases {
        create("ChatDatabase") {
            packageName.set("com.example.ai_window.database")
        }
    }
}
```

**composeApp/build.gradle.kts** - wasmJs отключен:
```kotlin
// Day 9: wasmJs временно отключен из-за несовместимости с SQLDelight
// @OptIn(ExperimentalWasmDsl::class)
// wasmJs { ... }
```

### SQL схемы

**shared/src/commonMain/sqldelight/com/example/ai_window/ChatMessage.sq** (+81 строка):
- CREATE TABLE ChatMessage
- Индексы idx_timestamp, idx_isSummary
- 9 запросов (selectAll, selectRecent, insert, delete, и т.д.)

**Отключенные** (будут активированы позже):
- `ExperimentResult.sq.disabled`
- `ExpertMessage.sq.disabled`

### Database layer (shared)

**commonMain:**
- `database/DatabaseDriverFactory.kt` - expect class (+11 строк)
- `database/ChatRepository.kt` - CRUD операции (+95 строк)
- `database/Mappers.kt` - конвертация моделей (+86 строк)
- `database/DatabaseHolder.kt` - singleton (+72 строки)
- `database/ExperimentRepository.kt.disabled` - отложено

**androidMain:**
- `database/DatabaseDriverFactory.android.kt` - AndroidSqliteDriver (+17 строк)
- `database/PlatformDatabaseFactory.android.kt` - Context init (+23 строки)

**iosMain:**
- `database/DatabaseDriverFactory.ios.kt` - NativeSqliteDriver (+15 строк)
- `database/PlatformDatabaseFactory.ios.kt` - фабрика (+7 строк)

**jvmMain:**
- `database/DatabaseDriverFactory.jvm.kt` - JdbcSqliteDriver (+30 строк)
- `database/PlatformDatabaseFactory.jvm.kt` - фабрика (+7 строк)

**jsMain:**
- `database/DatabaseDriverFactory.js.kt` - UnsupportedOperationException (+13 строк)
- `database/PlatformDatabaseFactory.js.kt` - фабрика (+9 строк)

### Integration (composeApp)

**composeApp/src/commonMain/kotlin:**
- `util/DatabaseInit.kt` - утилита инициализации (+8 строк)
- `ChatViewModel.kt` - автосохранение (+68 строк изменений)
- `App.kt` - инициализация БД (+8 строк)

### Generated code (не в git)

**shared/build/generated/sqldelight/code/ChatDatabase/commonMain:**
- `com/example/ai_window/database/ChatDatabase.kt` - интерфейс БД
- `com/example/ai_window/database/shared/ChatDatabaseImpl.kt` - реализация
- `com/example/aiwindow/ChatMessage.kt` - data class
- `com/example/aiwindow/ChatMessageQueries.kt` - type-safe queries

**Всего добавлено**: ~550 строк кода (без комментариев)

## Выводы

### Ключевые достижения

✅ **SQLDelight интеграция** - type-safe персистентность для KMP
✅ **Multiplatform поддержка** - Android, iOS, Desktop работают одинаково
✅ **Автоматическое сохранение** - прозрачное для пользователя
✅ **Восстановление контекста** - история доступна между запусками
✅ **Metadata сохранение** - полная информация о сообщениях (токены, compression, parsing)
✅ **Repository паттерн** - чистая архитектура, легко тестировать
✅ **Graceful degradation** - приложение работает даже без БД (JS/WASM)
✅ **JSON сериализация** - сложные типы хранятся корректно

### Технические уроки

**1. SQLDelight type mappings имеют ограничения:**
- `INTEGER AS Boolean` генерирует некорректный import
- Лучше использовать нативные SQLite типы с ручной конвертацией
- Type safety на уровне Kotlin, а не SQL

**2. Async API не всегда нужен:**
- `generateAsync = true` усложняет инициализацию
- Для простых схем sync API проще
- Async полезен для сложных миграций

**3. Platform-specific драйверы требуют разных подходов:**
- Android: Context нужен для AndroidSqliteDriver
- iOS: Простая инициализация без зависимостей
- JVM: JDBC URL с явным путем к файлу
- Web: Нет SQLDelight поддержки, нужны альтернативы

**4. Repository паттерн упрощает тестирование:**
- Бизнес-логика отделена от БД
- Легко мокировать для unit-тестов
- Единая точка доступа к данным

**5. Optional database approach:**
- БД может быть недоступна на некоторых платформах
- Silent failures для сохранения не мешают UX
- Приложение gracefully degraded без персистентности

### Применение

Система полезна для:
- **Offline-first приложений** - данные доступны без сети
- **История взаимодействий** - восстановление контекста
- **Compression эффективность** - хранение summary вместо всех сообщений
- **Аналитика использования** - метаданные (токены, время) сохраняются
- **Тестирование** - воспроизводимость сценариев с сохраненными данными

### Основной урок

**Персистентность критична для качественного UX в AI приложениях:**

- **Без БД**: Пользователь теряет всю историю при перезапуске
- **С БД**: Seamless experience, контекст всегда доступен
- **Metadata**: Сохранение токенов/compression позволяет оптимизировать запросы
- **Multiplatform**: Единый API работает везде, platform differences скрыты

SQLDelight идеально подходит для KMP проектов благодаря type-safety и платформо-специфичным драйверам.

## Дальнейшие улучшения

- [ ] **Migrations** - поддержка изменений схемы БД
  ```kotlin
  override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long) {
      if (oldVersion < 2) {
          driver.execute(null, "ALTER TABLE ChatMessage ADD COLUMN tags TEXT", 0)
      }
  }
  ```

- [ ] **ExperimentRepository** - сохранение результатов экспериментов
  - TemperatureViewModel результаты (разные temperature значения)
  - CompressionComparisonViewModel метрики (с/без compression)
  - ModelComparisonViewModel результаты (сравнения моделей)

- [ ] **Pagination** - эффективная загрузка больших историй
  ```kotlin
  suspend fun getMessagesPaginated(page: Int, pageSize: Int): List<ChatMessage> {
      val offset = page * pageSize
      return database.chatMessageQueries
          .selectWithPagination(pageSize.toLong(), offset.toLong())
          .executeAsList()
          .map { it.toDomainModel() }
  }
  ```

- [ ] **Full-text search** - поиск по истории сообщений
  ```sql
  CREATE VIRTUAL TABLE ChatMessageFTS USING fts5(
      id, text, content=ChatMessage
  );
  ```

- [ ] **Синхронизация между устройствами** - CloudKit (iOS), Firebase (Android)
  ```kotlin
  suspend fun syncWithCloud() {
      val localMessages = getAllMessages()
      val cloudMessages = cloudSync.fetchMessages()
      // Merge strategy...
  }
  ```

- [ ] **Экспорт/импорт истории** - резервное копирование
  ```kotlin
  suspend fun exportToJson(): String {
      val messages = getAllMessages()
      return json.encodeToString(messages)
  }

  suspend fun importFromJson(jsonString: String) {
      val messages = json.decodeFromString<List<ChatMessage>>(jsonString)
      messages.forEach { saveChatMessage(it) }
  }
  ```

- [ ] **Database encryption** - SQLCipher для безопасности
  ```kotlin
  val driver = AndroidSqliteDriver(
      schema = ChatDatabase.Schema,
      context = context,
      name = "chat.db",
      callback = object : AndroidSqliteDriver.Callback(ChatDatabase.Schema) {
          override fun onOpen(db: SupportSQLiteDatabase) {
              db.execSQL("PRAGMA key = 'user_encryption_key'")
          }
      }
  )
  ```

- [ ] **Vacuum и оптимизация** - очистка удаленных данных
  ```kotlin
  suspend fun optimizeDatabase() {
      database.chatMessageQueries.transaction {
          database.executeRaw("VACUUM")
          database.executeRaw("ANALYZE")
      }
  }
  ```

- [ ] **Observability** - Flow API для реактивных обновлений
  ```kotlin
  fun observeMessages(): Flow<List<ChatMessage>> {
      return database.chatMessageQueries
          .selectAll()
          .asFlow()
          .mapToList(Dispatchers.Default)
          .map { list -> list.map { it.toDomainModel() } }
  }
  ```

- [ ] **Partial updates** - обновление отдельных полей
  ```sql
  -- ChatMessage.sq
  updateTokens:
  UPDATE ChatMessage
  SET estimatedTokens = ?
  WHERE id = ?;
  ```

- [ ] **Soft delete** - пометка удаленных вместо физического удаления
  ```sql
  ALTER TABLE ChatMessage ADD COLUMN deletedAt INTEGER;

  -- Получать только не удаленные
  selectActive:
  SELECT * FROM ChatMessage
  WHERE deletedAt IS NULL
  ORDER BY timestamp ASC;
  ```
