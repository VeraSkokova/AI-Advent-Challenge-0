package ru.skokova.chatwithygpt.console

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.skokova.chatwithygpt.client.UniversalGptClient
import ru.skokova.chatwithygpt.client.YandexGptClient
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.config.RoleConfig
import ru.skokova.chatwithygpt.data.KindMentor
import ru.skokova.chatwithygpt.data.Persona
import ru.skokova.chatwithygpt.data.Personas
import ru.skokova.chatwithygpt.data.StrictAuditor
import ru.skokova.chatwithygpt.models.GenerationResult
import ru.skokova.chatwithygpt.models.Message
import ru.skokova.chatwithygpt.models.ModelConfig
import ru.skokova.chatwithygpt.models.ModelsRepository
import ru.skokova.chatwithygpt.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets

class ConsoleApp(private val configPath: String = "local.properties") {

    private val config = ApiConfig.load(configPath)
    private val roleConfig = RoleConfig.load()
    private var currentModel: ModelConfig = ModelsRepository.YandexPro
    private val logger = Logger()
    private lateinit var client: UniversalGptClient

    private var currentPersona: Persona = Personas.LiteratureTeacher
    private val conversationHistory = mutableListOf<Message>()
    private var totalTokens = 0
    private var currentMaxTokens = 1000

    suspend fun run() {
        logger.banner()
        setupPhase()
        roleSelectionPhase()
        chatPhase()
    }

    private fun setupPhase() {
        logger.println("⚙️  API Configuration", Logger.Color.CYAN)
        logger.println()

        try {
            if (!config.isConfigured()) {
                logger.error("API Key and Folder ID are required!")
                return
            }
            client = UniversalGptClient(config)
            logger.success("✓ Configuration loaded successfully")
        } catch (e: Exception) {
            logger.error("Configuration error: ${e.message}")
            return
        }
        logger.println()
    }

    // --- НОВАЯ ФУНКЦИЯ: ВЫБОР РОЛИ ---
    private fun roleSelectionPhase() {
        logger.println("🎭 Role Selection", Logger.Color.CYAN)
        logger.println()

        // Приоритет 1: Если роль задана в конфиге
        if (!roleConfig.roleId.isNullOrBlank()) {
            val selectedPersona = findPersonaById(roleConfig.roleId!!)
            if (selectedPersona != null) {
                currentPersona = selectedPersona
                logger.success("✓ Role loaded from config: ${currentPersona.id}")
                logger.println()
                return
            } else {
                logger.error("Role '${roleConfig.roleId}' not found in config. Showing menu.")
                logger.println()
            }
        }

        // Приоритет 2: Показываем меню
        val availablePersonas = listOf(
            Personas.LiteratureTeacher,
            Personas.SystemAnalyst,
            Personas.MobileArchitect,
            KindMentor,
            StrictAuditor,
            Personas.ExperimentalPersona
        )

        logger.println("Choose a role:")
        availablePersonas.forEachIndexed { idx, persona ->
            println("  ${idx + 1}. ${persona.id}")
        }
        logger.println()

        print("Enter role number (1-${availablePersonas.size}): ")

        val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
        val input = reader.readLine()?.trim()?.toIntOrNull() ?: 1

        if (input in 1..availablePersonas.size) {
            currentPersona = availablePersonas[input - 1]
            logger.success("✓ Selected role: ${currentPersona.id}")
        } else {
            logger.error("Invalid choice. Using default: LiteratureTeacher")
            currentPersona = Personas.LiteratureTeacher
        }
        logger.println()
    }

    // --- ВСПОМОГАТЕЛЬНАЯ ФУНКЦИЯ: ПОИСК ПЕРСОНЫ ПО ID ---
    private fun findPersonaById(id: String): Persona? {
        return listOf(
            Personas.LiteratureTeacher,
            Personas.SystemAnalyst,
            Personas.MobileArchitect,
            KindMentor,
            StrictAuditor
        ).find { it.id == id }
    }

    private suspend fun chatPhase() {
        System.setOut(PrintStream(System.out, true, StandardCharsets.UTF_8))
        logger.println("💬 Chat (type 'exit' to quit, 'clear' to clear history, 'switch' to change role)", Logger.Color.CYAN)
        logger.println()

        val jsonToParse = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        // --- ПРОАКТИВНЫЙ СТАРТ (если персона требует) ---
        if (currentPersona.requiresProactiveStart) {
            print("Assistant: ")
            val initialRequest = listOf(Message("user", "START"))
            val greetingResult = client.sendMessage(initialRequest, currentPersona, currentModel, currentMaxTokens)

            greetingResult.onSuccess { (response, _) ->
                val jsonElement = jsonToParse.parseToJsonElement(response).jsonObject
                if (jsonElement["type"]?.jsonPrimitive?.content == "question") {
                    val text = jsonElement["text"]?.jsonPrimitive?.content ?: "..."
                    println(text)
                    conversationHistory.add(Message("assistant", response))
                }
            }.onFailure { error ->
                logger.error("Initialization failed: ${error.message}")
            }
            logger.println()
        }

        val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
        try {
            while (true) {
                print("You: ")
                val input = reader.readLine()?.trim() ?: continue

                when {
                    input.lowercase() == "exit" -> {
                        logger.println()
                        logger.println("👋 Goodbye!")
                        client.close()
                        break
                    }

                    input.lowercase() == "clear" -> {
                        conversationHistory.clear()
                        totalTokens = 0
                        logger.println("🗑️  Chat history cleared", Logger.Color.YELLOW)
                        continue
                    }

                    input.lowercase() == "switch" -> {
                        val allPersonas = listOf(
                            Personas.LiteratureTeacher,
                            Personas.SystemAnalyst,
                            Personas.MobileArchitect,
                            KindMentor,
                            StrictAuditor
                        )
                        val currentIdx = allPersonas.indexOfFirst { it.id == currentPersona.id }
                        val nextIdx = (currentIdx + 1) % allPersonas.size
                        currentPersona = allPersonas[nextIdx]

                        logger.println("🔄 Switched to: ${currentPersona.id} ", Logger.Color.YELLOW)
                        logger.println("History preserved. Context retained.", Logger.Color.GRAY)
                        continue
                    }

                    input.lowercase().startsWith("temp ") -> {
                        val tempValue = input.substringAfter("temp ").trim().toDoubleOrNull()
                        if (tempValue != null && tempValue in 0.0..2.0) {
                            currentPersona = currentPersona.copy(temperature = tempValue)
                            logger.println("🌡️  Temperature set to: $tempValue", Logger.Color.YELLOW)
                            logger.println("History preserved. Context retained.", Logger.Color.GRAY)
                        } else {
                            logger.error("Invalid temperature. Use 0.0 - 2.0")
                        }
                        continue
                    }

                    // Команда переключения модели
                    input.lowercase().startsWith("model ") -> {
                        val type = input.substringAfter("model ").trim().lowercase()
                        when (type) {
                            "lite" -> {
                                currentModel = ModelsRepository.YandexLite
                                logger.println("🔄 Model switched to: ${currentModel.name}", Logger.Color.YELLOW)
                            }
                            "pro" -> {
                                currentModel = ModelsRepository.YandexPro
                                logger.println("🔄 Model switched to: ${currentModel.name}", Logger.Color.YELLOW)
                            }
                            "qwen" -> {
                                currentModel = ModelsRepository.Qwen
                                logger.println("🔄 Model switched to: ${currentModel.name}", Logger.Color.YELLOW)
                            }
                            else -> logger.error("Unknown model. Use: model lite, model pro, model qwen")
                        }
                        continue
                    }

                    // Команда установки лимита токенов
                    input.lowercase().startsWith("limit ") -> {
                        val limit = input.substringAfter("limit ").trim().toIntOrNull()
                        if (limit != null && limit > 0) {
                            currentMaxTokens = limit
                            logger.println("🧱 MaxTokens limit set to: $currentMaxTokens", Logger.Color.YELLOW)
                        } else {
                            logger.error("Invalid limit. Usage: limit 500")
                        }
                        continue
                    }

                    input.lowercase().startsWith("benchmark ") -> {
                        val query = input.substringAfter("benchmark ").trim()
                        logger.println("\n🚀 Starting Benchmark for query: \"$query\"", Logger.Color.YELLOW)
                        logger.println("Persona: ${currentPersona.id}")
                        logger.println("Models: ${ModelsRepository.ALL.joinToString { it.name }}\n")

                        val results = mutableListOf<GenerationResult>()

                        // Сообщение пользователя
                        val testMessages = listOf(Message("user", query))

                        ModelsRepository.ALL.forEach { model ->
                            logger.println("⏳ Testing ${model.name}...", Logger.Color.CYAN)

                            // 1. ИСПОЛЬЗУЕМ currentPersona
                            val result = client.sendMessage(testMessages, currentPersona, model, currentMaxTokens)

                            result.onSuccess { res ->
                                results.add(res)

                                // 2. ВЫВОД ОТВЕТА
                                logger.println("📝 Response:", Logger.Color.GRAY)

                                // 3. ПАРСИНГ JSON (попытка)
                                try {
                                    val jsonElement = jsonToParse.parseToJsonElement(res.text).jsonObject

                                    // Если это наш стандартный формат с type/text
                                    if (jsonElement.containsKey("text")) {
                                        println(jsonElement["text"]?.jsonPrimitive?.content)
                                    } else if (jsonElement.containsKey("content")) {
                                        println(jsonElement["content"]?.jsonPrimitive?.content)
                                    } else {
                                        // Просто красивый JSON
                                        println(res.text)
                                    }
                                } catch (e: Exception) {
                                    // Не JSON — выводим как есть
                                    println(res.text)
                                }

                                logger.println("⏱️ ${res.durationMs}ms | 💰 %.4f rub\n".format(res.costRub), Logger.Color.GRAY)

                            }.onFailure { err ->
                                logger.error("❌ Error: ${err.message?.take(100)}...")
                            }
                        }

                        // Таблица (без изменений)
                        logger.println("\n📊 Benchmark Results:", Logger.Color.CYAN)
                        println("| Model | Time (ms) | Input Tks | Output Tks | Cost (rub) |")
                        println("|-------|-----------|-----------|------------|------------|")
                        results.forEach { r ->
                            val costStr = "%.4f".format(r.costRub)
                            println("| ${r.modelName.padEnd(15)} | ${r.durationMs.toString().padEnd(9)} | ${r.inputTokens.toString().padEnd(9)} | ${r.outputTokens.toString().padEnd(10)} | $costStr |")
                        }

                        logger.println("\n💾 Copy the table above for your report.", Logger.Color.GRAY)
                        continue
                    }

                    input.lowercase().startsWith("overflow_input") -> {
                        // Базовый абзац (можно взять из лекции или придумать)
                        val chunk = "Это тестовый абзац для проверки переполнения контекста. " +
                                "Мы повторяем его много раз, чтобы создать очень длинный запрос. "

                        val repeatCount = 1500  // начни, например, с 2000, потом увеличивай
                        val hugePrompt = buildString {
                            repeat(repeatCount) {
                                append(chunk)
                            }
                        }

                        logger.println("🚨 Trying input overflow with length=${hugePrompt.length} chars", Logger.Color.YELLOW)

                        val messages = listOf(Message("user", hugePrompt))

                        val result = client.sendMessage(messages, currentPersona, currentModel, currentMaxTokens)

                        result.onSuccess { res ->
                            logger.println("✅ Still fits. InputTokens=${res.inputTokens}, OutputTokens=${res.outputTokens}", Logger.Color.GREEN)
                            logger.println("Model response (truncated):", Logger.Color.GRAY)
                            println(res.text.take(500) + "...")
                        }.onFailure { e ->
                            logger.error("❌ Overflow error: ${e.message}")
                        }

                        continue
                    }

                    else -> {}
                }

                if (input.isEmpty()) continue

                conversationHistory.add(Message("user", input))
                print("Assistant: ")

                val result = client.sendMessage(conversationHistory, currentPersona, currentModel, currentMaxTokens)

                result.onSuccess { (response, tokens) ->
                    conversationHistory.add(Message("assistant", response))

                    try {
                        val jsonElement = jsonToParse.parseToJsonElement(response).jsonObject

                        val type = jsonElement["type"]?.jsonPrimitive?.content

                        when (type) {
                            "question" -> {
                                val text = jsonElement["text"]?.jsonPrimitive?.content ?: "..."
                                val tip = jsonElement["tip"]?.jsonPrimitive?.content

                                logger.println("\n🤖 Assistant:", Logger.Color.CYAN)
                                println(text)

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
                                        element.printPretty(indent = "   ")
                                    }
                                }

                                logger.println("\n────────────────────────────────────────", Logger.Color.GRAY)
                            }

                            "creative" -> {
                                val content = jsonElement["content"]?.jsonPrimitive?.content ?: ""
                                val reasoning = jsonElement["reasoning"]?.jsonPrimitive?.content ?: ""

                                logger.println("\n✨ Creative Output:", Logger.Color.CYAN)
                                println(content)

                                if (!reasoning.isNullOrBlank()) {
                                    logger.println("\n📌 Reasoning: $reasoning", Logger.Color.GRAY)
                                }
                            }

                            else -> {
                                val text = jsonElement["text"]?.jsonPrimitive?.content
                                    ?: jsonElement["content"]?.jsonPrimitive?.content

                                if (text != null) {
                                    println(text)
                                } else {
                                    println(response)
                                }
                            }
                        }

                    } catch (_: Exception) {
                        logger.error("Raw response (parsing failed):")
                        println(response)
                    }

                    totalTokens += tokens
                    logger.println("[Tokens: $tokens | Total: $totalTokens]", Logger.Color.GRAY)

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

// Функция-расширение для красивой печати JSON
fun JsonElement.printPretty(indent: String = "   ") {
    when (this) {
        is JsonObject -> {
            this.entries.forEach { (key, value) ->
                val prettyKey = key.replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                print("$indent• $prettyKey: ")

                if (value is JsonPrimitive) {
                    println(value.content)
                } else {
                    println()
                    value.printPretty(indent + "  ")
                }
            }
        }
        is JsonArray -> {
            this.forEach { item ->
                print("$indent- ")
                if (item is JsonPrimitive) {
                    println(item.content)
                } else {
                    println()
                    item.printPretty(indent + "  ")
                }
            }
        }
        is JsonPrimitive -> {
            println(this.content)
        }
    }
}
