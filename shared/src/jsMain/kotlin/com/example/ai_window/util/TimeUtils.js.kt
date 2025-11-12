package com.example.ai_window.util

actual fun getCurrentTimeMillis(): Long {
    return kotlin.js.Date.now().toLong()
}
