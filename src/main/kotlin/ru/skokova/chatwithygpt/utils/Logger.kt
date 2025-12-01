package ru.skokova.chatwithygpt.utils

class Logger {
    enum class Color(val code: String) {
        CYAN("\u001B[36m"),
        GREEN("\u001B[32m"),
        RED("\u001B[31m"),
        YELLOW("\u001B[33m"),
        GRAY("\u001B[90m"),
        RESET("\u001B[0m")
    }

    fun banner() {
        println("""
            ╔═══════════════════════════════════════════╗
            ║    Yandex GPT Console Application         ║
            ║    Kotlin + Ktor Client                   ║
            ╚═══════════════════════════════════════════╝
        """.trimIndent())
    }

    fun println(message: String = "", color: Color = Color.RESET) {
        kotlin.io.println("${color.code}$message${Color.RESET.code}")
    }

    fun success(message: String) = println(message, Color.GREEN)
    fun error(message: String) = println(message, Color.RED)
    fun warning(message: String) = println(message, Color.YELLOW)
}