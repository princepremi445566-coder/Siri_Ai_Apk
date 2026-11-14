package com.example.service

import android.util.Log
import com.example.voice.WakeEngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

interface BackgroundServiceController {
    fun pauseWakeWordForForegroundCommand()
    fun resumeWakeWordAfterConversation()
}

interface ForegroundAssistantListener {
    fun onWakeWordActivatedInForeground()
}

/**
 * Thread-safe coordinator between SiriVoiceService (background) and SiriViewModel / MainActivity (foreground).
 * Prevents duplicate services, prevents mic contention between wake-word detection and SpeechRecognizer,
 * avoids stale conversation locks during app switching, and resumes wake-word listening automatically.
 */
object SiriServiceBridge {

    private const val TAG = "SiriServiceBridge"
    private const val STALE_CONVERSATION_MS = 45_000L

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _wakeEngineStatus = MutableStateFlow(WakeEngineStatus.STOPPED)
    val wakeEngineStatus: StateFlow<WakeEngineStatus> = _wakeEngineStatus.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _isConversationActive = MutableStateFlow(false)
    val isConversationActive: StateFlow<Boolean> = _isConversationActive.asStateFlow()

    private val _lastDiagnosticMessage = MutableStateFlow<String?>(null)
    val lastDiagnosticMessage: StateFlow<String?> = _lastDiagnosticMessage.asStateFlow()

    @Volatile
    private var conversationStartedAtMs: Long = 0L

    @Volatile
    private var serviceControllerRef: WeakReference<BackgroundServiceController>? = null

    @Volatile
    private var foregroundListenerRef: WeakReference<ForegroundAssistantListener>? = null

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _wakeEngineStatus.value = WakeEngineStatus.STOPPED
            _isConversationActive.value = false
            conversationStartedAtMs = 0L
        }
    }

    fun setWakeEngineStatus(status: WakeEngineStatus) {
        if (_wakeEngineStatus.value != status) {
            Log.d(TAG, "Wake engine status -> $status")
            _wakeEngineStatus.value = status
        }
    }

    fun recordDiagnostic(message: String?) {
        if (!message.isNullOrBlank()) {
            Log.w(TAG, "Diagnostic: $message")
        }
        _lastDiagnosticMessage.value = message
    }

    fun setAppInForeground(inForeground: Boolean) {
        Log.d(TAG, "App foreground state -> $inForeground")
        _isAppInForeground.value = inForeground
    }

    fun attachServiceController(controller: BackgroundServiceController) {
        serviceControllerRef = WeakReference(controller)
    }

    fun detachServiceController(controller: BackgroundServiceController) {
        if (serviceControllerRef?.get() === controller) {
            serviceControllerRef = null
        }
    }

    fun attachForegroundListener(listener: ForegroundAssistantListener) {
        foregroundListenerRef = WeakReference(listener)
    }

    fun detachForegroundListener(listener: ForegroundAssistantListener) {
        if (foregroundListenerRef?.get() === listener) {
            foregroundListenerRef = null
        }
    }

    fun hasActiveForegroundUi(): Boolean {
        return _isAppInForeground.value && foregroundListenerRef?.get() != null
    }

    fun isConversationActivelyLocked(): Boolean {
        if (!_isConversationActive.value) return false
        val started = conversationStartedAtMs
        if (started > 0L && (System.currentTimeMillis() - started) > STALE_CONVERSATION_MS) {
            Log.w(TAG, "Detected stale conversation lock (> ${STALE_CONVERSATION_MS}ms); clearing so background listening can recover")
            _isConversationActive.value = false
            conversationStartedAtMs = 0L
            return false
        }
        return true
    }

    fun dispatchWakeWordToForeground(): Boolean {
        val listener = foregroundListenerRef?.get()
        return if (_isAppInForeground.value && listener != null) {
            _isConversationActive.value = true
            conversationStartedAtMs = System.currentTimeMillis()
            listener.onWakeWordActivatedInForeground()
            true
        } else {
            false
        }
    }

    fun notifyConversationStarted() {
        val alreadyActive = _isConversationActive.value
        _isConversationActive.value = true
        conversationStartedAtMs = System.currentTimeMillis()
        if (!alreadyActive) {
            Log.d(TAG, "Conversation started -> pausing background wake-word")
        }
        serviceControllerRef?.get()?.pauseWakeWordForForegroundCommand()
    }

    fun notifyConversationFinished() {
        val wasActive = _isConversationActive.getAndSetFalseCompat()
        conversationStartedAtMs = 0L
        if (wasActive) {
            Log.d(TAG, "Conversation finished -> resuming background wake-word")
        }
        serviceControllerRef?.get()?.resumeWakeWordAfterConversation()
    }

    private fun MutableStateFlow<Boolean>.getAndSetFalseCompat(): Boolean {
        val prev = value
        value = false
        return prev
    }
}
