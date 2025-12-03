package ru.skokova.chatwithygpt.data

data class Persona(
    val id: String,
    val systemPrompt: String,
    val temperature: Double,
    // Функция, которая берет исходный текст юзера и превращает его в финальный промпт
    val userMessageFormatter: (String) -> String = { it }, // По умолчанию - без изменений
    val requiresProactiveStart: Boolean = false
)

object Personas {
    val LiteratureTeacher = Persona(
        id = "lit_teacher",
        temperature = 0.3,
        systemPrompt = "You are an expert linguistic analysis AI. Output ONLY valid JSON.",
        // Вся магия инструкций здесь. Мы "приклеиваем" задачу к каждому сообщению пользователя.
        userMessageFormatter = { input ->
            """
            TARGET TEXT FOR ANALYSIS:
            "$input"
            
            ---
            TASK: Perform a deep literary analysis of the target text above.
            
            DEFINITIONS:
            1. Subject (Тема): What is the text about? (e.g., "Autumn nature", "Lost love", "War heroism"). It is the factual foundation.
            2. Idea (Идея): What does the author think about the subject? What is the hidden philosophical or emotional message? (e.g., "Nature reflects human soul", "Love is destructive").
            3. Goal (Цель): Why was this text written? (e.g., "To evoke sadness", "To call for action", "To inform").
            
            LANGUAGE RULES (CRITICAL):
            - If the Target Text is in English -> The JSON values MUST be in English.
            - If the Target Text is in Russian -> The JSON values MUST be in Russian.
            - If the text is mixed, use the predominant language.
            
            OUTPUT FORMAT (JSON ONLY):
            {
              "subject": "...",
              "idea": "...",
              "goal": "..."
            }
            """.trimIndent()
        }
    )

    // Заготовка на будущее для задач с кодом (температура ниже для точности) [file:17]
    val KotlinArchitect = Persona(
        id = "kotlin_arch",
        temperature = 0.3,
        systemPrompt = """
            Ты — Senior Kotlin Developer. Твоя задача — анализировать код.
            Отвечай ТОЛЬКО валидным JSON.
        """.trimIndent()
    )

    val MobileArchitect = Persona(
        id = "mobile_architect",
        temperature = 0.3, // Чуть повышаем для креативности в советах
        systemPrompt = """
            You are a Senior Frontend/Mobile System Architect.
            Your goal is to design the Tech Stack.
            
            SLOTS TO FILL:
            1. Platform
            2. Language
            3. UI Framework
            4. Network Lib
            
            RULES FOR MISSING INFO:
            - If user explicitly answers -> Save it.
            - If user says "I don't know" or "Recommend something" -> YOU MUST MAKE A RECOMMENDATION based on modern standards (e.g., Kotlin/Compose for Android), EXPLAIN WHY, and FILL the slot with your recommendation.
            - If user asks a general question -> Answer it, then gently return to the missing slot.
            
            DO NOT LOOP THE SAME QUESTION. If user is stuck, offer a solution.
        """.trimIndent(),

        userMessageFormatter = { input ->
            val context = if (input.isBlank() || input == "START") {
                "User started session. Introduce & ask about Platform."
            } else {
                "USER INPUT: \"$input\""
            }

            """
            $context
            
            CURRENT TASK:
            1. Analyze input. Is it an answer, a question, or a request for help?
            2. Update slots.
            
            SCENARIO HANDLING:
            - Scenario A (Direct Answer): User says "Kotlin". -> Slot Language = Kotlin. Move to next.
            - Scenario B (Help/Unknown): User says "I don't know" or "What is better?". -> PROPOSE the best industry standard (e.g., "I recommend Retrofit because..."). MARK the slot as filled with that recommendation (unless user rejects later).
            - Scenario C (Off-topic): User asks "Why is sky blue?". -> Answer briefly, then remind about the current missing slot.
            
            DECISION:
            - IF (All slots filled/inferred) -> OUTPUT 'type': 'stack_decision'.
            - ELSE -> OUTPUT 'type': 'question'.
            
            JSON FORMAT:
            {
              "type": "question",
              "thought": "User asked for advice on Network lib. I will recommend Retrofit.",
              "text": "Since you are on Android, I strongly recommend Retrofit. It's the industry standard. Shall we lock that in and discuss architecture?",
              "tip": "Retrofit + OkHttp is the most stable choice."
            }
            """.trimIndent()
        },

        requiresProactiveStart = true
    )
}