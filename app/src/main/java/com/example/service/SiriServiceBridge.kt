package com.example.service

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
 * and resumes wake-word listening automatically once a voice conversation completes.
 */
object SiriServiceBridge {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _wakeEngineStatus = MutableStateFlow(WakeEngineStatus.STOPPED)
    val wakeEngineStatus: StateFlow<WakeEngineStatus> = _wakeEngineStatus.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _isConversationActive = MutableStateFlow(false)
    val isConversationActive: StateFlow<Boolean> = _isConversationActive.asStateFlow()

    @Volatile
    private var serviceControllerRef: WeakReference<BackgroundServiceController>? = null

    @Volatile
    private var foregroundListenerRef: WeakReference<ForegroundAssistantListener>? = null

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _wakeEngineStatus.value = WakeEngineStatus.STOPPED
        }
    }

    fun setWakeEngineStatus(status: WakeEngineStatus) {
        _wakeEngineStatus.value = status
    }

    fun setAppInForeground(inForeground: Boolean) {
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

    fun dispatchWakeWordToForeground(): Boolean {
        val listener = foregroundListenerRef?.get()
        return if (_isAppInForeground.value && listener != null) {
            _isConversationActive.value = true
            listener.onWakeWordActivatedInForeground()
            true
        } else {
            false
        }
    }

    fun notifyConversationStarted() {
        _isConversationActive.value = true
        serviceControllerRef?.get()?.pauseWakeWordForForegroundCommand()
    }

    fun notifyConversationFinished() {
        _isConversationActive.value = false
        serviceControllerRef?.get()?.resumeWakeWordAfterConversation()
    }
}
