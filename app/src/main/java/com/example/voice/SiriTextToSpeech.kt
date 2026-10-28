package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.LanguageOption
import com.example.model.VoiceGender
import com.example.model.VoicePersonality
import java.util.Locale

class SiriTextToSpeech(
    private val context: Context,
    private val onStateChange: (isSpeaking: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentPersonality: VoicePersonality = VoicePersonality.FRIENDLY
    private var currentVoiceGender: VoiceGender = VoiceGender.FEMALE
    private var currentLanguage: LanguageOption = LanguageOption.AUTO
    private var customSpeedMultiplier: Float = 1.0f
    private var customPitchMultiplier: Float = 1.0f

    private var activeFinalUtteranceId: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            applyVoiceSettings()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    requestAudioFocus()
                    onStateChange(true)
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == activeFinalUtteranceId || activeFinalUtteranceId == null) {
                        abandonAudioFocus()
                        onStateChange(false)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    abandonAudioFocus()
                    onStateChange(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    abandonAudioFocus()
                    onStateChange(false)
                }
            })
            Log.d("SiriTTS", "TextToSpeech successfully initialized")
        } else {
            Log.e("SiriTTS", "TextToSpeech initialization failed with status $status")
        }
    }

    fun setPersonality(personality: VoicePersonality) {
        currentPersonality = personality
        applyVoiceSettings()
    }

    fun setVoiceGender(gender: VoiceGender) {
        currentVoiceGender = gender
        applyVoiceSettings()
    }

    fun setLanguage(language: LanguageOption) {
        currentLanguage = language
        applyVoiceSettings()
    }

    fun setSpeedMultiplier(multiplier: Float) {
        customSpeedMultiplier = multiplier.coerceIn(0.5f, 2.0f)
        applyVoiceSettings()
    }

    fun setPitchMultiplier(multiplier: Float) {
        customPitchMultiplier = multiplier.coerceIn(0.5f, 2.0f)
        applyVoiceSettings()
    }

    private fun getTargetLocale(text: String? = null): Locale {
        return when (currentLanguage) {
            LanguageOption.HINDI -> Locale.forLanguageTag("hi-IN")
            LanguageOption.HINGLISH -> {
                // If text is provided and contains Devanagari script, use Hindi locale; otherwise use Indian English for natural Hinglish
                if (text != null && text.any { it in '\u0900'..'\u097F' }) {
                    Locale.forLanguageTag("hi-IN")
                } else {
                    Locale.forLanguageTag("en-IN")
                }
            }
            LanguageOption.ENGLISH -> Locale.US
            LanguageOption.AUTO -> {
                if (text != null) detectLanguageFromText(text) else Locale.US
            }
        }
    }

    fun applyVoiceSettings(forText: String? = null) {
        val engine = tts ?: return
        if (!isInitialized) return

        val locale = getTargetLocale(forText)
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to English (India or US) if target locale not supported on device
            if (locale.language == "hi") {
                val fallbackResult = engine.setLanguage(Locale.forLanguageTag("en-IN"))
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
            } else {
                engine.setLanguage(Locale.US)
            }
        }

        applyGenderVoice(locale)

        val effectivePitch = (currentPersonality.pitch * customPitchMultiplier * currentVoiceGender.pitchFactor).coerceIn(0.5f, 2.0f)
        val effectiveRate = (currentPersonality.speechRate * customSpeedMultiplier).coerceIn(0.5f, 2.0f)

        engine.setPitch(effectivePitch)
        engine.setSpeechRate(effectiveRate)
    }

    private fun applyGenderVoice(targetLocale: Locale) {
        val engine = tts ?: return
        try {
            val availableVoices = engine.voices
            if (!availableVoices.isNullOrEmpty()) {
                val matchingLocaleVoices = availableVoices.filter { voice ->
                    voice.locale.language.equals(targetLocale.language, ignoreCase = true)
                }
                val pool = if (matchingLocaleVoices.isNotEmpty()) matchingLocaleVoices else availableVoices.toList()

                val selectedVoice = when (currentVoiceGender) {
                    VoiceGender.FEMALE -> {
                        pool.firstOrNull { voice ->
                            val n = voice.name.lowercase()
                            (n.contains("female") || n.contains("#female") || n.contains("-fem-") ||
                             n.contains("_fem") || n.contains("sfg") || n.contains("f00") ||
                             n.contains("woman") || n.contains("hia") || n.contains("hic") ||
                             n.contains("hie") || n.contains("-a-") || n.contains("-c-") ||
                             n.contains("-d-") || n.contains("-f-") || n.contains("end") || n.contains("ene")) &&
                             !n.contains("male_")
                        } ?: pool.firstOrNull()
                    }
                    VoiceGender.MALE -> {
                        pool.firstOrNull { voice ->
                            val n = voice.name.lowercase()
                            (n.contains("male") || n.contains("-male-") || n.contains("_male") ||
                             n.contains("m00") || n.contains("man") || n.contains("hib") ||
                             n.contains("hid") || n.contains("-b-") || n.contains("-e-") ||
                             n.contains("enc")) && !n.contains("female")
                        } ?: pool.firstOrNull()
                    }
                    VoiceGender.SYSTEM_DEFAULT -> null
                }
                if (selectedVoice != null) {
                    engine.voice = selectedVoice
                    Log.d("SiriTTS", "Applied engine voice: ${selectedVoice.name} for $currentVoiceGender")
                }
            }
        } catch (e: Exception) {
            Log.d("SiriTTS", "Voice selection fallback: ${e.message}")
        }
    }

    fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            .replace(Regex("""\*([^*]+)\*"""), "$1")
            .replace(Regex("""__([^_]+)__"""), "$1")
            .replace(Regex("""_([^_]+)_"""), "$1")
            .replace(Regex("""#{1,6}\s*"""), "")
            .replace(Regex("""```[\s\S]*?```"""), "")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
            .replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""^\s*[-*•]\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\s*\d+\.\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""\s*[-—–]{1,2}\s*"""), ", ")
            .replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun detectLanguageFromText(text: String): Locale {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) {
            return Locale.forLanguageTag("hi-IN")
        }
        val lower = text.lowercase()
        val hinglishMarkers = listOf(
            "hai", "hain", "karo", "kya", "batao", "samachar", "chalu", "shant",
            "kaise", "samay", "namaste", "dhanyawad", "shukriya", "bilkul",
            "badhiya", "theek", "hoon", "aaj", "kal", "diya", "madad", "chutkula"
        )
        val matchCount = hinglishMarkers.count { lower.contains(Regex("""\b$it\b""")) }
        return if (matchCount >= 1) {
            Locale.forLanguageTag("en-IN")
        } else {
            Locale.US
        }
    }

    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { /* handle ducking */ }
                    .build()
                audioFocusRequest = focusRequest
                am.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.e("SiriTTS", "Error requesting audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e("SiriTTS", "Error abandoning audio focus", e)
        }
    }

    fun speak(text: String, utteranceId: String = "siri_speech_${System.currentTimeMillis()}") {
        if (!isInitialized || tts == null) {
            Log.w("SiriTTS", "TTS not initialized yet")
            return
        }
        stop()

        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) return

        // Always apply target locale, pitch, speed, and gender voice for this utterance
        applyVoiceSettings(clean)

        // Split by sentence boundaries to insert natural breath pauses
        val sentences = clean.split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return

        val finalId = "${utteranceId}_final"
        activeFinalUtteranceId = finalId

        for ((index, sentence) in sentences.withIndex()) {
            val isLast = (index == sentences.lastIndex)
            val chunkId = if (isLast) finalId else "${utteranceId}_part_$index"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkId)
            }
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(sentence, queueMode, params, chunkId)
            if (!isLast) {
                // Calm, natural 140ms micro-pause between sentences
                tts?.playSilentUtterance(140L, TextToSpeech.QUEUE_ADD, null)
            }
        }
    }

    fun stop() {
        activeFinalUtteranceId = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("SiriTTS", "Error stopping TTS", e)
        }
        abandonAudioFocus()
        onStateChange(false)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("SiriTTS", "Error shutting down TTS", e)
        }
        abandonAudioFocus()
        tts = null
    }
}
