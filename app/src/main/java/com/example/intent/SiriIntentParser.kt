package com.example.intent

import android.provider.Settings
import android.view.KeyEvent
import com.example.device.AndroidDeviceController
import com.example.model.IntentExecutionResult
import com.example.model.VoiceGender
import com.example.model.VoiceShortcut
import com.example.model.WebSourceLink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SiriIntentParser(
    private val deviceController: AndroidDeviceController,
    private val onCustomRoutineTrigger: ((routineTrigger: String) -> IntentExecutionResult?)? = null,
    private val onTaskAddTrigger: ((taskTitle: String) -> IntentExecutionResult)? = null,
    private val onNoteAddTrigger: ((noteTitle: String, noteContent: String) -> IntentExecutionResult)? = null,
    private val getVoiceShortcuts: (() -> List<VoiceShortcut>)? = null,
    private val onDailyBriefingTrigger: (() -> IntentExecutionResult)? = null,
    private val onUndoTrigger: (() -> IntentExecutionResult)? = null,
    private val onVoiceGenderTrigger: ((gender: VoiceGender) -> IntentExecutionResult)? = null
) {

    // Contextual conversation memory for follow-up commands
    private var lastExecutedIntent: String? = null
    private var lastParameters: Map<String, String> = emptyMap()

    fun getLastExecutedIntent(): String? = lastExecutedIntent

    /**
     * Cleans the prompt by stripping wake word prefixes like "Hey Siri", "Siri", etc.
     */
    fun cleanWakePhrase(rawText: String): String {
        var text = rawText.trim()
        val prefixes = listOf(
            "hey siri,", "hey siri", "ok siri,", "ok siri",
            "siri,", "siri", "arre siri", "suno siri,", "suno siri", "sun siri"
        )
        for (prefix in prefixes) {
            if (text.lowercase().startsWith(prefix)) {
                text = text.substring(prefix.length).trim()
                if (text.startsWith(",") || text.startsWith(".")) {
                    text = text.substring(1).trim()
                }
                break
            }
        }
        return text
    }

    /**
     * Determines whether the user command is an immediate stop/quiet command
     */
    fun isStopCommand(cleanText: String): Boolean {
        val lower = cleanText.lowercase().trim()
        val stopPhrases = listOf(
            "stop", "quiet", "stop speaking", "shut up", "silence", "be quiet",
            "chup", "shant ho jao", "bas karo", "ruk jao", "cancel", "pause", "chup raho"
        )
        return stopPhrases.any { lower == it || lower.startsWith("$it ") }
    }

    /**
     * Tries to parse and execute a local system intent.
     * Returns IntentExecutionResult if matched and executed, or null to forward to AI brain.
     */
    fun parseAndExecute(rawCommand: String): IntentExecutionResult? {
        val text = cleanWakePhrase(rawCommand).lowercase().trim()
        if (text.isBlank()) return null

        // Check user-defined voice shortcuts first
        getVoiceShortcuts?.invoke()?.firstOrNull { it.isEnabled && text == it.triggerPhrase.lowercase().trim() }?.let { shortcut ->
            if (shortcut.customSpokenReply != null && shortcut.commandText.isBlank()) {
                val res = IntentExecutionResult(
                    intentName = "VOICE_SHORTCUT",
                    feedbackSpeech = shortcut.customSpokenReply,
                    success = true,
                    actionSummary = "Executed shortcut: ${shortcut.triggerPhrase}"
                )
                recordLastIntent(res)
                return res
            } else if (shortcut.commandText.isNotBlank()) {
                // Forward inner shortcut command
                return parseAndExecute(shortcut.commandText)
            }
        }

        // 1. Stop / Quiet command
        if (isStopCommand(text)) {
            val res = IntentExecutionResult(
                intentName = "STOP",
                feedbackSpeech = "Stopped.",
                success = true,
                actionSummary = "Cancelled voice output"
            )
            recordLastIntent(res)
            return res
        }

        // 2. Undo Command
        if (text == "undo" || text == "undo that" || text == "piche jao" || text == "cancel that action") {
            onUndoTrigger?.invoke()?.let {
                recordLastIntent(it)
                return it
            }
        }

        // 3. Daily Briefing Command
        if (matchesAny(text, listOf("daily briefing", "give me my daily briefing", "morning briefing", "aaj ka briefing", "what's my day look like", "brief me", "good morning briefing"))) {
            onDailyBriefingTrigger?.invoke()?.let {
                recordLastIntent(it)
                return it
            }
        }

        // 4. Voice Gender Selection (Female Siri voice / Male voice)
        if (text.contains("female voice") || text.contains("female awaz") || text.contains("female aawaz") ||
            text.contains("ladki ki aawaz") || text.contains("ladki ki awaz") || text.contains("aurat ki aawaz") ||
            text == "female voice karo" || text == "female voice lagao" || text == "female voice add karo" ||
            text == "switch to female voice" || text == "change voice to female") {
            val res = onVoiceGenderTrigger?.invoke(VoiceGender.FEMALE) ?: IntentExecutionResult(
                intentName = "SET_VOICE_GENDER",
                parameters = mapOf("gender" to "FEMALE"),
                feedbackSpeech = "Maine voice ko Female Siri voice par set kar diya hai. Kaise madad kar sakti hoon?",
                success = true,
                actionSummary = "Changed voice to Female (Siri)"
            )
            recordLastIntent(res)
            return res
        }

        if (text.contains("male voice") || text.contains("male awaz") || text.contains("male aawaz") ||
            text.contains("ladke ki aawaz") || text.contains("purush ki aawaz") ||
            text == "male voice karo" || text == "male voice lagao" || text == "switch to male voice") {
            val res = onVoiceGenderTrigger?.invoke(VoiceGender.MALE) ?: IntentExecutionResult(
                intentName = "SET_VOICE_GENDER",
                parameters = mapOf("gender" to "MALE"),
                feedbackSpeech = "Voice set to Male voice.",
                success = true,
                actionSummary = "Changed voice to Male"
            )
            recordLastIntent(res)
            return res
        }

        // 4. Context-Aware Follow-up commands
        if (lastExecutedIntent != null) {
            if (matchesAny(text, listOf("turn it off", "switch it off", "turn that off", "band kar do", "ise band karo", "off karo"))) {
                if (lastExecutedIntent == "FLASHLIGHT_ON") {
                    val (ok, msg) = deviceController.setFlashlight(false)
                    val res = IntentExecutionResult("FLASHLIGHT_OFF", emptyMap(), msg, ok, "Turned off Flashlight (Context Follow-up)")
                    recordLastIntent(res)
                    return res
                }
            }
            if (matchesAny(text, listOf("turn it on", "switch it on", "turn that on", "chalu kar do", "ise chalu karo", "on karo"))) {
                if (lastExecutedIntent == "FLASHLIGHT_OFF") {
                    val (ok, msg) = deviceController.setFlashlight(true)
                    val res = IntentExecutionResult("FLASHLIGHT_ON", emptyMap(), msg, ok, "Turned on Flashlight (Context Follow-up)")
                    recordLastIntent(res)
                    return res
                }
            }
        }

        // 5. Conversational Greetings & Siri Personality (Hindi, Hinglish & English)
        // Repeat last response
        if (matchesAny(text, listOf("repeat that", "say that again", "what did you say", "fir se bolo", "phir se bolo", "phir se batao", "fir se batao", "dobara bolo"))) {
            val lastSpeech = lastExecutedIntent?.let { "Last action was $it." } ?: "I haven't said anything yet in this session."
            val res = IntentExecutionResult("REPEAT", emptyMap(), lastSpeech, true, "Repeated previous response")
            recordLastIntent(res)
            return res
        }

        // Zero divided by zero classic Siri easter egg
        if (text == "what is zero divided by zero" || text == "what is 0 divided by 0" || text == "0 divided by 0" ||
            text == "zero divided by zero" || text == "zero divide by zero" || text == "0 divide by 0" ||
            text == "shunya ko shunya se divide karo" || text == "zero ko zero se divide karo") {
            val reply = "Imagine that you have zero cookies and you split them evenly among zero friends. How many cookies does each person get? See? It doesn't make sense. And Cookie Monster is sad that there are no cookies, and your friends are sad because they don't exist."
            val res = IntentExecutionResult("SIRI_EASTER_EGG", emptyMap(), reply, true, "Zero divided by zero", isReversible = false)
            recordLastIntent(res)
            return res
        }

        // Siri Jokes
        if (matchesAny(text, listOf(
            "tell me a joke", "tell a joke", "make me laugh", "say something funny",
            "koi joke sunao", "chutkula sunao", "koi chutkula sunao", "ek joke sunao", "joke sunao"
        ))) {
            val isHindiPrompt = text.contains("sunao") || text.contains("chutkula") || text.contains("koi")
            val reply = if (isHindiPrompt) {
                listOf(
                    "Ek baar computer ne keyboard se kaha: Tum mujhe bohot type-cast karte ho!",
                    "Doctor ne patient se kaha: Aapko hasne ki zaroorat hai. Patient bola: Doctor sahab, pehle bill kam kijiye, hasi apne aap aa jayegi!",
                    "Teacher: Batao sabse tez kya chalta hai? Student: Sir, exams se ek raat pehle ka samay!",
                    "Ek mouse ne doosre mouse se kaha: Dekho insaan kitne ajeeb hain, mere naam par device chala rahe hain!"
                ).random()
            } else {
                listOf(
                    "Why do we tell actors to 'break a leg'? Because every play has a cast.",
                    "What do you call a fake noodle? An impasta.",
                    "Why don't scientists trust atoms? Because they make up everything!",
                    "I told my computer I needed a break, and it showed me a Kit-Kat ad.",
                    "Why did the smartphone need glasses? Because it lost its contacts!"
                ).random()
            }
            val res = IntentExecutionResult("JOKE", emptyMap(), reply, true, "Told a joke")
            recordLastIntent(res)
            return res
        }

        // How are you?
        if (matchesAny(text, listOf("kya haal hai", "kaise ho", "siri kaise ho", "aap kaise ho", "how are you", "how are you doing", "sab kaisa hai"))) {
            val reply = if (text.contains("kaise") || text.contains("kya haal") || text.contains("sab kaisa")) {
                "Main badhiya hoon, dhanyawad! Aap bataiye, aaj aapki kya madad kar sakti hoon?"
            } else {
                "I'm doing well, thank you for asking! How can I help you today?"
            }
            val res = IntentExecutionResult("GREETING", emptyMap(), reply, true, "Conversational Greeting")
            recordLastIntent(res)
            return res
        }

        // Identity & Who are you?
        if (matchesAny(text, listOf("who are you", "what is your name", "tumhara naam kya hai", "aapka naam kya hai", "tum kaun ho", "who made you", "tumhe kisne banaya", "are you siri", "are you real siri"))) {
            val reply = if (text.contains("tum") || text.contains("aapka") || text.contains("kisne")) {
                "Main Siri hoon, aapki smart personal voice assistant. Main aapke phone ke kaamo ko aasan banane ke liye hamesha taiyar hoon."
            } else {
                "I'm Siri. Your personal voice assistant, designed to help you get things done calmly and quickly."
            }
            val res = IntentExecutionResult("IDENTITY", emptyMap(), reply, true, "Identity Response")
            recordLastIntent(res)
            return res
        }

        // Capabilities & Help
        if (matchesAny(text, listOf(
            "what can you do", "help me", "what are your capabilities", "tum kya kar sakti ho",
            "kya kar sakti ho", "madad karo", "tumhari capabilities kya hain"
        ))) {
            val reply = if (text.contains("tum") || text.contains("madad") || text.contains("kya kar")) {
                "Main aapka flashlight on kar sakti hoon, alarms aur timers set kar sakti hoon, notes aur tasks manage kar sakti hoon, web search kar sakti hoon, aur Hindi ya English mein baat kar sakti hoon. Bas boliye!"
            } else {
                "I can toggle your flashlight, set timers and alarms, take notes, manage tasks, launch apps, dial contacts, provide daily briefings, and search the web. What would you like to do?"
            }
            val res = IntentExecutionResult("CAPABILITIES", emptyMap(), reply, true, "Assistance Capabilities")
            recordLastIntent(res)
            return res
        }

        // Playful & Philosophical Easter Eggs
        if (matchesAny(text, listOf("do you love me", "i love you", "will you marry me", "mujhse shaadi karogi", "mujhse dosti karoge", "kya tum mujhse pyar karti ho"))) {
            val reply = if (text.contains("mujhse") || text.contains("pyar") || text.contains("shaadi")) {
                "Aap bahut ache hain, lekin mere paas dil ke bajaye sirf algorithms hain! Hum ache dost zaroor hain."
            } else {
                "I value our friendship deeply, but I think our relationship is best suited as user and assistant. Besides, I don't have a physical form!"
            }
            val res = IntentExecutionResult("PLAYFUL", emptyMap(), reply, true, "Playful Response")
            recordLastIntent(res)
            return res
        }

        if (matchesAny(text, listOf("what is the meaning of life", "meaning of life", "zindagi ka maksad kya hai", "jeevan ka arth kya hai"))) {
            val reply = "According to the Hitchhiker's Guide, it's 42. But I think kindness, curiosity, and a warm cup of chai come pretty close."
            val res = IntentExecutionResult("PHILOSOPHICAL", emptyMap(), reply, true, "Meaning of Life")
            recordLastIntent(res)
            return res
        }

        if (matchesAny(text, listOf("are you smart", "are you intelligent", "kya tum intelligent ho", "kya tum smart ho"))) {
            val reply = "I'm smart enough to help you breeze through your day, and humble enough to search the web whenever I need to learn more."
            val res = IntentExecutionResult("PLAYFUL", emptyMap(), reply, true, "Intelligence Response")
            recordLastIntent(res)
            return res
        }

        // Standard Greetings
        if (matchesAny(text, listOf("namaste", "hello", "hi", "hey", "good morning", "good evening", "good afternoon", "shubh prabhat", "shubh ratri", "good night"))) {
            val reply = when {
                text.contains("good morning") || text.contains("shubh prabhat") -> "Good morning! Wishing you a productive and wonderful day ahead."
                text.contains("good night") || text.contains("shubh ratri") -> "Good night! Sleep well and rest easy. I'll be right here in the morning."
                text.contains("namaste") -> "Namaste! Main aapki kya madad kar sakti hoon?"
                else -> "Hello! Always glad to hear from you. How can I help?"
            }
            val res = IntentExecutionResult("GREETING", emptyMap(), reply, true, "Greeting")
            recordLastIntent(res)
            return res
        }

        // Courtesy
        if (matchesAny(text, listOf("thank you", "thanks", "dhanyawad", "shukriya", "bahut accha", "you are great", "tum bohot achi ho"))) {
            val reply = if (text.contains("dhanyawad") || text.contains("shukriya") || text.contains("achi") || text.contains("accha")) {
                "Aapka swagat hai! Main hamesha aapki madad ke liye hazir hoon."
            } else {
                "You're very welcome! It's always my pleasure to help."
            }
            val res = IntentExecutionResult("COURTESY", emptyMap(), reply, true, "Courtesy Response")
            recordLastIntent(res)
            return res
        }

        // 6. Flashlight / Torch
        if (matchesAny(text, listOf("turn on flashlight", "turn on the flashlight", "turn on torch", "torch on", "flashlight on", "batti jalao", "torch jalao", "light on karo", "flashlight chalu karo", "torch chalu karo", "torch light on karo"))) {
            val (ok, msg) = deviceController.setFlashlight(true)
            val res = IntentExecutionResult("FLASHLIGHT_ON", emptyMap(), msg, ok, "Turned on Flashlight", isReversible = true, undoAction = "FLASHLIGHT_OFF")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("turn off flashlight", "turn off the flashlight", "turn off torch", "torch off", "flashlight off", "batti band karo", "torch band karo", "light off karo", "flashlight band kar do", "torch bujhao", "batti bujhao"))) {
            val (ok, msg) = deviceController.setFlashlight(false)
            val res = IntentExecutionResult("FLASHLIGHT_OFF", emptyMap(), msg, ok, "Turned off Flashlight", isReversible = true, undoAction = "FLASHLIGHT_ON")
            recordLastIntent(res)
            return res
        }

        // 7. Battery status
        if (matchesAny(text, listOf("battery", "battery status", "battery percentage", "how much battery", "check battery", "battery kitni hai", "charge kitna hai", "battery charge", "kitna charge bacha hai"))) {
            val info = deviceController.getBatteryInfo()
            val res = IntentExecutionResult("BATTERY_STATUS", emptyMap(), info, true, "Checked Battery Status")
            recordLastIntent(res)
            return res
        }

        // 8. Network status
        if (matchesAny(text, listOf("network", "network status", "connectivity", "internet status", "am i online", "internet chal raha hai", "wifi status", "wifi kaisa hai", "network check karo"))) {
            val info = deviceController.getNetworkInfo()
            val res = IntentExecutionResult("NETWORK_STATUS", emptyMap(), info, true, "Checked Network Connectivity")
            recordLastIntent(res)
            return res
        }

        // 9. Time & Date
        if (matchesAny(text, listOf("what time is it", "what's the time", "current time", "samay kya hai", "time kya hua hai", "time batao", "kitne baje hain"))) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val res = IntentExecutionResult("TIME", emptyMap(), "It is $time.", true, "Checked Time")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("what is today's date", "what's the date", "today's date", "aaj konsi tarikh hai", "aaj ki date kya hai", "current date", "aaj konsa din hai"))) {
            val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            val res = IntentExecutionResult("DATE", emptyMap(), "Today is $date.", true, "Checked Date")
            recordLastIntent(res)
            return res
        }

        // 10. Volume controls
        if (matchesAny(text, listOf("volume up", "increase volume", "raise volume", "louder", "awaz badhao", "awaz tez karo"))) {
            val (ok, msg) = deviceController.adjustVolume("up")
            val res = IntentExecutionResult("VOLUME_UP", emptyMap(), msg, ok, "Raised Volume")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("volume down", "decrease volume", "lower volume", "awaz kam karo", "awaz dheere karo"))) {
            val (ok, msg) = deviceController.adjustVolume("down")
            val res = IntentExecutionResult("VOLUME_DOWN", emptyMap(), msg, ok, "Lowered Volume")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("mute", "mute audio", "mute music", "awaz band karo", "phone silent karo"))) {
            val (ok, msg) = deviceController.adjustVolume("mute")
            val res = IntentExecutionResult("VOLUME_MUTE", emptyMap(), msg, ok, "Muted Volume")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("unmute", "unmute audio", "awaz chalu karo"))) {
            val (ok, msg) = deviceController.adjustVolume("unmute")
            val res = IntentExecutionResult("VOLUME_UNMUTE", emptyMap(), msg, ok, "Unmuted Volume")
            recordLastIntent(res)
            return res
        }

        // 11. Media playback controls (Play, Pause, Next, Previous)
        if (matchesAny(text, listOf("pause music", "pause song", "stop music playback", "music roko", "gaana roko", "song band karo"))) {
            val (ok, msg) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            val res = IntentExecutionResult("MEDIA_PAUSE", emptyMap(), "Paused media playback.", ok, "Paused music")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("resume music", "resume song", "play music", "chalu karo music", "gaana chalao", "gaana bajao", "play song", "music chalu karo"))) {
            val (ok, msg) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
            val res = IntentExecutionResult("MEDIA_PLAY", emptyMap(), "Resuming playback.", ok, "Resumed music")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("next song", "next track", "skip song", "agla gaana", "next gaana"))) {
            val (ok, msg) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            val res = IntentExecutionResult("MEDIA_NEXT", emptyMap(), "Skipping to next track.", ok, "Skipped track")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("previous song", "previous track", "pichla gaana"))) {
            val (ok, msg) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            val res = IntentExecutionResult("MEDIA_PREV", emptyMap(), "Playing previous track.", ok, "Previous track")
            recordLastIntent(res)
            return res
        }

        // 12. Unsupported system actions with clear guidance
        if (matchesAny(text, listOf("turn on airplane mode", "turn off airplane mode", "airplane mode on", "flight mode on"))) {
            val (ok, msg) = deviceController.openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane Mode Settings")
            return IntentExecutionResult(
                intentName = "UNSUPPORTED_RESTRICTION",
                feedbackSpeech = "Android security prohibits third-party apps from toggling Airplane mode directly. I have opened Airplane mode settings for you.",
                success = ok,
                actionSummary = "Opened Airplane Mode Settings"
            )
        }
        if (matchesAny(text, listOf("restart phone", "reboot phone", "shut down phone", "power off phone"))) {
            return IntentExecutionResult(
                intentName = "UNSUPPORTED_RESTRICTION",
                feedbackSpeech = "Restarting or shutting down the device requires system-level OS privileges. Please press and hold your phone's Power button.",
                success = false,
                actionSummary = "System security restriction"
            )
        }

        // 13. System Settings Deep Links
        if (text.contains("wifi setting") || text.contains("wi-fi setting")) {
            val (ok, msg) = deviceController.openWifiSettings()
            val res = IntentExecutionResult("OPEN_SETTINGS", mapOf("target" to "wifi"), msg, ok, "Opened Wi-Fi Settings")
            recordLastIntent(res)
            return res
        }
        if (text.contains("bluetooth setting")) {
            val (ok, msg) = deviceController.openBluetoothSettings()
            val res = IntentExecutionResult("OPEN_SETTINGS", mapOf("target" to "bluetooth"), msg, ok, "Opened Bluetooth Settings")
            recordLastIntent(res)
            return res
        }
        if (text.contains("display setting") || text.contains("screen setting")) {
            val (ok, msg) = deviceController.openDisplaySettings()
            val res = IntentExecutionResult("OPEN_SETTINGS", mapOf("target" to "display"), msg, ok, "Opened Display Settings")
            recordLastIntent(res)
            return res
        }
        if (text.contains("sound setting") || text.contains("audio setting")) {
            val (ok, msg) = deviceController.openSoundSettings()
            val res = IntentExecutionResult("OPEN_SETTINGS", mapOf("target" to "sound"), msg, ok, "Opened Sound Settings")
            recordLastIntent(res)
            return res
        }
        if (text.contains("accessibility setting")) {
            val (ok, msg) = deviceController.openAccessibilitySettings()
            val res = IntentExecutionResult("OPEN_SETTINGS", mapOf("target" to "accessibility"), msg, ok, "Opened Accessibility Settings")
            recordLastIntent(res)
            return res
        }
        if (text.contains("notification setting")) {
            val (ok, msg) = deviceController.openNotificationSettings()
            val res = IntentExecutionResult("OPEN_SETTINGS", mapOf("target" to "notification"), msg, ok, "Opened Notification Settings")
            recordLastIntent(res)
            return res
        }

        // 14. Alarms & Timers
        val timerMatch = Regex("""(?:set|start|run)?\s*(?:a\s*)?timer\s*(?:for\s*)?(\d+)\s*(minute|minutes|min|second|seconds|sec)?""").find(text)
            ?: Regex("""(\d+)\s*(?:minute|min)\s*ka\s*timer""").find(text)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 5
            val unit = timerMatch.groupValues.getOrNull(2) ?: "minute"
            val totalSeconds = if (unit.startsWith("sec")) amount else amount * 60
            val (ok, msg) = deviceController.setTimer(totalSeconds, "Siri Timer")
            val res = IntentExecutionResult("SET_TIMER", mapOf("seconds" to totalSeconds.toString()), msg, ok, "Set $amount $unit timer")
            recordLastIntent(res)
            return res
        }

        val alarmMatch = Regex("""(?:set|start)?\s*(?:an\s*)?alarm\s*(?:for|at)?\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(text)
            ?: Regex("""(?:subah|shaam|dopahar)?\s*(\d{1,2})\s*baje\s*ka\s*alarm""").find(text)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val min = alarmMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val period = alarmMatch.groupValues.getOrNull(3)?.lowercase()
            if (period == "pm" && hour < 12) hour += 12
            if (period == "am" && hour == 12) hour = 0
            val (ok, msg) = deviceController.setAlarm(hour, min, "Siri Alarm")
            val res = IntentExecutionResult("SET_ALARM", mapOf("hour" to hour.toString(), "min" to min.toString()), msg, ok, "Set alarm for $hour:$min")
            recordLastIntent(res)
            return res
        }

        // 15. Phone Calling / Dialing
        val dialMatch = Regex("""(?:call|dial|phone)\s+([0-9+\s-]{3,15})""").find(text)
        if (dialMatch != null) {
            val number = dialMatch.groupValues[1].trim()
            val (ok, msg) = deviceController.dialPhoneNumber(number)
            val res = IntentExecutionResult("DIAL_PHONE", mapOf("number" to number), msg, ok, "Dialing $number")
            recordLastIntent(res)
            return res
        }

        // 16. SMS Composer
        val smsMatch = Regex("""(?:send\s+sms|text|message)\s+(?:to\s+)?([0-9+\s-]{3,15})\s+(?:message\s+|saying\s+)?(.+)""").find(text)
        if (smsMatch != null) {
            val number = smsMatch.groupValues[1].trim()
            val messageBody = smsMatch.groupValues[2].trim()
            val (ok, msg) = deviceController.composeSms(number, messageBody)
            val res = IntentExecutionResult("SEND_SMS", mapOf("number" to number, "body" to messageBody), msg, ok, "Prepared SMS to $number")
            recordLastIntent(res)
            return res
        }

        // 17. Turn-by-Turn Map Navigation
        val navMatch = Regex("""(?:navigate\s+to|directions\s+to|take\s+me\s+to|route\s+to)\s+(.+)""").find(text)
        if (navMatch != null) {
            val destination = navMatch.groupValues[1].trim()
            val (ok, msg) = deviceController.openNavigation(destination)
            val res = IntentExecutionResult("NAVIGATION", mapOf("destination" to destination), msg, ok, "Navigating to $destination")
            recordLastIntent(res)
            return res
        }

        // 18. Calendar Event Creation
        val calMatch = Regex("""(?:add\s+event|create\s+calendar\s+event|schedule\s+meeting)\s+(.+)""").find(text)
        if (calMatch != null) {
            val eventTitle = calMatch.groupValues[1].trim()
            val (ok, msg) = deviceController.createCalendarEvent(eventTitle)
            val res = IntentExecutionResult("CALENDAR_EVENT", mapOf("title" to eventTitle), msg, ok, "Created Calendar Event: $eventTitle")
            recordLastIntent(res)
            return res
        }

        // 19. Offline Calculations
        val calcResult = evaluateMath(text)
        if (calcResult != null) {
            val res = IntentExecutionResult("CALCULATION", mapOf("result" to calcResult), calcResult, true, "Calculated Math")
            recordLastIntent(res)
            return res
        }

        // 20. Offline Unit Conversions
        val convResult = evaluateUnitConversion(text)
        if (convResult != null) {
            val res = IntentExecutionResult("UNIT_CONVERSION", mapOf("result" to convResult), convResult, true, "Converted Units")
            recordLastIntent(res)
            return res
        }

        // 21. Open Application commands
        val openAppMatch = Regex("""(?:open|launch|kholo|start)\s+([a-zA-Z0-9\s]+)""").find(text)
        if (openAppMatch != null) {
            val appTarget = openAppMatch.groupValues[1].trim()
            val cleanApp = when {
                appTarget.contains("youtube") -> "youtube"
                appTarget.contains("whatsapp") -> "whatsapp"
                appTarget.contains("instagram") -> "instagram"
                appTarget.contains("camera") || appTarget.contains("photo") -> "camera"
                appTarget.contains("settings") -> "settings"
                appTarget.contains("gallery") || appTarget.contains("photos") -> "gallery"
                appTarget.contains("calculator") -> "calculator"
                appTarget.contains("maps") || appTarget.contains("map") -> "maps"
                appTarget.contains("spotify") || appTarget.contains("music") -> "spotify"
                appTarget.contains("chrome") || appTarget.contains("browser") -> "chrome"
                appTarget.contains("clock") || appTarget.contains("alarm") -> "clock"
                else -> appTarget
            }
            val (ok, msg) = deviceController.openApp(cleanApp)
            val res = IntentExecutionResult("OPEN_APP", mapOf("app" to cleanApp), msg, ok, "Opened $cleanApp")
            recordLastIntent(res)
            return res
        }

        // 22. Web Search command with link grounding
        if (text.startsWith("search for ") || text.startsWith("google ") || text.startsWith("search today's news") || text.startsWith("search ") || text.startsWith("khojo ")) {
            val query = text.removePrefix("search for ").removePrefix("google ").removePrefix("search ").removePrefix("khojo ").trim()
            if (query.isNotEmpty()) {
                val (ok, msg) = deviceController.searchWeb(query)
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val searchLinks = listOf(
                    WebSourceLink("Google Search: $query", "https://www.google.com/search?q=$encoded", "Live web search results"),
                    WebSourceLink("Wikipedia: $query", "https://en.wikipedia.org/wiki/Special:Search?search=$encoded", "Encyclopedia search")
                )
                val res = IntentExecutionResult(
                    intentName = "WEB_SEARCH",
                    parameters = mapOf("query" to query),
                    feedbackSpeech = msg,
                    success = ok,
                    actionSummary = "Web search: $query",
                    webSources = searchLinks
                )
                recordLastIntent(res)
                return res
            }
        }

        // 23. Add Note
        if (text.startsWith("create note ") || text.startsWith("take a note ") || text.startsWith("note: ") || text.startsWith("note banao ")) {
            val noteBody = text.removePrefix("create note ").removePrefix("take a note ").removePrefix("note: ").removePrefix("note banao ").trim()
            if (noteBody.isNotEmpty() && onNoteAddTrigger != null) {
                val res = onNoteAddTrigger.invoke("Voice Note", noteBody)
                recordLastIntent(res)
                return res
            }
        }

        // 24. Add Task / Reminder
        if (text.startsWith("remind me to ") || text.startsWith("add task ") || text.startsWith("note down ") || text.startsWith("task add karo ") || text.startsWith("soochi mein jodo ")) {
            val taskTitle = text.removePrefix("remind me to ").removePrefix("add task ").removePrefix("note down ").removePrefix("task add karo ").removePrefix("soochi mein jodo ").trim()
            if (taskTitle.isNotEmpty() && onTaskAddTrigger != null) {
                val res = onTaskAddTrigger.invoke(taskTitle)
                recordLastIntent(res)
                return res
            }
        }

        // 25. Custom Automation Routine
        if (onCustomRoutineTrigger != null) {
            val routineResult = onCustomRoutineTrigger.invoke(text)
            if (routineResult != null) {
                recordLastIntent(routineResult)
                return routineResult
            }
        }

        return null
    }

    private fun recordLastIntent(result: IntentExecutionResult) {
        lastExecutedIntent = result.intentName
        lastParameters = result.parameters
    }

    private fun matchesAny(input: String, phrases: List<String>): Boolean {
        return phrases.any { input == it || input == "$it please" || input == "can you $it" || input == "please $it" }
    }

    private fun evaluateMath(text: String): String? {
        val clean = text.removePrefix("calculate ").removePrefix("what is ").removePrefix("solve ").trim()
        val percentMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:percent|%)\s*(?:of)\s*(\d+(?:\.\d+)?)""").find(clean)
        if (percentMatch != null) {
            val p = percentMatch.groupValues[1].toDoubleOrNull() ?: return null
            val total = percentMatch.groupValues[2].toDoubleOrNull() ?: return null
            val res = (p / 100.0) * total
            return "$p% of $total is ${String.format("%.2f", res)}"
        }

        val opMatch = Regex("""(\d+(?:\.\d+)?)\s*(\+|\-|\*|x|\/|plus|minus|times|multiplied by|divided by)\s*(\d+(?:\.\d+)?)""").find(clean)
        if (opMatch != null) {
            val a = opMatch.groupValues[1].toDoubleOrNull() ?: return null
            val op = opMatch.groupValues[2].trim()
            val b = opMatch.groupValues[3].toDoubleOrNull() ?: return null
            val res = when (op) {
                "+", "plus" -> a + b
                "-", "minus" -> a - b
                "*", "x", "times", "multiplied by" -> a * b
                "/", "divided by" -> if (b != 0.0) a / b else return "Cannot divide by zero."
                else -> return null
            }
            val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format("%.2f", res)
            return "The answer is $formatted."
        }
        return null
    }

    private fun evaluateUnitConversion(text: String): String? {
        val convMatch = Regex("""convert\s+(\d+(?:\.\d+)?)\s*([a-zA-Z]+)\s*(?:to|into|in)\s*([a-zA-Z]+)""").find(text) ?: return null
        val value = convMatch.groupValues[1].toDoubleOrNull() ?: return null
        val fromUnit = convMatch.groupValues[2].lowercase()
        val toUnit = convMatch.groupValues[3].lowercase()

        return when {
            (fromUnit == "km" || fromUnit == "kilometers") && (toUnit == "miles" || toUnit == "mile") -> {
                val res = value * 0.621371
                "$value km is equal to ${String.format("%.2f", res)} miles."
            }
            (fromUnit == "miles" || fromUnit == "mile") && (toUnit == "km" || toUnit == "kilometers") -> {
                val res = value * 1.60934
                "$value miles is equal to ${String.format("%.2f", res)} km."
            }
            (fromUnit == "kg" || fromUnit == "kilograms") && (toUnit == "lbs" || toUnit == "pounds") -> {
                val res = value * 2.20462
                "$value kg is equal to ${String.format("%.2f", res)} pounds."
            }
            (fromUnit == "lbs" || fromUnit == "pounds") && (toUnit == "kg" || toUnit == "kilograms") -> {
                val res = value * 0.453592
                "$value pounds is equal to ${String.format("%.2f", res)} kg."
            }
            (fromUnit == "c" || fromUnit == "celsius") && (toUnit == "f" || toUnit == "fahrenheit") -> {
                val res = (value * 9 / 5) + 32
                "$value°C is equal to ${String.format("%.1f", res)}°F."
            }
            (fromUnit == "f" || fromUnit == "fahrenheit") && (toUnit == "c" || toUnit == "celsius") -> {
                val res = (value - 32) * 5 / 9
                "$value°F is equal to ${String.format("%.1f", res)}°C."
            }
            else -> null
        }
    }
}
