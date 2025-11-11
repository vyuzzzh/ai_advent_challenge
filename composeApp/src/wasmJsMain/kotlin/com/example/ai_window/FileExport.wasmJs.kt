package com.example.ai_window

/**
 * WasmJS реализация экспорта файлов
 * Временная заглушка - полноценная поддержка требует external declarations
 */
actual fun saveTextToFile(content: String, filename: String) {
    println("WasmJS: File export not fully supported yet")
    println("File: $filename")
    println("Content length: ${content.length} characters")
    // TODO: Implement proper file download using external declarations for browser APIs
}
