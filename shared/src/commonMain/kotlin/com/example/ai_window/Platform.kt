package com.example.ai_window

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform