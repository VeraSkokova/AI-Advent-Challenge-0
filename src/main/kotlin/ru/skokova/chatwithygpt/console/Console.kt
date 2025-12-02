package ru.skokova.chatwithygpt.console

import jdk.internal.org.jline.reader.LineReader
import ru.skokova.chatwithygpt.client.YandexGptClient
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.models.Message
import ru.skokova.chatwithygpt.utils.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ConsoleApp(private val configPath: String = "local.properties") {
    private val config = ApiConfig(fileName = configPath)
    private val logger = Logger()
    private lateinit var client: YandexGptClient
    private val conversationHistory = mutableListOf<Message>()
    private var totalTokens = 0

    suspend fun run() {
        logger.banner()
        setupPhase()
        chatPhase()
    }

    private fun setupPhase() {
        logger.println("⚙️  API Configuration", Logger.Color.CYAN)
        logger.println()

        config.loadFromLocalProperties()

        if (!config.isConfigured()) {
            logger.error("API Key and Folder ID are required!")
            return
        }

        client = YandexGptClient(config)
        logger.success("✓ Configuration saved. Ready to chat!")
        logger.println()
    }

    private suspend fun chatPhase() {
        System.setOut(PrintStream(System.out, true, StandardCharsets.UTF_8))
        logger.println("💬 Chat (type 'exit' to quit, 'clear' to clear history)", Logger.Color.CYAN)
        logger.println()

        val reader = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
        try {
            while (true) {
                print("You: ")
                val input = reader.readLine()?.trim() ?: continue

                when (input.lowercase()) {
                    "exit" -> {
                        logger.println()
                        logger.println("👋 Goodbye!")
                        client.close()
                        break
                    }
                    "clear" -> {
                        conversationHistory.clear()
                        totalTokens = 0
                        logger.println("🗑️  Chat history cleared", Logger.Color.YELLOW)
                        continue
                    }
                    else -> {}
                }

                if (input.isEmpty()) continue
                logger.println("input = $input")

                conversationHistory.add(Message("user", input))

                print("Assistant: ")
                val result = client.sendMessage(conversationHistory)

                result.onSuccess { (response, tokens) ->
                    println(response)
                    conversationHistory.add(Message("assistant", response))
                    totalTokens += tokens
                    logger.println(
                        "[Tokens: $tokens | Total: $totalTokens]",
                        Logger.Color.GRAY
                    )
                }.onFailure { error ->
                    logger.error("Error: ${error.message}")
                }

                logger.println()
            }
        } finally {
            reader.close()
        }
    }
}