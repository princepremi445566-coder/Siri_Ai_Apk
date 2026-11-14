package com.example.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.ai.AiBrainRepository
import com.example.data.SiriRepository
import com.example.device.AndroidDeviceController
import com.example.intent.SiriIntentParser
import com.example.model.ChatMessage
import com.example.model.InformationFreshness
import com.example.voice.OfflineWakeWordEngine
import com.example.voice.SiriSpeechRecognizer
import com.example.voice.SiriTextToSpeech
import com.example.voice.WakeEngineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class SiriVoiceService : Service(), BackgroundServiceController {

    companion object {
        private const val TAG = "SiriVoiceService"
        const val CHANNEL_ID = "siri_voice_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_SIRI_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_SIRI_SERVICE"
        const val ACTION_TRIGGER_LISTEN = "ACTION_TRIGGER_LISTEN"
        const val ACTION_TRIGGER_BACKGROUND_LISTEN = "ACTION_TRIGGER_BACKGROUND_LISTEN"
        const val EXTRA_TRIGGER_LISTEN = "EXTRA_TRIGGER_LISTEN"

        private const val WAKELOCK_TAG = "SiriAI:BackgroundVoiceSession"
        private const val SESSION_WAKE_TIMEOUT_MS = 25_000L

        @Volatile
        var isServiceRunning = false

        fun start(context: Context) {
            val appCtx = context.applicationContext
            if (ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot start microphone foreground service without RECORD_AUDIO permission")
                SiriServiceBridge.setWakeEngineStatus(WakeEngineStatus.PERMISSION_MISSING)
                return
            }
            // Prevent duplicate startForegroundService calls when service is already healthy & active
            if (isServiceRunning && SiriServiceBridge.wakeEngineStatus.value != WakeEngineStatus.STOPPED) {
                Log.d(TAG, "SiriVoiceService.start() skipped: already running (${SiriServiceBridge.wakeEngineStatus.value})")
                return
            }
            val intent = Intent(appCtx, SiriVoiceService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "Android 12+ prevented starting foreground service from background: ${e.message}")
                    SiriServiceBridge.recordDiagnostic("Foreground service blocked from background start")
                } else {
                    Log.e(TAG, "Failed to start SiriVoiceService: ${e.message}")
                }
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            if (!isServiceRunning) {
                SiriServiceBridge.setServiceRunning(false)
                return
            }
            val intent = Intent(appCtx, SiriVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                appCtx.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping service: ${e.message}")
                isServiceRunning = false
                SiriServiceBridge.setServiceRunning(false)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isForegroundStarted = AtomicBoolean(false)
    private val isBackgroundConversationActive = AtomicBoolean(false)

    private lateinit var repository: SiriRepository
    private lateinit var deviceController: AndroidDeviceController
    private lateinit var aiBrain: AiBrainRepository
    private lateinit var intentParser: SiriIntentParser

    private var wakeWordEngine: OfflineWakeWordEngine? = null
    private var backgroundSpeechRecognizer: SiriSpeechRecognizer? = null
    private var backgroundTts: SiriTextToSpeech? = null

    private var sessionWatchdogJob: Job? = null
    private var commandLaunchJob: Job? = null
    private var resumeWakeJob: Job? = null
    private var aiJob: Job? = null
    private var configObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var hasRetriedBareWakeWord = false

    @Volatile
    private var recognizedCommandInSession = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate()")
        createNotificationChannel()
        initComponents()
        SiriServiceBridge.attachServiceController(this)
    }

    private fun initComponents() {
        val appCtx = applicationContext
        repository = SiriRepository.getInstance(appCtx)
        deviceController = AndroidDeviceController(appCtx)
        aiBrain = AiBrainRepository(appCtx)

        intentParser = SiriIntentParser(
            deviceController = deviceController,
            onCustomRoutineTrigger = { trigger ->
                val routine = repository.routines.value.firstOrNull {
                    it.isEnabled && trigger.contains(it.triggerPhrase.lowercase())
                }
                if (routine != null) {
                    serviceScope.launch {
                        for (step in routine.steps) {
                            when (step.actionType) {
                                "TIMER" -> deviceController.setTimer(step.parameter.toIntOrNull() ?: 300, routine.name)
                                "TORCH" -> deviceController.setFlashlight(step.parameter.toBoolean())
                                "VOLUME" -> deviceController.adjustVolume(step.parameter)
                                "ALARM" -> deviceController.setAlarm(7, 0, routine.name)
                                "INFO_BATTERY" -> deviceController.getBatteryInfo()
                                "INFO_NETWORK" -> deviceController.getNetworkInfo()
                                "APP" -> deviceController.openApp(step.parameter)
                                "SPEAK" -> if (step.parameter.isNotBlank()) backgroundTts?.speak(step.parameter)
                            }
                            delay(350)
                        }
                    }
                    com.example.model.IntentExecutionResult(
                        intentName = "AUTOMATION_ROUTINE",
                        feedbackSpeech = "Running '${routine.name}' routine.",
                        success = true,
                        actionSummary = "Executed ${routine.steps.size} steps"
                    )
                } else null
            },
            onTaskAddTrigger = { taskTitle ->
                repository.addTask(taskTitle)
                com.example.model.IntentExecutionResult(
                    intentName = "ADD_TASK",
                    feedbackSpeech = "Added '$taskTitle' to your tasks.",
                    success = true,
                    actionSummary = "Added Task: $taskTitle"
                )
            },
            onNoteAddTrigger = { noteTitle, noteContent ->
                repository.addNote(noteTitle, noteContent)
                com.example.model.IntentExecutionResult(
                    intentName = "ADD_NOTE",
                    feedbackSpeech = "Note saved: $noteContent",
                    success = true,
                    actionSummary = "Saved Note"
                )
            },
            getVoiceShortcuts = { repository.shortcuts.value },
            onVoiceGenderTrigger = { gender ->
                repository.updateVoiceGender(gender)
                backgroundTts?.setVoiceGender(gender)
                com.example.model.IntentExecutionResult(
                    intentName = "SET_VOICE_GENDER",
                    feedbackSpeech = "Voice updated to ${gender.displayName}.",
                    success = true,
                    actionSummary = "Changed voice to ${gender.displayName}"
                )
            },
            getCurrentLanguage = { repository.selectedLanguage.value },
            getAssistantName = { repository.effectiveAssistantName() },
            getUserAddress = { repository.effectiveUserAddress() }
        )

        backgroundTts = SiriTextToSpeech(appCtx) { isSpeaking ->
            if (!isSpeaking && isBackgroundConversationActive.get()) {
                finishBackgroundConversationAndResumeWakeWord()
            }
        }.apply {
            setPersonality(repository.voicePersonality.value)
            setVoiceGender(repository.voiceGender.value)
            setLanguage(repository.selectedLanguage.value)
            setSpeedMultiplier(repository.customSpeedMultiplier.value)
            setPitchMultiplier(repository.customPitchMultiplier.value)
        }

        backgroundSpeechRecognizer = SiriSpeechRecognizer(
            context = appCtx,
            onResult = { spokenText ->
                handleBackgroundSpokenCommand(spokenText)
            },
            onPartialResult = { partial ->
                if (intentParser.isStopCommand(partial)) {
                    recognizedCommandInSession = true
                    backgroundSpeechRecognizer?.cancel()
                    backgroundTts?.stop()
                    finishBackgroundConversationAndResumeWakeWord()
                }
            },
            onRmsDbChanged = { },
            onErrorOccurred = { errorMsg ->
                if (isBackgroundConversationActive.get()) {
                    Log.w(TAG, "Background SpeechRecognizer error: $errorMsg")
                    SiriServiceBridge.recordDiagnostic(errorMsg)
                    if (!recognizedCommandInSession) {
                        wakeWordEngine?.recordFalseWakeTrigger()
                    }
                    finishBackgroundConversationAndResumeWakeWord()
                }
            },
            onListeningStateChanged = { listening ->
                Log.d(TAG, "Background SpeechRecognizer listening=$listening")
            },
            onSpeechDetected = {
                backgroundTts?.stop()
            },
            onNoMatchOrTimeout = {
                if (isBackgroundConversationActive.get()) {
                    Log.d(TAG, "No speech/timeout in background session; returning to wake-word")
                    if (!recognizedCommandInSession) {
                        wakeWordEngine?.recordFalseWakeTrigger()
                    }
                    finishBackgroundConversationAndResumeWakeWord()
                }
            }
        ).apply {
            setLanguage(repository.selectedLanguage.value)
        }

        wakeWordEngine = OfflineWakeWordEngine(
            context = appCtx,
            onWakeWordDetected = {
                mainHandler.post {
                    onWakeWordDetected()
                }
            },
            onStatusChanged = { status ->
                SiriServiceBridge.setWakeEngineStatus(status)
            }
        ).apply {
            setWakeWordOption(repository.wakeWordOption.value)
            setCustomAssistantName(repository.effectiveAssistantName())
        }

        observeRepositorySettings()
    }

    private fun observeRepositorySettings() {
        configObserverJob?.cancel()
        configObserverJob = serviceScope.launch {
            launch {
                repository.wakeWordOption.drop(1).distinctUntilChanged().collectLatest { option ->
                    wakeWordEngine?.setWakeWordOption(option)
                }
            }
            launch {
                repository.userPreferences.drop(1).distinctUntilChanged().collectLatest { prefs ->
                    val name = prefs.assistantName.trim().ifBlank { "Siri" }
                    wakeWordEngine?.setCustomAssistantName(name)
                    if (isForegroundStarted.get() && !isBackgroundConversationActive.get()) {
                        updateNotificationText("$name AI is running in background")
                    }
                }
            }
            launch {
                repository.wakeWordEnabled.drop(1).distinctUntilChanged().collectLatest { enabled ->
                    if (!isServiceRunning) return@collectLatest
                    if (enabled && !isBackgroundConversationActive.get() && !SiriServiceBridge.isConversationActivelyLocked()) {
                        wakeWordEngine?.start()
                    } else if (!enabled) {
                        wakeWordEngine?.stop()
                    }
                }
            }
            launch {
                repository.selectedLanguage.drop(1).distinctUntilChanged().collectLatest { lang ->
                    backgroundTts?.setLanguage(lang)
                    backgroundSpeechRecognizer?.setLanguage(lang)
                }
            }
            launch {
                repository.voiceGender.drop(1).distinctUntilChanged().collectLatest { gender ->
                    backgroundTts?.setVoiceGender(gender)
                }
            }
            launch {
                repository.voicePersonality.drop(1).distinctUntilChanged().collectLatest { p ->
                    backgroundTts?.setPersonality(p)
                }
            }
            launch {
                repository.customSpeedMultiplier.drop(1).distinctUntilChanged().collectLatest { speed ->
                    backgroundTts?.setSpeedMultiplier(speed)
                }
            }
            launch {
                repository.customPitchMultiplier.drop(1).distinctUntilChanged().collectLatest { pitch ->
                    backgroundTts?.setPitchMultiplier(pitch)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action startId=$startId isForegroundStarted=${isForegroundStarted.get()} isServiceRunning=$isServiceRunning")

        when (action) {
            ACTION_STOP -> {
                repository.setBackgroundServiceEnabled(false)
                shutdownServiceGracefully()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_BACKGROUND_LISTEN -> {
                if (!ensureForegroundStarted("Listening for your command...")) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                isServiceRunning = true
                SiriServiceBridge.setServiceRunning(true)
                mainHandler.post {
                    startBackgroundVoiceCommandSession(playChime = false)
                }
                return START_STICKY
            }
        }

        // If service is recreated by system (intent == null) and user had disabled it, stop cleanly
        if (intent == null && !repository.backgroundServiceEnabled.value) {
            Log.i(TAG, "Service recreated with backgroundServiceEnabled=false -> stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        // Idempotent handling: avoid interrupting an already-running foreground service & AudioRecord loop
        if (isForegroundStarted.get() && isServiceRunning) {
            if (repository.wakeWordEnabled.value &&
                !isBackgroundConversationActive.get() &&
                !SiriServiceBridge.isConversationActivelyLocked()
            ) {
                wakeWordEngine?.start()
            }
            return START_STICKY
        }

        val assistantName = repository.effectiveAssistantName()
        if (!ensureForegroundStarted("$assistantName AI is running in background")) {
            stopSelf()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        SiriServiceBridge.setServiceRunning(true)

        if (repository.wakeWordEnabled.value &&
            !isBackgroundConversationActive.get() &&
            !SiriServiceBridge.isConversationActivelyLocked()
        ) {
            wakeWordEngine?.start()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved() - keeping foreground voice service alive (enabled=${repository.backgroundServiceEnabled.value})")
    }

    private fun ensureForegroundStarted(statusText: String): Boolean {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            Log.e(TAG, "RECORD_AUDIO permission missing; cannot run microphone foreground service.")
            SiriServiceBridge.setWakeEngineStatus(WakeEngineStatus.PERMISSION_MISSING)
            return false
        }

        val notification = buildNotification(statusText)
        return try {
            if (!isForegroundStarted.get()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundStarted.set(true)
            } else {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            SiriServiceBridge.recordDiagnostic("Could not start foreground mic service: ${e.message}")
            false
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val listenIntent = Intent(this, SiriVoiceService::class.java).apply {
            action = ACTION_TRIGGER_BACKGROUND_LISTEN
        }
        val listenPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                2,
                listenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                2,
                listenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val stopIntent = Intent(this, SiriVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val assistantName = repository.effectiveAssistantName()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$assistantName AI")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_btn_speak_now, "Ask $assistantName", listenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotificationText(contentText: String) {
        if (!isForegroundStarted.get()) return
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun onWakeWordDetected() {
        if (!isServiceRunning) return
        if (isBackgroundConversationActive.get() || SiriServiceBridge.isConversationActivelyLocked()) {
            Log.d(TAG, "Ignoring wake trigger while conversation is active")
            return
        }

        // Always yield AudioRecord microphone first before opening SpeechRecognizer
        wakeWordEngine?.pauseForCommand()
        playAssistantChime()

        // If app UI is visible in foreground, trigger foreground ViewModel so orb & chat update live
        if (SiriServiceBridge.dispatchWakeWordToForeground()) {
            val assistantName = repository.effectiveAssistantName()
            updateNotificationText("Listening via $assistantName...")
            return
        }

        // Otherwise execute full hands-free voice conversation directly in background
        startBackgroundVoiceCommandSession(playChime = false)
    }

    private fun startBackgroundVoiceCommandSession(playChime: Boolean) {
        if (!isBackgroundConversationActive.compareAndSet(false, true)) {
            Log.d(TAG, "Background voice session already active; ignoring duplicate start")
            return
        }

        resumeWakeJob?.cancel()
        commandLaunchJob?.cancel()
        hasRetriedBareWakeWord = false
        recognizedCommandInSession = false

        wakeWordEngine?.pauseForCommand()
        if (playChime) {
            playAssistantChime()
        }

        acquireShortWakeLock()
        updateNotificationText("Listening for your command...")

        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            Log.w(TAG, "SpeechRecognizer unavailable on this device for background commands")
            SiriServiceBridge.recordDiagnostic("Speech recognizer unavailable on device")
            finishBackgroundConversationAndResumeWakeWord()
            return
        }

        // Watchdog guarantees we always release wake lock and return to wake-word mode
        sessionWatchdogJob?.cancel()
        sessionWatchdogJob = serviceScope.launch {
            delay(SESSION_WAKE_TIMEOUT_MS)
            if (isBackgroundConversationActive.get()) {
                Log.w(TAG, "Background session watchdog timeout -> returning to wake-word listening")
                backgroundSpeechRecognizer?.cancel()
                backgroundTts?.stop()
                finishBackgroundConversationAndResumeWakeWord()
            }
        }

        backgroundSpeechRecognizer?.setLanguage(repository.selectedLanguage.value)
        commandLaunchJob = serviceScope.launch {
            // Allow AudioRecord hardware release before opening SpeechRecognizer
            delay(180L)
            if (isBackgroundConversationActive.get()) {
                backgroundSpeechRecognizer?.startListening()
            }
        }
    }

    private fun handleBackgroundSpokenCommand(rawText: String) {
        val cleanText = intentParser.cleanWakePhrase(rawText).trim()
        if (cleanText.isEmpty()) {
            // If user said only "Hey Siri" in background, give them one immediate follow-up listen window
            if (!hasRetriedBareWakeWord && isBackgroundConversationActive.get()) {
                hasRetriedBareWakeWord = true
                recognizedCommandInSession = true
                wakeWordEngine?.recordValidCommandRecognized()
                Log.d(TAG, "Bare wake-word detected in background; re-arming recognizer once for command")
                playAssistantChime()
                updateNotificationText("Listening for your command...")
                commandLaunchJob?.cancel()
                commandLaunchJob = serviceScope.launch {
                    delay(200L)
                    if (isBackgroundConversationActive.get()) {
                        backgroundSpeechRecognizer?.startListening()
                    }
                }
                return
            }
            if (!recognizedCommandInSession) {
                wakeWordEngine?.recordFalseWakeTrigger()
            }
            finishBackgroundConversationAndResumeWakeWord()
            return
        }

        recognizedCommandInSession = true
        wakeWordEngine?.recordValidCommandRecognized()

        if (intentParser.isStopCommand(cleanText)) {
            backgroundTts?.stop()
            finishBackgroundConversationAndResumeWakeWord()
            return
        }

        val requestSessionId = repository.currentSessionId.value
        repository.addMessage(
            ChatMessage(
                sessionId = requestSessionId,
                text = rawText,
                isFromUser = true
            )
        )

        val localResult = intentParser.parseAndExecute(cleanText)
        if (localResult != null) {
            repository.addMessage(
                ChatMessage(
                    sessionId = requestSessionId,
                    text = localResult.feedbackSpeech,
                    isFromUser = false,
                    actionType = localResult.intentName,
                    actionSummary = localResult.actionSummary,
                    freshness = InformationFreshness.VERIFIED_SYSTEM
                )
            )
            updateNotificationText("${repository.effectiveAssistantName()} is speaking...")
            backgroundTts?.speak(localResult.feedbackSpeech)
            return
        }

        aiJob?.cancel()
        val activeAssistantName = repository.effectiveAssistantName()
        updateNotificationText("$activeAssistantName is thinking...")
        aiJob = serviceScope.launch {
            try {
                val currentSessionMsgs = repository.messages.value.filter { it.sessionId == requestSessionId }
                val recentPairs = currentSessionMsgs.dropLast(1).takeLast(12).map { it.text to it.isFromUser }
                val aiResult = aiBrain.generateResponse(
                    prompt = cleanText,
                    config = repository.aiConfig.value,
                    language = repository.selectedLanguage.value,
                    recentHistory = recentPairs,
                    userPrefs = repository.userPreferences.value
                )
                repository.addMessage(
                    ChatMessage(
                        sessionId = requestSessionId,
                        text = aiResult.text,
                        isFromUser = false,
                        freshness = InformationFreshness.AI_INTERPRETATION,
                        latencyMs = aiResult.latencyMs
                    )
                )
                updateNotificationText("$activeAssistantName is speaking...")
                backgroundTts?.speak(aiResult.text)
            } catch (e: Exception) {
                Log.e(TAG, "Background AI generation error", e)
                finishBackgroundConversationAndResumeWakeWord()
            }
        }
    }

    private fun finishBackgroundConversationAndResumeWakeWord() {
        sessionWatchdogJob?.cancel()
        commandLaunchJob?.cancel()
        isBackgroundConversationActive.set(false)
        releaseWakeLockSafely()

        if (isServiceRunning) {
            val assistantName = repository.effectiveAssistantName()
            updateNotificationText("$assistantName AI is running in background")
            if (repository.wakeWordEnabled.value) {
                scheduleWakeWordResume()
            }
        }
    }

    private fun scheduleWakeWordResume() {
        resumeWakeJob?.cancel()
        resumeWakeJob = serviceScope.launch {
            // Cooldown after SpeechRecognizer/TTS audio focus release before reopening AudioRecord
            delay(650L)
            if (isServiceRunning &&
                !isBackgroundConversationActive.get() &&
                !SiriServiceBridge.isConversationActivelyLocked() &&
                repository.wakeWordEnabled.value
            ) {
                Log.d(TAG, "Resuming offline wake-word listening after conversation")
                wakeWordEngine?.resume()
            }
        }
    }

    // BackgroundServiceController callbacks from foreground SiriViewModel
    override fun pauseWakeWordForForegroundCommand() {
        resumeWakeJob?.cancel()
        wakeWordEngine?.pauseForCommand()
        updateNotificationText("${repository.effectiveAssistantName()} active...")
    }

    override fun resumeWakeWordAfterConversation() {
        if (!isServiceRunning) return
        updateNotificationText("${repository.effectiveAssistantName()} AI is running in background")
        if (repository.wakeWordEnabled.value) {
            scheduleWakeWordResume()
        }
    }

    private fun playAssistantChime() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 110)
                mainHandler.postDelayed({
                    try {
                        tone.release()
                    } catch (_: Exception) {
                    }
                }, 200L)
            }
        } catch (_: Exception) {
        }
    }

    private fun acquireShortWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(SESSION_WAKE_TIMEOUT_MS + 2000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire skipped: ${e.message}")
        }
    }

    private fun releaseWakeLockSafely() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun shutdownServiceGracefully() {
        Log.i(TAG, "shutdownServiceGracefully()")
        isServiceRunning = false
        isForegroundStarted.set(false)
        isBackgroundConversationActive.set(false)
        SiriServiceBridge.setServiceRunning(false)
        sessionWatchdogJob?.cancel()
        commandLaunchJob?.cancel()
        resumeWakeJob?.cancel()
        aiJob?.cancel()
        wakeWordEngine?.stop()
        backgroundSpeechRecognizer?.cancel()
        backgroundTts?.stop()
        releaseWakeLockSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
        SiriServiceBridge.detachServiceController(this)
        isServiceRunning = false
        isForegroundStarted.set(false)
        isBackgroundConversationActive.set(false)
        SiriServiceBridge.setServiceRunning(false)
        wakeWordEngine?.destroy()
        backgroundSpeechRecognizer?.destroy()
        backgroundTts?.shutdown()
        releaseWakeLockSafely()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Siri AI Background Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Siri AI running in background for offline 'Hey Siri' wake-word detection"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
