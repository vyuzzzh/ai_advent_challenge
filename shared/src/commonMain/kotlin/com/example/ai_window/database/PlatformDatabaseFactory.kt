package com.example.ai_window.database

/**
 * Day 9: Платформо-специфичная функция для создания DatabaseDriverFactory.
 * Каждая платформа предоставляет свою реализацию.
 */
expect fun createDatabaseDriverFactory(): DatabaseDriverFactory
