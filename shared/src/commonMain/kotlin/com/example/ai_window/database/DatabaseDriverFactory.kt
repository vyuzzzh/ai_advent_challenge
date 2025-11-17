package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Day 9: Фабрика для создания SQLite драйвера на каждой платформе.
 * Каждая платформа предоставляет свою реализацию (actual).
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
