package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.model.LanguageOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe wrapper around Android's SpeechRecognizer.
 * Enforces main-thread execution, lazy creation, session-generation fencing to drop stale callbacks,
 * and structured error diagnostics without crashing or leaving dangling listening states.
 */
class SiriSpeechRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onRmsDbChanged: (Float) -> Unit,
    private val onErrorOccurred: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onSpeechDetected: (() -> Unit)? = null,
    private val onNoMatchOrTimeout: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "SpeechRec"
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var isListening: Boolean = false
    private val isDestroyed = AtomicBoolean(false)
    private val sessionGeneration = AtomicLong(0L)

    @Volatile
    private var currentLanguage: LanguageOption = LanguageOption.AUTO

    // Duplicate command prevention & debouncing
    private var lastRecognizedText: String = ""
    private var lastRecognizedTimestamp: Long = 0L

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun createRecognizerOnMain() {
        if (isDestroyed.get()) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring destroy error before recreate: ${e.message}")
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
    }

    private fun attachListenerForGeneration(gen: Long) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (gen != sessionGeneration.get() || isDestroyed.get()) return
                Log.d(TAG, "onReadyForSpeech (gen=$gen)")
                isListening = true
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {
                if (gen != sessionGeneration.get() || isDestroyed.get()) return
                Log.d(TAG, "onBeginningOfSpeech (gen=$gen)")
                isListening = true
                onListeningStateChanged(true)
                onSpeechDetected?.invoke()
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (gen != sessionGeneration.get() || isDestroyed.get()) return
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                onRmsDbChanged(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (gen != sessionGeneration.get() || isDestroyed.get()) return
                Log.d(TAG, "onEndOfSpeech (gen=$gen)")
                isListening = false
                onListeningStateChanged(false)
            }

            override fun onError(error: Int) {
                if (gen != sessionGeneration.get() || isDestroyed.get()) {
                    Log.d(TAG, "Ignored stale onError=$error for old gen=$gen")
                    return
                }
                isListening = false
                onListeningStateChanged(false)
                val errorMsg = mapErrorCode(error)
                Log.w(TAG, "Recognition error=$error ($errorMsg) gen=$gen")

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_AUDIO
                ) {
                    destroyRecognizerInstance()
                }

                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onErrorOccurred(errorMsg)
                } else {
                    onNoMatchOrTimeout?.invoke()
                }
            }

            override fun onResults(results: Bundle?) {
                if (gen != sessionGeneration.get() || isDestroyed.get()) return
                isListening = false
                onListeningStateChanged(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()?.trim()
                if (!spokenText.isNullOrEmpty()) {
                    val now = System.currentTimeMillis()
                    if (spokenText.equals(lastRecognizedText, ignoreCase = true) && (now - lastRecognizedTimestamp) < 1500) {
                        Log.d(TAG, "Debounced duplicate speech command: $spokenText")
                        onNoMatchOrTimeout?.invoke()
                        return
                    }
                    lastRecognizedText = spokenText
                    lastRecognizedTimestamp = now
                    Log.d(TAG, "Recognized command (gen=$gen): $spokenText")
                    onResult(spokenText)
                } else {
                    Log.d(TAG, "Empty onResults (gen=$gen)")
                    onNoMatchOrTimeout?.invoke()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (gen != sessionGeneration.get() || isDestroyed.get()) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()
                if (!partial.isNullOrEmpty()) {
                    onPartialResult(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun mapErrorCode(error: Int): String = when (error) {
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

    private fun destroyRecognizerInstance() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    fun setLanguage(language: LanguageOption) {
        currentLanguage = language
    }

    fun startListening() {
        runOnMain {
            if (isDestroyed.get()) return@runOnMain
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                Log.e(TAG, "SpeechRecognizer unavailable on device")
                onErrorOccurred("Speech recognizer is not available on this device.")
                return@runOnMain
            }

            if (isListening) {
                Log.d(TAG, "Cancelling previous active SpeechRecognizer session before starting new one")
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }
                isListening = false
            }

            val gen = sessionGeneration.incrementAndGet()
            if (speechRecognizer == null) {
                createRecognizerOnMain()
            }
            if (speechRecognizer == null) {
                onErrorOccurred("Could not initialize speech recognizer.")
                return@runOnMain
            }
            attachListenerForGeneration(gen)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)

                val langLocale = when (currentLanguage) {
                    LanguageOption.HINDI -> "hi-IN"
                    LanguageOption.HINGLISH -> "en-IN"
                    LanguageOption.ENGLISH -> "en-US"
                    LanguageOption.AUTO -> "en-US"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langLocale)
                if (currentLanguage == LanguageOption.AUTO || currentLanguage == LanguageOption.HINGLISH) {
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US"))
                }
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }

            try {
                Log.d(TAG, "startListening (gen=$gen, lang=$currentLanguage)")
                speechRecognizer?.startListening(intent)
                isListening = true
                onListeningStateChanged(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening (gen=$gen)", e)
                isListening = false
                destroyRecognizerInstance()
                onErrorOccurred("Could not start microphone: ${e.message}")
            }
        }
    }

    fun stopListening() {
        runOnMain {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping listening", e)
            }
            isListening = false
            onListeningStateChanged(false)
        }
    }

    fun cancel() {
        runOnMain {
            sessionGeneration.incrementAndGet()
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling listening", e)
            }
            if (isListening) {
                isListening = false
                onListeningStateChanged(false)
            }
        }
    }

    fun destroy() {
        isDestroyed.set(true)
        runOnMain {
            sessionGeneration.incrementAndGet()
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            isListening = false
            destroyRecognizerInstance()
        }
    }

    fun isCurrentlyListening(): Boolean = isListening
}
