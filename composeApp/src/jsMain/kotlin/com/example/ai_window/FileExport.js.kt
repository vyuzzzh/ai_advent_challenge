package com.example.ai_window

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * JS реализация экспорта файлов (browser download)
 */
actual fun saveTextToFile(content: String, filename: String) {
    try {
        val blob = Blob(arrayOf(content), BlobPropertyBag(type = "text/plain;charset=utf-8"))
        val url = URL.createObjectURL(blob)

        val link = document.createElement("a")
        link.setAttribute("href", url)
        link.setAttribute("download", filename)
        link.asDynamic().click()

        // Clean up
        window.setTimeout({
            URL.revokeObjectURL(url)
        }, 100)

        println("File download initiated: $filename")
    } catch (e: Exception) {
        println("Error saving file: ${e.message}")
    }
}
