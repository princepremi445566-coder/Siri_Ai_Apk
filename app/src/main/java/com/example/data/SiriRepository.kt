package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ActionExecutionLog
import com.example.model.AiConfig
import com.example.model.AutomationRoutine
import com.example.model.AutomationRule
import com.example.model.AutomationStep
import com.example.model.ChatMessage
import com.example.model.ConversationSession
import com.example.model.FlashcardItem
import com.example.model.InformationFreshness
import com.example.model.LanguageOption
import com.example.model.NoteItem
import com.example.model.ResponseLengthPreference
import com.example.model.SmartDevice
import com.example.model.TaskItem
import com.example.model.UpdateInfo
import com.example.model.UserPreferences
import com.example.model.VoiceGender
import com.example.model.VoicePersonality
import com.example.model.VoiceShortcut
import com.example.model.WakeWordOption
import com.example.model.WebSourceLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SiriRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("siri_ai_prefs", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    val sessions: StateFlow<List<ConversationSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow("default")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    private val _flashcards = MutableStateFlow<List<FlashcardItem>>(emptyList())
    val flashcards: StateFlow<List<FlashcardItem>> = _flashcards.asStateFlow()

    private val _routines = MutableStateFlow<List<AutomationRoutine>>(emptyList())
    val routines: StateFlow<List<AutomationRoutine>> = _routines.asStateFlow()

    private val _shortcuts = MutableStateFlow<List<VoiceShortcut>>(emptyList())
    val shortcuts: StateFlow<List<VoiceShortcut>> = _shortcuts.asStateFlow()

    private val _automationRules = MutableStateFlow<List<AutomationRule>>(emptyList())
    val automationRules: StateFlow<List<AutomationRule>> = _automationRules.asStateFlow()

    private val _actionLogs = MutableStateFlow<List<ActionExecutionLog>>(emptyList())
    val actionLogs: StateFlow<List<ActionExecutionLog>> = _actionLogs.asStateFlow()

    private val _userPreferences = MutableStateFlow(loadUserPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    private val _smartDevices = MutableStateFlow<List<SmartDevice>>(emptyList())
    val smartDevices: StateFlow<List<SmartDevice>> = _smartDevices.asStateFlow()

    private val _aiConfig = MutableStateFlow(loadAiConfig())
    val aiConfig: StateFlow<AiConfig> = _aiConfig.asStateFlow()

    private val _voicePersonality = MutableStateFlow(loadVoicePersonality())
    val voicePersonality: StateFlow<VoicePersonality> = _voicePersonality.asStateFlow()

    private val _voiceGender = MutableStateFlow(loadVoiceGender())
    val voiceGender: StateFlow<VoiceGender> = _voiceGender.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(loadLanguage())
    val selectedLanguage: StateFlow<LanguageOption> = _selectedLanguage.asStateFlow()

    private val _wakeWordOption = MutableStateFlow(loadWakeWord())
    val wakeWordOption: StateFlow<WakeWordOption> = _wakeWordOption.asStateFlow()

    private val _continuousListening = MutableStateFlow(prefs.getBoolean("continuous_listening", false))
    val continuousListening: StateFlow<Boolean> = _continuousListening.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(prefs.getBoolean("wake_word_enabled", true))
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _customSpeedMultiplier = MutableStateFlow(prefs.getFloat("speed_multiplier", 1.0f))
    val customSpeedMultiplier: StateFlow<Float> = _customSpeedMultiplier.asStateFlow()

    private val _customPitchMultiplier = MutableStateFlow(prefs.getFloat("pitch_multiplier", 1.0f))
    val customPitchMultiplier: StateFlow<Float> = _customPitchMultiplier.asStateFlow()

    private val _updateInfo = MutableStateFlow(UpdateInfo())
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo.asStateFlow()

    init {
        loadSessions()
        loadTasks()
        loadNotes()
        loadFlashcards()
        loadRoutines()
        loadShortcuts()
        loadAutomationRules()
        loadSmartDevices()
        loadHistory()
    }

    // --- AI Configuration ---
    fun updateAiConfig(newConfig: AiConfig) {
        _aiConfig.value = newConfig
        prefs.edit()
            .putString("ai_provider", newConfig.provider)
            .putString("ai_model", newConfig.model)
            .putString("custom_api_key", newConfig.customApiKey)
            .putString("custom_endpoint", newConfig.customEndpoint)
            .putFloat("ai_temp", newConfig.temperature)
            .putInt("ai_max_tokens", newConfig.maxOutputTokens)
            .putString("response_length", newConfig.responseLength.name)
            .putString("custom_greeting", newConfig.customGreeting)
            .apply()
    }

    private fun loadAiConfig(): AiConfig {
        val lenName = prefs.getString("response_length", ResponseLengthPreference.BALANCED.name)
        val responseLen = try {
            ResponseLengthPreference.valueOf(lenName ?: ResponseLengthPreference.BALANCED.name)
        } catch (e: Exception) {
            ResponseLengthPreference.BALANCED
        }

        return AiConfig(
            provider = prefs.getString("ai_provider", "Google Gemini") ?: "Google Gemini",
            model = prefs.getString("ai_model", "gemini-2.5-flash") ?: "gemini-2.5-flash",
            customApiKey = prefs.getString("custom_api_key", "") ?: "",
            customEndpoint = prefs.getString("custom_endpoint", "") ?: "",
            temperature = prefs.getFloat("ai_temp", 0.7f),
            maxOutputTokens = prefs.getInt("ai_max_tokens", 512),
            responseLength = responseLen,
            customGreeting = prefs.getString("custom_greeting", "Hello! I am Siri, your personal voice assistant.") ?: "Hello! I am Siri, your personal voice assistant."
        )
    }

    // --- Voice Personality & Speech Controls ---
    fun updatePersonality(personality: VoicePersonality) {
        _voicePersonality.value = personality
        prefs.edit().putString("personality", personality.name).apply()
    }

    private fun loadVoicePersonality(): VoicePersonality {
        val name = prefs.getString("personality", VoicePersonality.FRIENDLY.name)
        return try {
            VoicePersonality.valueOf(name ?: VoicePersonality.FRIENDLY.name)
        } catch (e: Exception) {
            VoicePersonality.FRIENDLY
        }
    }

    fun updateVoiceGender(gender: VoiceGender) {
        _voiceGender.value = gender
        prefs.edit().putString("voice_gender", gender.name).apply()
    }

    private fun loadVoiceGender(): VoiceGender {
        val name = prefs.getString("voice_gender", VoiceGender.FEMALE.name)
        return try {
            VoiceGender.valueOf(name ?: VoiceGender.FEMALE.name)
        } catch (e: Exception) {
            VoiceGender.FEMALE
        }
    }

    fun setSpeedMultiplier(multiplier: Float) {
        _customSpeedMultiplier.value = multiplier
        prefs.edit().putFloat("speed_multiplier", multiplier).apply()
    }

    fun setPitchMultiplier(multiplier: Float) {
        _customPitchMultiplier.value = multiplier
        prefs.edit().putFloat("pitch_multiplier", multiplier).apply()
    }

    fun updateLanguage(lang: LanguageOption) {
        _selectedLanguage.value = lang
        prefs.edit().putString("language", lang.name).apply()
    }

    private fun loadLanguage(): LanguageOption {
        val name = prefs.getString("language", LanguageOption.AUTO.name)
        return try {
            LanguageOption.valueOf(name ?: LanguageOption.AUTO.name)
        } catch (e: Exception) {
            LanguageOption.AUTO
        }
    }

    fun updateWakeWord(wake: WakeWordOption) {
        _wakeWordOption.value = wake
        prefs.edit().putString("wake_word_phrase", wake.name).apply()
    }

    private fun loadWakeWord(): WakeWordOption {
        val name = prefs.getString("wake_word_phrase", WakeWordOption.HEY_SIRI.name)
        return try {
            WakeWordOption.valueOf(name ?: WakeWordOption.HEY_SIRI.name)
        } catch (e: Exception) {
            WakeWordOption.HEY_SIRI
        }
    }

    fun setContinuousListening(enabled: Boolean) {
        _continuousListening.value = enabled
        prefs.edit().putBoolean("continuous_listening", enabled).apply()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _wakeWordEnabled.value = enabled
        prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
    }

    // --- User Preferences Memory ---
    fun updateUserPreferences(newPrefs: UserPreferences) {
        _userPreferences.value = newPrefs
        prefs.edit()
            .putString("pref_user_name", newPrefs.userName)
            .putString("pref_home_loc", newPrefs.homeLocation)
            .putString("pref_work_loc", newPrefs.workLocation)
            .putString("pref_temp_unit", newPrefs.preferredTempUnit)
            .putString("pref_briefing_time", newPrefs.morningBriefingTime)
            .putBoolean("pref_quiet_hours", newPrefs.quietHoursEnabled)
            .putString("pref_quiet_start", newPrefs.quietHoursStart)
            .putString("pref_quiet_end", newPrefs.quietHoursEnd)
            .putBoolean("pref_filter_sensitive", newPrefs.filterSensitiveNotifications)
            .putBoolean("pref_reduced_motion", newPrefs.reducedMotion)
            .putInt("pref_session_timeout", newPrefs.voiceSessionTimeoutSeconds)
            .apply()
    }

    private fun loadUserPreferences(): UserPreferences {
        return UserPreferences(
            userName = prefs.getString("pref_user_name", "Friend") ?: "Friend",
            homeLocation = prefs.getString("pref_home_loc", "Home") ?: "Home",
            workLocation = prefs.getString("pref_work_loc", "Office") ?: "Office",
            preferredTempUnit = prefs.getString("pref_temp_unit", "Celsius (°C)") ?: "Celsius (°C)",
            morningBriefingTime = prefs.getString("pref_briefing_time", "08:00 AM") ?: "08:00 AM",
            quietHoursEnabled = prefs.getBoolean("pref_quiet_hours", false),
            quietHoursStart = prefs.getString("pref_quiet_start", "22:00") ?: "22:00",
            quietHoursEnd = prefs.getString("pref_quiet_end", "07:00") ?: "07:00",
            filterSensitiveNotifications = prefs.getBoolean("pref_filter_sensitive", true),
            reducedMotion = prefs.getBoolean("pref_reduced_motion", false),
            voiceSessionTimeoutSeconds = prefs.getInt("pref_session_timeout", 30)
        )
    }

    // --- Action Execution History & Undo ---
    fun logActionExecution(actionName: String, summary: String, isSuccess: Boolean, isReversible: Boolean = false, undoPayload: String? = null) {
        val log = ActionExecutionLog(
            actionName = actionName,
            summary = summary,
            isSuccess = isSuccess,
            isReversible = isReversible,
            undoPayload = undoPayload
        )
        val updated = (listOf(log) + _actionLogs.value).take(40)
        _actionLogs.value = updated
    }

    // --- Voice Shortcuts ---
    fun addVoiceShortcut(triggerPhrase: String, commandText: String, spokenReply: String? = null) {
        val shortcut = VoiceShortcut(
            triggerPhrase = triggerPhrase.lowercase().trim(),
            commandText = commandText.trim(),
            customSpokenReply = spokenReply?.trim()
        )
        val updated = _shortcuts.value + shortcut
        _shortcuts.value = updated
        saveShortcuts(updated)
    }

    fun toggleVoiceShortcut(id: String) {
        val updated = _shortcuts.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        _shortcuts.value = updated
        saveShortcuts(updated)
    }

    fun deleteVoiceShortcut(id: String) {
        val updated = _shortcuts.value.filterNot { it.id == id }
        _shortcuts.value = updated
        saveShortcuts(updated)
    }

    private fun saveShortcuts(list: List<VoiceShortcut>) {
        val arr = JSONArray()
        for (s in list) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("triggerPhrase", s.triggerPhrase)
                put("commandText", s.commandText)
                put("customSpokenReply", s.customSpokenReply ?: "")
                put("isEnabled", s.isEnabled)
            }
            arr.put(obj)
        }
        prefs.edit().putString("voice_shortcuts", arr.toString()).apply()
    }

    private fun loadShortcuts() {
        val json = prefs.getString("voice_shortcuts", null)
        if (json.isNullOrBlank()) {
            _shortcuts.value = listOf(
                VoiceShortcut(triggerPhrase = "heading out", commandText = "battery status", customSpokenReply = "Have a safe trip! Checking battery."),
                VoiceShortcut(triggerPhrase = "coffee break", commandText = "timer for 5 minutes", customSpokenReply = "Enjoy your coffee break. 5-minute timer started."),
                VoiceShortcut(triggerPhrase = "relax time", commandText = "volume down", customSpokenReply = "Relaxing mode enabled. Volume lowered.")
            )
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<VoiceShortcut>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VoiceShortcut(
                        id = obj.getString("id"),
                        triggerPhrase = obj.getString("triggerPhrase"),
                        commandText = obj.getString("commandText"),
                        customSpokenReply = obj.optString("customSpokenReply").takeIf { it.isNotBlank() },
                        isEnabled = obj.getBoolean("isEnabled")
                    )
                )
            }
            _shortcuts.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Automation Rules ---
    fun addAutomationRule(name: String, conditionType: String, thresholdValue: String, actionType: String) {
        val rule = AutomationRule(
            name = name,
            conditionType = conditionType,
            thresholdValue = thresholdValue,
            actionType = actionType,
            isEnabled = true
        )
        val updated = _automationRules.value + rule
        _automationRules.value = updated
        saveAutomationRules(updated)
    }

    fun toggleAutomationRule(id: String) {
        val updated = _automationRules.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        _automationRules.value = updated
        saveAutomationRules(updated)
    }

    fun deleteAutomationRule(id: String) {
        val updated = _automationRules.value.filterNot { it.id == id }
        _automationRules.value = updated
        saveAutomationRules(updated)
    }

    private fun saveAutomationRules(list: List<AutomationRule>) {
        val arr = JSONArray()
        for (r in list) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("conditionType", r.conditionType)
                put("thresholdValue", r.thresholdValue)
                put("actionType", r.actionType)
                put("isEnabled", r.isEnabled)
            }
            arr.put(obj)
        }
        prefs.edit().putString("automation_rules", arr.toString()).apply()
    }

    private fun loadAutomationRules() {
        val json = prefs.getString("automation_rules", null)
        if (json.isNullOrBlank()) {
            _automationRules.value = listOf(
                AutomationRule(
                    name = "Low Battery Audio Saver",
                    conditionType = "BATTERY_LOW",
                    thresholdValue = "20",
                    actionType = "MUTE_AUDIO",
                    isEnabled = true
                ),
                AutomationRule(
                    name = "Night Distraction Blocker",
                    conditionType = "TIME_EVENING",
                    thresholdValue = "22:00",
                    actionType = "BEDTIME_ROUTINE",
                    isEnabled = true
                )
            )
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<AutomationRule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AutomationRule(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        conditionType = obj.getString("conditionType"),
                        thresholdValue = obj.getString("thresholdValue"),
                        actionType = obj.getString("actionType"),
                        isEnabled = obj.getBoolean("isEnabled")
                    )
                )
            }
            _automationRules.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Conversation Sessions ---
    fun createNewSession(title: String = "Conversation ${sessions.value.size + 1}", category: String = "General"): String {
        val newSession = ConversationSession(title = title, category = category)
        val updated = listOf(newSession) + _sessions.value
        _sessions.value = updated
        _currentSessionId.value = newSession.id
        saveSessions(updated)
        addMessage(
            ChatMessage(
                sessionId = newSession.id,
                text = "${_aiConfig.value.customGreeting} What can I assist you with?",
                isFromUser = false
            )
        )
        return newSession.id
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun deleteSession(sessionId: String) {
        val updatedSessions = _sessions.value.filterNot { it.id == sessionId }
        _sessions.value = updatedSessions
        saveSessions(updatedSessions)

        val updatedMessages = _messages.value.filterNot { it.sessionId == sessionId }
        _messages.value = updatedMessages
        saveHistory(updatedMessages)

        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = updatedSessions.firstOrNull()?.id ?: "default"
        }
    }

    private fun saveSessions(list: List<ConversationSession>) {
        val arr = JSONArray()
        for (s in list) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("timestamp", s.timestamp)
                put("category", s.category)
            }
            arr.put(obj)
        }
        prefs.edit().putString("chat_sessions", arr.toString()).apply()
    }

    private fun loadSessions() {
        val json = prefs.getString("chat_sessions", null)
        if (json.isNullOrBlank()) {
            val defaultSession = ConversationSession(id = "default", title = "General Assistant", category = "General")
            _sessions.value = listOf(defaultSession)
            _currentSessionId.value = "default"
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<ConversationSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ConversationSession(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        timestamp = obj.getLong("timestamp"),
                        category = obj.optString("category", "General")
                    )
                )
            }
            _sessions.value = list
            _currentSessionId.value = list.firstOrNull()?.id ?: "default"
        } catch (e: Exception) {
            _sessions.value = listOf(ConversationSession(id = "default", title = "General Assistant", category = "General"))
        }
    }

    fun exportCurrentSession(): String {
        val currentMessages = _messages.value.filter { it.sessionId == _currentSessionId.value }
        val sb = StringBuilder("=== Siri AI Conversation Export ===\n\n")
        for (msg in currentMessages) {
            val sender = if (msg.isFromUser) "User" else "Siri"
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
            sb.append("[$date] $sender:\n${msg.text}\n")
            if (msg.actionSummary != null) {
                sb.append("Action: ${msg.actionSummary}\n")
            }
            if (msg.sources.isNotEmpty()) {
                sb.append("Sources:\n")
                for (src in msg.sources) {
                    sb.append(" - ${src.title} (${src.url})\n")
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    // --- Messages History ---
    fun addMessage(message: ChatMessage) {
        val updated = _messages.value + message.copy(sessionId = _currentSessionId.value)
        _messages.value = updated
        saveHistory(updated)
    }

    fun clearHistory() {
        _messages.value = emptyList()
        prefs.edit().remove("chat_history").apply()
    }

    fun clearAllPersonalData() {
        _messages.value = emptyList()
        _tasks.value = emptyList()
        _notes.value = emptyList()
        _flashcards.value = emptyList()
        _actionLogs.value = emptyList()
        prefs.edit().clear().apply()
        // Re-initialize baseline defaults
        _sessions.value = listOf(ConversationSession(id = "default", title = "General Assistant", category = "General"))
        _currentSessionId.value = "default"
    }

    private fun saveHistory(list: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            for (msg in list.takeLast(60)) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("sessionId", msg.sessionId)
                    put("text", msg.text)
                    put("isFromUser", msg.isFromUser)
                    put("timestamp", msg.timestamp)
                    put("actionType", msg.actionType ?: "")
                    put("actionSummary", msg.actionSummary ?: "")
                    put("imageUri", msg.imageUri ?: "")
                    put("isError", msg.isError)
                    put("freshness", msg.freshness.name)
                    if (msg.latencyMs != null) put("latencyMs", msg.latencyMs)

                    val sourcesArr = JSONArray()
                    for (src in msg.sources) {
                        val srcObj = JSONObject().apply {
                            put("title", src.title)
                            put("url", src.url)
                            put("snippet", src.snippet)
                        }
                        sourcesArr.put(srcObj)
                    }
                    put("sources", sourcesArr)
                }
                arr.put(obj)
            }
            prefs.edit().putString("chat_history", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadHistory() {
        val json = prefs.getString("chat_history", null)
        if (json.isNullOrBlank()) {
            _messages.value = listOf(
                ChatMessage(
                    sessionId = "default",
                    text = "Hello! I am Siri, your personal AI voice assistant on Android. You can speak to me in English, Hindi, or Hinglish. Ask me questions, tell me to open apps, turn on the flashlight, take notes, manage study sessions, analyze photos, or automate your day.",
                    isFromUser = false,
                    freshness = InformationFreshness.VERIFIED_SYSTEM
                )
            )
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sourcesList = mutableListOf<WebSourceLink>()
                val sourcesArr = obj.optJSONArray("sources")
                if (sourcesArr != null) {
                    for (j in 0 until sourcesArr.length()) {
                        val sObj = sourcesArr.getJSONObject(j)
                        sourcesList.add(
                            WebSourceLink(
                                title = sObj.getString("title"),
                                url = sObj.getString("url"),
                                snippet = sObj.optString("snippet")
                            )
                        )
                    }
                }

                val freshName = obj.optString("freshness", InformationFreshness.AI_INTERPRETATION.name)
                val freshness = try {
                    InformationFreshness.valueOf(freshName)
                } catch (e: Exception) {
                    InformationFreshness.AI_INTERPRETATION
                }

                list.add(
                    ChatMessage(
                        id = obj.optString("id"),
                        sessionId = obj.optString("sessionId", "default"),
                        text = obj.getString("text"),
                        isFromUser = obj.getBoolean("isFromUser"),
                        timestamp = obj.getLong("timestamp"),
                        actionType = obj.optString("actionType").takeIf { it.isNotBlank() },
                        actionSummary = obj.optString("actionSummary").takeIf { it.isNotBlank() },
                        imageUri = obj.optString("imageUri").takeIf { it.isNotBlank() },
                        isError = obj.optBoolean("isError", false),
                        sources = sourcesList,
                        freshness = freshness,
                        latencyMs = if (obj.has("latencyMs")) obj.getLong("latencyMs") else null
                    )
                )
            }
            _messages.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Notes System ---
    fun addNote(title: String, content: String, category: String = "General") {
        val note = NoteItem(title = title, content = content, category = category)
        val updated = listOf(note) + _notes.value
        _notes.value = updated
        saveNotes(updated)
    }

    fun editNote(id: String, title: String, content: String, category: String) {
        val updated = _notes.value.map {
            if (it.id == id) it.copy(title = title, content = content, category = category) else it
        }
        _notes.value = updated
        saveNotes(updated)
    }

    fun deleteNote(id: String) {
        val updated = _notes.value.filterNot { it.id == id }
        _notes.value = updated
        saveNotes(updated)
    }

    fun exportNotes(): String {
        val sb = StringBuilder("=== Siri AI Notes Export ===\n\n")
        for (n in _notes.value) {
            sb.append("• [${n.category}] ${n.title}\n${n.content}\n\n")
        }
        return sb.toString()
    }

    private fun saveNotes(list: List<NoteItem>) {
        val arr = JSONArray()
        for (n in list) {
            val obj = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("category", n.category)
                put("timestamp", n.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString("notes_data", arr.toString()).apply()
    }

    private fun loadNotes() {
        val json = prefs.getString("notes_data", null)
        if (json.isNullOrBlank()) {
            _notes.value = listOf(
                NoteItem(title = "Study Session Plan", content = "Review Chapter 4 calculus and practice 5 problems.", category = "Study"),
                NoteItem(title = "Shopping Checklist", content = "Almonds, green tea, notebook, stylus pen.", category = "Personal"),
                NoteItem(title = "Project Milestones", content = "Deliver voice assistant prototype with offline intent routing.", category = "Work")
            )
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<NoteItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    NoteItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        category = obj.optString("category", "General"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _notes.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Flashcards System ---
    fun addFlashcard(question: String, answer: String, topic: String = "General") {
        val card = FlashcardItem(question = question, answer = answer, topic = topic)
        val updated = _flashcards.value + card
        _flashcards.value = updated
        saveFlashcards(updated)
    }

    fun deleteFlashcard(id: String) {
        val updated = _flashcards.value.filterNot { it.id == id }
        _flashcards.value = updated
        saveFlashcards(updated)
    }

    private fun saveFlashcards(list: List<FlashcardItem>) {
        val arr = JSONArray()
        for (c in list) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("question", c.question)
                put("answer", c.answer)
                put("topic", c.topic)
            }
            arr.put(obj)
        }
        prefs.edit().putString("flashcards_data", arr.toString()).apply()
    }

    private fun loadFlashcards() {
        val json = prefs.getString("flashcards_data", null)
        if (json.isNullOrBlank()) {
            _flashcards.value = listOf(
                FlashcardItem(question = "What is the speed of light in vacuum?", answer = "Approximately 3 x 10^8 m/s", topic = "Physics"),
                FlashcardItem(question = "What is the capital of France?", answer = "Paris", topic = "Geography"),
                FlashcardItem(question = "What does AI stand for?", answer = "Artificial Intelligence", topic = "Computer Science")
            )
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<FlashcardItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FlashcardItem(
                        id = obj.getString("id"),
                        question = obj.getString("question"),
                        answer = obj.getString("answer"),
                        topic = obj.optString("topic", "General")
                    )
                )
            }
            _flashcards.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Tasks & Reminders ---
    fun addTask(title: String, timeLabel: String = "Today", category: String = "General", priority: String = "Normal") {
        val task = TaskItem(title = title, timeLabel = timeLabel, category = category, priority = priority)
        val updated = _tasks.value + task
        _tasks.value = updated
        saveTasks(updated)
    }

    fun toggleTask(taskId: String) {
        val updated = _tasks.value.map {
            if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
        }
        _tasks.value = updated
        saveTasks(updated)
    }

    fun removeTask(taskId: String) {
        val updated = _tasks.value.filterNot { it.id == taskId }
        _tasks.value = updated
        saveTasks(updated)
    }

    private fun saveTasks(list: List<TaskItem>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("timeLabel", item.timeLabel)
                put("category", item.category)
                put("priority", item.priority)
                put("isCompleted", item.isCompleted)
            }
            arr.put(obj)
        }
        prefs.edit().putString("tasks_data", arr.toString()).apply()
    }

    private fun loadTasks() {
        val json = prefs.getString("tasks_data", null)
        if (json.isNullOrBlank()) {
            _tasks.value = listOf(
                TaskItem(title = "Review project deliverables", timeLabel = "10:00 AM", category = "Work", priority = "High", isCompleted = false),
                TaskItem(title = "Complete 25-minute study focus", timeLabel = "02:30 PM", category = "Study", priority = "Normal", isCompleted = false),
                TaskItem(title = "Evening walk & health check", timeLabel = "06:00 PM", category = "Personal", priority = "Low", isCompleted = true)
            )
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<TaskItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TaskItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        timeLabel = obj.optString("timeLabel", "Today"),
                        category = obj.optString("category", "General"),
                        priority = obj.optString("priority", "Normal"),
                        isCompleted = obj.getBoolean("isCompleted")
                    )
                )
            }
            _tasks.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Smart Home Devices ---
    fun toggleSmartDevice(deviceId: String) {
        val updated = _smartDevices.value.map {
            if (it.id == deviceId) {
                val next = !it.isOn
                it.copy(isOn = next, stateValue = if (next) "On (100%)" else "Off")
            } else it
        }
        _smartDevices.value = updated
    }

    fun addSmartDevice(name: String, type: String) {
        val dev = SmartDevice("dev_${System.currentTimeMillis()}", name, type, false, "Off")
        _smartDevices.value = _smartDevices.value + dev
    }

    fun removeSmartDevice(deviceId: String) {
        _smartDevices.value = _smartDevices.value.filterNot { it.id == deviceId }
    }

    private fun loadSmartDevices() {
        _smartDevices.value = listOf(
            SmartDevice("dev_1", "Living Room Lights", "LIGHT", true, "On (80%)"),
            SmartDevice("dev_2", "Bedroom Lamp", "LIGHT", false, "Off"),
            SmartDevice("dev_3", "Desk Smart Plug", "PLUG", true, "On"),
            SmartDevice("dev_4", "Climate Thermostat", "THERMOSTAT", true, "22°C")
        )
    }

    // --- Custom Automations ---
    fun toggleRoutine(routineId: String) {
        val updated = _routines.value.map {
            if (it.id == routineId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _routines.value = updated
        saveRoutines(updated)
    }

    fun addRoutine(routine: AutomationRoutine) {
        val updated = _routines.value + routine
        _routines.value = updated
        saveRoutines(updated)
    }

    private fun saveRoutines(list: List<AutomationRoutine>) {
        val arr = JSONArray()
        for (r in list) {
            val rObj = JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("triggerPhrase", r.triggerPhrase)
                put("isEnabled", r.isEnabled)
                put("category", r.category)
                if (r.scheduledTime != null) put("scheduledTime", r.scheduledTime)
                val stepsArr = JSONArray()
                for (s in r.steps) {
                    val sObj = JSONObject().apply {
                        put("id", s.id)
                        put("actionType", s.actionType)
                        put("parameter", s.parameter)
                        put("description", s.description)
                    }
                    stepsArr.put(sObj)
                }
                put("steps", stepsArr)
            }
            arr.put(rObj)
        }
        prefs.edit().putString("routines_data", arr.toString()).apply()
    }

    private fun loadRoutines() {
        val json = prefs.getString("routines_data", null)
        if (json.isNullOrBlank()) {
            _routines.value = listOf(
                AutomationRoutine(
                    id = "routine_study",
                    name = "Study Mode",
                    triggerPhrase = "start study mode",
                    steps = listOf(
                        AutomationStep(actionType = "TIMER", parameter = "1500", description = "Set 25-minute focus timer"),
                        AutomationStep(actionType = "TORCH", parameter = "false", description = "Turn off flashlight"),
                        AutomationStep(actionType = "SPEAK", parameter = "Study mode activated. Focus for 25 minutes.", description = "Announce session start")
                    ),
                    isEnabled = true,
                    category = "Productivity"
                ),
                AutomationRoutine(
                    id = "routine_work",
                    name = "Work Mode",
                    triggerPhrase = "start work mode",
                    steps = listOf(
                        AutomationStep(actionType = "VOLUME", parameter = "mute", description = "Silence media notifications"),
                        AutomationStep(actionType = "SPEAK", parameter = "Work mode enabled. Distractions minimized.", description = "Spoken confirmation")
                    ),
                    isEnabled = true,
                    category = "Productivity"
                ),
                AutomationRoutine(
                    id = "routine_sleep",
                    name = "Sleep Mode",
                    triggerPhrase = "bedtime mode",
                    steps = listOf(
                        AutomationStep(actionType = "VOLUME", parameter = "mute", description = "Mute media volume"),
                        AutomationStep(actionType = "TORCH", parameter = "false", description = "Turn off flashlight"),
                        AutomationStep(actionType = "ALARM", parameter = "7:00 AM", description = "Set 7:00 AM wake up alarm"),
                        AutomationStep(actionType = "SPEAK", parameter = "Goodnight. Audio muted and alarm set.", description = "Say goodnight")
                    ),
                    isEnabled = true,
                    category = "Lifestyle"
                ),
                AutomationRoutine(
                    id = "routine_driving",
                    name = "Driving Mode",
                    triggerPhrase = "start driving mode",
                    steps = listOf(
                        AutomationStep(actionType = "VOLUME", parameter = "up", description = "Set volume high for navigation"),
                        AutomationStep(actionType = "SPEAK", parameter = "Driving mode activated. Hands-free listening enabled.", description = "Voice safety prompt")
                    ),
                    isEnabled = true,
                    category = "Travel"
                ),
                AutomationRoutine(
                    id = "routine_morning",
                    name = "Morning Routine",
                    triggerPhrase = "good morning",
                    steps = listOf(
                        AutomationStep(actionType = "INFO_BATTERY", parameter = "", description = "Check battery level"),
                        AutomationStep(actionType = "INFO_NETWORK", parameter = "", description = "Check internet status"),
                        AutomationStep(actionType = "SPEAK", parameter = "Good morning! Ready for a successful day.", description = "Morning briefing")
                    ),
                    isEnabled = true,
                    category = "Lifestyle"
                )
            )
            return
        }
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<AutomationRoutine>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val stepsArr = obj.getJSONArray("steps")
                val stepsList = mutableListOf<AutomationStep>()
                for (j in 0 until stepsArr.length()) {
                    val sObj = stepsArr.getJSONObject(j)
                    stepsList.add(
                        AutomationStep(
                            id = sObj.optString("id"),
                            actionType = sObj.getString("actionType"),
                            parameter = sObj.getString("parameter"),
                            description = sObj.getString("description")
                        )
                    )
                }
                list.add(
                    AutomationRoutine(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        triggerPhrase = obj.getString("triggerPhrase"),
                        steps = stepsList,
                        isEnabled = obj.getBoolean("isEnabled"),
                        category = obj.optString("category", "Custom"),
                        scheduledTime = obj.optString("scheduledTime").takeIf { it.isNotBlank() }
                    )
                )
            }
            _routines.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
