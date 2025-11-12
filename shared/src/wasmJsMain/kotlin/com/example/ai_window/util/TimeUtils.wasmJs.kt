package com.example.ai_window.util

actual fun getCurrentTimeMillis(): Long {
    return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
