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
}

