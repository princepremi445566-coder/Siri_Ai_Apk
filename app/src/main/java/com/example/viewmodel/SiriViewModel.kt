package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiBrainRepository
import com.example.data.SiriRepository
import com.example.device.AndroidDeviceController
import com.example.intent.SiriIntentParser
import com.example.model.AutomationRoutine
import com.example.model.ChatMessage
import com.example.model.InformationFreshness
import com.example.model.IntentExecutionResult
import com.example.model.LanguageOption
import com.example.model.VoiceGender
import com.example.model.VoicePersonality
import com.example.model.VoiceState
import com.example.service.SiriNotificationListenerService
import com.example.voice.SiriSpeechRecognizer
import com.example.voice.SiriTextToSpeech
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SiriViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SiriRepository(application)
    val deviceController = AndroidDeviceController(application)
    val aiBrain = AiBrainRepository(application)

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0.1f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _lastAssistantSpeech = MutableStateFlow("")
    val lastAssistantSpeech: StateFlow<String> = _lastAssistantSpeech.asStateFlow()

    private val _isTorchActive = MutableStateFlow(false)
    val isTorchActive: StateFlow<Boolean> = _isTorchActive.asStateFlow()

    // Focus timer state
    private val _focusTimerRemaining = MutableStateFlow(25 * 60)
    val focusTimerRemaining: StateFlow<Int> = _focusTimerRemaining.asStateFlow()
    private val _isFocusTimerRunning = MutableStateFlow(false)
    val isFocusTimerRunning: StateFlow<Boolean> = _isFocusTimerRunning.asStateFlow()
    private var focusTimerJob: Job? = null

    // Sleep timer state for media playback
    private val _sleepTimerRemaining = MutableStateFlow(0)
    val sleepTimerRemaining: StateFlow<Int> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Voice session timeout job
    private var sessionTimeoutJob: Job? = null

    // AI generation job for cancellation
    private var aiJob: Job? = null

    // Camera / Image analysis state
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _apiConnectionTestResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val apiConnectionTestResult: StateFlow<Pair<Boolean, String>?> = _apiConnectionTestResult.asStateFlow()
    private val _isTestingApi = MutableStateFlow(false)
    val isTestingApi: StateFlow<Boolean> = _isTestingApi.asStateFlow()

    // Chat history search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Transient UI toast / notification events
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    // In-app update check state
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var speechRecognizer: SiriSpeechRecognizer? = null
    private var textToSpeech: SiriTextToSpeech? = null
    private lateinit var intentParser: SiriIntentParser

    init {
        initTts()
        initSpeechRecognizer()
        initIntentParser()
    }

    private fun initTts() {
        textToSpeech = SiriTextToSpeech(getApplication()) { isSpeaking ->
            if (isSpeaking) {
                _voiceState.value = VoiceState.SPEAKING
            } else {
                if (_voiceState.value == VoiceState.SPEAKING) {
                    _voiceState.value = VoiceState.IDLE
                    // Re-arm listening if continuous mode is enabled
                    if (repository.continuousListening.value) {
                        viewModelScope.launch {
                            delay(400)
                            startListening()
                        }
                    }
                }
            }
        }.apply {
            setPersonality(repository.voicePersonality.value)
            setVoiceGender(repository.voiceGender.value)
            setLanguage(repository.selectedLanguage.value)
            setSpeedMultiplier(repository.customSpeedMultiplier.value)
            setPitchMultiplier(repository.customPitchMultiplier.value)
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SiriSpeechRecognizer(
            context = getApplication(),
            onResult = { recognizedText ->
                _transcription.value = recognizedText
                handleUserUtterance(recognizedText)
            },
            onPartialResult = { partial ->
                _transcription.value = partial
                if (intentParser.isStopCommand(partial)) {
                    stopAllSpeech()
                }
            },
            onRmsDbChanged = { level ->
                _audioLevel.value = level
            },
            onErrorOccurred = { errorMsg ->
                Log.d("SiriVM", "Speech error: $errorMsg")
                _voiceState.value = VoiceState.IDLE
            },
            onListeningStateChanged = { listening ->
                if (listening) {
                    _voiceState.value = VoiceState.LISTENING
                    startVoiceSessionTimeout()
                } else if (_voiceState.value == VoiceState.LISTENING) {
                    _voiceState.value = VoiceState.THINKING
                    sessionTimeoutJob?.cancel()
                }
            },
            onSpeechDetected = {
                // Instant voice interruption (barge-in): if assistant is speaking, silence immediately
                if (_voiceState.value == VoiceState.SPEAKING) {
                    textToSpeech?.stop()
                    _voiceState.value = VoiceState.LISTENING
                }
            }
        ).apply {
            setLanguage(repository.selectedLanguage.value)
        }
    }

    private fun initIntentParser() {
        intentParser = SiriIntentParser(
            deviceController = deviceController,
            onCustomRoutineTrigger = { trigger ->
                val routine = repository.routines.value.firstOrNull {
                    it.isEnabled && trigger.contains(it.triggerPhrase.lowercase())
                }
                if (routine != null) {
                    executeRoutine(routine)
                } else null
            },
            onTaskAddTrigger = { taskTitle ->
                repository.addTask(taskTitle)
                repository.logActionExecution("ADD_TASK", "Added Task: $taskTitle", true, true, taskTitle)
                IntentExecutionResult(
                    intentName = "ADD_TASK",
                    parameters = mapOf("title" to taskTitle),
                    feedbackSpeech = "Added '$taskTitle' to your tasks.",
                    success = true,
                    actionSummary = "Added Task: $taskTitle",
                    isReversible = true
                )
            },
            onNoteAddTrigger = { noteTitle, noteContent ->
                repository.addNote(noteTitle, noteContent)
                repository.logActionExecution("ADD_NOTE", "Saved Note: $noteTitle", true, false)
                IntentExecutionResult(
                    intentName = "ADD_NOTE",
                    parameters = mapOf("title" to noteTitle, "content" to noteContent),
                    feedbackSpeech = "Note saved: $noteContent",
                    success = true,
                    actionSummary = "Saved Note"
                )
            },
            getVoiceShortcuts = { repository.shortcuts.value },
            onDailyBriefingTrigger = { generateDailyBriefing() },
            onUndoTrigger = { performUndo() },
            onVoiceGenderTrigger = { gender ->
                setVoiceGender(gender)
                val lang = repository.selectedLanguage.value
                val speech = when (gender) {
                    VoiceGender.FEMALE -> {
                        when (lang) {
                            LanguageOption.HINDI -> "मैंने आवाज़ को फीमेल सिरी आवाज़ पर सेट कर दिया है। मैं आपकी क्या मदद करूँ?"
                            LanguageOption.HINGLISH -> "Maine voice ko Female Siri voice par set kar diya hai. Kaise madad kar sakti hoon?"
                            LanguageOption.ENGLISH, LanguageOption.AUTO -> "Voice set to Female Siri voice. How can I help you?"
                        }
                    }
                    VoiceGender.MALE -> {
                        when (lang) {
                            LanguageOption.HINDI -> "मैंने आवाज़ को मेल आवाज़ पर सेट कर दिया है।"
                            LanguageOption.HINGLISH -> "Voice ko Male voice par set kar diya hai."
                            LanguageOption.ENGLISH, LanguageOption.AUTO -> "Voice has been set to Male voice."
                        }
                    }
                    VoiceGender.SYSTEM_DEFAULT -> {
                        when (lang) {
                            LanguageOption.HINDI -> "आवाज़ सिस्टम डिफ़ॉल्ट पर रीसेट कर दी गई है।"
                            LanguageOption.HINGLISH -> "Voice system default par reset kar di gayi hai."
                            LanguageOption.ENGLISH, LanguageOption.AUTO -> "Voice reset to system default."
                        }
                    }
                }
                IntentExecutionResult(
                    intentName = "SET_VOICE_GENDER",
                    parameters = mapOf("gender" to gender.name),
                    feedbackSpeech = speech,
                    success = true,
                    actionSummary = "Changed voice to ${gender.displayName}"
                )
            },
            getCurrentLanguage = { repository.selectedLanguage.value }
        )
    }

    private fun startVoiceSessionTimeout() {
        sessionTimeoutJob?.cancel()
        val timeoutSeconds = repository.userPreferences.value.voiceSessionTimeoutSeconds
        if (timeoutSeconds > 0) {
            sessionTimeoutJob = viewModelScope.launch {
                delay(timeoutSeconds * 1000L)
                if (_voiceState.value == VoiceState.LISTENING) {
                    stopListening()
                    _uiEvents.emit("Listening session timed out to save battery.")
                }
            }
        }
    }

    fun startListening() {
        stopAllSpeech()
        _transcription.value = "Listening..."
        _voiceState.value = VoiceState.LISTENING
        speechRecognizer?.startListening()
    }

    fun stopListening() {
        sessionTimeoutJob?.cancel()
        speechRecognizer?.stopListening()
        _voiceState.value = VoiceState.IDLE
    }

    fun stopAllSpeech() {
        sessionTimeoutJob?.cancel()
        aiJob?.cancel()
        textToSpeech?.stop()
        speechRecognizer?.cancel()
        _voiceState.value = VoiceState.IDLE
    }

    fun setPersonality(personality: VoicePersonality) {
        repository.updatePersonality(personality)
        textToSpeech?.setPersonality(personality)
    }

    fun setVoiceGender(gender: VoiceGender) {
        repository.updateVoiceGender(gender)
        textToSpeech?.setVoiceGender(gender)
        viewModelScope.launch {
            _uiEvents.emit("Voice set to: ${gender.displayName}")
        }
    }

    fun setLanguage(language: LanguageOption) {
        repository.updateLanguage(language)
        textToSpeech?.setLanguage(language)
        speechRecognizer?.setLanguage(language)
        viewModelScope.launch {
            _uiEvents.emit("Language set to: ${language.displayName}")
        }
    }

    fun setSpeedMultiplier(multiplier: Float) {
        repository.setSpeedMultiplier(multiplier)
        textToSpeech?.setSpeedMultiplier(multiplier)
    }

    fun setPitchMultiplier(multiplier: Float) {
        repository.setPitchMultiplier(multiplier)
        textToSpeech?.setPitchMultiplier(multiplier)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFlashlight() {
        val next = !_isTorchActive.value
        val (ok, _) = deviceController.setFlashlight(next)
        if (ok) {
            _isTorchActive.value = next
            repository.logActionExecution("FLASHLIGHT", if (next) "Turned on Flashlight" else "Turned off Flashlight", true, true)
        }
    }

    fun setSelectedImage(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun copyResponseToClipboard(text: String) {
        val ok = deviceController.copyToClipboard("Siri AI Response", text)
        viewModelScope.launch {
            _uiEvents.emit(if (ok) "Copied response to clipboard" else "Could not copy to clipboard")
        }
    }

    /**
     * Main entry point for user voice or typed input
     */
    fun handleUserUtterance(rawText: String) {
        val cleanText = intentParser.cleanWakePhrase(rawText).trim()
        if (cleanText.isEmpty()) {
            _voiceState.value = VoiceState.IDLE
            return
        }

        // Cancel any pending outdated AI request
        aiJob?.cancel()

        // Add user chat bubble
        val currentImage = _selectedImageUri.value
        val userMsg = ChatMessage(
            sessionId = repository.currentSessionId.value,
            text = rawText,
            isFromUser = true,
            imageUri = currentImage?.toString()
        )
        repository.addMessage(userMsg)

        _selectedImageUri.value = null

        // Check for immediate STOP command
        if (intentParser.isStopCommand(cleanText)) {
            stopAllSpeech()
            return
        }

        // Check if user asks to read notifications
        if (cleanText.contains("read my notification") || cleanText.contains("check notification") || cleanText.contains("summarize notification")) {
            readNotifications()
            return
        }

        // 1. Try local system intent execution first
        val localIntentResult = intentParser.parseAndExecute(cleanText)
        if (localIntentResult != null) {
            handleIntentResult(localIntentResult)
            return
        }

        // 2. Query Gemini AI brain (Multimodal capable)
        _voiceState.value = VoiceState.THINKING
        aiJob = viewModelScope.launch {
            try {
                val currentSessionMsgs = repository.messages.value.filter { it.sessionId == repository.currentSessionId.value }
                val recentPairs = currentSessionMsgs.takeLast(10).map { it.text to it.isFromUser }
                val aiResult = aiBrain.generateResponse(
                    prompt = cleanText,
                    config = repository.aiConfig.value,
                    language = repository.selectedLanguage.value,
                    recentHistory = recentPairs,
                    imageUri = currentImage
                )

                _lastAssistantSpeech.value = aiResult.text
                val assistantMsg = ChatMessage(
                    sessionId = repository.currentSessionId.value,
                    text = aiResult.text,
                    isFromUser = false,
                    freshness = InformationFreshness.AI_INTERPRETATION,
                    latencyMs = aiResult.latencyMs
                )
                repository.addMessage(assistantMsg)
                speakResponse(aiResult.text)
            } catch (e: Exception) {
                Log.e("SiriVM", "AI generation error", e)
                _voiceState.value = VoiceState.ERROR
                val errMsg = "Sorry, I had trouble processing that request. Please try again."
                repository.addMessage(
                    ChatMessage(
                        sessionId = repository.currentSessionId.value,
                        text = errMsg,
                        isFromUser = false,
                        isError = true
                    )
                )
                speakResponse(errMsg)
            }
        }
    }

    private fun handleIntentResult(result: IntentExecutionResult) {
        _lastAssistantSpeech.value = result.feedbackSpeech
        if (result.intentName.startsWith("FLASHLIGHT")) {
            _isTorchActive.value = deviceController.isFlashlightActive()
        }

        val freshness = when {
            result.intentName == "WEB_SEARCH" -> InformationFreshness.WEB_SEARCH
            else -> InformationFreshness.VERIFIED_SYSTEM
        }

        val assistantMsg = ChatMessage(
            sessionId = repository.currentSessionId.value,
            text = result.feedbackSpeech,
            isFromUser = false,
            actionType = result.intentName,
            actionSummary = result.actionSummary,
            sources = result.webSources,
            freshness = freshness
        )
        repository.addMessage(assistantMsg)
        repository.logActionExecution(result.intentName, result.actionSummary ?: result.feedbackSpeech, result.success, result.isReversible, result.undoAction)
        speakResponse(result.feedbackSpeech)
    }

    private fun speakResponse(text: String) {
        _voiceState.value = VoiceState.SPEAKING
        textToSpeech?.speak(text)
    }

    /**
     * Generates a composite Daily Briefing
     */
    fun triggerDailyBriefing() {
        val res = generateDailyBriefing()
        handleIntentResult(res)
    }

    private fun generateDailyBriefing(): IntentExecutionResult {
        val prefs = repository.userPreferences.value
        val name = prefs.userName
        val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val batteryInfo = deviceController.getBatteryInfo()
        val networkInfo = deviceController.getNetworkInfo()
        val pendingTasks = repository.tasks.value.filter { !it.isCompleted }
        val highPriority = pendingTasks.filter { it.priority == "High" }

        val taskSummary = if (pendingTasks.isEmpty()) {
            "You have no pending tasks today."
        } else {
            "You have ${pendingTasks.size} pending tasks${if (highPriority.isNotEmpty()) ", including ${highPriority.size} high priority" else ""}."
        }

        val speech = "Good day, $name! Today is $date, and it's $time. $batteryInfo $networkInfo $taskSummary Have a wonderful day!"
        return IntentExecutionResult(
            intentName = "DAILY_BRIEFING",
            feedbackSpeech = speech,
            success = true,
            actionSummary = "Delivered Daily Briefing for $name"
        )
    }

    /**
     * Performs Undo of the last reversible action
     */
    fun performUndo(): IntentExecutionResult {
        val lastLog = repository.actionLogs.value.firstOrNull { it.isReversible }
        if (lastLog == null) {
            val speech = "There are no recent actions that can be undone."
            return IntentExecutionResult("UNDO", emptyMap(), speech, false, "Nothing to undo")
        }

        return when (lastLog.actionName) {
            "FLASHLIGHT_ON" -> {
                val (ok, _) = deviceController.setFlashlight(false)
                _isTorchActive.value = false
                IntentExecutionResult("UNDO_FLASHLIGHT", emptyMap(), "Turned off flashlight.", ok, "Undid Flashlight ON")
            }
            "FLASHLIGHT_OFF" -> {
                val (ok, _) = deviceController.setFlashlight(true)
                _isTorchActive.value = true
                IntentExecutionResult("UNDO_FLASHLIGHT", emptyMap(), "Turned flashlight back on.", ok, "Undid Flashlight OFF")
            }
            "ADD_TASK" -> {
                val title = lastLog.undoPayload
                if (title != null) {
                    val task = repository.tasks.value.firstOrNull { it.title == title }
                    if (task != null) {
                        repository.removeTask(task.id)
                    }
                }
                IntentExecutionResult("UNDO_TASK", emptyMap(), "Removed the task you just added.", true, "Undid Task addition")
            }
            else -> {
                IntentExecutionResult("UNDO", emptyMap(), "Action cannot be undone automatically.", false, "Undo unsupported")
            }
        }
    }

    /**
     * Executes custom multi-step routine
     */
    fun executeRoutine(routine: AutomationRoutine): IntentExecutionResult {
        viewModelScope.launch {
            for (step in routine.steps) {
                when (step.actionType) {
                    "TIMER" -> {
                        val secs = step.parameter.toIntOrNull() ?: 300
                        deviceController.setTimer(secs, routine.name)
                    }
                    "TORCH" -> {
                        val enable = step.parameter.toBoolean()
                        deviceController.setFlashlight(enable)
                        _isTorchActive.value = enable
                    }
                    "VOLUME" -> {
                        deviceController.adjustVolume(step.parameter)
                    }
                    "ALARM" -> {
                        deviceController.setAlarm(7, 0, routine.name)
                    }
                    "INFO_BATTERY" -> {
                        deviceController.getBatteryInfo()
                    }
                    "INFO_NETWORK" -> {
                        deviceController.getNetworkInfo()
                    }
                    "APP" -> {
                        deviceController.openApp(step.parameter)
                    }
                    "SPEAK" -> {
                        speakResponse(step.parameter)
                    }
                }
                delay(350)
            }
        }
        val feedback = "Running '${routine.name}' routine with ${routine.steps.size} steps."
        return IntentExecutionResult(
            intentName = "AUTOMATION_ROUTINE",
            parameters = mapOf("routine" to routine.name),
            feedbackSpeech = feedback,
            success = true,
            actionSummary = "Executed ${routine.steps.size} automation steps"
        )
    }

    private fun readNotifications() {
        val notifications = SiriNotificationListenerService.getRecentNotifications()
        val speech = if (notifications.isEmpty()) {
            "You have no new notifications captured, or notification listener permission is not enabled yet."
        } else {
            val summary = notifications.take(3).joinToString("; ") { "${it.title}: ${it.text}" }
            "Here are your latest notifications: $summary"
        }
        val msg = ChatMessage(
            sessionId = repository.currentSessionId.value,
            text = speech,
            isFromUser = false,
            actionType = "READ_NOTIFICATIONS",
            actionSummary = "Checked ${notifications.size} notifications",
            freshness = InformationFreshness.VERIFIED_SYSTEM
        )
        repository.addMessage(msg)
        speakResponse(speech)
    }

    // --- Focus Timer ---
    fun toggleFocusTimer() {
        if (_isFocusTimerRunning.value) {
            pauseFocusTimer()
        } else {
            startFocusTimer()
        }
    }

    fun startFocusTimer() {
        _isFocusTimerRunning.value = true
        focusTimerJob?.cancel()
        focusTimerJob = viewModelScope.launch {
            while (_focusTimerRemaining.value > 0 && _isFocusTimerRunning.value) {
                delay(1000)
                _focusTimerRemaining.value -= 1
            }
            if (_focusTimerRemaining.value == 0) {
                _isFocusTimerRunning.value = false
                speakResponse("Focus study session complete! Take a 5-minute break.")
            }
        }
    }

    fun pauseFocusTimer() {
        _isFocusTimerRunning.value = false
        focusTimerJob?.cancel()
    }

    fun resetFocusTimer(minutes: Int = 25) {
        pauseFocusTimer()
        _focusTimerRemaining.value = minutes * 60
    }

    // --- Sleep Timer for Media ---
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = minutes * 60
        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerRemaining.value > 0) {
                delay(1000)
                _sleepTimerRemaining.value -= 1
            }
            deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            deviceController.adjustVolume("mute")
            speakResponse("Sleep timer finished. Media playback paused.")
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = 0
    }

    // --- In-App Update Verification ---
    fun checkForUpdates() {
        _showUpdateDialog.value = true
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    // --- API Configuration Test ---
    fun testApiConnection() {
        _isTestingApi.value = true
        _apiConnectionTestResult.value = null
        viewModelScope.launch {
            val result = aiBrain.testConnection(repository.aiConfig.value)
            _apiConnectionTestResult.value = result
            _isTestingApi.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
        focusTimerJob?.cancel()
        sleepTimerJob?.cancel()
        sessionTimeoutJob?.cancel()
        aiJob?.cancel()
    }
}
