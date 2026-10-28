package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.model.LanguageOption

class SiriSpeechRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onRmsDbChanged: (Float) -> Unit,
    private val onErrorOccurred: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onSpeechDetected: (() -> Unit)? = null
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private var currentLanguage: LanguageOption = LanguageOption.AUTO

    // Duplicate command prevention & debouncing
    private var lastRecognizedText: String = ""
    private var lastRecognizedTimestamp: Long = 0L

    init {
        createRecognizer()
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("SiriSpeech", "Speech recognition not available on this device")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListeningStateChanged(true)
                }

                override fun onBeginningOfSpeech() {
                    isListening = true
                    onListeningStateChanged(true)
                    onSpeechDetected?.invoke()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Normalize -2dB to 10dB into 0.05 - 1.0 range
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                    onRmsDbChanged(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    onListeningStateChanged(false)
                }

                override fun onError(error: Int) {
                    isListening = false
                    onListeningStateChanged(false)
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client connection error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out"
                        else -> "Speech recognition error ($error)"
                    }
                    Log.d("SiriSpeech", "Recognition error: $errorMsg")
                    // Do not treat silence as a fatal error
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        onErrorOccurred(errorMsg)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onListeningStateChanged(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim()
                    if (!spokenText.isNullOrEmpty()) {
                        val now = System.currentTimeMillis()
                        // Duplicate prevention: ignore if exact same text within 1500ms
                        if (spokenText.equals(lastRecognizedText, ignoreCase = true) && (now - lastRecognizedTimestamp) < 1500) {
                            Log.d("SiriSpeech", "Debounced duplicate speech command: $spokenText")
                            return
                        }
                        lastRecognizedText = spokenText
                        lastRecognizedTimestamp = now
                        onResult(spokenText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim()
                    if (!partial.isNullOrEmpty()) {
                        onPartialResult(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun setLanguage(language: LanguageOption) {
        currentLanguage = language
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorOccurred("Speech recognizer is not available on this device.")
            return
        }

        if (speechRecognizer == null) {
            createRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            val langLocale = when (currentLanguage) {
                LanguageOption.HINDI -> "hi-IN"
                LanguageOption.HINGLISH -> "hi-IN"
                LanguageOption.ENGLISH -> "en-US"
                LanguageOption.AUTO -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langLocale)
            if (currentLanguage == LanguageOption.AUTO) {
                // Hint additional languages for multilingual Indian & English recognition
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-US"))
            }
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e("SiriSpeech", "Failed to start listening", e)
            onErrorOccurred("Could not start microphone: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("SiriSpeech", "Error stopping listening", e)
        }
        isListening = false
        onListeningStateChanged(false)
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e("SiriSpeech", "Error cancelling listening", e)
        }
        isListening = false
        onListeningStateChanged(false)
    }

    fun destroy() {
        cancel()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("SiriSpeech", "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }

    fun isCurrentlyListening(): Boolean = isListening
}
