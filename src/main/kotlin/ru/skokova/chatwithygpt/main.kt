package ru.skokova.chatwithygpt

import ru.skokova.chatwithygpt.console.ConsoleApp

suspend fun main(args: Array<String>) {
    System.setOut(java.io.PrintStream(System.out, true, "UTF-8"))
    System.setErr(java.io.PrintStream(System.err, true, "UTF-8"))

    val configPath = args.getOrNull(0) ?: "local.properties"
    val app = ConsoleApp(configPath)
    app.run()
}