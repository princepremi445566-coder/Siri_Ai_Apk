package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SiriRepository
import com.example.device.AndroidDeviceController
import com.example.intent.SiriIntentParser
import com.example.model.LanguageOption
import com.example.model.VoicePersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppNameResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Siri AI", appName)
    }

    @Test
    fun testWakeWordStripping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        assertEquals("turn on flashlight", parser.cleanWakePhrase("Hey Siri, turn on flashlight"))
        assertEquals("what time is it", parser.cleanWakePhrase("Hey Siri what time is it"))
        assertEquals("open youtube", parser.cleanWakePhrase("Siri, open youtube"))
        assertEquals("battery status", parser.cleanWakePhrase("Suno Siri, battery status"))
    }

    @Test
    fun testStopCommands() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        assertTrue(parser.isStopCommand("stop"))
        assertTrue(parser.isStopCommand("quiet"))
        assertTrue(parser.isStopCommand("shant ho jao"))
        assertTrue(parser.isStopCommand("chup"))
        assertFalse(parser.isStopCommand("turn on flashlight"))
    }

    @Test
    fun testLocalIntentsParsing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        val timeResult = parser.parseAndExecute("what time is it")
        assertNotNull(timeResult)
        assertEquals("TIME", timeResult?.intentName)

        val dateResult = parser.parseAndExecute("what is today's date")
        assertNotNull(dateResult)
        assertEquals("DATE", dateResult?.intentName)

        val batteryResult = parser.parseAndExecute("how much battery")
        assertNotNull(batteryResult)
        assertEquals("BATTERY_STATUS", batteryResult?.intentName)

        val timerResult = parser.parseAndExecute("set timer for 10 minutes")
        assertNotNull(timerResult)
        assertEquals("SET_TIMER", timerResult?.intentName)
    }

    @Test
    fun testCalculationsAndUnitConversions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        val mathResult = parser.parseAndExecute("calculate 25 * 4")
        assertNotNull(mathResult)
        assertEquals("CALCULATION", mathResult?.intentName)
        assertTrue(mathResult?.feedbackSpeech?.contains("100") == true)

        val percentResult = parser.parseAndExecute("what is 20 percent of 150")
        assertNotNull(percentResult)
        assertEquals("CALCULATION", percentResult?.intentName)
        assertTrue(percentResult?.feedbackSpeech?.contains("30") == true)

        val unitResult = parser.parseAndExecute("convert 10 km to miles")
        assertNotNull(unitResult)
        assertEquals("UNIT_CONVERSION", unitResult?.intentName)
        assertTrue(unitResult?.feedbackSpeech?.contains("6.21") == true)
    }

    @Test
    fun testDialerAndNavigationIntents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        val dialResult = parser.parseAndExecute("call 9876543210")
        assertNotNull(dialResult)
        assertEquals("DIAL_PHONE", dialResult?.intentName)

        val navResult = parser.parseAndExecute("navigate to Central Park")
        assertNotNull(navResult)
        assertEquals("NAVIGATION", navResult?.intentName)
    }

    @Test
    fun testRepositoryPersonalizationAndTasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SiriRepository(context)

        repo.updatePersonality(VoicePersonality.CHEERFUL)
        assertEquals(VoicePersonality.CHEERFUL, repo.voicePersonality.value)

        repo.updateLanguage(LanguageOption.HINDI)
        assertEquals(LanguageOption.HINDI, repo.selectedLanguage.value)

        val initialCount = repo.tasks.value.size
        repo.addTask("Prepare presentation", "Tomorrow")
        assertEquals(initialCount + 1, repo.tasks.value.size)
    }

    @Test
    fun testNotesAndSessionsRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SiriRepository(context)

        val initNotes = repo.notes.value.size
        repo.addNote("Calculus Formulas", "Integral of sin(x) is -cos(x)", "Study")
        assertEquals(initNotes + 1, repo.notes.value.size)

        val initSessions = repo.sessions.value.size
        val newSessionId = repo.createNewSession("Research Session")
        assertEquals(initSessions + 1, repo.sessions.value.size)
        assertEquals(newSessionId, repo.currentSessionId.value)
    }

    @Test
    fun testHindiAndHinglishIntents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        val greetingResult = parser.parseAndExecute("kya haal hai")
        assertNotNull(greetingResult)
        assertEquals("GREETING", greetingResult?.intentName)
        assertTrue(greetingResult?.feedbackSpeech?.contains("Main badhiya hoon") == true)

        val courtesyResult = parser.parseAndExecute("shukriya")
        assertNotNull(courtesyResult)
        assertEquals("COURTESY", courtesyResult?.intentName)
    }

    @Test
    fun testContextualFollowUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        // First command
        parser.parseAndExecute("turn on flashlight")
        assertEquals("FLASHLIGHT_ON", parser.getLastExecutedIntent())

        // Follow-up contextual command
        val followUp = parser.parseAndExecute("turn it off")
        assertNotNull(followUp)
        assertEquals("FLASHLIGHT_OFF", followUp?.intentName)
    }

    @Test
    fun testVoiceShortcutsAndRoutines() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SiriRepository(context)

        val initialCount = repo.shortcuts.value.size
        repo.addVoiceShortcut("leaving home", "turn off flashlight", "Goodbye! Safe travels.")
        assertEquals(initialCount + 1, repo.shortcuts.value.size)

        val added = repo.shortcuts.value.firstOrNull { it.triggerPhrase == "leaving home" }
        assertNotNull(added)
        assertEquals("turn off flashlight", added?.commandText)
    }

    @Test
    fun testSiriEasterEggsAndJokes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        // Zero divided by zero Easter Egg
        val zeroResult = parser.parseAndExecute("what is zero divided by zero")
        assertNotNull(zeroResult)
        assertEquals("SIRI_EASTER_EGG", zeroResult?.intentName)
        assertTrue(zeroResult?.feedbackSpeech?.contains("Cookie Monster") == true)

        // Tell a joke
        val jokeResult = parser.parseAndExecute("tell me a joke")
        assertNotNull(jokeResult)
        assertEquals("JOKE", jokeResult?.intentName)
        assertTrue(jokeResult?.feedbackSpeech?.isNotBlank() == true)

        // Hindi Joke
        val hindiJoke = parser.parseAndExecute("koi chutkula sunao")
        assertNotNull(hindiJoke)
        assertEquals("JOKE", hindiJoke?.intentName)
        assertTrue(hindiJoke?.feedbackSpeech?.isNotBlank() == true)

        // Identity
        val identityResult = parser.parseAndExecute("who are you")
        assertNotNull(identityResult)
        assertEquals("IDENTITY", identityResult?.intentName)
        assertTrue(identityResult?.feedbackSpeech?.contains("Siri") == true)

        // Capabilities
        val capResult = parser.parseAndExecute("what can you do")
        assertNotNull(capResult)
        assertEquals("CAPABILITIES", capResult?.intentName)
        assertTrue(capResult?.feedbackSpeech?.contains("flashlight") == true)
    }

    @Test
    fun testSpeechSanitization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tts = com.example.voice.SiriTextToSpeech(context) {}
        val raw = "**Certainly!** Here is a *quick* note: # Important\n- Item 1\nCheck https://example.com"
        val clean = tts.sanitizeForSpeech(raw)
        assertFalse(clean.contains("**"))
        assertFalse(clean.contains("https://"))
        assertFalse(clean.contains("#"))
        assertTrue(clean.contains("Certainly! Here is a quick note"))
        tts.shutdown()
    }

    @Test
    fun testManifestUpdateDownloadUrlAndDirectOpen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = SiriRepository(app)
        val expectedUrl = "https://github.com/princepremi445566-coder/Siri_Ai_Apk/releases/latest/download/app-debug.apk"
        assertEquals(expectedUrl, repo.updateInfo.value.downloadUrl)

        val controller = AndroidDeviceController(app)
        val (opened, _) = controller.openApp(expectedUrl)
        assertTrue(opened)
        val launchedIntent = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(launchedIntent)
        assertEquals(android.content.Intent.ACTION_VIEW, launchedIntent.action)
        assertEquals(expectedUrl, launchedIntent.data?.toString())
    }

    @Test
    fun testOfflineWakeWordAcousticMatcher() {
        val sampleRate = com.example.voice.HeySiriAcousticAnalyzer.SAMPLE_RATE
        val frameSize = com.example.voice.HeySiriAcousticAnalyzer.FRAME_SAMPLES

        // 1. Silence / low hum should NOT trigger
        val humFrames = List(25) {
            val samples = ShortArray(frameSize) { i ->
                (120.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 100.0 * i / sampleRate)).toInt().toShort()
            }
            com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(samples)
        }
        assertFalse(
            com.example.voice.HeySiriAcousticAnalyzer.matchesWakeWordContour(
                frames = humFrames,
                noiseFloorRms = 180.0,
                wakeWordOption = com.example.model.WakeWordOption.HEY_SIRI
            )
        )

        // 2. Synthetic "Hey Siri" contour: voiced syllable 1 -> silence dip -> sibilant /s/ -> voiced syllable 2
        val voiced600 = ShortArray(frameSize) { i ->
            (5500.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 600.0 * i / sampleRate)).toInt().toShort()
        }
        val quietGap = ShortArray(frameSize) { 30 }
        val sibilant5500 = ShortArray(frameSize) { i ->
            (3200.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 5500.0 * i / sampleRate)).toInt().toShort()
        }
        val voiced1000 = ShortArray(frameSize) { i ->
            (5200.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * i / sampleRate)).toInt().toShort()
        }

        val wakeFrames = buildList {
            repeat(8) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(voiced600)) }
            repeat(3) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(quietGap)) }
            repeat(5) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(sibilant5500)) }
            repeat(10) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(voiced1000)) }
        }
        assertTrue(
            com.example.voice.HeySiriAcousticAnalyzer.matchesWakeWordContour(
                frames = wakeFrames,
                noiseFloorRms = 200.0,
                wakeWordOption = com.example.model.WakeWordOption.HEY_SIRI
            )
        )
    }

    @Test
    fun testSiriVoiceServiceForegroundLifecycleAndDuplicatePrevention() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)

        // Without RECORD_AUDIO permission, service should not run or fake active state
        com.example.service.SiriVoiceService.start(app)
        assertFalse(com.example.service.SiriVoiceService.isServiceRunning)

        // Grant RECORD_AUDIO permission
        shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

        val serviceController = org.robolectric.Robolectric.buildService(
            com.example.service.SiriVoiceService::class.java,
            android.content.Intent(app, com.example.service.SiriVoiceService::class.java).apply {
                action = com.example.service.SiriVoiceService.ACTION_START
            }
        )
        val service = serviceController.create().startCommand(0, 1).get()
        assertTrue(com.example.service.SiriVoiceService.isServiceRunning)
        assertTrue(com.example.service.SiriServiceBridge.isServiceRunning.value)

        // Check persistent notification content text
        val shadowService = org.robolectric.Shadows.shadowOf(service)
        val notification = shadowService.lastForegroundNotification
        assertNotNull(notification)
        val contentText = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        assertEquals("Siri AI is running in background", contentText)

        // Repeated startCommand should not crash or break service state
        serviceController.startCommand(0, 2)
        assertTrue(com.example.service.SiriVoiceService.isServiceRunning)

        // Explicit stop action cleanly shuts down service
        service.onStartCommand(
            android.content.Intent(app, com.example.service.SiriVoiceService::class.java).apply {
                action = com.example.service.SiriVoiceService.ACTION_STOP
            },
            0,
            3
        )
        assertFalse(com.example.service.SiriVoiceService.isServiceRunning)
        assertFalse(com.example.service.SiriServiceBridge.isServiceRunning.value)
        serviceController.destroy()
    }

    @Test
    fun testAnchoredIntentParsingDoesNotHijackNormalQuestions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        val parser = SiriIntentParser(deviceController)

        assertEquals(null, parser.parseAndExecute("what is open source software"))
        assertEquals(null, parser.parseAndExecute("how to call a REST API in Kotlin"))
        assertEquals(null, parser.parseAndExecute("explain how to start a small business"))
        assertEquals(null, parser.parseAndExecute("Sirius is the brightest star"))

        // Quick action "What is 0 / 0?" should trigger Cookie Monster Easter egg
        val cookieRes = parser.parseAndExecute("What is 0 / 0?")
        assertNotNull(cookieRes)
        assertEquals("SIRI_EASTER_EGG", cookieRes?.intentName)
        assertTrue(cookieRes?.feedbackSpeech?.contains("Cookie Monster") == true)
    }

    @Test
    fun testHindiLocalizationCasingPreservationAndRepeat() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceController = AndroidDeviceController(context)
        var capturedNote = ""
        val parser = SiriIntentParser(
            deviceController = deviceController,
            onNoteAddTrigger = { title, content ->
                capturedNote = "$title: $content"
                com.example.model.IntentExecutionResult("ADD_NOTE", emptyMap(), "Saved", true)
            },
            getCurrentLanguage = { LanguageOption.HINDI },
            getLastAssistantSpeech = { "नमस्ते, मैं आपकी मदद के लिए तैयार हूँ।" }
        )

        parser.parseAndExecute("create note Meet Dr. Sharma at 5 PM")
        assertEquals("Voice Note: Meet Dr. Sharma at 5 PM", capturedNote)

        val repeatRes = parser.parseAndExecute("repeat that")
        assertEquals("नमस्ते, मैं आपकी मदद के लिए तैयार हूँ।", repeatRes?.feedbackSpeech)

        val timeHi = parser.parseAndExecute("what time is it")
        assertTrue(timeHi?.feedbackSpeech?.contains("अभी समय") == true)

        val secTimer = parser.parseAndExecute("30 second ka timer")
        assertEquals("SET_TIMER", secTimer?.intentName)
        assertEquals("30", secTimer?.parameters?.get("seconds"))
    }

    @Test
    fun testRepositoryPersistenceSessionSafetyAndDataWipe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = SiriRepository(context)
        repo1.clearAllPersonalData()

        val initialDevices = repo1.smartDevices.value.size
        repo1.addSmartDevice("Studio Lamp", "LIGHT")
        assertEquals(initialDevices + 1, repo1.smartDevices.value.size)

        // Reload new instance to confirm SmartDevices persistence
        val repo2 = SiriRepository(context)
        assertTrue(repo2.smartDevices.value.any { it.name == "Studio Lamp" })

        // Deleting last remaining session should preserve a valid fallback default session
        repo2.deleteSession("default")
        assertTrue(repo2.sessions.value.isNotEmpty())
        assertEquals("default", repo2.currentSessionId.value)

        // Explicit message sessionId should not be overwritten by currentSessionId
        val extraSession = repo2.createNewSession("Work")
        repo2.switchSession("default")
        repo2.addMessage(com.example.model.ChatMessage(sessionId = extraSession, text = "Hello Work", isFromUser = true))
        assertTrue(repo2.messages.value.any { it.text == "Hello Work" && it.sessionId == extraSession })

        // clearAllPersonalData resets personality/language back to baseline defaults
        repo2.updateLanguage(LanguageOption.HINDI)
        repo2.clearAllPersonalData()
        assertEquals(LanguageOption.AUTO, repo2.selectedLanguage.value)
        assertEquals(com.example.model.VoiceGender.FEMALE, repo2.voiceGender.value)
    }

    @Test
    fun testTtsSpeakDoesNotEmitPrematureIdleCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val states = mutableListOf<Boolean>()
        val tts = com.example.voice.SiriTextToSpeech(context) { isSpeaking ->
            states.add(isSpeaking)
        }
        tts.speak("Hello Siri")
        // Calling speak() must not emit a synchronous `false` before speaking begins
        assertFalse(states.contains(false))
        tts.shutdown()
    }

    @Suppress("DEPRECATION")
    private fun registerFakeLauncherApp(
        app: android.app.Application,
        packageName: String,
        label: String,
        activityName: String = "$packageName.MainActivity"
    ) {
        val shadowPm = org.robolectric.Shadows.shadowOf(app.packageManager)
        val component = android.content.ComponentName(packageName, activityName)

        val appInfo = android.content.pm.ApplicationInfo().apply {
            this.packageName = packageName
            this.name = label
            this.nonLocalizedLabel = label
        }
        val actInfo = android.content.pm.ActivityInfo().apply {
            this.packageName = packageName
            this.name = activityName
            this.applicationInfo = appInfo
            this.nonLocalizedLabel = label
            this.exported = true
        }
        val pkgInfo = android.content.pm.PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = appInfo
            this.activities = arrayOf(actInfo)
        }
        shadowPm.installPackage(pkgInfo)

        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfo = android.content.pm.ResolveInfo().apply {
            this.activityInfo = actInfo
            this.nonLocalizedLabel = label
        }
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)

        val targetedIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        shadowPm.addResolveInfoForIntent(targetedIntent, resolveInfo)

        val componentIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            setComponent(component)
        }
        shadowPm.addResolveInfoForIntent(componentIntent, resolveInfo)
    }

    @Test
    fun testOpenAllPopularAppsAndDynamicInstalledApps() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)

        val popularApps = listOf(
            "com.whatsapp" to "WhatsApp",
            "com.facebook.katana" to "Facebook",
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.google.android.googlequicksearchbox" to "Google",
            "com.android.chrome" to "Chrome",
            "com.google.android.gm" to "Gmail",
            "com.google.android.apps.maps" to "Google Maps",
            "org.telegram.messenger" to "Telegram",
            "com.spotify.music" to "Spotify",
            "com.snapchat.android" to "Snapchat",
            "com.google.android.dialer" to "Phone",
            "com.google.android.apps.messaging" to "Messages",
            "com.google.android.GoogleCamera" to "Camera",
            "com.google.android.apps.photos" to "Gallery",
            "com.android.settings" to "Settings",
            "com.android.vending" to "Play Store"
        )
        popularApps.forEach { (pkg, label) -> registerFakeLauncherApp(app, pkg, label) }
        registerFakeLauncherApp(app, "md.obsidian", "Obsidian Notes")

        val parser = SiriIntentParser(AndroidDeviceController(app))

        val whatsapp = parser.parseAndExecute("Hey Siri, open WhatsApp.")
        assertTrue(whatsapp?.success == true)
        assertEquals("OPEN_APP", whatsapp?.intentName)
        assertEquals("com.whatsapp", shadowApp.nextStartedActivity?.`package`)

        val instagram = parser.parseAndExecute("Hey Siri, Instagram kholo.")
        assertTrue(instagram?.success == true)
        assertEquals("OPEN_APP", instagram?.intentName)
        assertEquals("com.instagram.android", shadowApp.nextStartedActivity?.`package`)

        val youtube = parser.parseAndExecute("Hey Siri, YouTube open karo.")
        assertTrue(youtube?.success == true)
        assertEquals("OPEN_APP", youtube?.intentName)
        assertEquals("com.google.android.youtube", shadowApp.nextStartedActivity?.`package`)

        for ((pkg, label) in popularApps) {
            val res = parser.parseAndExecute("open $label")
            assertTrue("Expected $label to open", res?.success == true)
            assertEquals("OPEN_APP", res?.intentName)
            assertEquals(pkg, shadowApp.nextStartedActivity?.`package`)
        }

        // Dynamic installed app lookup by custom name
        val customApp = parser.parseAndExecute("Obsidian Notes kholo")
        assertTrue(customApp?.success == true)
        assertEquals("OPEN_APP", customApp?.intentName)
        assertEquals("md.obsidian", shadowApp.nextStartedActivity?.`package`)
    }

    @Test
    fun testAmbiguousAppDisambiguationAndNotInstalledHandling() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)

        registerFakeLauncherApp(app, "com.keep.notes", "Keep Notes")
        registerFakeLauncherApp(app, "com.color.notes", "Color Notes")

        val parser = SiriIntentParser(AndroidDeviceController(app))

        // Ambiguous partial name triggers clarification instead of guessing
        val ambiguousRes = parser.parseAndExecute("open notes")
        assertNotNull(ambiguousRes)
        assertEquals("OPEN_APP_DISAMBIGUATION", ambiguousRes?.intentName)
        assertTrue(ambiguousRes?.feedbackSpeech?.contains("Keep Notes") == true)
        assertTrue(ambiguousRes?.feedbackSpeech?.contains("Color Notes") == true)

        // Follow-up reply selects the second option
        val followUpRes = parser.parseAndExecute("second")
        assertTrue(followUpRes?.success == true)
        assertEquals("OPEN_APP", followUpRes?.intentName)
        assertEquals("com.color.notes", shadowApp.nextStartedActivity?.`package`)

        // App not installed fails honestly
        val missingRes = parser.parseAndExecute("open SomeUnknownGhostApp")
        assertNotNull(missingRes)
        assertEquals("OPEN_APP", missingRes?.intentName)
        assertFalse(missingRes?.success ?: true)
        assertTrue(missingRes?.feedbackSpeech?.contains("not installed", ignoreCase = true) == true)
    }

    @Test
    fun testInAppSearchAndHonestDestinations() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)

        registerFakeLauncherApp(app, "com.google.android.youtube", "YouTube")
        registerFakeLauncherApp(app, "com.instagram.android", "Instagram")
        registerFakeLauncherApp(app, "com.facebook.katana", "Facebook")
        registerFakeLauncherApp(app, "com.google.android.googlequicksearchbox", "Google")
        registerFakeLauncherApp(app, "com.android.chrome", "Chrome")
        registerFakeLauncherApp(app, "com.spotify.music", "Spotify")
        registerFakeLauncherApp(app, "com.android.vending", "Play Store")

        val parser = SiriIntentParser(AndroidDeviceController(app))

        val ytSearch = parser.parseAndExecute("Siri, YouTube par AI tutorials search karo.")
        assertTrue(ytSearch?.success == true)
        assertEquals("IN_APP_SEARCH", ytSearch?.intentName)
        val ytIntent = shadowApp.nextStartedActivity
        assertEquals("com.google.android.youtube", ytIntent?.`package`)
        assertEquals("AI tutorials", ytIntent?.getStringExtra(android.app.SearchManager.QUERY))

        val googleSearch = parser.parseAndExecute("Siri, Google par Bihar news search karo.")
        assertTrue(googleSearch?.success == true)
        assertEquals("IN_APP_SEARCH", googleSearch?.intentName)
        val gIntent = shadowApp.nextStartedActivity
        assertEquals("Bihar news", gIntent?.getStringExtra(android.app.SearchManager.QUERY))

        val instaSearch = parser.parseAndExecute("Siri, Instagram par technology search karo.")
        assertTrue(instaSearch?.success == true)
        assertEquals("IN_APP_SEARCH_PARTIAL", instaSearch?.intentName)
        assertTrue(instaSearch?.feedbackSpeech?.contains("Instagram") == true)
        assertNotNull(shadowApp.nextStartedActivity)

        val fbSearch = parser.parseAndExecute("Siri, Facebook par latest AI news search karo.")
        assertTrue(fbSearch?.success == true)
        assertEquals("IN_APP_SEARCH_PARTIAL", fbSearch?.intentName)
        assertTrue(fbSearch?.feedbackSpeech?.contains("Facebook") == true)
        assertNotNull(shadowApp.nextStartedActivity)

        val chromeSite = parser.parseAndExecute("Siri, Chrome mein wikipedia.org open karo.")
        assertTrue(chromeSite?.success == true)
        assertEquals("OPEN_WEBSITE", chromeSite?.intentName)
        val chromeIntent = shadowApp.nextStartedActivity
        assertEquals("https://wikipedia.org", chromeIntent?.data?.toString())
        assertEquals("com.android.chrome", chromeIntent?.`package`)

        val chromePlain = parser.parseAndExecute("Siri, Chrome mein website open karo.")
        assertTrue(chromePlain?.success == true)
        assertNotNull(shadowApp.nextStartedActivity)
    }

    @Test
    fun testAllFourteenSystemSettingsShortcutsAndSystemControls() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        val parser = SiriIntentParser(AndroidDeviceController(app))

        val settingsCommands = listOf(
            "Hey Siri, Bluetooth settings kholo." to "bluetooth",
            "Hey Siri, Wi-Fi settings kholo." to "wifi",
            "Hey Siri, mobile data settings kholo." to "mobile_data",
            "Hey Siri, hotspot settings kholo." to "hotspot",
            "open location settings" to "location",
            "open display settings" to "display",
            "open sound settings" to "sound",
            "open battery settings" to "battery",
            "open battery optimization settings" to "battery_optimization",
            "open app permissions" to "app_permissions",
            "open notification settings" to "notification",
            "open airplane mode settings" to "airplane_mode",
            "open language settings" to "language",
            "open accessibility settings" to "accessibility"
        )

        for ((command, expectedTarget) in settingsCommands) {
            val res = parser.parseAndExecute(command)
            assertNotNull("Null result for $command", res)
            assertEquals("OPEN_SETTINGS", res?.intentName)
            assertEquals(expectedTarget, res?.parameters?.get("target"))
            assertTrue("Failed for $command", res?.success == true)
            assertNotNull("No activity started for $command", shadowApp.nextStartedActivity)
        }

        // Bluetooth toggle on API 34 safely falls back to Bluetooth Settings instead of bypassing restriction
        val btOn = parser.parseAndExecute("bluetooth on karo")
        assertEquals("BLUETOOTH_ON", btOn?.intentName)
        assertEquals("bluetooth_settings", btOn?.parameters?.get("fallback"))
        assertEquals(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, shadowApp.nextStartedActivity?.action)

        // Brightness adjustment when permitted updates SCREEN_BRIGHTNESS directly
        val permittedParser = SiriIntentParser(AndroidDeviceController(app) { true })
        val brightUpPermitted = permittedParser.parseAndExecute("increase brightness")
        assertEquals("BRIGHTNESS_CONTROL", brightUpPermitted?.intentName)
        assertTrue(brightUpPermitted?.success == true)
        val updatedBrightness = android.provider.Settings.System.getInt(
            app.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
            -1
        )
        assertTrue(updatedBrightness > 128)

        // Brightness adjustment without WRITE_SETTINGS opens official settings fallback cleanly
        val deniedParser = SiriIntentParser(AndroidDeviceController(app) { false })
        val brightUpDenied = deniedParser.parseAndExecute("increase brightness")
        assertEquals("BRIGHTNESS_CONTROL", brightUpDenied?.intentName)
        assertTrue(brightUpDenied?.success == true)
        val brightnessSettingsIntent = shadowApp.nextStartedActivity
        assertNotNull(brightnessSettingsIntent)
        assertEquals(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, brightnessSettingsIntent?.action)
    }

    @Test
    fun testCustomAssistantNameAndUserAddressingPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = SiriRepository(context)
        repo1.clearAllPersonalData()

        assertEquals("Siri", repo1.effectiveAssistantName())
        assertEquals("Friend", repo1.effectiveUserAddress())

        repo1.updateUserPreferences(
            repo1.userPreferences.value.copy(
                assistantName = "Nova",
                userName = "Boss"
            )
        )

        // Reload fresh repository instance to verify permanent SharedPreferences persistence
        val repo2 = SiriRepository(context)
        assertEquals("Nova", repo2.effectiveAssistantName())
        assertEquals("Boss", repo2.effectiveUserAddress())

        // Blank values safely fall back to defaults instead of corrupting identity
        repo2.updateUserPreferences(repo2.userPreferences.value.copy(assistantName = "   ", userName = ""))
        assertEquals("Siri", repo2.effectiveAssistantName())
        assertEquals("Friend", repo2.effectiveUserAddress())

        repo2.clearAllPersonalData()
    }

    @Test
    fun testCustomAssistantWakePhraseAndConversationalResponses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = AndroidDeviceController(context)
        var currentLang = LanguageOption.ENGLISH
        val parser = SiriIntentParser(
            deviceController = controller,
            getCurrentLanguage = { currentLang },
            getAssistantName = { "Aria" },
            getUserAddress = { "Boss" }
        )

        // Dynamic wake phrase stripping for custom assistant name
        assertEquals("turn on flashlight", parser.cleanWakePhrase("Hey Aria, turn on flashlight"))
        assertEquals("what time is it", parser.cleanWakePhrase("Suno Aria, what time is it"))
        assertEquals("battery status", parser.cleanWakePhrase("Aria battery status"))

        // English identity & user address
        val idEn = parser.parseAndExecute("who are you")
        assertEquals("IDENTITY", idEn?.intentName)
        assertTrue(idEn?.feedbackSpeech?.contains("Aria") == true)
        assertTrue(idEn?.feedbackSpeech?.contains("Boss") == true)

        val userIdEn = parser.parseAndExecute("what is my name")
        assertEquals("USER_IDENTITY", userIdEn?.intentName)
        assertTrue(userIdEn?.feedbackSpeech?.contains("Boss") == true)

        // Hinglish identity & greeting
        currentLang = LanguageOption.HINGLISH
        val hiGreeting = parser.parseAndExecute("namaste")
        assertEquals("GREETING", hiGreeting?.intentName)
        assertTrue(hiGreeting?.feedbackSpeech?.contains("Aria") == true)
        assertTrue(hiGreeting?.feedbackSpeech?.contains("Boss") == true)

        // Pure Hindi identity
        currentLang = LanguageOption.HINDI
        val pureHindiId = parser.parseAndExecute("tumhara naam kya hai")
        assertEquals("IDENTITY", pureHindiId?.intentName)
        assertTrue(pureHindiId?.feedbackSpeech?.contains("Aria") == true)
        assertTrue(pureHindiId?.feedbackSpeech?.contains("Boss") == true)
    }

    @Test
    fun testCustomNonSibilantWakeWordContourAndNaturalTtsCadence() {
        val sampleRate = com.example.voice.HeySiriAcousticAnalyzer.SAMPLE_RATE
        val frameSize = com.example.voice.HeySiriAcousticAnalyzer.FRAME_SAMPLES

        val voiced600 = ShortArray(frameSize) { i ->
            (5500.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 600.0 * i / sampleRate)).toInt().toShort()
        }
        val quietGap = ShortArray(frameSize) { 30 }
        val voiced1000 = ShortArray(frameSize) { i ->
            (5200.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * i / sampleRate)).toInt().toShort()
        }

        // Pure voiced multi-syllable contour without /s/ sibilant
        val nonSibilantWakeFrames = buildList {
            repeat(9) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(voiced600)) }
            repeat(3) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(quietGap)) }
            repeat(10) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(voiced1000)) }
        }

        // Strict "Siri" still rejects non-sibilant contour to avoid false triggers
        assertFalse(
            com.example.voice.HeySiriAcousticAnalyzer.matchesWakeWordContour(
                frames = nonSibilantWakeFrames,
                noiseFloorRms = 200.0,
                wakeWordOption = com.example.model.WakeWordOption.HEY_SIRI,
                assistantName = "Siri"
            )
        )
        // Custom non-sibilant name "Aria" accepts the multi-syllable voiced cadence
        assertTrue(
            com.example.voice.HeySiriAcousticAnalyzer.matchesWakeWordContour(
                frames = nonSibilantWakeFrames,
                noiseFloorRms = 200.0,
                wakeWordOption = com.example.model.WakeWordOption.HEY_SIRI,
                assistantName = "Aria"
            )
        )

        // TTS pronunciation smoothing & natural pause cadence
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tts = com.example.voice.SiriTextToSpeech(context) {}
        val spoken = tts.sanitizeForSpeech("Temp is 24°C, battery 85%, e.g. Tea & Coffee.")
        assertTrue(spoken.contains("24 degrees Celsius"))
        assertTrue(spoken.contains("85 percent"))
        assertTrue(spoken.contains("for example"))
        assertTrue(spoken.contains("Tea and Coffee"))

        val chunks = tts.buildSpokenChunksWithPauses(
            "Good morning Boss, here is a quick update on your schedule today. Battery is ready."
        )
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.any { it.second == 65L })
        assertTrue(chunks.any { it.second == 180L })
        assertEquals(0L, chunks.last().second)
        tts.shutdown()

        // AI Brain offline conversational fallback reflects custom identity and follow-up language
        val brain = com.example.ai.AiBrainRepository(context)
        val offlineFollowUp = brain.buildNaturalOfflineReply(
            prompt = "aur batao",
            language = LanguageOption.HINGLISH,
            assistantName = "Nova",
            userAddress = "Prince",
            recentHistory = listOf("hi" to true, "Hello Prince!" to false)
        )
        assertTrue(offlineFollowUp.contains("Prince"))
    }

    @Test
    fun testBackgroundMicStabilityAppSwitchAndBridgeRecovery() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.grantPermissions(android.Manifest.permission.RECORD_AUDIO)

        val vm = com.example.viewmodel.SiriViewModel(app)
        com.example.service.SiriServiceBridge.setAppInForeground(true)

        val controller = org.robolectric.Robolectric.buildService(
            com.example.service.SiriVoiceService::class.java,
            android.content.Intent(app, com.example.service.SiriVoiceService::class.java).apply {
                action = com.example.service.SiriVoiceService.ACTION_START
            }
        )
        val service = controller.create().startCommand(0, 1).get()
        assertTrue(com.example.service.SiriVoiceService.isServiceRunning)

        // Calling SiriVoiceService.start() while already running & active should be idempotent (no duplicate startForegroundService)
        com.example.service.SiriServiceBridge.setWakeEngineStatus(com.example.voice.WakeEngineStatus.LISTENING)
        com.example.service.SiriVoiceService.start(app)
        assertEquals(null, shadowApp.nextStartedService)

        // Simulate user tapping mic, then switching to WhatsApp/Instagram/YouTube/Chrome before speaking
        vm.startListening()
        assertTrue(com.example.service.SiriServiceBridge.isConversationActive.value)
        com.example.service.SiriServiceBridge.setAppInForeground(false)
        vm.onAppBackgrounded()
        assertFalse(com.example.service.SiriServiceBridge.isConversationActive.value)

        // Swiping task / backgrounding should preserve running foreground service when enabled
        service.onTaskRemoved(android.content.Intent(app, com.example.MainActivity::class.java))
        assertTrue(com.example.service.SiriVoiceService.isServiceRunning)

        // Stop cleanly
        service.onStartCommand(
            android.content.Intent(app, com.example.service.SiriVoiceService::class.java).apply {
                action = com.example.service.SiriVoiceService.ACTION_STOP
            },
            0,
            2
        )
        assertFalse(com.example.service.SiriVoiceService.isServiceRunning)
        controller.destroy()
        vm.stopAllSpeech()
    }

    @Test
    fun testContinuousMediaAudioRejectedByWakeWordContour() {
        val sampleRate = com.example.voice.HeySiriAcousticAnalyzer.SAMPLE_RATE
        val frameSize = com.example.voice.HeySiriAcousticAnalyzer.FRAME_SAMPLES

        val loudSpeech = ShortArray(frameSize) { i ->
            (4800.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 600.0 * i / sampleRate)).toInt().toShort()
        }
        val sibilant = ShortArray(frameSize) { i ->
            (3000.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 5500.0 * i / sampleRate)).toInt().toShort()
        }

        // Simulate 108 frames (~2.16s) of continuous loud YouTube/Instagram dialogue without natural phrase end
        val continuousMediaFrames = buildList {
            repeat(50) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(loudSpeech)) }
            repeat(8) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(sibilant)) }
            repeat(50) { add(com.example.voice.HeySiriAcousticAnalyzer.extractFrameFeatures(loudSpeech)) }
        }
        assertFalse(
            com.example.voice.HeySiriAcousticAnalyzer.matchesWakeWordContour(
                frames = continuousMediaFrames,
                noiseFloorRms = 220.0,
                wakeWordOption = com.example.model.WakeWordOption.HEY_SIRI,
                assistantName = "Siri"
            )
        )
    }
}
