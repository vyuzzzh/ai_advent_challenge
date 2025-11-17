package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Day 9: JavaScript реализация DatabaseDriverFactory.
 * SQLDelight не поддерживается для JS - база данных отключена.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        throw UnsupportedOperationException("Database is not supported on JS platform")
    }
}
