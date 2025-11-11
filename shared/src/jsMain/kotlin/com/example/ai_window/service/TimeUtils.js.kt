package com.example.ai_window.service

actual fun getCurrentTimeMillis(): Long {
    return kotlin.js.Date.now().toLong()
}
