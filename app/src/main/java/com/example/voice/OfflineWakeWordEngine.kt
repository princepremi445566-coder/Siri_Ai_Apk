package com.example.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.WakeWordOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

enum class WakeEngineStatus(val displayLabel: String) {
    STOPPED("Wake-word stopped"),
    LISTENING("Listening offline for 'Hey Siri'"),
    PAUSED_COMMAND("Processing voice command"),
    PAUSED_SPEAKING("Paused while assistant speaks"),
    PAUSED_CALL("Paused during phone call"),
    MIC_BUSY("Microphone temporarily in use by another app"),
    PERMISSION_MISSING("Microphone permission required"),
    UNSUPPORTED_HARDWARE("Microphone hardware unavailable");

    fun labelForAssistant(assistantName: String): String {
        val cleanName = assistantName.trim().ifBlank { "Siri" }
        return when (this) {
            LISTENING -> "Listening offline for 'Hey $cleanName'"
            PAUSED_SPEAKING -> "Paused while $cleanName speaks"
            else -> displayLabel
        }
    }
}

/**
 * Pure Kotlin DSP analyzer for offline "Hey Siri" / "Suno Siri" wake-word cadence & acoustic verification.
 * Runs 100% locally in RAM with zero network calls and zero disk recording.
 */
object HeySiriAcousticAnalyzer {

    const val SAMPLE_RATE = 16000
    const val FRAME_SAMPLES = 320 // 20ms @ 16kHz

    private val goertzel600Coeff = 2.0 * cos(2.0 * PI * 600.0 / SAMPLE_RATE)
    private val goertzel1000Coeff = 2.0 * cos(2.0 * PI * 1000.0 / SAMPLE_RATE)
    private val goertzel5000Coeff = 2.0 * cos(2.0 * PI * 5000.0 / SAMPLE_RATE)
    private val goertzel6200Coeff = 2.0 * cos(2.0 * PI * 6200.0 / SAMPLE_RATE)

    data class FrameFeatures(
        val rms: Double,
        val zcr: Double,
        val voicedEnergy: Double,
        val sibilantEnergy: Double
    )

    fun extractFrameFeatures(samples: ShortArray, count: Int = samples.size): FrameFeatures {
        if (count <= 1) return FrameFeatures(0.0, 0.0, 0.0, 0.0)

        var sumSq = 0.0
        var zeroCrossings = 0
        var prev = samples[0].toInt()

        var q1_600 = 0.0
        var q2_600 = 0.0
        var q1_1000 = 0.0
        var q2_1000 = 0.0
        var q1_5000 = 0.0
        var q2_5000 = 0.0
        var q1_6200 = 0.0
        var q2_6200 = 0.0

        for (i in 0 until count) {
            val sInt = samples[i].toInt()
            val sNorm = sInt / 32768.0
            sumSq += sInt.toDouble() * sInt.toDouble()

            if (i > 0 && ((prev >= 0 && sInt < 0) || (prev < 0 && sInt >= 0))) {
                zeroCrossings++
            }
            prev = sInt

            val q0_600 = goertzel600Coeff * q1_600 - q2_600 + sNorm
            q2_600 = q1_600
            q1_600 = q0_600

            val q0_1000 = goertzel1000Coeff * q1_1000 - q2_1000 + sNorm
            q2_1000 = q1_1000
            q1_1000 = q0_1000

            val q0_5000 = goertzel5000Coeff * q1_5000 - q2_5000 + sNorm
            q2_5000 = q1_5000
            q1_5000 = q0_5000

            val q0_6200 = goertzel6200Coeff * q1_6200 - q2_6200 + sNorm
            q2_6200 = q1_6200
            q1_6200 = q0_6200
        }

        val rms = sqrt(sumSq / count)
        val zcr = zeroCrossings.toDouble() / (count - 1)
        val e600 = q1_600 * q1_600 + q2_600 * q2_600 - q1_600 * q2_600 * goertzel600Coeff
        val e1000 = q1_1000 * q1_1000 + q2_1000 * q2_1000 - q1_1000 * q2_1000 * goertzel1000Coeff
        val e5000 = q1_5000 * q1_5000 + q2_5000 * q2_5000 - q1_5000 * q2_5000 * goertzel5000Coeff
        val e6200 = q1_6200 * q1_6200 + q2_6200 * q2_6200 - q1_6200 * q2_6200 * goertzel6200Coeff

        return FrameFeatures(
            rms = rms,
            zcr = zcr,
            voicedEnergy = e600 + e1000,
            sibilantEnergy = e5000 + e6200
        )
    }

    fun hasSibilantPhoneme(assistantName: String): Boolean {
        val clean = assistantName.trim().lowercase(java.util.Locale.ROOT)
        if (clean.isBlank()) return true
        return clean.any { it in setOf('s', 'z', 'c', 'x', 'स', 'श', 'ष', 'ज') }
    }

    data class WakeWordMatchResult(
        val matched: Boolean,
        val confidence: Float,
        val peakSnr: Double,
        val durationFrames: Int
    )

    /**
     * Evaluates a captured speech burst (sequence of 20ms FrameFeatures) with multi-factor
     * acoustic confidence scoring to prevent false activations on TV, background dialogue,
     * and ambient noise:
     * - Compact wake-phrase duration (17..85 frames for "Hey Siri" / "Suno Siri")
     * - Peak SNR >= 3.0x noise floor and healthy dynamic crest factor
     * - Temporal phonetic ordering for sibilant names ("Siri"): voiced onset -> /s/ fricative -> tail vowel
     * - Bounded syllable count (rejects long multi-word conversation sentences)
     */
    fun evaluateWakeWordContour(
        frames: List<FrameFeatures>,
        noiseFloorRms: Double,
        wakeWordOption: WakeWordOption = WakeWordOption.HEY_SIRI,
        assistantName: String = "Siri"
    ): WakeWordMatchResult {
        val maxFramesForOption = when (wakeWordOption) {
            WakeWordOption.SIRI -> 68
            WakeWordOption.HEY_SIRI, WakeWordOption.SUNO_SIRI -> 85
        }
        if (frames.size < 17 || frames.size > maxFramesForOption) {
            return WakeWordMatchResult(false, 0f, 0.0, frames.size)
        }

        val safeFloor = noiseFloorRms.coerceAtLeast(90.0)
        val speechThreshold = (safeFloor * 2.45).coerceAtLeast(350.0)
        var activeSpeechFrames = 0
        var voicedFrames = 0
        var sibilantFrames = 0
        var syllablePeaks = 0
        var inSyllable = false
        val peakThreshold = speechThreshold * 1.15
        val dipThreshold = speechThreshold * 0.72

        var maxRms = 0.0
        var activeRmsSum = 0.0
        var firstVoicedIdx = -1
        var firstSibilantIdx = -1
        var lastSibilantIdx = -1
        var voicedAfterSibilantFrames = 0

        for ((idx, frame) in frames.withIndex()) {
            if (frame.rms > maxRms) maxRms = frame.rms
            if (frame.rms >= speechThreshold) {
                activeSpeechFrames++
                activeRmsSum += frame.rms
            }
            val isVoicedFrame = frame.voicedEnergy > frame.sibilantEnergy * 0.85 && frame.rms >= speechThreshold * 0.85
            if (isVoicedFrame) {
                voicedFrames++
                if (firstVoicedIdx == -1) firstVoicedIdx = idx
                if (firstSibilantIdx != -1) voicedAfterSibilantFrames++
            }
            // Sibilant /s/ in "Siri" exhibits high zero-crossing rate and high-band energy
            val isSibilantFrame = frame.zcr >= 0.16 && frame.sibilantEnergy > frame.voicedEnergy * 0.12 && frame.rms >= safeFloor * 1.5
            if (isSibilantFrame) {
                sibilantFrames++
                if (firstSibilantIdx == -1) firstSibilantIdx = idx
                lastSibilantIdx = idx
            }

            if (!inSyllable && frame.rms >= peakThreshold) {
                inSyllable = true
                syllablePeaks++
            } else if (inSyllable && frame.rms < dipThreshold) {
                inSyllable = false
            }
        }

        // Reject continuous speaker music / TV dialogue that runs near capture ceiling without pause
        if (frames.size >= 82 && activeSpeechFrames > (frames.size * 0.85)) {
            return WakeWordMatchResult(false, 0.10f, maxRms / safeFloor, frames.size)
        }

        val peakSnr = maxRms / safeFloor
        val meanActiveRms = if (activeSpeechFrames > 0) activeRmsSum / activeSpeechFrames else 0.0
        val meanSnr = meanActiveRms / safeFloor

        val minActiveFrames = when (wakeWordOption) {
            WakeWordOption.SIRI -> 10
            WakeWordOption.HEY_SIRI, WakeWordOption.SUNO_SIRI -> 14
        }
        val minPeaks = when (wakeWordOption) {
            WakeWordOption.SIRI -> 1
            WakeWordOption.HEY_SIRI, WakeWordOption.SUNO_SIRI -> 2
        }
        val maxPeaks = when (wakeWordOption) {
            WakeWordOption.SIRI -> 3
            WakeWordOption.HEY_SIRI, WakeWordOption.SUNO_SIRI -> 4
        }
        val requiresSibilant = hasSibilantPhoneme(assistantName)

        // Temporal phonetic sequence check:
        // "Hey Siri" / "Suno Siri" must have voiced onset ("Hey"/"Suno") before /s/, AND voiced tail ("iri") after /s/
        val validPhoneticOrder = if (!requiresSibilant) {
            true
        } else if (wakeWordOption == WakeWordOption.SIRI) {
            sibilantFrames >= 2 && voicedAfterSibilantFrames >= 4
        } else {
            val sibilantSpan = if (firstSibilantIdx >= 0 && lastSibilantIdx >= firstSibilantIdx) {
                lastSibilantIdx - firstSibilantIdx + 1
            } else 0
            // One compact sibilant island (not scattered /s/ across an unrelated multi-word sentence)
            sibilantFrames >= 2 &&
                firstVoicedIdx in 0 until firstSibilantIdx &&
                voicedAfterSibilantFrames >= 4 &&
                sibilantSpan <= 18
        }

        val hardPass = activeSpeechFrames >= minActiveFrames &&
            voicedFrames >= 6 &&
            peakSnr >= 3.0 &&
            meanSnr >= 2.25 &&
            syllablePeaks in minPeaks..maxPeaks &&
            (!requiresSibilant || (sibilantFrames >= 2 && validPhoneticOrder))

        if (!hardPass) {
            return WakeWordMatchResult(false, 0.2f, peakSnr, frames.size)
        }

        // Weighted confidence score (0f..1f)
        val snrScore = ((peakSnr - 2.5) / 6.0).coerceIn(0.0, 1.0).toFloat()
        val durationScore = if (frames.size in 20..62) 1.0f else 0.72f
        val peakScore = if (syllablePeaks in minPeaks..3) 1.0f else 0.78f
        val sequenceScore = if (validPhoneticOrder) 1.0f else 0.0f
        val confidence = (snrScore * 0.35f) + (durationScore * 0.25f) + (peakScore * 0.15f) + (sequenceScore * 0.25f)

        val requiredConfidence = if (safeFloor > 420.0) 0.72f else 0.66f
        return WakeWordMatchResult(
            matched = confidence >= requiredConfidence,
            confidence = confidence,
            peakSnr = peakSnr,
            durationFrames = frames.size
        )
    }

    fun matchesWakeWordContour(
        frames: List<FrameFeatures>,
        noiseFloorRms: Double,
        wakeWordOption: WakeWordOption = WakeWordOption.HEY_SIRI,
        assistantName: String = "Siri"
    ): Boolean {
        return evaluateWakeWordContour(frames, noiseFloorRms, wakeWordOption, assistantName).matched
    }
}

/**
 * Lightweight, battery-optimized offline wake-word detector using AudioRecord + DSP contour matching.
 * Features:
 * - Single serialized capture coroutine guarded by Mutex (no overlapping AudioRecord sessions)
 * - Idempotent start()/resume() (never tears down a healthy AudioRecord when already listening)
 * - AudioRecordingCallback monitoring for Android 10+ silencing / mic preemption by apps like WhatsApp or Camera
 * - False-trigger adaptive cooldown to prevent speaker media (YouTube/Instagram) ping-pong
 */
class OfflineWakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: (WakeWordOption) -> Unit,
    private val onStatusChanged: (WakeEngineStatus) -> Unit
) {
    companion object {
        private const val TAG = "WakeEngine"
        private const val BASE_COOLDOWN_MS = 2_500L
        private const val MAX_COOLDOWN_MS = 10_000L
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineMutex = Mutex()
    private var captureJob: Job? = null

    private val isEnabled = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isSilencedBySystem = AtomicBoolean(false)
    private val lastTriggerTimestamp = AtomicLong(0L)
    private val consecutiveFalseTriggers = AtomicInteger(0)
    private val activeAudioSessionId = AtomicInteger(AudioManager.AUDIO_SESSION_ID_GENERATE)

    @Volatile
    private var currentWakeOption: WakeWordOption = WakeWordOption.HEY_SIRI

    @Volatile
    private var customAssistantName: String = "Siri"

    @Volatile
    private var currentStatus: WakeEngineStatus = WakeEngineStatus.STOPPED

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    fun getStatus(): WakeEngineStatus = currentStatus

    fun isActivelyCapturing(): Boolean = isEnabled.get() && !isPaused.get() && captureJob?.isActive == true

    fun isOnDeviceRecognizerSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    }

    fun setWakeWordOption(option: WakeWordOption) {
        currentWakeOption = option
    }

    fun setCustomAssistantName(name: String) {
        customAssistantName = name.trim().ifBlank { "Siri" }
    }

    fun recordFalseWakeTrigger() {
        val count = consecutiveFalseTriggers.incrementAndGet().coerceAtMost(4)
        Log.d(TAG, "Recorded false wake trigger #$count; extending cooldown to ${effectiveCooldownMs()}ms")
    }

    fun recordValidCommandRecognized() {
        if (consecutiveFalseTriggers.getAndSet(0) > 0) {
            Log.d(TAG, "Valid command recognized; reset wake cooldown to ${BASE_COOLDOWN_MS}ms")
        }
    }

    private fun effectiveCooldownMs(): Long {
        val falseCount = consecutiveFalseTriggers.get()
        return when {
            falseCount <= 0 -> BASE_COOLDOWN_MS
            falseCount == 1 -> 5_000L
            falseCount == 2 -> 7_500L
            else -> MAX_COOLDOWN_MS
        }
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "start() skipped: RECORD_AUDIO permission missing")
            updateStatus(WakeEngineStatus.PERMISSION_MISSING)
            return
        }
        val wasEnabled = isEnabled.getAndSet(true)
        val wasPaused = isPaused.getAndSet(false)
        if (wasEnabled && !wasPaused && captureJob?.isActive == true) {
            Log.d(TAG, "start() no-op: wake engine already active (status=$currentStatus)")
            return
        }
        Log.d(TAG, "start() launching wake-word capture loop (wasEnabled=$wasEnabled, wasPaused=$wasPaused)")
        ensureCaptureLoopRunning()
    }

    fun pauseForCommand() {
        Log.d(TAG, "pauseForCommand()")
        isPaused.set(true)
        updateStatus(WakeEngineStatus.PAUSED_COMMAND)
        cancelCaptureLoopLockedAsync()
    }

    fun pauseForSpeaking() {
        Log.d(TAG, "pauseForSpeaking()")
        isPaused.set(true)
        updateStatus(WakeEngineStatus.PAUSED_SPEAKING)
        cancelCaptureLoopLockedAsync()
    }

    fun resume() {
        if (!isEnabled.get()) {
            Log.d(TAG, "resume() ignored: engine disabled")
            return
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus(WakeEngineStatus.PERMISSION_MISSING)
            return
        }
        val wasPaused = isPaused.getAndSet(false)
        if (!wasPaused && captureJob?.isActive == true) {
            Log.d(TAG, "resume() no-op: capture loop already active")
            return
        }
        Log.d(TAG, "resume() restarting wake-word capture loop")
        ensureCaptureLoopRunning()
    }

    fun stop() {
        Log.d(TAG, "stop()")
        isEnabled.set(false)
        isPaused.set(false)
        isSilencedBySystem.set(false)
        cancelCaptureLoopLockedAsync()
        unregisterAudioRecordingCallback()
        updateStatus(WakeEngineStatus.STOPPED)
    }

    fun destroy() {
        Log.d(TAG, "destroy()")
        stop()
        scope.cancel()
    }

    private fun cancelCaptureLoopLockedAsync() {
        scope.launch {
            engineMutex.withLock {
                val job = captureJob
                captureJob = null
                if (job != null && job.isActive) {
                    job.cancelAndJoin()
                }
            }
        }
    }

    private fun ensureCaptureLoopRunning() {
        scope.launch {
            engineMutex.withLock {
                if (!isEnabled.get() || isPaused.get()) return@withLock
                if (captureJob?.isActive == true) return@withLock

                registerAudioRecordingCallback()
                captureJob = scope.launch {
                    runSerializedCaptureLoop()
                }
            }
        }
    }

    private fun registerAudioRecordingCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (recordingCallback != null) return
        val am = audioManager ?: return

        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                val mySessionId = activeAudioSessionId.get()
                var silenced = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val myConfig = configs.firstOrNull { it.clientAudioSessionId == mySessionId }
                    if (myConfig != null && myConfig.isClientSilenced) {
                        silenced = true
                    }
                    // Also treat another active foreground non-assistant capture session as busy
                    val otherActive = configs.any {
                        it.clientAudioSessionId != mySessionId && !it.isClientSilenced
                    }
                    if (otherActive && !isPaused.get()) {
                        silenced = true
                    }
                } else {
                    val otherActive = configs.any { it.clientAudioSessionId != mySessionId }
                    if (otherActive && !isPaused.get()) {
                        silenced = true
                    }
                }
                val prev = isSilencedBySystem.getAndSet(silenced)
                if (prev != silenced) {
                    Log.d(TAG, "AudioRecordingCallback: systemSilenced=$silenced (configs=${configs.size}, mySession=$mySessionId)")
                }
            }
        }
        recordingCallback = callback
        try {
            am.registerAudioRecordingCallback(callback, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register AudioRecordingCallback: ${e.message}")
            recordingCallback = null
        }
    }

    private fun unregisterAudioRecordingCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val cb = recordingCallback ?: return
        recordingCallback = null
        try {
            audioManager?.unregisterAudioRecordingCallback(cb)
        } catch (_: Exception) {
        }
    }

    private fun hasActiveCall(): Boolean {
        val audioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        return audioMode == AudioManager.MODE_IN_CALL ||
            audioMode == AudioManager.MODE_RINGTONE ||
            audioMode == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun hasExternalActiveMicCapture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val am = audioManager ?: return false
        return try {
            val mySession = activeAudioSessionId.get()
            val configs = am.activeRecordingConfigurations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                configs.any { it.clientAudioSessionId != mySession && !it.isClientSilenced }
            } else {
                configs.any { it.clientAudioSessionId != mySession }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun updateStatus(status: WakeEngineStatus) {
        if (currentStatus != status) {
            currentStatus = status
            onStatusChanged(status)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runSerializedCaptureLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Exception) {
        }

        val minBuf = AudioRecord.getMinBufferSize(
            HeySiriAcousticAnalyzer.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize returned invalid size: $minBuf")
            updateStatus(WakeEngineStatus.UNSUPPORTED_HARDWARE)
            return
        }

        val bufferSize = maxOf(minBuf * 2, HeySiriAcousticAnalyzer.FRAME_SAMPLES * 16)
        val frameBuffer = ShortArray(HeySiriAcousticAnalyzer.FRAME_SAMPLES)
        var backoffMs = 1_000L

        while (scope.isActive && isEnabled.get() && !isPaused.get()) {
            // Respect active calls or VoIP sessions cleanly without opening AudioRecord
            if (hasActiveCall()) {
                updateStatus(WakeEngineStatus.PAUSED_CALL)
                delay(1_500L)
                continue
            }

            // Do not fight another active microphone app (WhatsApp voice, Camera, etc.)
            if (isSilencedBySystem.get() || hasExternalActiveMicCapture()) {
                updateStatus(WakeEngineStatus.MIC_BUSY)
                delay(1_500L)
                // Re-check live configurations in case callback didn't fire for release
                isSilencedBySystem.set(hasExternalActiveMicCapture())
                continue
            }

            var recorder: AudioRecord? = null
            try {
                val activeRecorder = createAudioRecord(bufferSize)
                if (activeRecorder == null || activeRecorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord initialization failed (state=${activeRecorder?.state}); backing off ${backoffMs}ms")
                    activeRecorder?.release()
                    updateStatus(WakeEngineStatus.MIC_BUSY)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(6_000L)
                    continue
                }
                recorder = activeRecorder
                activeAudioSessionId.set(activeRecorder.audioSessionId)

                activeRecorder.startRecording()
                if (activeRecorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.w(TAG, "AudioRecord failed to enter RECORDSTATE_RECORDING; backing off ${backoffMs}ms")
                    activeAudioSessionId.set(AudioManager.AUDIO_SESSION_ID_GENERATE)
                    activeRecorder.release()
                    recorder = null
                    updateStatus(WakeEngineStatus.MIC_BUSY)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(6_000L)
                    continue
                }

                Log.i(TAG, "AudioRecord started stably (sessionId=${activeRecorder.audioSessionId}, source=${activeRecorder.audioSource})")
                backoffMs = 1_000L
                updateStatus(WakeEngineStatus.LISTENING)

                var noiseFloor = 240.0
                var speechActive = false
                var silenceFrames = 0
                var onsetConfirmCount = 0
                var prevOnsetFrame: HeySiriAcousticAnalyzer.FrameFeatures? = null
                var consecutiveZeroReads = 0
                var consecutiveSilencedFrames = 0
                val candidateFrames = ArrayList<HeySiriAcousticAnalyzer.FrameFeatures>(90)

                while (scope.isActive && isEnabled.get() && !isPaused.get()) {
                    if (hasActiveCall()) {
                        Log.d(TAG, "Yielding AudioRecord for active phone/VoIP call")
                        updateStatus(WakeEngineStatus.PAUSED_CALL)
                        break
                    }

                    if (isSilencedBySystem.get()) {
                        Log.d(TAG, "Yielding AudioRecord: silenced by higher-priority foreground mic user")
                        updateStatus(WakeEngineStatus.MIC_BUSY)
                        break
                    }

                    val readCount = activeRecorder.read(frameBuffer, 0, frameBuffer.size)
                    if (readCount < 0) {
                        Log.w(TAG, "AudioRecord.read error $readCount; releasing and backing off")
                        updateStatus(WakeEngineStatus.MIC_BUSY)
                        break
                    }
                    if (readCount == 0) {
                        consecutiveZeroReads++
                        if (consecutiveZeroReads >= 25) {
                            Log.w(TAG, "AudioRecord returned 25 consecutive 0-sample reads; resetting cleanly")
                            updateStatus(WakeEngineStatus.MIC_BUSY)
                            break
                        }
                        delay(20L)
                        continue
                    }
                    consecutiveZeroReads = 0

                    val features = HeySiriAcousticAnalyzer.extractFrameFeatures(frameBuffer, readCount)

                    // Ignore muted/zeroed frames (common on Android 10+ right when another app preempts mic)
                    if (features.rms < 1.0) {
                        consecutiveSilencedFrames++
                        if (consecutiveSilencedFrames >= 30 && hasExternalActiveMicCapture()) {
                            Log.d(TAG, "Detected zeroed audio frames with external mic client active -> yielding mic")
                            isSilencedBySystem.set(true)
                            updateStatus(WakeEngineStatus.MIC_BUSY)
                            break
                        }
                        continue
                    }
                    consecutiveSilencedFrames = 0

                    val onsetThreshold = (noiseFloor * 3.1).coerceAtLeast(450.0)

                    if (!speechActive) {
                        if (features.rms < onsetThreshold * 0.85) {
                            onsetConfirmCount = 0
                            prevOnsetFrame = null
                            noiseFloor = (noiseFloor * 0.985 + features.rms * 0.015).coerceIn(110.0, 750.0)
                        } else if (features.rms >= onsetThreshold && features.voicedEnergy > 0.0) {
                            onsetConfirmCount++
                            if (onsetConfirmCount >= 2) {
                                speechActive = true
                                silenceFrames = 0
                                candidateFrames.clear()
                                prevOnsetFrame?.let { candidateFrames.add(it) }
                                candidateFrames.add(features)
                                onsetConfirmCount = 0
                                prevOnsetFrame = null
                            } else {
                                prevOnsetFrame = features
                            }
                        }

                        if (!speechActive && powerManager?.isPowerSaveMode == true) {
                            delay(10L)
                        }
                    } else {
                        candidateFrames.add(features)
                        if (features.rms < noiseFloor * 1.9) {
                            silenceFrames++
                        } else {
                            silenceFrames = 0
                        }

                        // End of speech burst (~240ms silence hangover) or max wake-word window (~1.8s)
                        if (silenceFrames >= 12 || candidateFrames.size >= 90) {
                            val eval = HeySiriAcousticAnalyzer.evaluateWakeWordContour(
                                frames = candidateFrames,
                                noiseFloorRms = noiseFloor,
                                wakeWordOption = currentWakeOption,
                                assistantName = customAssistantName
                            )
                            candidateFrames.clear()
                            speechActive = false
                            silenceFrames = 0

                            val now = System.currentTimeMillis()
                            val cooldownMs = effectiveCooldownMs()
                            if (eval.matched && (now - lastTriggerTimestamp.get()) > cooldownMs) {
                                lastTriggerTimestamp.set(now)
                                Log.d(
                                    TAG,
                                    "Offline wake-word detected (${currentWakeOption.label}/$customAssistantName, conf=${"%.2f".format(eval.confidence)}, snr=${"%.1f".format(eval.peakSnr)})"
                                )
                                isPaused.set(true)
                                updateStatus(WakeEngineStatus.PAUSED_COMMAND)
                                try {
                                    activeRecorder.stop()
                                } catch (_: Exception) {
                                }
                                activeRecorder.release()
                                activeAudioSessionId.set(AudioManager.AUDIO_SESSION_ID_GENERATE)
                                recorder = null
                                onWakeWordDetected(currentWakeOption)
                                return
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission denied during capture", e)
                updateStatus(WakeEngineStatus.PERMISSION_MISSING)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Wake-word capture exception: ${e.message}")
                updateStatus(WakeEngineStatus.MIC_BUSY)
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(6_000L)
            } finally {
                activeAudioSessionId.set(AudioManager.AUDIO_SESSION_ID_GENERATE)
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                try {
                    recorder?.release()
                } catch (_: Exception) {
                }
            }

            if (scope.isActive && isEnabled.get() && !isPaused.get()) {
                delay(400L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferSize: Int): AudioRecord? {
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                HeySiriAcousticAnalyzer.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).let { rec ->
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    rec
                } else {
                    rec.release()
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        HeySiriAcousticAnalyzer.SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            null
        }
    }
}
