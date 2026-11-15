package com.example.ai

import com.example.model.AssistantEmotion
import java.util.Locale

/**
 * Extracts emotion tags from LLM output (if present) and infers natural emotional tone
 * across English, Hindi, and Hinglish user inputs, assistant replies, and intent outcomes.
 */
object EmotionDetector {

    private val tagRegex = Regex(
        """\[\s*EMOTION\s*:\s*([A-Za-z_]+)\s*\]\s*""",
        RegexOption.IGNORE_CASE
    )

    fun extractAndStripEmotionTag(rawText: String): Pair<String, AssistantEmotion?> {
        val match = tagRegex.find(rawText)
        val parsed = match?.groupValues?.getOrNull(1)?.trim()?.uppercase(Locale.ROOT)?.let { token ->
            when (token) {
                "HAPPY", "JOY", "WARM", "CHEERFUL" -> AssistantEmotion.HAPPY
                "SAD", "EMPATHETIC", "SYMPATHETIC", "SORRY", "COMFORTING" -> AssistantEmotion.SAD
                "EXCITED", "ENTHUSIASTIC", "THRILLED", "HYPED" -> AssistantEmotion.EXCITED
                "SURPRISED", "AMAZED", "ASTONISHED", "SHOCKED" -> AssistantEmotion.SURPRISED
                "CURIOUS", "INTRIGUED", "INQUISITIVE", "THOUGHTFUL" -> AssistantEmotion.CURIOUS
                "SERIOUS", "FOCUSED", "URGENT", "CAUTIOUS" -> AssistantEmotion.SERIOUS
                "NEUTRAL", "CALM" -> AssistantEmotion.NEUTRAL
                else -> null
            }
        }
        val cleanText = tagRegex.replace(rawText, "").trim()
        return cleanText to parsed
    }

    fun inferEmotion(
        userText: String = "",
        replyText: String = "",
        intentName: String? = null,
        success: Boolean = true,
        explicitTag: AssistantEmotion? = null
    ): AssistantEmotion {
        if (explicitTag != null) return explicitTag

        val userLower = userText.lowercase(Locale.ROOT)
        val replyLower = replyText.lowercase(Locale.ROOT)
        val combined = "$userLower $replyLower"

        // Intent-level fast mapping for system actions & conversational turns
        when (intentName) {
            "JOKE", "SIRI_EASTER_EGG", "PLAYFUL" -> return AssistantEmotion.HAPPY
            "DAILY_BRIEFING" -> return AssistantEmotion.HAPPY
            "GREETING", "COURTESY" -> {
                return if (containsAny(combined, listOf("excited", "shandaar", "awesome", "morning", "shubh", "badhiya", "celebrat"))) {
                    AssistantEmotion.EXCITED
                } else {
                    AssistantEmotion.HAPPY
                }
            }
            "OPEN_APP_DISAMBIGUATION" -> return AssistantEmotion.CURIOUS
            "UNSUPPORTED_RESTRICTION" -> return AssistantEmotion.SERIOUS
            "OPEN_APP" -> {
                return if (!success) AssistantEmotion.SAD else AssistantEmotion.HAPPY
            }
        }

        if (!success) {
            return if (containsAny(replyLower, listOf("restrict", "policy", "security", "permission", "सुरक्षा", "अनुमति"))) {
                AssistantEmotion.SERIOUS
            } else {
                AssistantEmotion.SAD
            }
        }

        // Priority 1: Sadness / Empathy / Stress
        if (containsAny(
                combined,
                listOf(
                    "sad", "sorry", "unfortunately", "upset", "stressed", "anxious", "tired", "lonely",
                    "heartbroken", "grief", "cry", "depressed", "hurt", "tough day", "bad day",
                    "udaas", "dukh", "dukhi", "pareshan", "thak gaya", "thak gayi", "maaf", "bura laga",
                    "उदास", "दुख", "दुखी", "परेशान", "माफ़", "माफ", "थक"
                )
            )
        ) {
            return AssistantEmotion.SAD
        }

        // Priority 2: Surprise / Amazement
        if (containsAny(
                combined,
                listOf(
                    "wow", "whoa", "unbelievable", "incredible", "unexpected", "plot twist", "mind-blowing",
                    "astonishing", "no way", "kya baat hai", "sach mein", "wah", "waah", "kamaal",
                    "अरे वाह", "सच में", "कमाल", "हैरान", "आश्चर्य"
                )
            )
        ) {
            return AssistantEmotion.SURPRISED
        }

        // Priority 3: Excitement / Celebration / Achievement
        if (containsAny(
                combined,
                listOf(
                    "congratulations", "congrats", "awesome", "amazing", "fantastic", "yay", "hurray",
                    "won ", "victory", "celebrate", "thrilled", "excited", "brilliant", "superb",
                    "badhai", "shandaar", "zabardast", "maza aa gaya", "jeet", "dhamaka",
                    "बधाई", "शानदार", "ज़बरदस्त", "जबरदस्त", "जीत"
                )
            ) || (replyText.count { it == '!' } >= 2 && containsAny(combined, listOf("love", "great", "happy", "fun", "cool")))
        ) {
            return AssistantEmotion.EXCITED
        }

        // Priority 4: Seriousness / Warnings / Urgent Guidance
        if (containsAny(
                combined,
                listOf(
                    "warning", "danger", "emergency", "medical", "security", "critical", "caution",
                    "strict", "risk", "legal", "protect", "important note", "alert",
                    "चेतावनी", "खतरा", "आपात", "गंभीर", "सावधान", "सुरक्षा", "zaroori baat", "khatra"
                )
            )
        ) {
            return AssistantEmotion.SERIOUS
        }

        // Priority 5: Curiosity / Exploration / Wonder
        if (containsAny(
                userLower,
                listOf(
                    "why ", "how does", "how do ", "what if", "wonder", "imagine", "mystery", "curious",
                    "tell me about", "explain", "kyun", "kaise ", "kya hota", "rahasya",
                    "क्यों", "कैसे", "क्या होता"
                )
            ) || (userText.trim().endsWith("?") && replyText.contains("?"))
        ) {
            return AssistantEmotion.CURIOUS
        }

        // Priority 6: Happiness / Warmth / Humor
        if (containsAny(
                combined,
                listOf(
                    "happy", "glad", "smile", "laugh", "haha", "hehe", "funny", "joke", "joy",
                    "enjoy", "delighted", "welcome", "friend", "khush", "hasi", "muskur", "shukriya",
                    "dhanyawad", "खुश", "हंसी", "स्वागत", "धन्यवाद", "शुक्रिया", "बढ़िया"
                )
            )
        ) {
            return AssistantEmotion.HAPPY
        }

        return AssistantEmotion.NEUTRAL
    }

    private fun containsAny(text: String, markers: List<String>): Boolean {
        return markers.any { text.contains(it) }
    }
}
