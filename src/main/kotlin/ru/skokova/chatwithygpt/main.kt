package ru.skokova.chatwithygpt

import ru.skokova.chatwithygpt.console.ConsoleApp

suspend fun main(args: Array<String>) {
    val configPath = args.getOrNull(0) ?: "local.properties"
    val app = ConsoleApp(configPath)
    app.run()
}