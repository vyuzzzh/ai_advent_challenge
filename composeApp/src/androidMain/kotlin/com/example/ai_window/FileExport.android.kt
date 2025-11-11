package com.example.ai_window

import android.content.Context
import java.io.File

// Context will be passed from MainActivity
private var appContext: Context? = null

fun initFileExport(context: Context) {
    appContext = context
}

/**
 * Android реализация экспорта файлов
 */
actual fun saveTextToFile(content: String, filename: String) {
    val context = appContext ?: run {
        println("Error: Context not initialized for file export")
        return
    }

    try {
        val file = File(context.getExternalFilesDir(null), filename)
        file.writeText(content)
        println("File saved: ${file.absolutePath}")
    } catch (e: Exception) {
        println("Error saving file: ${e.message}")
    }
}
