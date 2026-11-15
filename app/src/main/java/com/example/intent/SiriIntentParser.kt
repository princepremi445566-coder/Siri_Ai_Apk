package com.example.intent

import android.provider.Settings
import android.view.KeyEvent
import com.example.ai.EmotionDetector
import com.example.device.AndroidDeviceController
import com.example.device.AppLaunchOutcome
import com.example.device.InAppSearchOutcome
import com.example.device.InstalledAppInfo
import com.example.device.SystemControlOutcome
import com.example.model.AssistantEmotion
import com.example.model.IntentExecutionResult
import com.example.model.LanguageOption
import com.example.model.VoiceGender
import com.example.model.VoiceShortcut
import com.example.model.WebSourceLink
import java.net.URLEncoder
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
    private val onVoiceGenderTrigger: ((gender: VoiceGender) -> IntentExecutionResult)? = null,
    private val getCurrentLanguage: (() -> LanguageOption)? = null,
    private val getLastAssistantSpeech: (() -> String)? = null,
    private val getAssistantName: (() -> String)? = null,
    private val getUserAddress: (() -> String)? = null
) {

    // Contextual conversation memory for follow-up commands
    private var lastExecutedIntent: String? = null
    private var lastParameters: Map<String, String> = emptyMap()
    private var pendingAppCandidates: List<InstalledAppInfo> = emptyList()
    private val lastVariedIndex = HashMap<String, Int>()

    fun getLastExecutedIntent(): String? = lastExecutedIntent

    private fun pickVaried(category: String, options: List<String>): String {
        if (options.isEmpty()) return ""
        if (options.size == 1) return options[0]
        val prev = lastVariedIndex[category] ?: -1
        val next = (prev + 1) % options.size
        lastVariedIndex[category] = next
        return options[next]
    }

    private fun assistantName(): String = getAssistantName?.invoke()?.trim()?.ifBlank { "Siri" } ?: "Siri"

    private fun userAddress(): String = getUserAddress?.invoke()?.trim()?.ifBlank { "Friend" } ?: "Friend"

    private fun spokenUserTag(): String {
        val addr = userAddress()
        return if (addr.equals("Friend", ignoreCase = true)) "" else ", $addr"
    }

    private fun isHindiMode(prompt: String): Boolean {
        val current = getCurrentLanguage?.invoke() ?: LanguageOption.AUTO
        return when (current) {
            LanguageOption.HINDI -> true
            LanguageOption.HINGLISH -> false
            LanguageOption.ENGLISH -> false
            LanguageOption.AUTO -> prompt.any { it in '\u0900'..'\u097F' }
        }
    }

    private fun isHinglishMode(prompt: String): Boolean {
        val current = getCurrentLanguage?.invoke() ?: LanguageOption.AUTO
        return when (current) {
            LanguageOption.HINDI -> false
            LanguageOption.HINGLISH -> true
            LanguageOption.ENGLISH -> false
            LanguageOption.AUTO -> {
                val lower = prompt.lowercase(Locale.ROOT)
                Regex("""\b(kaise|kya|batao|karo|kardo|chalu|shukriya|dhanyawad|samachar|sunao|haal|baje|kholo|band|badhao|kam|dhundo|khojo|mein|par|naam|mera|tumhara|aapka)\b""").containsMatchIn(lower)
            }
        }
    }

    private fun localized(prompt: String, hindi: String, hinglish: String, english: String): String {
        return when {
            isHindiMode(prompt) -> hindi
            isHinglishMode(prompt) -> hinglish
            else -> english
        }
    }

    /**
     * Cleans the prompt by stripping wake word prefixes for the customized assistant name
     * (e.g., "Hey <Name>", "<Name>", "Suno <Name>") as well as legacy "Hey Siri" phrases.
     */
    fun cleanWakePhrase(rawText: String): String {
        var text = rawText.trim()
        val customLower = assistantName().lowercase(Locale.ROOT)
        val prefixes = buildList {
            if (customLower.isNotBlank()) {
                add("hey $customLower")
                add("ok $customLower")
                add("hi $customLower")
                add("hello $customLower")
                add("arre $customLower")
                add("suno $customLower")
                add("sun $customLower")
                add("oye $customLower")
                add(customLower)
            }
            if (customLower != "siri") {
                add("hey siri")
                add("ok siri")
                add("arre siri")
                add("suno siri")
                add("sun siri")
                add("siri")
            }
        }
        val lower = text.lowercase(Locale.ROOT)
        for (prefix in prefixes) {
            if (lower == prefix) {
                return ""
            }
            if (lower.startsWith(prefix)) {
                val nextChar = text.getOrNull(prefix.length)
                if (nextChar == null || nextChar == ',' || nextChar == '.' || nextChar == ':' || nextChar.isWhitespace()) {
                    text = text.substring(prefix.length).trimStart(',', '.', ':', ' ')
                    break
                }
            }
        }
        return text.trim()
    }

    /**
     * Determines whether the user command is an immediate stop/quiet command
     */
    fun isStopCommand(cleanText: String): Boolean {
        val lower = cleanText.lowercase(Locale.ROOT).trim().trimEnd('!', '.', '?')
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
        val originalText = cleanWakePhrase(rawCommand).trim()
        val text = originalText.lowercase(Locale.ROOT)
        val normalizedExact = text.trimEnd('?', '!', '.', ' ')
        if (text.isBlank()) return null

        // 0. Pending ambiguous app disambiguation follow-up
        if (pendingAppCandidates.isNotEmpty()) {
            val selectedApp = resolvePendingAppSelection(normalizedExact, pendingAppCandidates)
            if (selectedApp != null) {
                pendingAppCandidates = emptyList()
                val outcome = deviceController.launchInstalledApp(selectedApp)
                val res = when (outcome) {
                    is AppLaunchOutcome.Launched -> {
                        val speech = localized(
                            text,
                            "${outcome.appLabel} खोल रही हूँ।",
                            "${outcome.appLabel} open kar rahi hoon.",
                            "Opening ${outcome.appLabel}."
                        )
                        IntentExecutionResult("OPEN_APP", mapOf("app" to outcome.appLabel, "package" to outcome.packageName), speech, true, "Opened ${outcome.appLabel}")
                    }
                    is AppLaunchOutcome.Failed -> IntentExecutionResult("OPEN_APP", emptyMap(), outcome.reason, false, "Open App Failed")
                    else -> IntentExecutionResult("OPEN_APP", emptyMap(), "${selectedApp.label} is not available.", false, "Open App Failed")
                }
                recordLastIntent(res)
                return res
            } else if (matchesAny(normalizedExact, listOf("cancel", "none", "rehne do", "mat kholo", "neither"))) {
                pendingAppCandidates = emptyList()
                val speech = localized(text, "ठीक है, रद्द कर दिया।", "Theek hai, cancel kar diya.", "Okay, cancelled.")
                val res = IntentExecutionResult("OPEN_APP_CANCELLED", emptyMap(), speech, true, "Cancelled app selection")
                recordLastIntent(res)
                return res
            } else {
                pendingAppCandidates = emptyList()
            }
        }

        // Check user-defined voice shortcuts first
        getVoiceShortcuts?.invoke()?.firstOrNull { it.isEnabled && normalizedExact == it.triggerPhrase.lowercase(Locale.ROOT).trim() }?.let { shortcut ->
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
                return parseAndExecute(shortcut.commandText)
            }
        }

        // 1. Stop / Quiet command
        if (isStopCommand(text)) {
            val res = IntentExecutionResult(
                intentName = "STOP",
                feedbackSpeech = localized(
                    text,
                    hindi = "ठीक है, रुक गई।",
                    hinglish = "Theek hai, ruk gayi.",
                    english = "Stopped."
                ),
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

        // 4. Voice Gender Selection (Female / Male voice)
        if (text.contains("female voice") || text.contains("female awaz") || text.contains("female aawaz") ||
            text.contains("ladki ki aawaz") || text.contains("ladki ki awaz") || text.contains("aurat ki aawaz") ||
            text == "female voice karo" || text == "female voice lagao" || text == "female voice add karo" ||
            text == "switch to female voice" || text == "change voice to female") {
            val aName = assistantName()
            val res = onVoiceGenderTrigger?.invoke(VoiceGender.FEMALE) ?: IntentExecutionResult(
                intentName = "SET_VOICE_GENDER",
                parameters = mapOf("gender" to "FEMALE"),
                feedbackSpeech = "Maine voice ko Female $aName voice par set kar diya hai. Kaise madad kar sakti hoon?",
                success = true,
                actionSummary = "Changed voice to Female ($aName)"
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
                if (lastExecutedIntent == "BLUETOOTH_ON") {
                    val res = buildBluetoothResult(text, enable = false)
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
                if (lastExecutedIntent == "BLUETOOTH_OFF") {
                    val res = buildBluetoothResult(text, enable = true)
                    recordLastIntent(res)
                    return res
                }
            }
        }

        // 5. Conversational Greetings & Siri Personality (Hindi, Hinglish & English)
        // Repeat last response
        if (matchesAny(normalizedExact, listOf("repeat that", "say that again", "what did you say", "fir se bolo", "phir se bolo", "phir se batao", "fir se batao", "dobara bolo"))) {
            val spoken = getLastAssistantSpeech?.invoke()?.takeIf { it.isNotBlank() }
                ?: lastExecutedIntent?.let {
                    localized(
                        text,
                        hindi = "पिछला काम $it था।",
                        hinglish = "Last action $it tha.",
                        english = "Last action was $it."
                    )
                }
                ?: localized(
                    text,
                    hindi = "मैंने इस सेशन में अभी तक कुछ नहीं कहा है।",
                    hinglish = "Maine is session mein abhi tak kuch nahi kaha hai.",
                    english = "I haven't said anything yet in this session."
                )
            val res = IntentExecutionResult("REPEAT", emptyMap(), spoken, true, "Repeated previous response")
            recordLastIntent(res)
            return res
        }

        // Zero divided by zero classic Siri easter egg (supports "0 / 0", "what is 0 / 0?", etc.)
        if (normalizedExact in setOf(
                "what is zero divided by zero",
                "what is 0 divided by 0",
                "0 divided by 0",
                "zero divided by zero",
                "zero divide by zero",
                "0 divide by 0",
                "what is 0 / 0",
                "what is 0/0",
                "0 / 0",
                "0/0",
                "shunya ko shunya se divide karo",
                "zero ko zero se divide karo"
            )
        ) {
            val reply = localized(
                text,
                hindi = "सोचिए आपके पास शून्य कुकीज़ हैं और आप उन्हें शून्य दोस्तों में बराबर बाँटते हैं। हर किसी को कितनी कुकीज़ मिलेंगी? बात समझ नहीं आती ना! और कुकी मॉन्स्टर उदास है क्योंकि कोई कुकी नहीं है, और आप उदास हैं क्योंकि कोई दोस्त नहीं है।",
                hinglish = "Sochiye aapke paas zero cookies hain aur aap unhe zero dosto mein barabar baant'te hain. Har kisi ko kitni cookies milengi? Sense nahi banta na! Cookie Monster udaas hai kyunki cookies nahi hain, aur aap udaas hain kyunki dost nahi hain.",
                english = "Imagine that you have zero cookies and you split them evenly among zero friends. How many cookies does each person get? See? It doesn't make sense. And Cookie Monster is sad that there are no cookies, and your friends are sad because they don't exist."
            )
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
            val res = IntentExecutionResult(
                intentName = "JOKE",
                feedbackSpeech = reply,
                success = true,
                actionSummary = "Told a joke",
                emotion = AssistantEmotion.HAPPY
            )
            recordLastIntent(res)
            return res
        }

        // How are you?
        if (matchesAny(text, listOf("kya haal hai", "kaise ho", "siri kaise ho", "aap kaise ho", "how are you", "how are you doing", "sab kaisa hai", "how's it going", "what's up"))) {
            val userTag = spokenUserTag()
            val reply = if (isHindiMode(text)) {
                pickVaried("how_hi", listOf(
                    "मैं बिल्कुल ठीक हूँ$userTag, पूछने के लिए धन्यवाद! बताइए, आज मैं आपकी क्या मदद कर सकती हूँ?",
                    "बहुत बढ़िया हूँ$userTag! उम्मीद है आपका दिन भी अच्छा जा रहा होगा। बोलिए क्या काम है?",
                    "एकदम मज़े में हूँ$userTag! बताइए आज किस चीज़ में आपका साथ दूँ?"
                ))
            } else if (text.contains("kaise") || text.contains("kya haal") || text.contains("sab kaisa") || isHinglishMode(text)) {
                pickVaried("how_hg", listOf(
                    "Main badhiya hoon$userTag, shukriya! Aap bataiye, aaj main aapki kya madad kar sakti hoon?",
                    "Main badhiya hoon$userTag! Umeed hai aap bhi ache honge. Boliye aaj kya karna hai?",
                    "Main badhiya hoon$userTag, bas aapki help ke liye ready! Koi kaam ho toh batayein."
                ))
            } else {
                pickVaried("how_en", listOf(
                    "I'm doing great$userTag, thanks for asking! What can I help you tackle today?",
                    "Feeling energized and ready to help$userTag! How's your day going?",
                    "Doing really well$userTag! What's on your mind right now?"
                ))
            }
            val res = IntentExecutionResult(
                intentName = "GREETING",
                feedbackSpeech = reply,
                success = true,
                actionSummary = "Conversational Greeting",
                emotion = AssistantEmotion.HAPPY
            )
            recordLastIntent(res)
            return res
        }

        // Identity & Who are you?
        val aName = assistantName()
        val uAddr = userAddress()
        val userTag = spokenUserTag()
        if (matchesAny(text, listOf(
                "who are you", "what is your name", "what's your name", "tumhara naam kya hai",
                "aapka naam kya hai", "tum kaun ho", "who made you", "tumhe kisne banaya",
                "are you siri", "are you real siri", "are you ${aName.lowercase(Locale.ROOT)}"
            ))) {
            val reply = if (isHindiMode(text)) {
                pickVaried("id_hi", listOf(
                    "मैं $aName हूँ$userTag, आपकी स्मार्ट पर्सनल वॉइस असिस्टेंट। मैं बातचीत करने, ऐप्स खोलने और आपके रोज़मर्रा के काम आसान बनाने के लिए हमेशा तैयार हूँ।",
                    "मेरा नाम $aName है$userTag! मैं आपकी पर्सनल AI साथी हूँ—चाहे कोई ऐप खोलना हो या कोई सवाल पूछना हो, बस बोल दीजिए।"
                ))
            } else if (text.contains("tum") || text.contains("aapka") || text.contains("kisne") || isHinglishMode(text)) {
                pickVaried("id_hg", listOf(
                    "Main $aName hoon$userTag, aapki personal AI voice assistant. Main aapse natural baat kar sakti hoon aur aapke phone ke kaam aasan bana sakti hoon.",
                    "Mera naam $aName hai$userTag! Main aapki smart voice companion hoon, apps kholne se lekar sawaalon ke jawab dene tak sab mein madad karne ke liye."
                ))
            } else {
                pickVaried("id_en", listOf(
                    "I'm $aName$userTag—your personal AI voice assistant, here to chat naturally and help you get things done calmly and quickly.",
                    "I'm $aName$userTag, your voice companion on Android. Ask me to open apps, search, control settings, or just talk things through."
                ))
            }
            val res = IntentExecutionResult(
                intentName = "IDENTITY",
                parameters = mapOf("assistantName" to aName),
                feedbackSpeech = reply,
                success = true,
                actionSummary = "Identity Response",
                emotion = AssistantEmotion.HAPPY
            )
            recordLastIntent(res)
            return res
        }

        // User Identity ("what is my name?", "how do you call me?")
        if (matchesAny(text, listOf(
                "what is my name", "what's my name", "who am i", "what do you call me",
                "how do you address me", "mera naam kya hai", "tum mujhe kya bulati ho", "mujhe kya bulaoge"
            ))) {
            val reply = localized(
                text,
                hindi = if (uAddr.equals("Friend", ignoreCase = true)) "आपने अभी कोई खास नाम सेट नहीं किया है, इसलिए मैं आपको दोस्त मानती हूँ। आप सेटिंग्स में अपनी पसंद का संबोधन चुन सकते हैं।" else "मैं आपको $uAddr कहकर बुलाती हूँ। आप चाहें तो सेटिंग्स से इसे कभी भी बदल सकते हैं।",
                hinglish = if (uAddr.equals("Friend", ignoreCase = true)) "Abhi koi custom naam set nahi hai, toh main aapko Friend bulati hoon. Aap Settings mein apna pasandida naam save kar sakte hain." else "Main aapko $uAddr bulati hoon! Aap Settings se jab chahein ise change kar sakte hain.",
                english = if (uAddr.equals("Friend", ignoreCase = true)) "You haven't set a custom title yet, so I just call you Friend. You can customize how I address you anytime in Settings." else "You asked me to call you $uAddr! You can update that anytime in Settings."
            )
            val res = IntentExecutionResult("USER_IDENTITY", mapOf("userAddress" to uAddr), reply, true, "User Addressing", emotion = AssistantEmotion.HAPPY)
            recordLastIntent(res)
            return res
        }

        // Capabilities & Help
        if (matchesAny(text, listOf(
            "what can you do", "help me", "what are your capabilities", "tum kya kar sakti ho",
            "kya kar sakti ho", "madad karo", "tumhari capabilities kya hain"
        ))) {
            val reply = if (isHindiMode(text)) {
                "मैं आपकी फ्लैशलाइट, ब्लूटूथ या ब्राइटनेस कंट्रोल कर सकती हूँ, अलार्म और टाइमर लगा सकती हूँ, ऐप्स खोलकर सर्च कर सकती हूँ, और आपके हर सवाल का जवाब दे सकती हूँ।"
            } else if (text.contains("tum") || text.contains("madad") || text.contains("kya kar") || isHinglishMode(text)) {
                "Main flashlight aur system settings control kar sakti hoon, apps ke andar search kar sakti hoon, alarms aur notes bana sakti hoon, aur aapse Hindi, Hinglish ya English mein baat kar sakti hoon. Boliye kya karna hai$userTag?"
            } else {
                "I can toggle flashlight and system controls, launch or search inside your apps, set alarms and notes, and have natural multi-turn conversations. What can I help with$userTag?"
            }
            val res = IntentExecutionResult("CAPABILITIES", emptyMap(), reply, true, "Assistance Capabilities", emotion = AssistantEmotion.EXCITED)
            recordLastIntent(res)
            return res
        }

        // Playful & Philosophical Easter Eggs
        if (matchesAny(text, listOf("do you love me", "i love you", "will you marry me", "mujhse shaadi karogi", "mujhse dosti karoge", "kya tum mujhse pyar karti ho"))) {
            val reply = if (isHindiMode(text)) {
                "आप बहुत अच्छे हैं$userTag, लेकिन मेरे पास दिल के बजाय केवल एल्गोरिदम हैं! हम हमेशा अच्छे साथी ज़रूर रहेंगे।"
            } else if (text.contains("mujhse") || text.contains("pyar") || text.contains("shaadi") || isHinglishMode(text)) {
                "Aap bahut ache hain$userTag, par mere paas dil ke bajaye algorithms hain! Hum hamesha badiya dost zaroor rahenge."
            } else {
                "That's sweet of you$userTag, though my heart runs purely on code. I'm always happy to be your loyal sidekick!"
            }
            val res = IntentExecutionResult("PLAYFUL", emptyMap(), reply, true, "Playful Response", emotion = AssistantEmotion.SURPRISED)
            recordLastIntent(res)
            return res
        }

        if (matchesAny(text, listOf("what is the meaning of life", "meaning of life", "zindagi ka maksad kya hai", "jeevan ka arth kya hai"))) {
            val reply = if (isHindiMode(text)) {
                "ज़िंदगी का असली मतलब खुश रहना, अपनों का साथ निभाना और हर दिन कुछ नया सीखना है।"
            } else {
                "Some say it's 42, though I'd bet on curiosity, good company, and a really great cup of chai."
            }
            val res = IntentExecutionResult("PHILOSOPHICAL", emptyMap(), reply, true, "Meaning of Life", emotion = AssistantEmotion.CURIOUS)
            recordLastIntent(res)
            return res
        }

        if (matchesAny(text, listOf("are you smart", "are you intelligent", "kya tum intelligent ho", "kya tum smart ho"))) {
            val reply = if (isHindiMode(text)) {
                "मैं आपके काम आसान बनाने जितनी समझदार हूँ$userTag, और हर बातचीत से कुछ नया सीखने की कोशिश करती हूँ।"
            } else {
                "Smart enough to keep your day running smoothly$userTag, and humble enough to look things up whenever you surprise me."
            }
            val res = IntentExecutionResult("PLAYFUL", emptyMap(), reply, true, "Intelligence Response", emotion = AssistantEmotion.HAPPY)
            recordLastIntent(res)
            return res
        }

        // Standard Greetings
        if (matchesAny(text, listOf("namaste", "hello", "hi", "hey", "good morning", "good evening", "good afternoon", "shubh prabhat", "shubh ratri", "good night"))) {
            val isNight = text.contains("good night") || text.contains("shubh ratri")
            val reply = when {
                text.contains("good morning") || text.contains("shubh prabhat") -> {
                    if (isHindiMode(text)) pickVaried("gm_hi", listOf(
                        "शुभ प्रभात$userTag! आज का दिन आपके लिए शानदार और सफल हो।",
                        "सुप्रभात$userTag! बताइए आज की सुबह कैसे शुरू करें?"
                    ))
                    else if (isHinglishMode(text)) pickVaried("gm_hg", listOf(
                        "Good morning$userTag! Aapka din shandaar rahe. Aaj kya plan hai?",
                        "Subah bakhair$userTag! Naya din aur nayi energy—batayein kya help karoon?"
                    ))
                    else pickVaried("gm_en", listOf(
                        "Good morning$userTag! Hope you're feeling refreshed. How can I kick off your day?",
                        "Morning$userTag! Ready when you are—what's first on your list today?"
                    ))
                }
                isNight -> {
                    if (isHindiMode(text)) "शुभ रात्रि$userTag! आराम से सोइए, सुबह मैं यहीं मिलूँगी।"
                    else if (isHinglishMode(text)) "Good night$userTag! Aaram se soiye, subah jab zaroorat ho bas awaz de dijiyega."
                    else "Good night$userTag! Sleep well, and just call $aName whenever you need me tomorrow."
                }
                text.contains("namaste") || isHindiMode(text) -> {
                    if (isHindiMode(text)) pickVaried("hi_nam", listOf(
                        "नमस्ते$userTag! मैं $aName हूँ, बताइए आपकी क्या सहायता कर सकती हूँ?",
                        "नमस्ते$userTag! कहिए, आज मैं आपके लिए क्या कर सकती हूँ?"
                    ))
                    else pickVaried("hg_nam", listOf(
                        "Namaste$userTag! Main $aName hoon, aaj main aapki kya madad kar sakti hoon?",
                        "Namaste$userTag! Boliye, kya haal chaal aur kya kaam hai?"
                    ))
                }
                isHinglishMode(text) -> pickVaried("hg_hi", listOf(
                    "Hello$userTag! Main $aName hoon. Boliye, aaj main aapki kya help kar sakti hoon?",
                    "Hi$userTag! Main $aName hoon—batayein aaj kis cheez mein madad chahiye?"
                ))
                else -> pickVaried("en_hi", listOf(
                    "Hey$userTag! I'm $aName, always happy to help. What's on your mind?",
                    "Hi there$userTag! Good to hear from you—what can we get done?",
                    "Hello$userTag! Tell me what you need and I'm on it."
                ))
            }
            val res = IntentExecutionResult(
                intentName = "GREETING",
                feedbackSpeech = reply,
                success = true,
                actionSummary = "Greeting",
                emotion = if (isNight) AssistantEmotion.NEUTRAL else AssistantEmotion.HAPPY
            )
            recordLastIntent(res)
            return res
        }

        // Courtesy
        if (matchesAny(text, listOf("thank you", "thanks", "dhanyawad", "shukriya", "bahut accha", "you are great", "tum bohot achi ho"))) {
            val reply = if (isHindiMode(text)) {
                pickVaried("thx_hi", listOf(
                    "आपका बहुत स्वागत है$userTag! जब भी ज़रूरत हो, बस बुला लीजिएगा।",
                    "कोई बात नहीं$userTag! आपकी मदद करके हमेशा खुशी होती है।"
                ))
            } else if (text.contains("dhanyawad") || text.contains("shukriya") || text.contains("achi") || text.contains("accha") || isHinglishMode(text)) {
                pickVaried("thx_hg", listOf(
                    "Aapka swagat hai$userTag! Jab bhi madad chahiye, bas $aName ko awaz de dijiyega.",
                    "Arre koi baat nahi$userTag! Hamesha hazir hoon aapki help ke liye."
                ))
            } else {
                pickVaried("thx_en", listOf(
                    "You're very welcome$userTag! Anytime you need a hand, I'm right here.",
                    "Happy to help$userTag! Just give me a shout whenever you need something."
                ))
            }
            val res = IntentExecutionResult("COURTESY", emptyMap(), reply, true, "Courtesy Response", emotion = AssistantEmotion.HAPPY)
            recordLastIntent(res)
            return res
        }

        // 6. Flashlight / Torch
        if (matchesAny(text, listOf("turn on flashlight", "turn on the flashlight", "turn on torch", "torch on", "flashlight on", "batti jalao", "torch jalao", "light on karo", "flashlight chalu karo", "torch chalu karo", "torch light on karo"))) {
            val (ok, msg) = deviceController.setFlashlight(true)
            val speech = if (isHindiMode(text)) {
                if (ok) "फ्लैशलाइट चालू कर दी गई है।" else "फ्लैशलाइट चालू नहीं हो सकी।"
            } else if (isHinglishMode(text) || text.contains("chalu") || text.contains("jalao") || text.contains("karo")) {
                if (ok) "Flashlight on kar di gayi hai." else "Flashlight on nahi ho saki."
            } else msg
            val res = IntentExecutionResult("FLASHLIGHT_ON", emptyMap(), speech, ok, "Turned on Flashlight", isReversible = true, undoAction = "FLASHLIGHT_OFF")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("turn off flashlight", "turn off the flashlight", "turn off torch", "torch off", "flashlight off", "batti band karo", "torch band karo", "light off karo", "flashlight band kar do", "torch bujhao", "batti bujhao"))) {
            val (ok, msg) = deviceController.setFlashlight(false)
            val speech = if (isHindiMode(text)) {
                if (ok) "फ्लैशलाइट बंद कर दी गई है।" else "फ्लैशलाइट बंद नहीं हो सकी।"
            } else if (isHinglishMode(text) || text.contains("band") || text.contains("bujhao")) {
                if (ok) "Flashlight off kar di gayi hai." else "Flashlight off nahi ho saki."
            } else msg
            val res = IntentExecutionResult("FLASHLIGHT_OFF", emptyMap(), speech, ok, "Turned off Flashlight", isReversible = true, undoAction = "FLASHLIGHT_ON")
            recordLastIntent(res)
            return res
        }

        // 7. Battery status
        if (matchesAny(text, listOf("battery", "battery status", "battery percentage", "how much battery", "check battery", "battery kitni hai", "charge kitna hai", "battery charge", "kitna charge bacha hai"))) {
            val rawInfo = deviceController.getBatteryInfo()
            val speech = if (isHindiMode(text)) {
                rawInfo.replace("Your battery is at ", "आपकी बैटरी ")
                    .replace(" and is currently charging.", "% है और फोन चार्ज हो रहा है।")
                    .replace(".", "% है।")
            } else if (isHinglishMode(text) || text.contains("kitni") || text.contains("kitna")) {
                rawInfo.replace("Your battery is at ", "Aapki battery ")
                    .replace(" and is currently charging.", "% hai aur charging chal rahi hai.")
                    .replace(".", "% hai.")
            } else {
                rawInfo
            }
            val res = IntentExecutionResult("BATTERY_STATUS", emptyMap(), speech, true, "Checked Battery Status")
            recordLastIntent(res)
            return res
        }

        // 8. Network status
        if (matchesAny(normalizedExact, listOf("network", "network status", "connectivity", "internet status", "am i online", "internet chal raha hai", "wifi status", "wifi kaisa hai", "network check karo"))) {
            val info = deviceController.getNetworkInfo()
            val speech = if (isHindiMode(text)) {
                when {
                    info.contains("Wi-Fi", ignoreCase = true) -> "आप वाई-फ़ाई से कनेक्टेड हैं और इंटरनेट चल रहा है।"
                    info.contains("Mobile Data", ignoreCase = true) -> "आप मोबाइल डेटा से कनेक्टेड हैं और ऑनलाइन हैं।"
                    info.contains("active", ignoreCase = true) -> "इंटरनेट कनेक्शन चालू है।"
                    else -> "अभी कोई सक्रिय इंटरनेट कनेक्शन नहीं मिला।"
                }
            } else if (isHinglishMode(text)) {
                when {
                    info.contains("Wi-Fi", ignoreCase = true) -> "Aap Wi-Fi se connected hain aur internet chal raha hai."
                    info.contains("Mobile Data", ignoreCase = true) -> "Aap Mobile Data se connected hain aur online hain."
                    info.contains("active", ignoreCase = true) -> "Internet connection active hai."
                    else -> "Abhi koi active internet connection nahi hai."
                }
            } else info
            val res = IntentExecutionResult("NETWORK_STATUS", emptyMap(), speech, true, "Checked Network Connectivity")
            recordLastIntent(res)
            return res
        }

        // 9. Time & Date
        if (matchesAny(normalizedExact, listOf("what time is it", "what's the time", "current time", "samay kya hai", "time kya hua hai", "time batao", "kitne baje hain"))) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val speech = localized(
                text,
                hindi = "अभी समय $time हुआ है।",
                hinglish = "Abhi samay $time ho raha hai.",
                english = "It is $time."
            )
            val res = IntentExecutionResult("TIME", emptyMap(), speech, true, "Checked Time")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("what is today's date", "what's the date", "today's date", "aaj konsi tarikh hai", "aaj ki date kya hai", "current date", "aaj konsa din hai"))) {
            val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            val speech = localized(
                text,
                hindi = "आज की तारीख $date है।",
                hinglish = "Aaj ki date $date hai.",
                english = "Today is $date."
            )
            val res = IntentExecutionResult("DATE", emptyMap(), speech, true, "Checked Date")
            recordLastIntent(res)
            return res
        }

        // 10. Volume controls
        if (matchesAny(normalizedExact, listOf("volume up", "increase volume", "raise volume", "louder", "awaz badhao", "awaz tez karo"))) {
            val (ok, msg) = deviceController.adjustVolume("up")
            val speech = if (ok) localized(text, "वॉल्यूम बढ़ा दिया गया है।", "Volume badha diya gaya hai.", msg) else msg
            val res = IntentExecutionResult("VOLUME_UP", emptyMap(), speech, ok, "Raised Volume")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("volume down", "decrease volume", "lower volume", "awaz kam karo", "awaz dheere karo"))) {
            val (ok, msg) = deviceController.adjustVolume("down")
            val speech = if (ok) localized(text, "वॉल्यूम कम कर दिया गया है।", "Volume kam kar diya gaya hai.", msg) else msg
            val res = IntentExecutionResult("VOLUME_DOWN", emptyMap(), speech, ok, "Lowered Volume")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("mute", "mute audio", "mute music", "awaz band karo", "phone silent karo"))) {
            val (ok, msg) = deviceController.adjustVolume("mute")
            val speech = if (ok) localized(text, "ऑडियो म्यूट कर दिया गया है।", "Audio mute kar diya gaya hai.", msg) else msg
            val res = IntentExecutionResult("VOLUME_MUTE", emptyMap(), speech, ok, "Muted Volume")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("unmute", "unmute audio", "awaz chalu karo"))) {
            val (ok, msg) = deviceController.adjustVolume("unmute")
            val speech = if (ok) localized(text, "ऑडियो अनम्यूट कर दिया गया है।", "Audio unmute kar diya gaya hai.", msg) else msg
            val res = IntentExecutionResult("VOLUME_UNMUTE", emptyMap(), speech, ok, "Unmuted Volume")
            recordLastIntent(res)
            return res
        }

        // 11. Media playback controls (Play, Pause, Next, Previous)
        if (matchesAny(normalizedExact, listOf("pause music", "pause song", "stop music playback", "music roko", "gaana roko", "song band karo"))) {
            val (ok, _) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            val speech = localized(text, "म्यूज़िक पॉज़ कर दिया गया है।", "Music pause kar diya hai.", "Paused media playback.")
            val res = IntentExecutionResult("MEDIA_PAUSE", emptyMap(), speech, ok, "Paused music")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("resume music", "resume song", "play music", "chalu karo music", "gaana chalao", "gaana bajao", "play song", "music chalu karo"))) {
            val (ok, _) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
            val speech = localized(text, "म्यूज़िक दोबारा शुरू कर रही हूँ।", "Music play kar rahi hoon.", "Resuming playback.")
            val res = IntentExecutionResult("MEDIA_PLAY", emptyMap(), speech, ok, "Resumed music")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("next song", "next track", "skip song", "agla gaana", "next gaana"))) {
            val (ok, _) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            val speech = localized(text, "अगला गाना चला रही हूँ।", "Next song play kar rahi hoon.", "Skipping to next track.")
            val res = IntentExecutionResult("MEDIA_NEXT", emptyMap(), speech, ok, "Skipped track")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("previous song", "previous track", "pichla gaana"))) {
            val (ok, _) = deviceController.sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            val speech = localized(text, "पिछला गाना चला रही हूँ।", "Previous song play kar rahi hoon.", "Playing previous track.")
            val res = IntentExecutionResult("MEDIA_PREV", emptyMap(), speech, ok, "Previous track")
            recordLastIntent(res)
            return res
        }

        // 12. System Controls (Bluetooth, Brightness, Hotspot/Wi-Fi/Location, Restricted actions)
        if (matchesAny(normalizedExact, listOf(
                "turn on bluetooth", "switch on bluetooth", "enable bluetooth", "bluetooth on",
                "bluetooth on karo", "bluetooth chalu karo", "ब्लूटूथ चालू करो", "ब्लूटूथ ऑन करो"
            ))
        ) {
            val res = buildBluetoothResult(text, enable = true)
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf(
                "turn off bluetooth", "switch off bluetooth", "disable bluetooth", "bluetooth off",
                "bluetooth off karo", "bluetooth band karo", "ब्लूटूथ बंद करो", "ब्लूटूथ ऑफ करो"
            ))
        ) {
            val res = buildBluetoothResult(text, enable = false)
            recordLastIntent(res)
            return res
        }

        // Brightness controls
        val brightnessPercentMatch = Regex("""^(?:please\s+|can\s+you\s+)?(?:set\s+)?brightness\s*(?:to\s+|at\s+)?(\d{1,3})\s*(?:percent|%)?(?:\s*karo)?$""").find(normalizedExact)
        if (brightnessPercentMatch != null) {
            val pct = brightnessPercentMatch.groupValues[1].toIntOrNull()?.coerceIn(5, 100) ?: 50
            val res = buildBrightnessResult(text, "set", pct)
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("increase brightness", "brightness up", "raise brightness", "more brightness", "brightness badhao", "screen brightness badhao", "रोशनी बढ़ाओ"))) {
            val res = buildBrightnessResult(text, "up")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("decrease brightness", "brightness down", "lower brightness", "dim screen", "reduce brightness", "brightness kam karo", "screen brightness kam karo", "रोशनी कम करो"))) {
            val res = buildBrightnessResult(text, "down")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("max brightness", "maximum brightness", "full brightness", "brightness full karo"))) {
            val res = buildBrightnessResult(text, "max")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("min brightness", "minimum brightness", "lowest brightness", "brightness minimum karo"))) {
            val res = buildBrightnessResult(text, "min")
            recordLastIntent(res)
            return res
        }
        if (matchesAny(normalizedExact, listOf("auto brightness", "adaptive brightness", "turn on auto brightness", "auto brightness on", "auto brightness karo"))) {
            val res = buildBrightnessResult(text, "auto")
            recordLastIntent(res)
            return res
        }

        if (matchesAny(text, listOf("turn on airplane mode", "turn off airplane mode", "airplane mode on", "flight mode on", "airplane mode off", "airplane mode chalu karo"))) {
            val (ok, _) = deviceController.openAirplaneModeSettings()
            val speech = localized(
                text,
                "एंड्रॉइड सुरक्षा नियम ऐप्स को सीधे एयरप्लेन मोड बदलने की अनुमति नहीं देते। मैंने एयरप्लेन मोड सेटिंग्स खोल दी हैं।",
                "Android security ki wajah se apps direct Airplane mode toggle nahi kar sakte. Maine Airplane mode settings khol di hain.",
                "Android restricts third-party apps from toggling Airplane mode directly. I opened Airplane Mode Settings for you."
            )
            val res = IntentExecutionResult(
                intentName = "OPEN_SETTINGS",
                parameters = mapOf("target" to "airplane_mode"),
                feedbackSpeech = speech,
                success = ok,
                actionSummary = "Opened Airplane Mode Settings"
            )
            recordLastIntent(res)
            return res
        }
        if (matchesAny(text, listOf("restart phone", "reboot phone", "shut down phone", "power off phone"))) {
            return IntentExecutionResult(
                intentName = "UNSUPPORTED_RESTRICTION",
                feedbackSpeech = "Restarting or shutting down the device requires system-level OS privileges. Please press and hold your phone's Power button.",
                success = false,
                actionSummary = "System security restriction"
            )
        }

        // 13. All 14 Android System Settings Shortcuts
        val settingsMatch = matchSystemSettingsShortcut(normalizedExact, text)
        if (settingsMatch != null) {
            recordLastIntent(settingsMatch)
            return settingsMatch
        }

        // 14. Alarms & Timers (Anchored to avoid mid-sentence false triggers)
        val enTimerMatch = Regex("""^(?:please\s+|can\s+you\s+)?(?:set|start|run)?\s*(?:a\s+)?timer\s*(?:for\s+)?(\d+)\s*(minute|minutes|min|second|seconds|sec)?$""").find(normalizedExact)
        val hiTimerMatch = Regex("""^(\d+)\s*(minute|minutes|min|second|seconds|sec)\s*(?:ka|ki)\s*timer$""").find(normalizedExact)
        val matchedTimer = enTimerMatch ?: hiTimerMatch
        if (matchedTimer != null) {
            val amount = matchedTimer.groupValues[1].toIntOrNull() ?: 0
            val unit = matchedTimer.groupValues.getOrNull(2)?.ifBlank { "minute" } ?: "minute"
            if (amount <= 0) {
                val errSpeech = localized(
                    text,
                    hindi = "कृपया सही समय बताएं, जैसे 5 मिनट का टाइमर।",
                    hinglish = "Please sahi duration batayein, jaise 5 minute ka timer.",
                    english = "Please specify a valid positive timer duration."
                )
                val res = IntentExecutionResult("SET_TIMER", emptyMap(), errSpeech, false, "Invalid timer duration")
                recordLastIntent(res)
                return res
            }
            val totalSeconds = if (unit.startsWith("sec")) amount else amount * 60
            val (ok, msg) = deviceController.setTimer(totalSeconds, "${assistantName()} Timer")
            val speech = if (ok) {
                val unitLabelHi = if (unit.startsWith("sec")) "सेकंड" else "मिनट"
                localized(
                    text,
                    hindi = "$amount $unitLabelHi का टाइमर सेट कर दिया गया है।",
                    hinglish = "$amount $unit ka timer set kar diya gaya hai.",
                    english = msg
                )
            } else msg
            val res = IntentExecutionResult("SET_TIMER", mapOf("seconds" to totalSeconds.toString()), speech, ok, "Set $amount $unit timer")
            recordLastIntent(res)
            return res
        }

        val enAlarmMatch = Regex("""^(?:please\s+|can\s+you\s+)?(?:set|start)?\s*(?:an\s+)?alarm\s*(?:for|at)?\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""").find(normalizedExact)
        val hiAlarmMatch = Regex("""^(?:(subah|shaam|raat|dopahar)\s+)?(\d{1,2})(?::(\d{2}))?\s*baje\s*(?:ka|ki)?\s*alarm(?:\s*(?:set\s*karo|lagao))?$""").find(normalizedExact)
        if (enAlarmMatch != null || hiAlarmMatch != null) {
            var hour: Int
            var min: Int
            if (enAlarmMatch != null) {
                hour = enAlarmMatch.groupValues[1].toIntOrNull() ?: -1
                min = enAlarmMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                val period = enAlarmMatch.groupValues.getOrNull(3)?.lowercase(Locale.ROOT)
                if (period == "pm" && hour in 1..11) hour += 12
                if (period == "am" && hour == 12) hour = 0
            } else {
                val tod = hiAlarmMatch!!.groupValues[1].lowercase(Locale.ROOT)
                hour = hiAlarmMatch.groupValues[2].toIntOrNull() ?: -1
                min = hiAlarmMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                if ((tod == "shaam" || tod == "raat" || tod == "dopahar") && hour in 1..11) hour += 12
                if (tod == "subah" && hour == 12) hour = 0
            }
            if (hour !in 0..23 || min !in 0..59) {
                val errSpeech = localized(
                    text,
                    hindi = "अलार्म के लिए सही समय बताएं, जैसे सुबह 7 बजे या 21:30।",
                    hinglish = "Alarm ke liye valid time batayein, jaise 7:30 AM.",
                    english = "Please specify a valid alarm time."
                )
                val res = IntentExecutionResult("SET_ALARM", emptyMap(), errSpeech, false, "Invalid alarm time")
                recordLastIntent(res)
                return res
            }
            val (ok, msg) = deviceController.setAlarm(hour, min, "Siri Alarm")
            val timeFormatted = String.format(Locale.US, "%02d:%02d", hour, min)
            val speech = if (ok) {
                localized(
                    text,
                    hindi = "$timeFormatted के लिए अलार्म सेट कर दिया गया है।",
                    hinglish = "$timeFormatted ke liye alarm set kar diya gaya hai.",
                    english = msg
                )
            } else msg
            val res = IntentExecutionResult("SET_ALARM", mapOf("hour" to hour.toString(), "min" to min.toString()), speech, ok, "Set alarm for $timeFormatted")
            recordLastIntent(res)
            return res
        }

        // 15. Phone Calling / Dialing
        val dialMatch = Regex("""^(?:please\s+|can\s+you\s+)?(?:call|dial|phone)\s+([0-9+\s-]{3,15})$""").find(normalizedExact)
        if (dialMatch != null) {
            val number = dialMatch.groupValues[1].trim()
            val (ok, msg) = deviceController.dialPhoneNumber(number)
            val speech = if (ok) {
                localized(text, "$number डायल कर रही हूँ।", "$number dial kar rahi hoon.", msg)
            } else msg
            val res = IntentExecutionResult("DIAL_PHONE", mapOf("number" to number), speech, ok, "Dialing $number")
            recordLastIntent(res)
            return res
        }

        // 16. SMS Composer (preserve original message casing)
        val smsMatch = Regex("""^(?:please\s+|can\s+you\s+)?(?:send\s+sms|text|message)\s+(?:to\s+)?([0-9+\s-]{3,15})\s+(?:message\s+|saying\s+)?(.+)$""", RegexOption.IGNORE_CASE).find(originalText)
        if (smsMatch != null) {
            val number = smsMatch.groupValues[1].trim()
            val messageBody = smsMatch.groupValues[2].trim()
            val (ok, msg) = deviceController.composeSms(number, messageBody)
            val speech = if (ok) {
                localized(text, "$number के लिए मैसेज तैयार है।", "$number ke liye SMS ready hai.", msg)
            } else msg
            val res = IntentExecutionResult("SEND_SMS", mapOf("number" to number, "body" to messageBody), speech, ok, "Prepared SMS to $number")
            recordLastIntent(res)
            return res
        }

        // 17. Turn-by-Turn Map Navigation (preserve original destination casing)
        val navMatch = Regex("""^(?:please\s+|can\s+you\s+)?(?:navigate\s+to|directions\s+to|take\s+me\s+to|route\s+to)\s+(.+)$""", RegexOption.IGNORE_CASE).find(originalText)
        if (navMatch != null) {
            val destination = navMatch.groupValues[1].trim()
            val (ok, msg) = deviceController.openNavigation(destination)
            val res = IntentExecutionResult("NAVIGATION", mapOf("destination" to destination), msg, ok, "Navigating to $destination")
            recordLastIntent(res)
            return res
        }

        // 18. Calendar Event Creation (preserve original title casing)
        val calMatch = Regex("""^(?:please\s+|can\s+you\s+)?(?:add\s+event|create\s+calendar\s+event|schedule\s+meeting)\s+(.+)$""", RegexOption.IGNORE_CASE).find(originalText)
        if (calMatch != null) {
            val eventTitle = calMatch.groupValues[1].trim()
            val (ok, msg) = deviceController.createCalendarEvent(eventTitle)
            val res = IntentExecutionResult("CALENDAR_EVENT", mapOf("title" to eventTitle), msg, ok, "Created Calendar Event: $eventTitle")
            recordLastIntent(res)
            return res
        }

        // 19. Offline Calculations
        val calcResult = evaluateMath(normalizedExact)
        if (calcResult != null) {
            val res = IntentExecutionResult("CALCULATION", mapOf("result" to calcResult), calcResult, true, "Calculated Math")
            recordLastIntent(res)
            return res
        }

        // 20. Offline Unit Conversions
        val convResult = evaluateUnitConversion(normalizedExact)
        if (convResult != null) {
            val res = IntentExecutionResult("UNIT_CONVERSION", mapOf("result" to convResult), convResult, true, "Converted Units")
            recordLastIntent(res)
            return res
        }

        // 20B. Chrome website opening & Search Inside Applications
        val inAppSearchResult = tryHandleInAppSearchAndChrome(originalText, text)
        if (inAppSearchResult != null) {
            recordLastIntent(inAppSearchResult)
            return inAppSearchResult
        }

        // 21. Smart Open Installed Application (supports prefix + Hindi/Hinglish postfix & ambiguity clarification)
        val trimmedCmd = originalText.trim().trimEnd('?', '!', '.', ' ')
        val openPrefixMatch = Regex(
            """^(?:please\s+|can\s+you\s+|could\s+you\s+|zara\s+|mujhe\s+|bhai\s+|yaar\s+|mere\s+liye\s+|कृपया\s+|ज़रा\s+)?(?:open|launch|start|run|kholo|khol\s+do|khol\s+de|open\s+karo|open\s+kardo|open\s+kar\s+do|chalu\s+karo|chalu\s+kardo|chalao|chala\s+do|खोलो|खोल\s+दो|खोल\s+दें|ओपन\s+करो|ओपन\s+कर\s+दो|चालू\s+करो|चलाओ|चला\s+दो)\s+(?:the\s+|app\s+|ऐप\s+)?([a-zA-Z\u0900-\u097F0-9\s.'&_-]+?)(?:\s+(?:app|ऐप|ko|को|please|plz))*$""",
            RegexOption.IGNORE_CASE
        ).find(trimmedCmd)
        val openPostfixMatch = Regex(
            """^(?:zara\s+|mujhe\s+|please\s+|bhai\s+|ज़रा\s+)?([a-zA-Z\u0900-\u097F0-9\s.'&_-]+?)(?:\s+(?:app|ऐप|ko|को))*?\s+(?:kholo|khol\s+do|khol\s+de|kholna\s+hai|open\s+karo|open\s+kardo|open\s+kar\s+do|open\s+karke\s+do|launch\s+karo|start\s+karo|chalu\s+karo|chalu\s+kardo|chalu\s+kar\s+do|chalao|chala\s+do|खोलो|खोल\s+दो|खोल\s+दें|खोलना\s+है|ओपन\s+करो|ओपन\s+कर\s+दो|चालू\s+करो|चलाओ|चला\s+दो)(?:\s+(?:please|plz|bhai|यार))?$""",
            RegexOption.IGNORE_CASE
        ).find(trimmedCmd)

        val appTargetRaw = (openPrefixMatch ?: openPostfixMatch)?.groupValues?.get(1)?.trim()
        if (!appTargetRaw.isNullOrBlank()) {
            val res = handleOpenAppCommand(appTargetRaw, text)
            recordLastIntent(res)
            return res
        }

        // 22. Web Search command with link grounding (preserve original casing)
        val searchPrefixes = listOf("search for ", "google ", "search ", "khojo ")
        val matchedSearchPrefix = searchPrefixes.firstOrNull { text.startsWith(it) }
        if (matchedSearchPrefix != null || text.startsWith("search today's news")) {
            val query = if (matchedSearchPrefix != null) {
                originalText.substring(matchedSearchPrefix.length).trim()
            } else {
                "today's news"
            }
            if (query.isNotEmpty()) {
                val (ok, msg) = deviceController.searchWeb(query)
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val searchLinks = listOf(
                    WebSourceLink("Google Search: $query", "https://www.google.com/search?q=$encoded", "Live web search results"),
                    WebSourceLink("Wikipedia: $query", "https://en.wikipedia.org/wiki/Special:Search?search=$encoded", "Encyclopedia search")
                )
                val speech = if (ok) {
                    localized(text, "\"$query\" वेब पर खोज रही हूँ।", "\"$query\" web par search kar rahi hoon.", msg)
                } else msg
                val res = IntentExecutionResult(
                    intentName = "WEB_SEARCH",
                    parameters = mapOf("query" to query),
                    feedbackSpeech = speech,
                    success = ok,
                    actionSummary = "Web search: $query",
                    webSources = searchLinks
                )
                recordLastIntent(res)
                return res
            }
        }

        // 23. Add Note (preserve original casing)
        val notePrefixes = listOf("create note ", "take a note ", "note: ", "note banao ")
        val matchedNotePrefix = notePrefixes.firstOrNull { text.startsWith(it) }
        if (matchedNotePrefix != null) {
            val noteBody = originalText.substring(matchedNotePrefix.length).trim()
            if (noteBody.isNotEmpty() && onNoteAddTrigger != null) {
                val res = onNoteAddTrigger.invoke("Voice Note", noteBody)
                recordLastIntent(res)
                return res
            }
        }

        // 24. Add Task / Reminder (preserve original casing)
        val taskPrefixes = listOf("remind me to ", "add task ", "note down ", "task add karo ", "soochi mein jodo ")
        val matchedTaskPrefix = taskPrefixes.firstOrNull { text.startsWith(it) }
        if (matchedTaskPrefix != null) {
            val taskTitle = originalText.substring(matchedTaskPrefix.length).trim()
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
        val clean = input.trim().trimEnd('?', '!', '.')
        return phrases.any { clean == it || clean == "$it please" || clean == "can you $it" || clean == "please $it" }
    }

    private fun evaluateMath(text: String): String? {
        val clean = text.removePrefix("calculate ").removePrefix("what is ").removePrefix("solve ").trim()
        val percentMatch = Regex("""^(\d+(?:\.\d+)?)\s*(?:percent|%)\s*(?:of)\s*(\d+(?:\.\d+)?)$""").find(clean)
        if (percentMatch != null) {
            val p = percentMatch.groupValues[1].toDoubleOrNull() ?: return null
            val total = percentMatch.groupValues[2].toDoubleOrNull() ?: return null
            val res = (p / 100.0) * total
            return "$p% of $total is ${String.format(Locale.US, "%.2f", res)}"
        }

        val opMatch = Regex("""^(\d+(?:\.\d+)?)\s*(\+|\-|\*|x|\/|plus|minus|times|multiplied by|divided by)\s*(\d+(?:\.\d+)?)$""").find(clean)
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
            val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
            return "The answer is $formatted."
        }
        return null
    }

    private fun resolvePendingAppSelection(input: String, candidates: List<InstalledAppInfo>): InstalledAppInfo? {
        val clean = input.trim().trimEnd('.', '!', '?').lowercase(Locale.ROOT)
        val index = when {
            clean in listOf("1", "first", "first one", "1st", "pehla", "pahla", "first app") -> 0
            clean in listOf("2", "second", "second one", "2nd", "dusra", "doosra", "second app") -> 1
            clean in listOf("3", "third", "third one", "3rd", "teesra", "third app") -> 2
            clean in listOf("4", "fourth", "fourth one", "4th", "chautha", "fourth app") -> 3
            else -> null
        }
        if (index != null && index in candidates.indices) {
            return candidates[index]
        }
        return candidates.firstOrNull { candidate ->
            val labelLower = candidate.label.lowercase(Locale.ROOT)
            labelLower == clean || labelLower.contains(clean) || clean.contains(labelLower)
        }
    }

    private fun buildBluetoothResult(text: String, enable: Boolean): IntentExecutionResult {
        val intentKey = if (enable) "BLUETOOTH_ON" else "BLUETOOTH_OFF"
        return when (val outcome = deviceController.setBluetoothEnabled(enable)) {
            is SystemControlOutcome.Applied -> {
                val speech = if (enable) {
                    localized(text, "ब्लूटूथ चालू कर दिया गया है।", "Bluetooth on kar diya gaya hai.", outcome.message)
                } else {
                    localized(text, "ब्लूटूथ बंद कर दिया गया है।", "Bluetooth off kar diya gaya hai.", outcome.message)
                }
                IntentExecutionResult(intentKey, mapOf("enabled" to enable.toString()), speech, true, outcome.message)
            }
            is SystemControlOutcome.OpenedSettingsFallback -> {
                val speech = localized(
                    text,
                    "एंड्रॉइड की सुरक्षा नीति के कारण मैं सीधे ब्लूटूथ नहीं बदल सकती। मैंने ब्लूटूथ सेटिंग्स खोल दी हैं।",
                    "Android ki policy ke kaaran Bluetooth direct toggle nahi ho sakta. Maine Bluetooth settings khol di hain.",
                    outcome.reason
                )
                IntentExecutionResult(intentKey, mapOf("fallback" to "bluetooth_settings"), speech, true, "Opened Bluetooth Settings")
            }
            is SystemControlOutcome.Failed -> {
                IntentExecutionResult(intentKey, emptyMap(), outcome.reason, false, "Bluetooth unavailable")
            }
        }
    }

    private fun buildBrightnessResult(text: String, action: String, percent: Int? = null): IntentExecutionResult {
        return when (val outcome = deviceController.adjustBrightness(action, percent)) {
            is SystemControlOutcome.Applied -> {
                val speech = localized(
                    text,
                    "स्क्रीन की ब्राइटनेस अपडेट कर दी गई है।",
                    "Screen brightness adjust kar di gayi hai.",
                    outcome.message
                )
                IntentExecutionResult("BRIGHTNESS_CONTROL", mapOf("action" to action), speech, true, outcome.message)
            }
            is SystemControlOutcome.OpenedSettingsFallback -> {
                val speech = localized(
                    text,
                    "ब्राइटनेस बदलने के लिए सिस्टम सेटिंग्स की अनुमति चाहिए। मैंने संबंधित सेटिंग्स खोल दी हैं।",
                    "Brightness badalne ke liye system settings permission chahiye. Maine settings screen khol di hai.",
                    outcome.reason
                )
                IntentExecutionResult("BRIGHTNESS_CONTROL", mapOf("fallback" to outcome.settingsLabel), speech, true, "Opened ${outcome.settingsLabel}")
            }
            is SystemControlOutcome.Failed -> {
                IntentExecutionResult("BRIGHTNESS_CONTROL", emptyMap(), outcome.reason, false, "Brightness error")
            }
        }
    }

    private fun matchSystemSettingsShortcut(normalizedExact: String, text: String): IntentExecutionResult? {
        data class SettingsRule(
            val key: String,
            val labelEn: String,
            val labelHi: String,
            val labelHinglish: String,
            val matches: () -> Boolean,
            val opener: () -> Pair<Boolean, String>,
            val isDirectToggleRestriction: Boolean = false
        )

        val rules = listOf(
            SettingsRule(
                key = "battery_optimization",
                labelEn = "Battery Optimization Settings",
                labelHi = "बैटरी ऑप्टिमाइज़ेशन सेटिंग्स",
                labelHinglish = "Battery Optimization settings",
                matches = {
                    text.contains("battery optimization") || text.contains("battery optimisation") ||
                        text.contains("ignore battery") || text.contains("doze setting") ||
                        text.contains("बैटरी ऑप्टिमाइजेशन")
                },
                opener = { deviceController.openBatteryOptimizationSettings() }
            ),
            SettingsRule(
                key = "battery",
                labelEn = "Battery Settings",
                labelHi = "बैटरी सेटिंग्स",
                labelHinglish = "Battery settings",
                matches = {
                    text.contains("battery setting") || text.contains("power usage setting") ||
                        text.contains("battery saver setting") || text.contains("बैटरी सेटिंग")
                },
                opener = { deviceController.openBatterySettings() }
            ),
            SettingsRule(
                key = "bluetooth",
                labelEn = "Bluetooth Settings",
                labelHi = "ब्लूटूथ सेटिंग्स",
                labelHinglish = "Bluetooth settings",
                matches = {
                    text.contains("bluetooth setting") || text.contains("ब्लूटूथ सेटिंग")
                },
                opener = { deviceController.openBluetoothSettings() }
            ),
            SettingsRule(
                key = "wifi",
                labelEn = "Wi-Fi Settings",
                labelHi = "वाई-फ़ाई सेटिंग्स",
                labelHinglish = "Wi-Fi settings",
                matches = {
                    text.contains("wifi setting") || text.contains("wi-fi setting") ||
                        text.contains("वाईफाई सेटिंग") || text.contains("वाई-फाई सेटिंग") ||
                        matchesAny(normalizedExact, listOf("open wifi", "open wi-fi", "wifi kholo", "wi-fi kholo", "turn on wifi", "turn off wifi", "wifi on karo", "wifi off karo"))
                },
                opener = { deviceController.openWifiSettings() },
                isDirectToggleRestriction = normalizedExact.contains("turn on") || normalizedExact.contains("turn off") || normalizedExact.contains("on karo") || normalizedExact.contains("off karo")
            ),
            SettingsRule(
                key = "mobile_data",
                labelEn = "Mobile Data Settings",
                labelHi = "मोबाइल डेटा सेटिंग्स",
                labelHinglish = "Mobile Data settings",
                matches = {
                    text.contains("mobile data setting") || text.contains("cellular setting") ||
                        text.contains("data usage setting") || text.contains("data setting") ||
                        text.contains("मोबाइल डेटा सेटिंग") ||
                        matchesAny(normalizedExact, listOf("open mobile data", "mobile data kholo", "turn on mobile data", "turn off mobile data", "mobile data on karo", "mobile data off karo"))
                },
                opener = { deviceController.openMobileDataSettings() },
                isDirectToggleRestriction = normalizedExact.contains("turn on") || normalizedExact.contains("turn off") || normalizedExact.contains("on karo") || normalizedExact.contains("off karo")
            ),
            SettingsRule(
                key = "hotspot",
                labelEn = "Hotspot Settings",
                labelHi = "हॉटस्पॉट सेटिंग्स",
                labelHinglish = "Hotspot settings",
                matches = {
                    text.contains("hotspot") || text.contains("tethering setting") || text.contains("हॉटस्पॉट")
                },
                opener = { deviceController.openHotspotSettings() },
                isDirectToggleRestriction = normalizedExact.contains("turn on") || normalizedExact.contains("turn off") || normalizedExact.contains("on karo") || normalizedExact.contains("off karo") || normalizedExact.contains("chalu")
            ),
            SettingsRule(
                key = "location",
                labelEn = "Location Settings",
                labelHi = "लोकेशन सेटिंग्स",
                labelHinglish = "Location settings",
                matches = {
                    text.contains("location setting") || text.contains("gps setting") ||
                        text.contains("लोकेशन सेटिंग") ||
                        matchesAny(normalizedExact, listOf("open location", "location kholo", "turn on location", "turn off location", "location on karo", "location off karo"))
                },
                opener = { deviceController.openLocationSettings() },
                isDirectToggleRestriction = normalizedExact.contains("turn on") || normalizedExact.contains("turn off") || normalizedExact.contains("on karo") || normalizedExact.contains("off karo")
            ),
            SettingsRule(
                key = "display",
                labelEn = "Display Settings",
                labelHi = "डिस्प्ले सेटिंग्स",
                labelHinglish = "Display settings",
                matches = {
                    text.contains("display setting") || text.contains("screen setting") ||
                        text.contains("brightness setting") || text.contains("डिस्प्ले सेटिंग")
                },
                opener = { deviceController.openDisplaySettings() }
            ),
            SettingsRule(
                key = "sound",
                labelEn = "Sound Settings",
                labelHi = "साउंड सेटिंग्स",
                labelHinglish = "Sound settings",
                matches = {
                    text.contains("sound setting") || text.contains("audio setting") ||
                        text.contains("ringtone setting") || text.contains("volume setting") ||
                        text.contains("साउंड सेटिंग")
                },
                opener = { deviceController.openSoundSettings() }
            ),
            SettingsRule(
                key = "app_permissions",
                labelEn = "App Permissions",
                labelHi = "ऐप परमिशन सेटिंग्स",
                labelHinglish = "App Permissions",
                matches = {
                    text.contains("app permission") || text.contains("permission setting") ||
                        text.contains("permissions setting") || text.contains("ऐप परमिशन")
                },
                opener = { deviceController.openAppPermissionsSettings() }
            ),
            SettingsRule(
                key = "notification",
                labelEn = "Notification Settings",
                labelHi = "नोटिफिकेशन सेटिंग्स",
                labelHinglish = "Notification settings",
                matches = {
                    text.contains("notification setting") || text.contains("notifications setting") ||
                        text.contains("नोटिफिकेशन सेटिंग")
                },
                opener = { deviceController.openNotificationSettings() }
            ),
            SettingsRule(
                key = "airplane_mode",
                labelEn = "Airplane Mode Settings",
                labelHi = "एयरप्लेन मोड सेटिंग्स",
                labelHinglish = "Airplane Mode settings",
                matches = {
                    text.contains("airplane mode setting") || text.contains("flight mode setting") ||
                        text.contains("एयरप्लेन मोड सेटिंग")
                },
                opener = { deviceController.openAirplaneModeSettings() }
            ),
            SettingsRule(
                key = "language",
                labelEn = "Language Settings",
                labelHi = "भाषा सेटिंग्स",
                labelHinglish = "Language settings",
                matches = {
                    text.contains("language setting") || text.contains("locale setting") ||
                        text.contains("input language setting") || text.contains("भाषा सेटिंग")
                },
                opener = { deviceController.openLanguageSettings() }
            ),
            SettingsRule(
                key = "accessibility",
                labelEn = "Accessibility Settings",
                labelHi = "एक्सेसिबिलिटी सेटिंग्स",
                labelHinglish = "Accessibility settings",
                matches = {
                    text.contains("accessibility setting") || text.contains("एक्सेसिबिलिटी सेटिंग")
                },
                opener = { deviceController.openAccessibilitySettings() }
            )
        )

        val matchedRule = rules.firstOrNull { it.matches() } ?: return null
        val (ok, msg) = matchedRule.opener()
        val speech = if (ok) {
            if (matchedRule.isDirectToggleRestriction) {
                localized(
                    text,
                    "एंड्रॉइड सुरक्षा नियम के कारण यह सेटिंग आपको मैन्युअल रूप से बदलनी होगी। मैंने ${matchedRule.labelHi} खोल दी हैं।",
                    "Android restriction ki wajah se aap ise manually toggle kar sakte hain. Maine ${matchedRule.labelHinglish} khol di hain.",
                    "Android restricts apps from toggling this directly. I opened ${matchedRule.labelEn} for you."
                )
            } else {
                localized(
                    text,
                    "${matchedRule.labelHi} खोल रही हूँ।",
                    "${matchedRule.labelHinglish} khol rahi hoon.",
                    "Opening ${matchedRule.labelEn}."
                )
            }
        } else {
            msg
        }
        return IntentExecutionResult(
            intentName = "OPEN_SETTINGS",
            parameters = mapOf("target" to matchedRule.key),
            feedbackSpeech = speech,
            success = ok,
            actionSummary = "Opened ${matchedRule.labelEn}"
        )
    }

    private fun tryHandleInAppSearchAndChrome(originalText: String, text: String): IntentExecutionResult? {
        val cleanOriginal = originalText.trim().trimEnd('?', '!', '.', ' ')

        // 1. Chrome website or search commands
        val chromeHindiMatch = Regex(
            """^(?:please\s+)?(?:in\s+|on\s+)?chrome\s+(?:mein|me|par|pe|में|पर)\s+(.+?)\s+(?:open\s*karo|open\s*kardo|kholo|khol\s*do|chala\s*do|launch\s*karo|search\s*karo|khojo|dhundo|खोलो|सर्च\s*करो)$""",
            RegexOption.IGNORE_CASE
        ).find(cleanOriginal)
        val chromeOpenEnMatch = Regex(
            """^(?:please\s+|can\s+you\s+)?open\s+(.+?)\s+(?:in|on)\s+chrome$""",
            RegexOption.IGNORE_CASE
        ).find(cleanOriginal)
        val chromeSearchEnMatch = Regex(
            """^(?:please\s+|can\s+you\s+)?search\s+(?:for\s+)?(.+?)\s+(?:in|on)\s+chrome$""",
            RegexOption.IGNORE_CASE
        ).find(cleanOriginal)

        val chromeTarget = (chromeHindiMatch ?: chromeOpenEnMatch ?: chromeSearchEnMatch)?.groupValues?.get(1)?.trim()
        if (!chromeTarget.isNullOrBlank()) {
            val outcome = deviceController.openOrSearchInChrome(chromeTarget)
            return formatInAppSearchOutcome(text, outcome)
        }

        // 2. Hindi / Hinglish In-App Search: "<App> par/mein <query> search karo / khojo / dhundo / chalao"
        val hindiInAppSearch = Regex(
            """^(?:please\s+)?([a-zA-Z\u0900-\u097F0-9.\s_-]{2,30}?)\s+(?:par|pe|mein|me|पर|में)\s+(.+?)\s+(?:search\s*karo|search\s*kardo|search\s*kar\s*do|khojo|dhundo|search|खोजो|सर्च\s*करो)$""",
            RegexOption.IGNORE_CASE
        ).find(cleanOriginal)
        if (hindiInAppSearch != null) {
            val appName = hindiInAppSearch.groupValues[1].trim()
            val query = hindiInAppSearch.groupValues[2].trim()
            if (appName.isNotBlank() && query.isNotBlank()) {
                val outcome = deviceController.searchInsideApp(appName, query)
                return formatInAppSearchOutcome(text, outcome)
            }
        }

        // 3. English In-App Search: "search [for] <query> on/in <app>" or "search <app> for <query>"
        val englishInAppSearch = Regex(
            """^(?:please\s+|can\s+you\s+)?search\s+(?:for\s+)?(.+?)\s+(?:on|in|inside)\s+([a-zA-Z0-9\s._-]{2,30})$""",
            RegexOption.IGNORE_CASE
        ).find(cleanOriginal)
        if (englishInAppSearch != null) {
            val query = englishInAppSearch.groupValues[1].trim()
            val appName = englishInAppSearch.groupValues[2].trim()
            if (appName.isNotBlank() && query.isNotBlank()) {
                val outcome = deviceController.searchInsideApp(appName, query)
                return formatInAppSearchOutcome(text, outcome)
            }
        }

        val englishAppForMatch = Regex(
            """^(?:please\s+|can\s+you\s+)?search\s+(youtube|instagram|insta|facebook|fb|google|chrome|maps|google\s+maps|spotify|play\s+store|playstore)\s+(?:for\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(cleanOriginal)
        if (englishAppForMatch != null) {
            val appName = englishAppForMatch.groupValues[1].trim()
            val query = englishAppForMatch.groupValues[2].trim()
            if (query.isNotBlank()) {
                val outcome = deviceController.searchInsideApp(appName, query)
                return formatInAppSearchOutcome(text, outcome)
            }
        }

        return null
    }

    private fun formatInAppSearchOutcome(text: String, outcome: InAppSearchOutcome): IntentExecutionResult {
        return when (outcome) {
            is InAppSearchOutcome.SearchedInApp -> {
                val encoded = URLEncoder.encode(outcome.query, "UTF-8")
                val link = when (outcome.appLabel) {
                    "YouTube" -> WebSourceLink("YouTube: ${outcome.query}", "https://www.youtube.com/results?search_query=$encoded", "YouTube search")
                    "Google" -> WebSourceLink("Google: ${outcome.query}", "https://www.google.com/search?q=$encoded", "Google search")
                    else -> null
                }
                val speech = localized(
                    text,
                    "${outcome.appLabel} पर \"${outcome.query}\" खोज रही हूँ।",
                    "${outcome.appLabel} par \"${outcome.query}\" search kar rahi hoon.",
                    "Searching \"${outcome.query}\" on ${outcome.appLabel}."
                )
                IntentExecutionResult(
                    intentName = "IN_APP_SEARCH",
                    parameters = mapOf("app" to outcome.appLabel, "query" to outcome.query),
                    feedbackSpeech = speech,
                    success = true,
                    actionSummary = "Searched ${outcome.appLabel}: ${outcome.query}",
                    webSources = listOfNotNull(link)
                )
            }
            is InAppSearchOutcome.OpenedDestination -> {
                IntentExecutionResult(
                    intentName = "IN_APP_SEARCH_PARTIAL",
                    parameters = mapOf("app" to outcome.appLabel, "query" to outcome.query),
                    feedbackSpeech = outcome.honestReason,
                    success = true,
                    actionSummary = "Opened ${outcome.appLabel} for ${outcome.query}"
                )
            }
            is InAppSearchOutcome.OpenedWebsite -> {
                val speech = localized(
                    text,
                    "${outcome.appLabel} में ${outcome.url} खोल रही हूँ।",
                    "${outcome.appLabel} mein ${outcome.url} open kar rahi hoon.",
                    "Opening ${outcome.url} in ${outcome.appLabel}."
                )
                IntentExecutionResult(
                    intentName = "OPEN_WEBSITE",
                    parameters = mapOf("browser" to outcome.appLabel, "url" to outcome.url),
                    feedbackSpeech = speech,
                    success = true,
                    actionSummary = "Opened ${outcome.url}"
                )
            }
            is InAppSearchOutcome.AppNotInstalled -> {
                val speech = localized(
                    text,
                    "${outcome.appLabel} आपके डिवाइस पर इंस्टॉल नहीं है।",
                    "${outcome.appLabel} aapke device par install nahi hai.",
                    "${outcome.appLabel} is not installed on this device."
                )
                IntentExecutionResult(
                    intentName = "IN_APP_SEARCH",
                    parameters = mapOf("app" to outcome.appLabel, "query" to outcome.query),
                    feedbackSpeech = speech,
                    success = false,
                    actionSummary = "${outcome.appLabel} not installed"
                )
            }
            is InAppSearchOutcome.Failed -> {
                IntentExecutionResult("IN_APP_SEARCH", emptyMap(), outcome.reason, false, "In-App Search Failed")
            }
        }
    }

    private fun handleOpenAppCommand(appTarget: String, text: String): IntentExecutionResult {
        return when (val outcome = deviceController.openAppSmart(appTarget)) {
            is AppLaunchOutcome.Launched -> {
                val speech = localized(
                    text,
                    pickVaried("app_hi", listOf(
                        "${outcome.appLabel} खोल रही हूँ।",
                        "${outcome.appLabel} अभी खोलती हूँ।"
                    )),
                    pickVaried("app_hg", listOf(
                        "${outcome.appLabel} open kar rahi hoon.",
                        "Zaroor, ${outcome.appLabel} abhi khol rahi hoon."
                    )),
                    pickVaried("app_en", listOf(
                        "Opening ${outcome.appLabel}.",
                        "Launching ${outcome.appLabel} for you.",
                        "Here's ${outcome.appLabel}."
                    ))
                )
                IntentExecutionResult(
                    intentName = "OPEN_APP",
                    parameters = mapOf("app" to outcome.appLabel, "package" to outcome.packageName),
                    feedbackSpeech = speech,
                    success = true,
                    actionSummary = "Opened ${outcome.appLabel}",
                    emotion = AssistantEmotion.HAPPY
                )
            }
            is AppLaunchOutcome.OpenedUrl -> {
                val speech = localized(text, "लिंक खोल रही हूँ।", "Link open kar rahi hoon.", "Opening link.")
                IntentExecutionResult(
                    intentName = "OPEN_URL",
                    parameters = mapOf("url" to outcome.url),
                    feedbackSpeech = speech,
                    success = true,
                    actionSummary = "Opened link",
                    emotion = AssistantEmotion.NEUTRAL
                )
            }
            is AppLaunchOutcome.Ambiguous -> {
                pendingAppCandidates = outcome.candidates
                val candidateNames = outcome.candidates.joinToString(", ") { it.label }
                val speech = localized(
                    text,
                    "मुझे एक से अधिक मिलते-जुलते ऐप मिले: $candidateNames। आप इनमें से कौन-सा खोलना चाहते हैं?",
                    "Mujhe ek se zyada matching apps mile: $candidateNames. Aap inmein se kaun-sa open karna chahte hain?",
                    "I found multiple matching apps: $candidateNames. Which one would you like me to open?"
                )
                IntentExecutionResult(
                    intentName = "OPEN_APP_DISAMBIGUATION",
                    parameters = mapOf("query" to outcome.requestedName, "candidates" to candidateNames),
                    feedbackSpeech = speech,
                    success = true,
                    actionSummary = "Clarifying app: $candidateNames",
                    emotion = AssistantEmotion.CURIOUS
                )
            }
            is AppLaunchOutcome.NotInstalled -> {
                val speech = localized(
                    text,
                    "${outcome.requestedName} आपके डिवाइस पर इंस्टॉल नहीं है।",
                    "${outcome.requestedName} aapke device par install nahi hai.",
                    "${outcome.requestedName} is not installed on this device."
                )
                IntentExecutionResult(
                    intentName = "OPEN_APP",
                    parameters = mapOf("app" to outcome.requestedName),
                    feedbackSpeech = speech,
                    success = false,
                    actionSummary = "${outcome.requestedName} not installed",
                    emotion = AssistantEmotion.SAD
                )
            }
            is AppLaunchOutcome.Failed -> {
                IntentExecutionResult(
                    intentName = "OPEN_APP",
                    parameters = mapOf("app" to appTarget),
                    feedbackSpeech = outcome.reason,
                    success = false,
                    actionSummary = "Open App Failed",
                    emotion = AssistantEmotion.SAD
                )
            }
        }
    }

    private fun evaluateUnitConversion(text: String): String? {
        val convMatch = Regex("""^convert\s+(\d+(?:\.\d+)?)\s*([a-zA-Z]+)\s*(?:to|into|in)\s*([a-zA-Z]+)$""").find(text) ?: return null
        val value = convMatch.groupValues[1].toDoubleOrNull() ?: return null
        val fromUnit = convMatch.groupValues[2].lowercase(Locale.ROOT)
        val toUnit = convMatch.groupValues[3].lowercase(Locale.ROOT)

        return when {
            (fromUnit == "km" || fromUnit == "kilometers") && (toUnit == "miles" || toUnit == "mile") -> {
                val res = value * 0.621371
                "$value km is equal to ${String.format(Locale.US, "%.2f", res)} miles."
            }
            (fromUnit == "miles" || fromUnit == "mile") && (toUnit == "km" || toUnit == "kilometers") -> {
                val res = value * 1.60934
                "$value miles is equal to ${String.format(Locale.US, "%.2f", res)} km."
            }
            (fromUnit == "kg" || fromUnit == "kilograms") && (toUnit == "lbs" || toUnit == "pounds") -> {
                val res = value * 2.20462
                "$value kg is equal to ${String.format(Locale.US, "%.2f", res)} pounds."
            }
            (fromUnit == "lbs" || fromUnit == "pounds") && (toUnit == "kg" || toUnit == "kilograms") -> {
                val res = value * 0.453592
                "$value pounds is equal to ${String.format(Locale.US, "%.2f", res)} kg."
            }
            (fromUnit == "c" || fromUnit == "celsius") && (toUnit == "f" || toUnit == "fahrenheit") -> {
                val res = (value * 9 / 5) + 32
                "$value°C is equal to ${String.format(Locale.US, "%.1f", res)}°F."
            }
            (fromUnit == "f" || fromUnit == "fahrenheit") && (toUnit == "c" || toUnit == "celsius") -> {
                val res = (value - 32) * 5 / 9
                "$value°F is equal to ${String.format(Locale.US, "%.1f", res)}°C."
            }
            else -> null
        }
    }
}
