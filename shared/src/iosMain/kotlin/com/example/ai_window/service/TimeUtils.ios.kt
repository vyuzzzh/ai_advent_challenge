package com.example.ai_window.service

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun getCurrentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).toLong()
}
