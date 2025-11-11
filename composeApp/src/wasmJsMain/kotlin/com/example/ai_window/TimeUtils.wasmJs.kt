package com.example.ai_window

actual fun getCurrentTimeMillis(): Long {
    return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
