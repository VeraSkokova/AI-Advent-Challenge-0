package ru.skokova.chatwithygpt.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.skokova.chatwithygpt.models.Message
import java.io.File
import java.nio.charset.StandardCharsets

object HistoryRepository {
    private const val FILE_NAME = "chat_history.json"
    private val json = Json { prettyPrint = true }

    fun save(history: List<Message>) {
        try {
            val jsonString = json.encodeToString(history)
            File(FILE_NAME).writeText(jsonString, StandardCharsets.UTF_8)
            // println("💾 History saved to $FILE_NAME") // Можно раскомментировать для отладки
        } catch (e: Exception) {
            System.err.println("Failed to save history: ${e.message}")
        }
    }

    fun load(): MutableList<Message> {
        val file = File(FILE_NAME)
        if (!file.exists()) return mutableListOf()

        return try {
            val jsonString = file.readText(StandardCharsets.UTF_8)
            json.decodeFromString<MutableList<Message>>(jsonString).also {
                println("📂 History loaded from $FILE_NAME (${it.size} messages)")
            }
        } catch (e: Exception) {
            System.err.println("Failed to load history: ${e.message}")
            mutableListOf()
        }
    }

    fun clear() {
        val file = File(FILE_NAME)
        if (file.exists()) file.delete()
    }
}
