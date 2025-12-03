package ru.skokova.chatwithygpt.console

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.skokova.chatwithygpt.client.YandexGptClient
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.data.Personas
import ru.skokova.chatwithygpt.models.Message
import ru.skokova.chatwithygpt.presentation.printPretty
import ru.skokova.chatwithygpt.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets

class ConsoleApp(private val configPath: String = "local.properties") {
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

        val apiConfig = try {
            ApiConfig.load(configPath)
        } catch (e: Exception) {
            logger.error(e.message ?: "Config error")
            return
        }
        client = YandexGptClient(apiConfig)

        logger.success("✓ Configuration saved. Ready to chat!")
        logger.println()
    }

    private suspend fun chatPhase() {
        System.setOut(PrintStream(System.out, true, StandardCharsets.UTF_8))
        logger.println("💬 Chat (type 'exit' to quit, 'clear' to clear history)", Logger.Color.CYAN)
        //logger.println("This chat takes your input and retrieves its subject, idea and goal")
        logger.println()

        val jsonToParse = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val currentPersona = Personas.MobileArchitect // Легко меняется на другую

        // --- УСЛОВНАЯ ПРОАКТИВНАЯ ИНИЦИАЛИЗАЦИЯ ---
        if (currentPersona.requiresProactiveStart) {
            print("Assistant: ")

            val initialRequest = listOf(Message("user", "START"))
            val greetingResult = client.sendMessage(
                messagesHistory = initialRequest,
                persona = currentPersona
            )

            greetingResult.onSuccess { (response, _) ->
                val jsonElement = jsonToParse.parseToJsonElement(response).jsonObject

                if (jsonElement["type"]?.jsonPrimitive?.content == "question") {
                    val text = jsonElement["text"]?.jsonPrimitive?.content ?: ""
                    val tip = jsonElement["tip"]?.jsonPrimitive?.content // Читаем совет

                    println("🤖 $text")

                    // Если есть совет, выводим его красивым цветом
                    if (!tip.isNullOrBlank()) {
                        logger.println("💡 TIP: $tip", Logger.Color.YELLOW)
                    }

                    conversationHistory.add(Message("assistant", response))
                }
            }.onFailure { error ->
                logger.error("Initialization failed: ${error.message}")
            }
            logger.println()
        }
        // ------------------------------------------

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

                conversationHistory.add(Message("user", input))

                print("Assistant: ")

                val result = client.sendMessage(conversationHistory, currentPersona)

                result.onSuccess { (response, tokens) ->

                    // Добавляем ответ в историю, чтобы контекст сохранялся (это важно!)
                    conversationHistory.add(Message("assistant", response))

                    try {
                        // Парсим JSON. Используем parseToJsonElement для гибкости
                        val jsonElement = jsonToParse.parseToJsonElement(response).jsonObject

                        // Определяем тип сообщения и выводим красиво
                        val type = jsonElement["type"]?.jsonPrimitive?.content

                        when (type) {
                            "question" -> {
                                // --- РЕЖИМ ВОПРОСА ---
                                val text = jsonElement["text"]?.jsonPrimitive?.content ?: "..."
                                val tip = jsonElement["tip"]?.jsonPrimitive?.content

                                // Печатаем основной текст вопроса (Желтым, как диалог)
                                logger.println("\n🤖 Assistant:", Logger.Color.CYAN)
                                println(text)

                                // Если есть совет (Tip), выводим его отдельно (Серым курсивом или другим цветом)
                                if (!tip.isNullOrBlank()) {
                                    logger.println("\n💡 Tip: $tip", Logger.Color.YELLOW)
                                }
                            }

                            "stack_decision", "tdd_result", "final_spec" -> {
                                logger.success("\n╔══════════════════════════════════════╗")
                                logger.success("║     TECHNICAL SPECIFICATION GENERATED      ║")
                                logger.success("╚══════════════════════════════════════╝")

                                val ignoredKeys = setOf("type", "thought")

                                jsonElement.entries.forEach { (key, element) ->
                                    if (key !in ignoredKeys) {
                                        val sectionTitle = key.replace("_", " ").uppercase()
                                        logger.println("\n🔹 $sectionTitle", Logger.Color.CYAN)

                                        // Вызов нашей новой функции-расширения
                                        element.printPretty(indent = "   ")
                                    }
                                }

                                logger.println("\n────────────────────────────────────────", Logger.Color.GRAY)
                            }

                            // --- Fallback (если пришел не наш JSON или другой формат) ---
                            else -> {
                                val text = jsonElement["text"]?.jsonPrimitive?.content
                                    ?: jsonElement["content"]?.jsonPrimitive?.content

                                if (text != null) {
                                    println(text)
                                } else {
                                    // Если совсем непонятно что - печатаем как есть, но аккуратно
                                    println(response)
                                }
                            }
                        }

                    } catch (_: Exception) {
                        // Если пришел не JSON (ошибка модели), печатаем сырой текст
                        logger.error("Raw response (parsing failed):")
                        println(response)
                    }

                    totalTokens += tokens
                    // logger.println("[Tokens: $tokens]", Logger.Color.GRAY)

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