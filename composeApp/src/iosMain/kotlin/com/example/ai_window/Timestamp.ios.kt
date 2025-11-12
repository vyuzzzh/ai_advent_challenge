package com.example.ai_window

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun getCurrentTimestamp(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
    return formatter.stringFromDate(NSDate())
}
