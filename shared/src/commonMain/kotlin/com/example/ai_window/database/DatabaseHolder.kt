package com.example.ai_window.database

/**
 * Day 9: Singleton holder для Database и Repositories.
 * Обеспечивает единственный экземпляр БД на всё приложение.
 */
object DatabaseHolder {
    private var database: ChatDatabase? = null
    private var chatRepository: ChatRepository? = null
    // private var experimentRepository: ExperimentRepository? = null // TODO Day 9: Добавить позже

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
            // experimentRepository = ExperimentRepository(database!!) // TODO Day 9: Добавить позже
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

    // TODO Day 9: Раскомментировать когда добавятся таблицы ExperimentResult и ExpertMessage
    // /**
    //  * Получает ExperimentRepository.
    //  * Throws IllegalStateException если БД не инициализирована.
    //  */
    // fun getExperimentRepository(): ExperimentRepository {
    //     return experimentRepository ?: throw IllegalStateException(
    //         "Database not initialized. Call DatabaseHolder.init() first."
    //     )
    // }

    /**
     * Очищает все данные (для тестов).
     */
    suspend fun clearAll() {
        chatRepository?.deleteAllMessages()
        // experimentRepository?.deleteAllExperiments() // если понадобится
    }
}
