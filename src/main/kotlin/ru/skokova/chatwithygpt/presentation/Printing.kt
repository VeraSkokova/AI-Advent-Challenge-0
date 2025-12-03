package ru.skokova.chatwithygpt.presentation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Функция-расширение для JsonElement
fun JsonElement.printPretty(indent: String = "   ") {
    when (this) {
        is JsonObject -> {
            this.entries.forEach { (key, value) ->
                // Форматируем ключ: "ui_framework" -> "Ui Framework"
                val prettyKey = key.replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                print("$indent• $prettyKey: ")

                if (value is JsonPrimitive) {
                    // Примитивы печатаем на той же строке
                    println(value.content)
                } else {
                    // Объекты и массивы переносим на новую строку
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
