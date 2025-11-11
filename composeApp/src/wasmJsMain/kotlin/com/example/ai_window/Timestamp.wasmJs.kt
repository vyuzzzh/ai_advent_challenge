package com.example.ai_window

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

actual fun getCurrentTimestamp(): String {
    val now = Clock.System.now()
    val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())

    val year = localDateTime.year
    val month = localDateTime.monthNumber.toString().padStart(2, '0')
    val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
    val hours = localDateTime.hour.toString().padStart(2, '0')
    val minutes = localDateTime.minute.toString().padStart(2, '0')
    val seconds = localDateTime.second.toString().padStart(2, '0')

    return "$year-$month-$day $hours:$minutes:$seconds"
}
