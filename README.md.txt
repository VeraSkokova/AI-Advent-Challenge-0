# AI Advent Challenge 0: Chat with Yandex GPT

Advanced Kotlin console application for exploring LLM behavior through system prompts and multi-persona conversations.

## Key Experiment: System Prompt Impact

### What We Discovered

This project demonstrates a fundamental principle of LLM interaction: **the same conversation history is interpreted completely differently depending on the active System Prompt**.

#### The Experiment

We ran the same dialogue with two different personas:

**Persona 1: Kind Mentor** (`temperature: 0.6`)
- System Prompt: [translate:Supportive, kind coding mentor. Explain simply, praise attempts, use emojis.]
- Response to unsafe code suggestion: [translate:"Great idea! However, let's make it secure using encryption. What do you think?"]

**Persona 2: Strict Auditor** (`temperature: 0.3`)
- System Prompt: [translate:Security-focused auditor. Flag vulnerabilities immediately, be concise, no fluff.]
- Response to the same suggestion: [translate:"CRITICAL VULNERABILITY. Storing passwords in plain text is highly insecure."]

**Same history. Different interpretations.**

### Key Findings

#### 1. Stateless Re-evaluation
The LLM does not "remember" previous system prompts. Each request is evaluated fresh through the lens of the current system prompt.

**Implication:** You can switch personas mid-conversation without modifying the history. The model re-reads everything through a new "filter".

#### 2. Dominance of System Prompt
The system prompt acts as a **cognitive frame** or **interpretive lens**. It determines:
- **Tone:** Friendly vs. formal vs. aggressive
- **Priorities:** Learning vs. security vs. performance
- **Success criteria:** What counts as a "good" answer
- **Scope of criticism:** What issues get flagged

**Implication:** The same code can be "a good start" (Mentor) or "critically insecure" (Auditor) depending solely on the system prompt.

#### 3. Architecture Matters
By encapsulating system prompts + parameters in `Persona` objects, we created:
- **Reusability:** Define once, use anywhere
- **Composability:** Switch personas dynamically
- **Clarity:** Each role has explicit, documented behavior

data class Persona(
val id: String,
val systemPrompt: String,
val temperature: Double,
val userMessageFormatter: (String) -> String = { it },
val requiresProactiveStart: Boolean = false
)

text

### Practical Implications

1. **For LLM Product Design**  
   Roles should be explicitly designed, tested, and documented. Don't hope for good behavior—architect it.

2. **For Multi-Agent Systems**  
   Different agents (Security Auditor, Code Reviewer, Mentor, Architect) all reading the same codebase will give radically different feedback. This is a feature, not a bug—use it strategically.

3. **For Prompt Engineering**  
   The system prompt is your primary tool for controlling LLM behavior. Invest time in crafting it well.

---

## Project Architecture

### Layers

1. **Config (`ApiConfig.kt`):**  
   - Loads secrets from environment variables or `local.properties`
   - No business logic

2. **Domain (`Persona.kt`):**  
   - Defines AI personas with role, system prompt, temperature
   - Each persona is a complete "character"

3. **Infrastructure (`YandexGptClient.kt`):**  
   - HTTP transport layer
   - Knows nothing about roles or tasks

4. **Application (`Console.kt`):**  
   - Orchestrates I/O and persona switching
   - Parses JSON responses and renders them as human-readable text

### Available Personas

- **LiteratureTeacher:** Analyzes texts as a literature teacher
- **SystemAnalyst:** Collects technical requirements, autonomously decides when to stop
- **MobileArchitect:** Designs frontend/mobile tech stacks with proactive first question
- **KindMentor:** Supportive coding mentor (for experiments)
- **StrictAuditor:** Security-focused code reviewer (for experiments)

---

## Configuration

### Environment Variables (Priority 1)
export YANDEX_GPT_API_KEY="your-api-key"
export YANDEX_GPT_FOLDER_ID="your-folder-id"
export YANDEX_GPT_MODEL_VERSION="latest"
export YANDEX_GPT_ROLE="mobile_architect" # Optional

text

### local.properties (Priority 2)
YANDEX_GPT_API_KEY=your-api-key
YANDEX_GPT_FOLDER_ID=your-folder-id
YANDEX_GPT_MODEL_VERSION=latest
YANDEX_GPT_ROLE=mobile_architect

text

If `YANDEX_GPT_ROLE` is not set, the application will show a menu at startup.

---

## Running the Application

./gradlew run

or
java -jar app.jar

text

### Interactive Commands

- `exit` – Quit
- `clear` – Clear conversation history
- `switch` – Toggle between current and other persona (experimental)

---

## Experiment Reproduction

To reproduce the Kind Mentor vs. Strict Auditor experiment:

1. Start with default role (or choose `kind_mentor` from menu)
2. Have a conversation about storing passwords insecurely
3. Type `switch` to change personas mid-conversation
4. Observe how the same history is interpreted differently

---

## Technical Stack

- **Language:** Kotlin
- **HTTP Client:** Ktor Client (CIO engine)
- **Serialization:** kotlinx.serialization
- **API:** Yandex Cloud Foundation Models (Text Generation v1)

---

## Lessons Learned

1. **System Prompts are powerful.** They're not just hints; they're the primary control mechanism.
2. **Context is relative.** The same conversation looks different through different eyes.
3. **Explicit design beats accidental behavior.** Define your roles clearly.

---

## Future Ideas

- [ ] Save conversation history to files
- [ ] Export final TDD/Specification as Markdown or PDF
- [ ] Add more specialized personas (DevOps Engineer, QA Tester, etc.)
- [ ] Implement memory/retrieval for long conversations (RAG)
- [ ] Support streaming responses for real-time feedback