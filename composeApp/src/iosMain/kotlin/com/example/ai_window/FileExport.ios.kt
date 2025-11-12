package com.example.ai_window

import platform.Foundation.*

/**
 * iOS реализация экспорта файлов
 * Упрощенная версия без обработки ошибок
 */
actual fun saveTextToFile(content: String, filename: String) {
    val fileManager = NSFileManager.defaultManager
    val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val documentsURL = urls.firstOrNull() as? NSURL

    if (documentsURL == null) {
        println("iOS: Could not get documents directory")
        return
    }

    val fileURL = documentsURL.URLByAppendingPathComponent(filename)
    if (fileURL == null) {
        println("iOS: Could not create file URL for $filename")
        return
    }

    // Простая запись без обработки ошибок
    val data = (content as NSString).dataUsingEncoding(NSUTF8StringEncoding)
    if (data != null) {
        data.writeToURL(fileURL, atomically = true)
        println("iOS: File saved to ${fileURL.path}")
    } else {
        println("iOS: Could not convert content to data")
    }
}
