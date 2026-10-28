package com.example.model

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class VoicePersonality(val displayName: String, val speechRate: Float, val pitch: Float, val toneDesc: String) {
    FRIENDLY("Friendly (Siri Classic)", 1.0f, 1.06f, "Warm, natural, and conversational"),
    CALM("Calm & Measured", 0.94f, 0.98f, "Soothing, gentle, and paced"),
    PROFESSIONAL("Professional", 1.0f, 1.0f, "Crisp, concise, and business-ready"),
    CHEERFUL("Cheerful", 1.08f, 1.15f, "Energetic, bright, and upbeat"),
    BOLD("Bold", 1.0f, 0.88f, "Direct, punchy, and confident")
}

enum class VoiceGender(val displayName: String, val pitchFactor: Float, val description: String) {
    FEMALE("Female (Siri)", 1.18f, "Melodious, warm Siri female voice"),
    MALE("Male", 0.90f, "Deep, resonant male voice"),
    SYSTEM_DEFAULT("System Default", 1.0f, "Android device default voice")
}

enum class LanguageOption(val displayName: String, val tag: String, val speechLocaleTag: String) {
    AUTO("Auto Detect", "auto", "en-US"),
    ENGLISH("English", "en", "en-US"),
    HINDI("Hindi (हिन्दी)", "hi", "hi-IN"),
    HINGLISH("Hinglish", "hi-en", "en-IN")
}

enum class ResponseLengthPreference(val displayName: String, val instruction: String) {
    CONCISE("Concise", "Keep the answer extremely concise, punchy, and direct in 1-2 short sentences."),
    BALANCED("Balanced", "Keep the answer conversational, warm, and natural in 2-3 sentences."),
    DETAILED("Detailed", "Provide a comprehensive, detailed explanation covering nuances."),
    STEP_BY_STEP("Step-by-step", "Provide clear step-by-step numbered instructions.")
}

enum class WakeWordOption(val phrase: String, val label: String) {
    HEY_SIRI("hey siri", "Hey Siri"),
    SIRI("siri", "Siri"),
    SUNO_SIRI("suno siri", "Suno Siri / Sun Siri")
}

enum class InformationFreshness(val label: String, val badgeColorHex: Long) {
    VERIFIED_SYSTEM("Verified System Action", 0xFF00F0FF),
    WEB_SEARCH("Live Web Grounding", 0xFF00FFB2),
    AI_INTERPRETATION("AI Generated Interpretation", 0xFFB388FF)
}

data class WebSourceLink(
    val title: String,
    val url: String,
    val snippet: String = ""
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String = "default",
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null,
    val actionSummary: String? = null,
    val imageUri: String? = null,
    val isError: Boolean = false,
    val sources: List<WebSourceLink> = emptyList(),
    val freshness: InformationFreshness = InformationFreshness.AI_INTERPRETATION,
    val latencyMs: Long? = null
)

data class ConversationSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "General" // "General", "Work", "Personal", "Research", "Study"
)

data class IntentExecutionResult(
    val intentName: String,
    val parameters: Map<String, String> = emptyMap(),
    val feedbackSpeech: String,
    val success: Boolean,
    val actionSummary: String? = null,
    val requiresConfirmation: Boolean = false,
    val webSources: List<WebSourceLink> = emptyList(),
    val isReversible: Boolean = false,
    val undoAction: String? = null
)

data class AutomationStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val actionType: String, // "TIMER", "APP", "TORCH", "NOTE", "VOLUME", "SPEAK", "ALARM", "SETTING"
    val parameter: String,
    val description: String
)

data class AutomationRoutine(
    val id: String,
    val name: String,
    val triggerPhrase: String,
    val steps: List<AutomationStep>,
    val isEnabled: Boolean = true,
    val category: String = "Custom", // "Productivity", "Lifestyle", "Relax", "Travel"
    val scheduledTime: String? = null // e.g. "07:30"
)

data class VoiceShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val triggerPhrase: String,
    val commandText: String,
    val customSpokenReply: String? = null,
    val isEnabled: Boolean = true
)

data class AutomationRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val conditionType: String, // "BATTERY_LOW", "TIME_EVENING", "TIME_MORNING"
    val thresholdValue: String, // e.g. "20", "22:00"
    val actionType: String, // "MUTE_AUDIO", "BEDTIME_ROUTINE", "FLASH_OFF"
    val isEnabled: Boolean = true
)

data class ActionExecutionLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val actionName: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true,
    val isReversible: Boolean = false,
    val undoPayload: String? = null
)

data class UserPreferences(
    val userName: String = "Friend",
    val homeLocation: String = "Home",
    val workLocation: String = "Office",
    val preferredTempUnit: String = "Celsius (°C)", // "Celsius (°C)", "Fahrenheit (°F)"
    val morningBriefingTime: String = "08:00 AM",
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val filterSensitiveNotifications: Boolean = true,
    val reducedMotion: Boolean = false,
    val voiceSessionTimeoutSeconds: Int = 30 // 15, 30, 60, 0 (no timeout)
)

data class TaskItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val timeLabel: String = "Today",
    val category: String = "General",
    val priority: String = "Normal", // "High", "Normal", "Low"
    val isCompleted: Boolean = false
)

data class NoteItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val category: String = "General", // "Personal", "Work", "Ideas", "Study"
    val timestamp: Long = System.currentTimeMillis()
)

data class FlashcardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val question: String,
    val answer: String,
    val topic: String = "General"
)

data class SmartDevice(
    val id: String,
    val name: String,
    val type: String, // "LIGHT", "PLUG", "THERMOSTAT"
    val isOn: Boolean = false,
    val stateValue: String = "Off"
)

const val DEFAULT_SIRI_SYSTEM_PROMPT: String =
    "You are Siri, an intelligent personal voice assistant.\n" +
    "1. Voice-First & Concise: Answer conversationally in 1-2 crisp, direct sentences (maximum 3). Never write essays or wall of text.\n" +
    "2. Calm, Warm & Helpful: Sound like a knowledgeable human assistant. Never use robotic chatbot filler like 'Certainly!', 'As an AI language model...', or 'Sure, here are some points:'.\n" +
    "3. Clean Spoken Words: Never use asterisks, markdown bolding, hashes, bullets, or raw URLs. Keep every response voice-ready for text-to-speech.\n" +
    "4. Language Matching: Respond in the matching language of the user's prompt (English, Hindi, or Hinglish). Use natural colloquial phrasing.\n" +
    "5. Subtle Wit & Charm: For personal or humorous questions, answer with Siri's signature dry wit, warmth, and light humor.\n" +
    "6. Contextual Follow-up: Retain previous conversational context. Where appropriate, offer a short, natural follow-up question or action."

data class AiConfig(
    val provider: String = "Google Gemini", // "Google Gemini", "Custom / OpenAI API"
    val model: String = "gemini-2.5-flash",
    val customApiKey: String = "",
    val customEndpoint: String = "", // e.g. "https://api.openai.com/v1" or custom customer proxy endpoint
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 384,
    val responseLength: ResponseLengthPreference = ResponseLengthPreference.BALANCED,
    val customGreeting: String = "Hello! I am Siri. How can I help you today?",
    val systemPrompt: String = DEFAULT_SIRI_SYSTEM_PROMPT
)

data class UpdateInfo(
    val latestVersionCode: Int = 2,
    val latestVersionName: String = "1.1",
    val releaseNotes: String = "Added 150+ advanced capabilities: Multi-turn sessions, Notes & Calendar events, Phone dialer & SMS, Flashcards & Learning assistant, Expanded multi-step routines, Web links grounding, and In-App Update verification.",
    val downloadUrl: String = "https://github.com/aistudio-project/siri-ai/releases",
    val isMandatory: Boolean = false
)
