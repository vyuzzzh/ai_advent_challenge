package com.example.ai_window

/**
 * Получить текущее время в миллисекундах (expect/actual)
 */
expect fun getCurrentTimeMillis(): Long

/**
 * Форматирование Double с указанным количеством десятичных знаков
 * Простая реализация без использования String.format()
 */
fun Double.formatDecimals(decimals: Int = 2): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 100.0
    }
    val rounded = (this * multiplier).toInt() / multiplier
    return rounded.toString()
}

/**
 * Форматирование Int как Double с десятичными знаками
 */
fun Int.formatDecimals(decimals: Int = 2): String {
    return this.toDouble().formatDecimals(decimals)
}
