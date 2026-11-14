package com.example.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
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
import java.util.concurrent.atomic.AtomicBoolean
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

    /**
     * Evaluates a captured speech burst (sequence of 20ms FrameFeatures) to decide whether it matches
     * the acoustic signature of "Hey <AssistantName>" / "Suno <AssistantName>" / "<AssistantName>":
     * - Duration: 340ms - 2200ms (17 to 110 frames)
     * - Multi-syllable structure (at least 2 distinct voiced/syllable energy peaks or strong 2-part contour)
     * - Sibilant fricative check when the configured assistant name contains sibilants (e.g. "Siri", "Jarvis"),
     *   or relaxed voiced-cadence contour when the custom name is non-sibilant (e.g. "Aria", "Nova", "Buddy").
     */
    fun matchesWakeWordContour(
        frames: List<FrameFeatures>,
        noiseFloorRms: Double,
        wakeWordOption: WakeWordOption = WakeWordOption.HEY_SIRI,
        assistantName: String = "Siri"
    ): Boolean {
        if (frames.size < 17 || frames.size > 110) return false

        val speechThreshold = (noiseFloorRms * 2.3).coerceAtLeast(320.0)
        var activeSpeechFrames = 0
        var voicedFrames = 0
        var sibilantFrames = 0
        var syllablePeaks = 0
        var inSyllable = false
        val peakThreshold = speechThreshold * 1.15
        val dipThreshold = speechThreshold * 0.75

        for (frame in frames) {
            if (frame.rms >= speechThreshold) {
                activeSpeechFrames++
            }
            if (frame.voicedEnergy > frame.sibilantEnergy * 0.8 && frame.rms >= speechThreshold * 0.85) {
                voicedFrames++
            }
            // Sibilant /s/ in "Siri" exhibits high zero-crossing rate and high-band energy
            if (frame.zcr >= 0.16 && frame.sibilantEnergy > frame.voicedEnergy * 0.12 && frame.rms >= noiseFloorRms * 1.5) {
                sibilantFrames++
            }

            if (!inSyllable && frame.rms >= peakThreshold) {
                inSyllable = true
                syllablePeaks++
            } else if (inSyllable && frame.rms < dipThreshold) {
                inSyllable = false
            }
        }

        val minActiveFrames = when (wakeWordOption) {
            WakeWordOption.SIRI -> 10
            WakeWordOption.HEY_SIRI, WakeWordOption.SUNO_SIRI -> 14
        }
        val minPeaks = when (wakeWordOption) {
            WakeWordOption.SIRI -> 1
            WakeWordOption.HEY_SIRI, WakeWordOption.SUNO_SIRI -> 2
        }
        val requiresSibilant = hasSibilantPhoneme(assistantName)

        return activeSpeechFrames >= minActiveFrames &&
            voicedFrames >= 6 &&
            (!requiresSibilant || sibilantFrames >= 2) &&
            syllablePeaks >= minPeaks
    }
}

/**
 * Lightweight, battery-optimized offline wake-word detector using AudioRecord + DSP contour matching.
 * Never records to disk and never streams audio to any remote server.
 */
class OfflineWakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: (WakeWordOption) -> Unit,
    private val onStatusChanged: (WakeEngineStatus) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    private val isEnabled = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val lastTriggerTimestamp = java.util.concurrent.atomic.AtomicLong(0L)

    @Volatile
    private var currentWakeOption: WakeWordOption = WakeWordOption.HEY_SIRI

    @Volatile
    private var customAssistantName: String = "Siri"

    @Volatile
    private var currentStatus: WakeEngineStatus = WakeEngineStatus.STOPPED

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    fun getStatus(): WakeEngineStatus = currentStatus

    fun isOnDeviceRecognizerSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    fun setWakeWordOption(option: WakeWordOption) {
        currentWakeOption = option
    }

    fun setCustomAssistantName(name: String) {
        customAssistantName = name.trim().ifBlank { "Siri" }
    }

    @Synchronized
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus(WakeEngineStatus.PERMISSION_MISSING)
            return
        }
        isPaused.set(false)
        if (isEnabled.compareAndSet(false, true) || captureJob?.isActive != true) {
            isEnabled.set(true)
            launchCaptureLoop()
        } else {
            updateStatus(WakeEngineStatus.LISTENING)
        }
    }

    @Synchronized
    fun pauseForCommand() {
        isPaused.set(true)
        updateStatus(WakeEngineStatus.PAUSED_COMMAND)
        stopCaptureLoopBlocking()
    }

    @Synchronized
    fun pauseForSpeaking() {
        isPaused.set(true)
        updateStatus(WakeEngineStatus.PAUSED_SPEAKING)
        stopCaptureLoopBlocking()
    }

    @Synchronized
    fun resume() {
        if (!isEnabled.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus(WakeEngineStatus.PERMISSION_MISSING)
            return
        }
        isPaused.set(false)
        if (captureJob?.isActive != true) {
            launchCaptureLoop()
        } else {
            updateStatus(WakeEngineStatus.LISTENING)
        }
    }

    @Synchronized
    fun stop() {
        isEnabled.set(false)
        isPaused.set(false)
        stopCaptureLoopBlocking()
        updateStatus(WakeEngineStatus.STOPPED)
    }

    @Synchronized
    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun stopCaptureLoopBlocking() {
        val job = captureJob
        captureJob = null
        job?.cancel()
    }

    private fun updateStatus(status: WakeEngineStatus) {
        if (currentStatus != status) {
            currentStatus = status
            onStatusChanged(status)
        }
    }

    @SuppressLint("MissingPermission")
    private fun launchCaptureLoop() {
        val oldJob = captureJob
        captureJob = scope.launch {
            oldJob?.cancelAndJoin()
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
                updateStatus(WakeEngineStatus.UNSUPPORTED_HARDWARE)
                return@launch
            }

            val bufferSize = maxOf(minBuf * 2, HeySiriAcousticAnalyzer.FRAME_SAMPLES * 16)
            val frameBuffer = ShortArray(HeySiriAcousticAnalyzer.FRAME_SAMPLES)
            var backoffMs = 500L

            while (isActive && isEnabled.get() && !isPaused.get()) {
                // Respect active phone calls / VoIP calls
                val audioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
                if (audioMode == AudioManager.MODE_IN_CALL ||
                    audioMode == AudioManager.MODE_RINGTONE ||
                    audioMode == AudioManager.MODE_IN_COMMUNICATION
                ) {
                    updateStatus(WakeEngineStatus.PAUSED_CALL)
                    delay(1000L)
                    continue
                }

                var recorder: AudioRecord? = null
                try {
                    val activeRecorder = createAudioRecord(bufferSize)
                    if (activeRecorder == null || activeRecorder.state != AudioRecord.STATE_INITIALIZED) {
                        activeRecorder?.release()
                        updateStatus(WakeEngineStatus.MIC_BUSY)
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(5000L)
                        continue
                    }
                    recorder = activeRecorder

                    activeRecorder.startRecording()
                    if (activeRecorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        activeRecorder.release()
                        recorder = null
                        updateStatus(WakeEngineStatus.MIC_BUSY)
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(5000L)
                        continue
                    }

                    backoffMs = 500L
                    updateStatus(WakeEngineStatus.LISTENING)

                    var noiseFloor = 220.0
                    var speechActive = false
                    var silenceFrames = 0
                    val candidateFrames = ArrayList<HeySiriAcousticAnalyzer.FrameFeatures>(115)

                    while (isActive && isEnabled.get() && !isPaused.get()) {
                        // Yield if a phone call starts mid-session
                        val modeNow = audioManager?.mode ?: AudioManager.MODE_NORMAL
                        if (modeNow == AudioManager.MODE_IN_CALL || modeNow == AudioManager.MODE_IN_COMMUNICATION) {
                            updateStatus(WakeEngineStatus.PAUSED_CALL)
                            break
                        }

                        val readCount = activeRecorder.read(frameBuffer, 0, frameBuffer.size)
                        if (readCount < 0) {
                            Log.w("OfflineWakeWord", "AudioRecord read error $readCount; backing off")
                            updateStatus(WakeEngineStatus.MIC_BUSY)
                            break
                        }
                        if (readCount == 0) {
                            delay(20L)
                            continue
                        }

                        val features = HeySiriAcousticAnalyzer.extractFrameFeatures(frameBuffer, readCount)
                        val onsetThreshold = (noiseFloor * 2.7).coerceAtLeast(360.0)

                        if (!speechActive) {
                            // Slow adaptive noise floor tracking during quiet periods
                            if (features.rms < onsetThreshold * 0.9) {
                                noiseFloor = (noiseFloor * 0.985 + features.rms * 0.015).coerceIn(80.0, 1200.0)
                            } else if (features.rms >= onsetThreshold && features.voicedEnergy > 0.0) {
                                speechActive = true
                                silenceFrames = 0
                                candidateFrames.clear()
                                candidateFrames.add(features)
                            }

                            // Slight battery saver duty yield when Android Power Saver is enabled
                            if (!speechActive && powerManager?.isPowerSaveMode == true) {
                                delay(10L)
                            }
                        } else {
                            candidateFrames.add(features)
                            if (features.rms < noiseFloor * 1.8) {
                                silenceFrames++
                            } else {
                                silenceFrames = 0
                            }

                            // End of speech burst (~260ms silence hangover) or max wake-word window (~2.2s)
                            if (silenceFrames >= 13 || candidateFrames.size >= 110) {
                                val matched = HeySiriAcousticAnalyzer.matchesWakeWordContour(
                                    frames = candidateFrames,
                                    noiseFloorRms = noiseFloor,
                                    wakeWordOption = currentWakeOption,
                                    assistantName = customAssistantName
                                )
                                candidateFrames.clear()
                                speechActive = false
                                silenceFrames = 0

                                val now = System.currentTimeMillis()
                                if (matched && (now - lastTriggerTimestamp.get()) > 2500L) {
                                    lastTriggerTimestamp.set(now)
                                    Log.d("OfflineWakeWord", "Offline wake-word candidate detected (${currentWakeOption.label} / $customAssistantName)")
                                    isPaused.set(true)
                                    updateStatus(WakeEngineStatus.PAUSED_COMMAND)
                                    try {
                                        activeRecorder.stop()
                                    } catch (_: Exception) {
                                    }
                                    activeRecorder.release()
                                    recorder = null
                                    onWakeWordDetected(currentWakeOption)
                                    return@launch
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("OfflineWakeWord", "Microphone permission denied", e)
                    updateStatus(WakeEngineStatus.PERMISSION_MISSING)
                    return@launch
                } catch (e: Exception) {
                    Log.e("OfflineWakeWord", "Wake-word capture error: ${e.message}")
                    updateStatus(WakeEngineStatus.MIC_BUSY)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(5000L)
                } finally {
                    try {
                        recorder?.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        recorder?.release()
                    } catch (_: Exception) {
                    }
                }
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
            Log.e("OfflineWakeWord", "Failed to create AudioRecord", e)
            null
        }
    }
}
