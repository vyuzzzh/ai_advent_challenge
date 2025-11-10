package com.example.ai_window

import kotlin.js.Date

actual fun getCurrentTimestamp(): String {
    val date = Date()
    val year = date.getFullYear()
    val month = (date.getMonth() + 1).toString().padStart(2, '0')
    val day = date.getDate().toString().padStart(2, '0')
    val hours = date.getHours().toString().padStart(2, '0')
    val minutes = date.getMinutes().toString().padStart(2, '0')
    val seconds = date.getSeconds().toString().padStart(2, '0')
    return "$year-$month-$day $hours:$minutes:$seconds"
}
