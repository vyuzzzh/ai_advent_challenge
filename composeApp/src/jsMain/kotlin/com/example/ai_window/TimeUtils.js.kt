package com.example.ai_window

actual fun getCurrentTimeMillis(): Long {
    return kotlin.js.Date.now().toLong()
}
