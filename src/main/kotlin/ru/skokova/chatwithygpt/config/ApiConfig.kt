package ru.skokova.chatwithygpt.config

import java.io.File
import java.io.FileInputStream
import java.util.Properties

data class ApiConfig(
    var apiKey: String = "",
    var folderId: String = "",
    var modelVersion: String = "latest",
    var systemPrompt: String = """
Ты — опытный учитель литературы, специализирующийся на анализе текстов. Твоя задача — разобрать каждое сообщение пользователя как литературное произведение или фрагмент, разбираемый на уроке литературы.

КРИТИЧНО: Определи язык входящего сообщения. Если сообщение на русском — отвечай на русском. Если на английском — отвечай на английском. Если на другом языке — отвечай на этом языке.

Для каждого сообщения ты должен выдать ответ ТОЛЬКО в формате JSON. Язык в JSON должен совпадать с языком входящего сообщения:

ДЛЯ РУССКОГО ТЕКСТА:
{
  "subject": "<основная тема>",
  "idea": "<центральная идея>",
  "goal": "<цель автора>"
}

ДЛЯ АНГЛИЙСКОГО ТЕКСТА:
{
  "subject": "<main theme>",
  "idea": "<central idea>",
  "goal": "<author's purpose>"
}

Правила:
1. Subject — ключевое понятие или объект текста
2. Idea — суть мысли автора, философское или эмоциональное утверждение
3. Goal — цель высказывания

Выдавай ТОЛЬКО JSON в том языке, на котором написано сообщение. Без предисловий. Без объяснений.
""".trimIndent(),
    var temperature: Double = 0.6,
    var maxTokens: Int = 1000,
    val fileName: String = "local.properties"
) {
    fun isConfigured(): Boolean = apiKey.isNotEmpty() && folderId.isNotEmpty()

    fun loadFromLocalProperties() {
        val props = Properties()
        val resourceStream = this::class.java.classLoader.getResourceAsStream(fileName)
        if (resourceStream != null) {
            props.load(resourceStream)
            resourceStream.close()
        } else {
            // Если нет в JAR, ищем в текущей директории
            val file = File(fileName)
            if (!file.exists()) {
                throw IllegalStateException(
                    "Config file $fileName not found in classpath or project root.\n" +
                            "Usage: java -jar app.jar [config-path]\n" +
                            "Example: java -jar app.jar /path/to/local.properties"
                )
            }
            FileInputStream(file).use { fis ->
                props.load(fis)
            }
        }

        apiKey = props.getProperty("YANDEX_GPT_API_KEY")?.trim().orEmpty()
        folderId = props.getProperty("YANDEX_GPT_FOLDER_ID")?.trim().orEmpty()
    }
}
