package ru.skokova.chatwithygpt.console

import ru.skokova.chatwithygpt.client.YandexGptClient
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.models.Message
import ru.skokova.chatwithygpt.utils.Logger
import java.nio.charset.Charset

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
        logger.println("💬 Chat (type 'exit' to quit, 'clear' to clear history)", Logger.Color.CYAN)
        logger.println()

        while (true) {
            print("You: ")
            val charset = Charset.defaultCharset()
            logger.success("Default charset: $charset")
            val input = readlnOrNull()?.trim()?.let { String(it.toByteArray(charset), Charsets.UTF_8) } ?: continue
            logger.success("input = $input")

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

            conversationHistory.add(Message("user", input))

            print("Assistant: ")
            val result = client.sendMessage(conversationHistory)

            result.onSuccess { (response, tokens) ->
                println(response)
                conversationHistory.add(Message("assistant", response))
                totalTokens = tokens
                logger.println(
                    "[Tokens: $tokens | Total: $totalTokens]",
                    Logger.Color.GRAY
                )
            }.onFailure { error ->
                logger.error("Error: ${error.message}")
            }

            logger.println()
        }
    }
}