package com.example.ai_window

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

actual fun getCurrentTimestamp(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return LocalDateTime.now().format(formatter)
}
