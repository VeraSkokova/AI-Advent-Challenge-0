package ru.skokova.chatwithygpt.config

import java.io.File
import java.io.FileInputStream
import java.util.Properties

data class ApiConfig(
    val apiKey: String,
    val folderId: String,
    val modelVersion: String
) {
    fun isConfigured(): Boolean = apiKey.isNotEmpty() && folderId.isNotEmpty()

    companion object {
        fun load(fileName: String = "local.properties"): ApiConfig {
            val props = Properties()
            val file = File(fileName)

            // 1. Читаем файл, если есть
            if (file.exists()) {
                FileInputStream(file).use { props.load(it) }
            }

            // 2. Достаем значения (Сначала Env Vars, потом файл)
            // Это Best Practice для облачных приложений
            val apiKey = System.getenv("YANDEX_GPT_API_KEY")
                ?: props.getProperty("YANDEX_GPT_API_KEY")?.trim()
                ?: throw IllegalStateException("API Key not found! Set YANDEX_GPT_API_KEY env var or in $fileName")

            val folderId = System.getenv("YANDEX_GPT_FOLDER_ID")
                ?: props.getProperty("YANDEX_GPT_FOLDER_ID")?.trim()
                ?: throw IllegalStateException("Folder ID not found! Set YANDEX_GPT_FOLDER_ID env var or in $fileName")

            val version = System.getenv("YANDEX_GPT_MODEL_VERSION")
                ?: props.getProperty("YANDEX_GPT_MODEL_VERSION")
                ?: "latest"

            return ApiConfig(apiKey, folderId, version)
        }
    }
}