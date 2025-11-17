package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Day 9: WASM реализация DatabaseDriverFactory.
 * SQLDelight не поддерживается для WASM - база данных отключена.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        throw UnsupportedOperationException("Database is not supported on WASM platform")
    }
}
