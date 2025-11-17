package com.example.ai_window.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.ai_window.database.ChatDatabase
import java.io.File

/**
 * Day 9: JVM (Desktop) реализация DatabaseDriverFactory.
 * Использует JdbcSqliteDriver для хранения БД в пользовательской директории.
 */
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
