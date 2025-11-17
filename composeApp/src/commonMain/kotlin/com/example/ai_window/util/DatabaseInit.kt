package com.example.ai_window.util

import com.example.ai_window.database.DatabaseHolder
import com.example.ai_window.database.createDatabaseDriverFactory

/**
 * Day 9: Инициализация базы данных.
 * Должна быть вызвана один раз при старте приложения.
 */
fun initDatabase() {
    DatabaseHolder.init(createDatabaseDriverFactory())
}
