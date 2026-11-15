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
import com.example.model.AssistantEmotion
import com.example.model.LanguageOption
import com.example.model.VoiceGender
import com.example.model.VoicePersonality
import java.util.Locale

class SiriTextToSpeech(
    private val context: Context,
    private val onStateChange: (isSpeaking: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var isShutdown = false
    private var pendingUtterance: Triple<String, String, AssistantEmotion>? = null

    @Volatile
    var lastVoiceFallbackNote: String? = null
        private set

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentPersonality: VoicePersonality = VoicePersonality.FRIENDLY
    private var currentVoiceGender: VoiceGender = VoiceGender.FEMALE
    private var currentLanguage: LanguageOption = LanguageOption.AUTO
    private var currentEmotion: AssistantEmotion = AssistantEmotion.NEUTRAL
    private var customSpeedMultiplier: Float = 1.0f
    private var customPitchMultiplier: Float = 1.0f

    // Cached voice/locale resolution to eliminate redundant Binder IPC lookups per utterance
    private var cachedVoices: List<android.speech.tts.Voice>? = null
    private var lastAppliedLocaleKey: String? = null
    private var lastAppliedGenderKey: VoiceGender? = null
    private var lastAppliedVoice: android.speech.tts.Voice? = null
    private var lastAppliedPitch: Float = -1f
    private var lastAppliedRate: Float = -1f

    private var activeFinalUtteranceId: String? = null
    private var speechSafetyRunnable: Runnable? = null

    private fun cancelSpeechSafetyWatchdog() {
        speechSafetyRunnable?.let { mainHandler.removeCallbacks(it) }
        speechSafetyRunnable = null
    }

    private fun scheduleSpeechSafetyWatchdog(textLength: Int) {
        cancelSpeechSafetyWatchdog()
        val timeoutMs = ((textLength * 95L) + 4_500L).coerceIn(6_000L, 45_000L)
        val expectedFinalId = activeFinalUtteranceId
        val runnable = Runnable {
            if (!isShutdown && expectedFinalId != null && activeFinalUtteranceId == expectedFinalId) {
                val stillSpeaking = try {
                    tts?.isSpeaking == true
                } catch (_: Exception) {
                    false
                }
                if (!stillSpeaking) {
                    Log.w("SiriTTS", "Speech safety watchdog cleared stalled TTS state ($expectedFinalId)")
                    activeFinalUtteranceId = null
                    abandonAudioFocus()
                    notifyState(false)
                } else {
                    scheduleSpeechSafetyWatchdog(60)
                }
            }
        }
        speechSafetyRunnable = runnable
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    private fun notifyState(isSpeaking: Boolean) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            onStateChange(isSpeaking)
        } else {
            mainHandler.post { onStateChange(isSpeaking) }
        }
    }

    override fun onInit(status: Int) {
        if (isShutdown) return
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            cachedVoices = try {
                tts?.voices?.toList()
            } catch (_: Exception) {
                null
            }
            applyVoiceSettings()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    requestAudioFocus()
                    notifyState(true)
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == activeFinalUtteranceId || activeFinalUtteranceId == null) {
                        cancelSpeechSafetyWatchdog()
                        activeFinalUtteranceId = null
                        abandonAudioFocus()
                        notifyState(false)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    cancelSpeechSafetyWatchdog()
                    activeFinalUtteranceId = null
                    abandonAudioFocus()
                    notifyState(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    cancelSpeechSafetyWatchdog()
                    activeFinalUtteranceId = null
                    abandonAudioFocus()
                    notifyState(false)
                }
            })
            Log.d("SiriTTS", "TextToSpeech successfully initialized (cachedVoices=${cachedVoices?.size ?: 0})")
            val pending = pendingUtterance
            pendingUtterance = null
            if (pending != null) {
                mainHandler.post {
                    if (!isShutdown) {
                        speak(pending.first, pending.third, pending.second)
                    }
                }
            }
        } else {
            Log.e("SiriTTS", "TextToSpeech initialization failed with status $status")
            pendingUtterance = null
            notifyState(false)
        }
    }

    fun setPersonality(personality: VoicePersonality) {
        currentPersonality = personality
        applyVoiceSettings()
    }

    fun setVoiceGender(gender: VoiceGender) {
        currentVoiceGender = gender
        lastAppliedGenderKey = null
        applyVoiceSettings()
    }

    fun setLanguage(language: LanguageOption) {
        currentLanguage = language
        lastAppliedLocaleKey = null
        applyVoiceSettings()
    }

    fun setEmotion(emotion: AssistantEmotion) {
        currentEmotion = emotion
        applyVoiceSettings(emotionOverride = emotion)
    }

    fun setSpeedMultiplier(multiplier: Float) {
        customSpeedMultiplier = multiplier.coerceIn(0.5f, 2.0f)
        applyVoiceSettings()
    }

    fun setPitchMultiplier(multiplier: Float) {
        customPitchMultiplier = multiplier.coerceIn(0.5f, 2.0f)
        applyVoiceSettings()
    }

    /**
     * Computes effective (pitch, speechRate) combining Personality, Gender, Custom Multipliers, and Active Emotion.
     */
    fun computeEffectiveVoiceParams(emotion: AssistantEmotion = currentEmotion): Pair<Float, Float> {
        val effectivePitch = (currentPersonality.pitch * customPitchMultiplier * currentVoiceGender.pitchFactor * emotion.pitchMultiplier)
            .coerceIn(0.55f, 1.85f)
        val effectiveRate = (currentPersonality.speechRate * customSpeedMultiplier * emotion.rateMultiplier)
            .coerceIn(0.55f, 1.85f)
        return effectivePitch to effectiveRate
    }

    private fun getTargetLocale(text: String? = null): Locale {
        return when (currentLanguage) {
            LanguageOption.HINDI -> Locale.forLanguageTag("hi-IN")
            LanguageOption.HINGLISH -> {
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

    fun applyVoiceSettings(forText: String? = null, emotionOverride: AssistantEmotion = currentEmotion) {
        val engine = tts ?: return
        if (!isInitialized) return

        val locale = getTargetLocale(forText)
        val localeTag = locale.toLanguageTag()

        if (lastAppliedLocaleKey != localeTag || lastAppliedGenderKey != currentVoiceGender) {
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
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
            lastAppliedLocaleKey = localeTag
            lastAppliedGenderKey = currentVoiceGender
        }

        val (effectivePitch, effectiveRate) = computeEffectiveVoiceParams(emotionOverride)
        if (kotlin.math.abs(lastAppliedPitch - effectivePitch) > 0.005f) {
            engine.setPitch(effectivePitch)
            lastAppliedPitch = effectivePitch
        }
        if (kotlin.math.abs(lastAppliedRate - effectiveRate) > 0.005f) {
            engine.setSpeechRate(effectiveRate)
            lastAppliedRate = effectiveRate
        }
    }

    private fun applyGenderVoice(targetLocale: Locale) {
        val engine = tts ?: return
        lastVoiceFallbackNote = null
        try {
            val availableVoices = cachedVoices ?: try {
                engine.voices?.toList()?.also { cachedVoices = it }
            } catch (_: Exception) {
                null
            }
            if (!availableVoices.isNullOrEmpty()) {
                val matchingLocaleVoices = availableVoices.filter { voice ->
                    voice.locale.language.equals(targetLocale.language, ignoreCase = true)
                }
                val pool = if (matchingLocaleVoices.isNotEmpty()) matchingLocaleVoices else availableVoices

                fun isFemaleVoice(voice: android.speech.tts.Voice): Boolean {
                    val n = voice.name.lowercase(Locale.ROOT)
                    val feats = voice.features?.joinToString(",")?.lowercase(Locale.ROOT).orEmpty()
                    return (feats.contains("female") || n.contains("female") || n.contains("#female") ||
                        n.contains("-fem-") || n.contains("_fem") || n.contains("sfg") || n.contains("f00") ||
                        n.contains("woman") || n.contains("hia") || n.contains("hic") ||
                        n.contains("hie") || n.contains("cfn") || n.contains("-a-") || n.contains("-c-") ||
                        n.contains("-d-") || n.contains("-f-") || n.contains("end") || n.contains("ene") ||
                        n.contains("tpf")) && !n.contains("male_") && !n.contains("-male-")
                }

                fun isMaleVoice(voice: android.speech.tts.Voice): Boolean {
                    val n = voice.name.lowercase(Locale.ROOT)
                    val feats = voice.features?.joinToString(",")?.lowercase(Locale.ROOT).orEmpty()
                    return (feats.contains("male") && !feats.contains("female")) ||
                        ((n.contains("male") || n.contains("-male-") || n.contains("_male") ||
                            n.contains("m00") || n.contains("man") || n.contains("hib") ||
                            n.contains("hid") || n.contains("-b-") || n.contains("-e-") ||
                            n.contains("enc") || n.contains("iom")) && !n.contains("female"))
                }

                val selectedVoice = when (currentVoiceGender) {
                    VoiceGender.FEMALE -> {
                        val exact = pool.firstOrNull { isFemaleVoice(it) }
                        if (exact == null) {
                            lastVoiceFallbackNote = "Dedicated female engine voice unavailable on device; using pitch-tuned fallback."
                        }
                        exact ?: pool.firstOrNull()
                    }
                    VoiceGender.MALE -> {
                        val exact = pool.firstOrNull { isMaleVoice(it) }
                        if (exact == null) {
                            lastVoiceFallbackNote = "Dedicated male engine voice unavailable on device; using pitch-tuned fallback."
                        }
                        exact ?: pool.firstOrNull()
                    }
                    VoiceGender.SYSTEM_DEFAULT -> null
                }
                if (selectedVoice != null && selectedVoice.name != lastAppliedVoice?.name) {
                    engine.voice = selectedVoice
                    lastAppliedVoice = selectedVoice
                    Log.d("SiriTTS", "Applied engine voice: ${selectedVoice.name} for $currentVoiceGender")
                }
            }
        } catch (e: Exception) {
            Log.d("SiriTTS", "Voice selection fallback: ${e.message}")
        }
    }

    fun sanitizeForSpeech(text: String): String {
        val stripped = text
            .replace(Regex("""\[\s*EMOTION\s*:\s*[A-Za-z_]+\s*\]\s*""", RegexOption.IGNORE_CASE), "")
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
            .replace(Regex("""\s*[—–]+\s*"""), ", ")
            .replace(Regex("""\s+-\s+"""), ", ")
            .replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return normalizePronunciation(stripped)
    }

    /**
     * Smooths abbreviations, units, and symbols so TTS pronounces replies naturally like a human.
     */
    fun normalizePronunciation(text: String): String {
        return text
            .replace(Regex("""\be\.g\.?\b""", RegexOption.IGNORE_CASE), "for example")
            .replace(Regex("""\bi\.e\.?\b""", RegexOption.IGNORE_CASE), "that is")
            .replace(Regex("""\betc\.?(?=\s|$)""", RegexOption.IGNORE_CASE), "and so on")
            .replace(Regex("""\bvs\.?\b""", RegexOption.IGNORE_CASE), "versus")
            .replace(Regex("""(\d+)\s*°\s*C\b""", RegexOption.IGNORE_CASE), "$1 degrees Celsius")
            .replace(Regex("""(\d+)\s*°\s*F\b""", RegexOption.IGNORE_CASE), "$1 degrees Fahrenheit")
            .replace(Regex("""(\d+)\s*km/h\b""", RegexOption.IGNORE_CASE), "$1 kilometers per hour")
            .replace(Regex("""(\d+)\s*%"""), "$1 percent")
            .replace(Regex("""\s+&\s+"""), " and ")
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
            "badhiya", "theek", "hoon", "aaj", "kal", "diya", "madad", "chutkula",
            "haan", "nahi", "accha", "bhai", "yaar", "samjho", "bata", "naam",
            "mera", "tumhara", "aapka", "kyunki", "kholo", "raha", "rahi", "hun"
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

    fun speak(
        text: String,
        emotion: AssistantEmotion = currentEmotion,
        utteranceId: String = "siri_speech_${System.currentTimeMillis()}"
    ) {
        if (isShutdown) {
            notifyState(false)
            return
        }
        currentEmotion = emotion
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) {
            notifyState(false)
            return
        }

        if (!isInitialized || tts == null) {
            Log.w("SiriTTS", "TTS initializing; queuing utterance")
            pendingUtterance = Triple(text, utteranceId, emotion)
            return
        }
        stopInternal(notifyIdle = false)

        applyVoiceSettings(clean, emotion)

        val speechChunks = buildSpokenChunksWithPauses(clean, emotion)
        if (speechChunks.isEmpty()) {
            notifyState(false)
            return
        }

        val finalId = "${utteranceId}_final"
        activeFinalUtteranceId = finalId

        var queuedAny = false
        for ((index, chunk) in speechChunks.withIndex()) {
            val (phrase, pauseAfterMs) = chunk
            val isLast = (index == speechChunks.lastIndex)
            val chunkId = if (isLast) finalId else "${utteranceId}_part_$index"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkId)
            }
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = tts?.speak(phrase, queueMode, params, chunkId) ?: TextToSpeech.ERROR
            if (result == TextToSpeech.SUCCESS) {
                queuedAny = true
                if (!isLast && pauseAfterMs > 0L) {
                    tts?.playSilentUtterance(pauseAfterMs, TextToSpeech.QUEUE_ADD, null)
                }
            } else {
                Log.w("SiriTTS", "TTS speak() returned error $result")
                break
            }
        }
        if (!queuedAny) {
            cancelSpeechSafetyWatchdog()
            activeFinalUtteranceId = null
            abandonAudioFocus()
            notifyState(false)
        } else {
            scheduleSpeechSafetyWatchdog(clean.length)
        }
    }

    /**
     * Queues an incremental sentence immediately during streaming AI generation so audio
     * starts playing as soon as the first clause arrives.
     */
    fun speakStreamSentence(
        sentence: String,
        emotion: AssistantEmotion = currentEmotion,
        isFirst: Boolean,
        isFinal: Boolean,
        streamId: String
    ) {
        if (isShutdown) return
        currentEmotion = emotion
        val clean = sanitizeForSpeech(sentence)
        if (clean.isBlank()) {
            if (isFinal && activeFinalUtteranceId == null) {
                notifyState(false)
            }
            return
        }

        if (!isInitialized || tts == null) {
            if (isFirst) {
                pendingUtterance = Triple(clean, streamId, emotion)
            }
            return
        }

        if (isFirst) {
            stopInternal(notifyIdle = false)
            applyVoiceSettings(clean, emotion)
        }

        val uttId = if (isFinal) "${streamId}_final" else "${streamId}_chunk_${System.nanoTime()}"
        if (isFinal) {
            activeFinalUtteranceId = uttId
        } else if (activeFinalUtteranceId == null) {
            activeFinalUtteranceId = "${streamId}_pending_final"
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uttId)
        }
        val queueMode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = tts?.speak(clean, queueMode, params, uttId) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.SUCCESS) {
            if (!isFinal) {
                tts?.playSilentUtterance(95L, TextToSpeech.QUEUE_ADD, null)
            }
            scheduleSpeechSafetyWatchdog(clean.length + 120)
        }
    }

    fun markStreamCompleted(streamId: String) {
        if (isShutdown) return
        val finalId = "${streamId}_done_sentinel"
        activeFinalUtteranceId = finalId
        val res = tts?.playSilentUtterance(10L, TextToSpeech.QUEUE_ADD, finalId)
        if (res != TextToSpeech.SUCCESS && tts?.isSpeaking != true) {
            cancelSpeechSafetyWatchdog()
            activeFinalUtteranceId = null
            abandonAudioFocus()
            notifyState(false)
        }
    }

    /**
     * Breaks sanitized text into sentences and long clauses with natural human breath pauses.
     */
    fun buildSpokenChunksWithPauses(
        cleanText: String,
        emotion: AssistantEmotion = currentEmotion
    ): List<Pair<String, Long>> {
        val sentencePause = when (emotion) {
            AssistantEmotion.EXCITED -> 120L
            AssistantEmotion.SAD, AssistantEmotion.SERIOUS -> 210L
            else -> 180L
        }
        val clausePause = when (emotion) {
            AssistantEmotion.EXCITED -> 45L
            AssistantEmotion.SAD, AssistantEmotion.SERIOUS -> 80L
            else -> 65L
        }

        val sentences = cleanText.split(Regex("""(?<=[.!?।])\s+""")).filter { it.isNotBlank() }
        val result = mutableListOf<Pair<String, Long>>()
        for ((sIdx, sentence) in sentences.withIndex()) {
            val isLastSentence = (sIdx == sentences.lastIndex)
            val clauses = if (sentence.length > 55) {
                sentence.split(Regex("""(?<=[,;:])\s+""")).filter { it.isNotBlank() }
            } else {
                listOf(sentence)
            }
            for ((cIdx, clause) in clauses.withIndex()) {
                val isLastClauseInSentence = (cIdx == clauses.lastIndex)
                val pauseMs = when {
                    isLastSentence && isLastClauseInSentence -> 0L
                    isLastClauseInSentence -> sentencePause
                    else -> clausePause
                }
                result.add(clause to pauseMs)
            }
        }
        return result
    }

    private fun stopInternal(notifyIdle: Boolean) {
        cancelSpeechSafetyWatchdog()
        pendingUtterance = null
        activeFinalUtteranceId = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("SiriTTS", "Error stopping TTS", e)
        }
        abandonAudioFocus()
        if (notifyIdle) {
            notifyState(false)
        }
    }

    fun stop() {
        stopInternal(notifyIdle = true)
    }

    fun shutdown() {
        isShutdown = true
        cancelSpeechSafetyWatchdog()
        pendingUtterance = null
        activeFinalUtteranceId = null
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
