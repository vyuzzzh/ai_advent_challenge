package com.example.ai_window.service

actual fun getCurrentTimeMillis(): Long {
    return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
