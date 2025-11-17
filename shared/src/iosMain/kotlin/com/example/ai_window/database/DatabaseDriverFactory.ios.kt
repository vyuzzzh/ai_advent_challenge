package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.ai_window.database.ChatDatabase

/**
 * Day 9: iOS реализация DatabaseDriverFactory.
 * Использует NativeSqliteDriver для хранения БД в app sandbox.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = ChatDatabase.Schema,
            name = "chat.db"
        )
    }
}
