package ru.skokova.chatwithygpt.config

import java.io.File
import java.io.FileInputStream
import java.util.Properties

data class ApiConfig(
    var apiKey: String = "",
    var folderId: String = "",
    var modelVersion: String = "latest",
    var systemPrompt: String = "You are a helpful assistant.",
    var temperature: Double = 0.6,
    var maxTokens: Int = 1000,
    val fileName: String = "local.properties"
) {
    fun isConfigured(): Boolean =
        apiKey.isNotEmpty() && folderId.isNotEmpty()

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
