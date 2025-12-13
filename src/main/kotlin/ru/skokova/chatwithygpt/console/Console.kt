package ru.skokova.chatwithygpt.console

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.skokova.chatwithygpt.client.UniversalGptClient
import ru.skokova.chatwithygpt.config.ApiConfig
import ru.skokova.chatwithygpt.config.RoleConfig
import ru.skokova.chatwithygpt.data.HistoryRepository
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
    private var conversationHistory: MutableList<Message> = HistoryRepository.load()
    private var totalTokens = 0
    private var currentMaxTokens = 1000

    private val jsonToParse = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun run() {
        logger.banner()
        setupPhase()
        roleSelectionPhase()

        if (conversationHistory.isEmpty()) {
            conversationHistory.add(Message("system", currentPersona.systemPrompt))
        } else {
            logger.println("📜 Restored context:", Logger.Color.GRAY)
            conversationHistory.takeLast(2).forEach { msg ->
                // Просто выводим роль и текст.
                // Так как мы теперь сохраняем чистый текст (см. Идею 1),
                // здесь не нужно парсить JSON!
                val preview = msg.text.replace("\n", " ").take(80) // Убираем переносы, берем начало
                println("   ${msg.role.uppercase()}: $preview...")
            }
            logger.println("...", Logger.Color.GRAY)
        }

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
                        logger.println("💾 Saving & Exiting...")

                        // Если история длинная, сожмем перед смертью
                        if (conversationHistory.size > 5) {
                            compressHistory() // Сжимаем "на дорожку"
                        }

                        HistoryRepository.save(conversationHistory) // Сохраняем уже сжатое

                        logger.println()
                        logger.println("👋 Goodbye!")
                        client.close()
                        break
                    }

                    input.lowercase() == "clear" -> {
                        conversationHistory.clear()
                        totalTokens = 0
                        logger.println("🗑️  Chat history cleared", Logger.Color.YELLOW)
                        conversationHistory.add(Message("system", currentPersona.systemPrompt))
                        HistoryRepository.clear() // Удаляем файл
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

                result.onSuccess { res ->
                    val responseRaw = res.text

                    // 1. Формируем красивый текст (И для консоли, И для истории)
                    // Используем нашу универсальную функцию
                    val formattedText = formatResponseForHistory(responseRaw)

                    // 2. Вывод в консоль
                    logger.println("\n🤖 Assistant:", Logger.Color.CYAN)
                    println(formattedText)

                    // 3. Сохранение в историю (того же самого чистого текста!)
                    conversationHistory.add(Message("assistant", formattedText))
                    HistoryRepository.save(conversationHistory)

                    // 4. Статистика токенов (как было)
                    val inputTks = res.inputTokens
                    val outputTks = res.outputTokens
                    val requestTotal = inputTks + outputTks
                    totalTokens += requestTotal
                    val costStr = "%.4f ₽".format(res.costRub)

                    logger.println(
                        "\n📊 Request: ${inputTks}(in) + ${outputTks}(out) = $requestTotal tks | Cost: $costStr",
                        Logger.Color.GRAY
                    )

                    // 5. Автоматическое сжатие (как было)
                    if (conversationHistory.size >= 10) {
                        compressHistory()
                    }

                }.onFailure { error ->
                    logger.error("Error: ${error.message}")
                }

                logger.println()
            }
        } finally {
            reader.close()
        }
    }

    private suspend fun compressHistory() {
        // Настройки
        val keepLastMessages = 2
        // Индекс 0 - это System Prompt текущей персоны, его не трогаем
        // Сжимаем от 1 до (size - keepLastMessages)

        if (conversationHistory.size <= (keepLastMessages + 2)) return // Нечего сжимать

        logger.println("\n🧹 Compressing conversation history...", Logger.Color.YELLOW)

        // 1. Выделяем кусок для сжатия
        val messagesToSummarize = conversationHistory.subList(1, conversationHistory.size - keepLastMessages)

        // Превращаем сообщения в текст диалога
        val dialogText = messagesToSummarize.joinToString("\n") { msg ->
            "${msg.role.uppercase()}: ${msg.text}"
        }

        // 2. Отправляем запрос на сжатие (используем Lite для экономии!)
        val summaryRequest = listOf(Message("user", "Сделай саммари этого диалога:\n$dialogText"))

        // Используем Lite модель и Summarizer персону
        val result = client.sendMessage(
            messages = summaryRequest,
            persona = Personas.Summarizer,
            model = ModelsRepository.YandexLite // Всегда Lite для дешевизны
        )

        result.onSuccess { res ->
            val rawSummary = res.text
            val cleanSummary = try {
                val jsonObject = jsonToParse.parseToJsonElement(rawSummary).jsonObject
                jsonObject["text"]?.jsonPrimitive?.content
                    ?: jsonObject["summary"]?.jsonPrimitive?.content
                    ?: jsonObject["саммари"]?.jsonPrimitive?.content
                    ?: rawSummary
            } catch (_: Exception) {
                rawSummary
            }

            if (cleanSummary.isBlank() || cleanSummary.trim() == "{}" || cleanSummary.length < 5) {
                logger.println("⚠️ Summary generation failed (empty result). Skipping compression.", Logger.Color.YELLOW)
                return@onSuccess // ПРЕРЫВАЕМ ОПЕРАЦИЮ, ИСТОРИЮ НЕ ТРОГАЕМ
            }

            // 3. Пересобираем историю
            val newHistory = mutableListOf<Message>()

            // Сохраняем исходный System Prompt
            newHistory.add(conversationHistory.first())

            // Добавляем Саммари как System сообщение (чтобы модель знала контекст)
            newHistory.add(Message("system", "PREVIOUS CONTEXT SUMMARY: $cleanSummary"))

            // Добавляем последние сообщения ("живой хвост")
            newHistory.addAll(conversationHistory.takeLast(keepLastMessages))

            // Подменяем историю
            val oldSize = conversationHistory.size
            conversationHistory = newHistory

            logger.println("✅ History compressed: $oldSize -> ${conversationHistory.size} messages.", Logger.Color.GREEN)
            logger.println("📉 Summary: ${cleanSummary.take(100)}...", Logger.Color.GRAY)

        }.onFailure { err ->
            logger.error("❌ Compression failed: ${err.message}")
        }
    }

    private fun formatResponseForHistory(response: String): String {
        try {
            // Очистка от Markdown
            // Мы просто удаляем первые и последние символы, если они похожи на блок кода
            var cleanJson = response.trim()

            // Удаляем ```
            if (cleanJson.startsWith("`") && cleanJson.contains("json")) {
                val index = cleanJson.indexOf("{")
                if (index != -1) cleanJson = cleanJson.substring(index)
            }
            // Удаляем просто ```
            else if (cleanJson.startsWith("`")) {
                val index = cleanJson.indexOf("{")
                if (index != -1) cleanJson = cleanJson.substring(index)
            }

            // Удаляем хвост ```
            val lastIndex = cleanJson.lastIndexOf("}")
            if (lastIndex != -1) {
                cleanJson = cleanJson.take(lastIndex + 1)
            }

            val jsonElement = jsonToParse.parseToJsonElement(cleanJson).jsonObject
            val type = jsonElement["type"]?.jsonPrimitive?.content

            return when (type) {
                "question", "response" -> {
                    val text = jsonElement["text"]?.jsonPrimitive?.content
                        ?: jsonElement["content"]?.jsonPrimitive?.content
                        ?: return response
                    val tip = jsonElement["tip"]?.jsonPrimitive?.content
                    if (tip != null) "$text\n\nTip: $tip" else text
                }
                "creative" -> {
                    val content = jsonElement["content"]?.jsonPrimitive?.content ?: ""
                    val reasoning = jsonElement["reasoning"]?.jsonPrimitive?.content
                    if (reasoning != null) "$content\n\n(Reasoning: $reasoning)" else content
                }
                "stack_decision", "tdd_result", "final_spec" -> {
                    val sb = StringBuilder()
                    sb.appendLine("=== ${type.replace("_", " ").uppercase()} ===")
                    jsonElement.entries.forEach { (key, element) ->
                        if (key != "type" && key != "thought") {
                            val value = if (element is JsonPrimitive) element.content else element.toString()
                            sb.appendLine("${key.replace("_", " ")}: $value")
                        }
                    }
                    sb.toString()
                }
                else -> response
            }
        } catch (_: Exception) {
            return response
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
