package com.example.ai_window.database

import android.content.Context

/**
 * Day 9: Android реализация createDatabaseDriverFactory.
 * Требует инициализации Android context через AndroidDatabaseInit.init().
 */
actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = AndroidDatabaseInit.context
        ?: throw IllegalStateException(
            "Android Context not initialized. Call AndroidDatabaseInit.init(context) first."
        )
    return DatabaseDriverFactory(context)
}

/**
 * Объект для хранения Android Application context.
 * Должен быть инициализирован из MainActivity или Application класса.
 */
object AndroidDatabaseInit {
    var context: Context? = null
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
