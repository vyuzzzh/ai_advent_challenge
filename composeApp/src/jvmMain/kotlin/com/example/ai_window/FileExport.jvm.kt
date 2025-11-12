package com.example.ai_window

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun saveTextToFile(content: String, filename: String) {
    try {
        // Открываем диалог сохранения файла
        val fileDialog = FileDialog(null as Frame?, "Сохранить отчет", FileDialog.SAVE)
        fileDialog.file = filename
        fileDialog.isVisible = true

        val directory = fileDialog.directory
        val file = fileDialog.file

        if (directory != null && file != null) {
            val filePath = File(directory, file)
            filePath.writeText(content)
            println("✅ Отчет сохранен: ${filePath.absolutePath}")
        } else {
            println("❌ Сохранение отменено пользователем")
        }
    } catch (e: Exception) {
        println("❌ Ошибка сохранения файла: ${e.message}")
        e.printStackTrace()
    }
}
