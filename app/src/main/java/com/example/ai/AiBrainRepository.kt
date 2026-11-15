package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.AiConfig
import com.example.model.AssistantEmotion
import com.example.model.LanguageOption
import com.example.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class AiGenerationResult(
    val text: String,
    val latencyMs: Long = 0L,
    val isSuccess: Boolean = true,
    val emotion: AssistantEmotion = AssistantEmotion.NEUTRAL
)

class AiBrainRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getEffectiveApiKey(config: AiConfig): String {
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return when {
            config.customApiKey.isNotBlank() -> config.customApiKey.trim()
            buildKey.isNotBlank() && buildKey != "YOUR_GEMINI_API_KEY" && buildKey != "MY_GEMINI_API_KEY" -> buildKey
            else -> ""
        }
    }

    /**
     * Tests API key connection and returns roundtrip latency in ms
     */
    suspend fun testConnection(config: AiConfig): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(config)
        val isCustomApi = config.provider == "Custom / OpenAI API" || config.customEndpoint.isNotBlank()

        if (apiKey.isBlank() && !isCustomApi) {
            return@withContext Pair(false, "No API key configured. Enter your personal Gemini API key or select Custom API.")
        }

        val startTime = System.currentTimeMillis()

        try {
            if (isCustomApi) {
                val rawEndpoint = if (config.customEndpoint.isNotBlank()) config.customEndpoint.trim() else "https://api.openai.com/v1"
                val url = if (rawEndpoint.contains("/chat/completions")) rawEndpoint else "${rawEndpoint.trimEnd('/')}/chat/completions"
                val modelName = config.model.ifBlank { "gpt-4o-mini" }
                val payload = JSONObject().apply {
                    put("model", modelName)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Respond with 'Connection successful'")
                        })
                    })
                    put("max_tokens", 10)
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val (isOk, respString, code) = client.newCall(reqBuilder.build()).execute().use { response ->
                    Triple(response.isSuccessful, response.body?.string().orEmpty(), response.code)
                }
                val latency = System.currentTimeMillis() - startTime

                if (isOk) {
                    Pair(true, "Connected to Custom API ($modelName) in ${latency}ms!")
                } else {
                    val errorMsg = try {
                        val json = JSONObject(respString)
                        json.optJSONObject("error")?.optString("message", "")
                            ?.takeIf { it.isNotBlank() }
                            ?: "HTTP $code: $respString"
                    } catch (e: Exception) {
                        "HTTP $code"
                    }
                    Pair(false, "Custom API Error ($latency ms): $errorMsg")
                }
            } else {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=$apiKey"
                val payload = JSONObject().apply {
                    val contents = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Respond with 'Connection successful'") })
                            })
                        })
                    }
                    put("contents", contents)
                }

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()
                val (isOk, respString, code) = client.newCall(request).execute().use { response ->
                    Triple(response.isSuccessful, response.body?.string().orEmpty(), response.code)
                }
                val latency = System.currentTimeMillis() - startTime

                if (isOk) {
                    Pair(true, "Connected to Gemini (${config.model}) in ${latency}ms!")
                } else {
                    val errorMsg = try {
                        JSONObject(respString).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP $code: $respString"
                    }
                    Pair(false, "Gemini Error ($latency ms): $errorMsg")
                }
            }
        } catch (e: IllegalArgumentException) {
            Pair(false, "Invalid endpoint URL. Please check that the URL starts with https:// or http://")
        } catch (e: Exception) {
            Pair(false, "Network error: ${e.localizedMessage ?: "Unknown failure"}")
        }
    }

    private fun buildFullSystemPrompt(
        config: AiConfig,
        language: LanguageOption,
        userPrefs: UserPreferences
    ): String {
        val assistantName = userPrefs.assistantName.trim().ifBlank { "Siri" }
        val userAddress = userPrefs.userName.trim().ifBlank { "Friend" }

        val langInstruction = when (language) {
            LanguageOption.HINDI -> "STRICT LANGUAGE REQUIREMENT: You MUST reply entirely in natural, warm, conversational Hindi using Devanagari script (हिन्दी). Sound friendly and human—avoid stiff textbook Hindi. Keep it crisp in 1-3 spoken sentences with zero markdown or bullet points."
            LanguageOption.HINGLISH -> "STRICT LANGUAGE REQUIREMENT: You MUST reply in natural, friendly everyday Hinglish written entirely in the Latin/Roman alphabet (for example: 'Haan bilkul! Chaliye isse aasan shabdon mein samajhte hain.'). Sound warm and human in 1-3 spoken sentences with zero markdown."
            LanguageOption.ENGLISH -> "STRICT LANGUAGE REQUIREMENT: You MUST reply in warm, fluent, conversational English using natural contractions and human cadence. Keep it concise (1-3 sentences) and voice-ready without markdown or robotic filler."
            LanguageOption.AUTO -> "Detect whether the user is speaking English, Hindi (Devanagari), or Hinglish (Latin alphabet), and reply naturally in that exact matching style. Keep it warm, human, and voice-ready in 1-3 sentences without markdown."
        }

        val currentTimeDate = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val addressRule = if (userAddress.equals("Friend", ignoreCase = true)) {
            "User addressing: Address the user warmly and naturally (you can occasionally say 'friend' or speak directly without forcing a title)."
        } else {
            "User addressing: The user asked to be addressed as '$userAddress'. Address them naturally as '$userAddress' where warm and fitting (do not force it into every single sentence)."
        }

        return "${config.systemPrompt}\n" +
            "Assistant identity: Your name is $assistantName. Always refer to yourself as $assistantName if asked who you are.\n" +
            "$addressRule\n" +
            "User context: Home='${userPrefs.homeLocation}', Work='${userPrefs.workLocation}', TempUnit='${userPrefs.preferredTempUnit}'.\n" +
            "Current date and time: $currentTimeDate\n" +
            "Length instruction: ${config.responseLength.instruction}\n" +
            "Language instruction: $langInstruction\n" +
            "Emotional intelligence: At the very beginning of your reply, optionally prepend a single emotion tag chosen ONLY from [EMOTION:HAPPY], [EMOTION:SAD], [EMOTION:EXCITED], [EMOTION:SURPRISED], [EMOTION:CURIOUS], [EMOTION:SERIOUS], or [EMOTION:NEUTRAL] matching the conversation's feeling. Vary your phrasing across turns so you never sound repetitive or robotic.\n" +
            "Conversational continuity: Maintain deep context across turns. Understand short follow-ups ('why?', 'tell me more', 'aur batao', 'short mein batao', 'in Hindi', 'what about tomorrow?', 'that'/'it'/'iska') cleanly from the recent dialogue."
    }

    private fun buildGeminiPayload(
        prompt: String,
        config: AiConfig,
        fullSystemPrompt: String,
        recentHistory: List<Pair<String, Boolean>>,
        imageUri: Uri?
    ): JSONObject {
        val root = JSONObject()
        val sysInstructionObj = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", fullSystemPrompt) })
            })
        }
        root.put("systemInstruction", sysInstructionObj)

        val contentsArray = JSONArray()
        val historySlice = recentHistory.takeLast(12)
        for ((text, isUser) in historySlice) {
            val role = if (isUser) "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", text) })
                })
            })
        }

        val userTurnParts = JSONArray()
        if (imageUri != null) {
            val base64Image = readImageAsBase64(imageUri)
            if (base64Image != null) {
                val inlineData = JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                }
                userTurnParts.put(JSONObject().apply {
                    put("inline_data", inlineData)
                })
            }
        }
        userTurnParts.put(JSONObject().apply { put("text", prompt) })
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", userTurnParts)
        })
        root.put("contents", contentsArray)

        val genConfig = JSONObject().apply {
            put("temperature", config.temperature.toDouble())
            put("maxOutputTokens", config.maxOutputTokens)
            // Minimize Gemini 2.5 Flash thinking latency for real-time conversational turn-taking
            if (config.model.contains("gemini-2.5", ignoreCase = true) &&
                !config.model.contains("pro", ignoreCase = true)
            ) {
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingBudget", 0)
                })
            }
        }
        root.put("generationConfig", genConfig)
        return root
    }

    /**
     * Streaming conversational generation for low-latency voice turn-taking.
     * Emits each complete sentence via [onSentenceReady] as soon as it arrives from Gemini SSE,
     * falling back cleanly to [generateResponse] on custom endpoints, multimodal inputs, or SSE fallback.
     */
    suspend fun generateResponseStreaming(
        prompt: String,
        config: AiConfig,
        language: LanguageOption,
        recentHistory: List<Pair<String, Boolean>> = emptyList(),
        imageUri: Uri? = null,
        userPrefs: UserPreferences = UserPreferences(),
        onSentenceReady: suspend (sentence: String, isFirst: Boolean, emotion: AssistantEmotion) -> Unit
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(config)
        val isCustomApi = config.provider == "Custom / OpenAI API" || config.customEndpoint.isNotBlank()

        if ((apiKey.isBlank() && !isCustomApi) || isCustomApi || imageUri != null) {
            val res = generateResponse(
                prompt = prompt,
                config = config,
                language = language,
                recentHistory = recentHistory,
                imageUri = imageUri,
                userPrefs = userPrefs
            )
            onSentenceReady(res.text, true, res.emotion)
            return@withContext res
        }

        val startTime = System.currentTimeMillis()
        val fullSystemPrompt = buildFullSystemPrompt(config, language, userPrefs)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:streamGenerateContent?alt=sse&key=$apiKey"
        val payload = buildGeminiPayload(prompt, config, fullSystemPrompt, recentHistory, null)
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val streamResult = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errBody = response.body?.string().orEmpty()
                    Log.w("SiriAI", "SSE stream error $code, falling back: $errBody")
                    return@use null
                }

                val body = response.body ?: return@use null
                val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))

                val rawAccumulated = StringBuilder()
                val pendingBuffer = StringBuilder()
                var headerResolved = false
                var detectedTagEmotion: AssistantEmotion? = null
                var emittedFirst = false
                var activeEmotion = EmotionDetector.inferEmotion(prompt, "", isSuccess = true)

                val eventLines = StringBuilder()
                suspend fun processEventJson(jsonStr: String) {
                    if (jsonStr.isBlank() || jsonStr == "[DONE]") return
                    val deltaText = extractTextFromGeminiJson(jsonStr)
                    if (deltaText.isEmpty()) return
                    rawAccumulated.append(deltaText)
                    pendingBuffer.append(deltaText)

                    if (!headerResolved) {
                        val currentTrimmed = pendingBuffer.toString().trimStart()
                        if (currentTrimmed.startsWith("[EMOTION:", ignoreCase = true) ||
                            (currentTrimmed.startsWith("[") && currentTrimmed.length < 24 && !currentTrimmed.contains("]"))
                        ) {
                            val closeIdx = currentTrimmed.indexOf(']')
                            if (closeIdx < 0) {
                                return
                            }
                            val (tag, stripped) = EmotionDetector.extractAndStripEmotionTag(currentTrimmed)
                            detectedTagEmotion = tag
                            activeEmotion = EmotionDetector.inferEmotion(prompt, stripped, explicitTag = tag)
                            pendingBuffer.clear()
                            pendingBuffer.append(stripped)
                            headerResolved = true
                        } else if (currentTrimmed.isNotEmpty()) {
                            headerResolved = true
                        }
                    }

                    // Flush completed sentences immediately for fast TTS playback
                    while (true) {
                        val bufStr = pendingBuffer.toString()
                        val boundaryIdx = findSentenceBoundary(bufStr)
                        if (boundaryIdx < 0) break
                        val sentenceRaw = bufStr.substring(0, boundaryIdx + 1)
                        val remaining = bufStr.substring(boundaryIdx + 1).trimStart()
                        pendingBuffer.clear()
                        pendingBuffer.append(remaining)
                        val cleaned = cleanVoiceText(sentenceRaw)
                        if (cleaned.isNotBlank()) {
                            if (!emittedFirst) {
                                activeEmotion = EmotionDetector.inferEmotion(
                                    prompt,
                                    cleaned,
                                    explicitTag = detectedTagEmotion
                                )
                            }
                            onSentenceReady(cleaned, !emittedFirst, activeEmotion)
                            emittedFirst = true
                        }
                    }
                }

                var line: String? = reader.readLine()
                while (line != null) {
                    val trimmedLine = line.trimEnd()
                    if (trimmedLine.startsWith("data:")) {
                        eventLines.append(trimmedLine.removePrefix("data:").trim())
                    } else if (trimmedLine.isEmpty() && eventLines.isNotEmpty()) {
                        processEventJson(eventLines.toString())
                        eventLines.clear()
                    }
                    line = reader.readLine()
                }
                if (eventLines.isNotEmpty()) {
                    processEventJson(eventLines.toString())
                }

                val (tag, fullStripped) = EmotionDetector.extractAndStripEmotionTag(rawAccumulated.toString())
                val cleanedFull = cleanVoiceText(fullStripped)
                val finalEmotion = EmotionDetector.inferEmotion(prompt, cleanedFull, explicitTag = tag ?: detectedTagEmotion)

                // Emit remaining tail
                val (_, strippedTail) = EmotionDetector.extractAndStripEmotionTag(pendingBuffer.toString())
                val remainingClean = cleanVoiceText(strippedTail)
                if (remainingClean.isNotBlank()) {
                    onSentenceReady(remainingClean, !emittedFirst, finalEmotion)
                }

                val latency = System.currentTimeMillis() - startTime
                if (cleanedFull.isNotBlank()) {
                    AiGenerationResult(
                        text = cleanedFull,
                        latencyMs = latency,
                        isSuccess = true,
                        emotion = finalEmotion
                    )
                } else {
                    null
                }
            }

            if (streamResult != null) {
                return@withContext streamResult
            }
        } catch (e: Exception) {
            Log.w("SiriAI", "Streaming fallback due to: ${e.message}")
        }

        // Clean fallback to standard request if SSE was unavailable or empty
        val fallback = generateResponse(
            prompt = prompt,
            config = config,
            language = language,
            recentHistory = recentHistory,
            imageUri = null,
            userPrefs = userPrefs
        )
        onSentenceReady(fallback.text, true, fallback.emotion)
        fallback
    }

    private fun extractTextFromGeminiJson(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val candidates = json.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            buildString {
                for (i in 0 until parts.length()) {
                    val pObj = parts.optJSONObject(i) ?: continue
                    if (!pObj.optBoolean("thought", false)) {
                        append(pObj.optString("text", ""))
                    }
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            if ((ch == '.' || ch == '!' || ch == '?' || ch == '।') && i >= 14) {
                val next = text.getOrNull(i + 1)
                // Avoid splitting abbreviations like "e.g." or decimals "3.5"
                if (ch == '.' && next != null && next.isDigit()) continue
                if (next == null || next.isWhitespace()) {
                    return i
                }
            }
        }
        return -1
    }

    /**
     * Generates a conversational response with history, vision support, and latency reporting
     */
    suspend fun generateResponse(
        prompt: String,
        config: AiConfig,
        language: LanguageOption,
        recentHistory: List<Pair<String, Boolean>> = emptyList(),
        imageUri: Uri? = null,
        userPrefs: UserPreferences = UserPreferences()
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(config)
        val startTime = System.currentTimeMillis()
        val isCustomApi = config.provider == "Custom / OpenAI API" || config.customEndpoint.isNotBlank()
        val assistantName = userPrefs.assistantName.trim().ifBlank { "Siri" }
        val userAddress = userPrefs.userName.trim().ifBlank { "Friend" }

        if (apiKey.isBlank() && !isCustomApi) {
            val offlineMsg = buildNaturalOfflineReply(
                prompt = prompt,
                language = language,
                assistantName = assistantName,
                userAddress = userAddress,
                recentHistory = recentHistory
            )
            val emotion = EmotionDetector.inferEmotion(prompt, offlineMsg, isSuccess = false)
            return@withContext AiGenerationResult(
                text = offlineMsg,
                latencyMs = 5L,
                isSuccess = false,
                emotion = emotion
            )
        }

        val fullSystemPrompt = buildFullSystemPrompt(config, language, userPrefs)

        try {
            if (isCustomApi) {
                // OpenAI-compatible / Custom endpoint
                val rawEndpoint = if (config.customEndpoint.isNotBlank()) config.customEndpoint.trim() else "https://api.openai.com/v1"
                val url = if (rawEndpoint.contains("/chat/completions")) rawEndpoint else "${rawEndpoint.trimEnd('/')}/chat/completions"
                val modelName = config.model.ifBlank { "gpt-4o-mini" }

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", fullSystemPrompt)
                    })
                    val historySlice = recentHistory.takeLast(12)
                    for ((text, isUser) in historySlice) {
                        put(JSONObject().apply {
                            put("role", if (isUser) "user" else "assistant")
                            put("content", text)
                        })
                    }
                    val base64Img = if (imageUri != null) readImageAsBase64(imageUri) else null
                    if (base64Img != null) {
                        val contentParts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Img")
                                })
                            })
                        }
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", contentParts)
                        })
                    } else {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                }

                val payload = JSONObject().apply {
                    put("model", modelName)
                    put("messages", messagesArray)
                    put("temperature", config.temperature.toDouble())
                    put("max_tokens", config.maxOutputTokens)
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val (isOk, respString, code) = client.newCall(reqBuilder.build()).execute().use { response ->
                    Triple(response.isSuccessful, response.body?.string().orEmpty(), response.code)
                }
                val latency = System.currentTimeMillis() - startTime

                if (!isOk) {
                    val errMsg = try {
                        JSONObject(respString).optJSONObject("error")?.optString("message", "")
                            ?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                    } catch (e: Exception) {
                        "HTTP $code"
                    }
                    return@withContext AiGenerationResult(
                        text = "Error connecting to Custom API: $errMsg. Local device controls remain active.",
                        latencyMs = latency,
                        isSuccess = false,
                        emotion = AssistantEmotion.SAD
                    )
                }

                val respJson = JSONObject(respString)
                val choices = respJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val msgObj = firstChoice.optJSONObject("message")
                    val content = msgObj?.optString("content", "") ?: ""
                    if (content.isNotBlank()) {
                        val (tag, stripped) = EmotionDetector.extractAndStripEmotionTag(content)
                        val cleaned = cleanVoiceText(stripped)
                        val emotion = EmotionDetector.inferEmotion(prompt, cleaned, explicitTag = tag)
                        return@withContext AiGenerationResult(
                            text = cleaned,
                            latencyMs = latency,
                            isSuccess = true,
                            emotion = emotion
                        )
                    }
                }
                return@withContext AiGenerationResult("Empty response received from Custom API.", latency, false, AssistantEmotion.SAD)
            } else {
                // Google Gemini API
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=$apiKey"
                val root = buildGeminiPayload(prompt, config, fullSystemPrompt, recentHistory, imageUri)

                val body = root.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                val (isOk, respString, code) = client.newCall(request).execute().use { response ->
                    Triple(response.isSuccessful, response.body?.string().orEmpty(), response.code)
                }
                val latency = System.currentTimeMillis() - startTime

                if (!isOk) {
                    Log.e("SiriAI", "API Error $code: $respString")
                    val errMsg = try {
                        JSONObject(respString).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP $code"
                    }
                    return@withContext AiGenerationResult(
                        text = "I encountered an error connecting to the AI brain: $errMsg. I can still perform local device actions for you.",
                        latencyMs = latency,
                        isSuccess = false,
                        emotion = AssistantEmotion.SAD
                    )
                }

                val combinedText = extractTextFromGeminiJson(respString)
                if (combinedText.isNotBlank()) {
                    val (tag, stripped) = EmotionDetector.extractAndStripEmotionTag(combinedText)
                    val cleaned = cleanVoiceText(stripped)
                    val emotion = EmotionDetector.inferEmotion(prompt, cleaned, explicitTag = tag)
                    return@withContext AiGenerationResult(
                        text = cleaned,
                        latencyMs = latency,
                        isSuccess = true,
                        emotion = emotion
                    )
                }
                AiGenerationResult("I heard you, but could not generate a response. Please try again.", latency, false, AssistantEmotion.SAD)
            }
        } catch (e: IllegalArgumentException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e("SiriAI", "Invalid URL", e)
            AiGenerationResult("Invalid API endpoint URL. Please check Settings.", latency, false, AssistantEmotion.SERIOUS)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e("SiriAI", "Generation exception", e)
            AiGenerationResult("I'm having trouble connecting to the network right now. Device controls and local features are still available.", latency, false, AssistantEmotion.SAD)
        }
    }

    private fun readImageAsBase64(uri: Uri): String? {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return null

            val maxDimension = 1024
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val targetW: Int
                val targetH: Int
                if (width > height) {
                    targetW = maxDimension
                    targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
                } else {
                    targetH = maxDimension
                    targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
                }
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            val bytes = ByteArrayOutputStream().use { outputStream ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                outputStream.toByteArray()
            }
            scaledBitmap.recycle()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("SiriAI", "Failed to encode image to base64", e)
            null
        }
    }

    fun buildNaturalOfflineReply(
        prompt: String,
        language: LanguageOption,
        assistantName: String,
        userAddress: String,
        recentHistory: List<Pair<String, Boolean>> = emptyList()
    ): String {
        val lower = prompt.lowercase(java.util.Locale.ROOT).trim()
        val hasDevanagari = prompt.any { it in '\u0900'..'\u097F' }
        val hasHinglish = Regex("""\b(hai|hain|kaise|kya|batao|karo|bhai|yaar|hoon|aap|tum|mera|madad|accha|theek|kyunki|samjho)\b""").containsMatchIn(lower)
        val effectiveLang = when (language) {
            LanguageOption.AUTO -> when {
                hasDevanagari -> LanguageOption.HINDI
                hasHinglish -> LanguageOption.HINGLISH
                else -> LanguageOption.ENGLISH
            }
            else -> language
        }

        val nameTag = if (userAddress.equals("Friend", ignoreCase = true)) "" else ", $userAddress"
        val isFollowUp = Regex("""^(why|how|tell me more|more|explain|aur batao|kyun|kaise|matlab)\b""").containsMatchIn(lower) &&
            recentHistory.any { !it.second }

        if (isFollowUp) {
            return when (effectiveLang) {
                LanguageOption.HINDI -> "$assistantName अभी बिना इंटरनेट AI कुंजी के काम कर रहा है${if (nameTag.isNotEmpty()) ", $userAddress" else ""}। विस्तार से फॉलो-अप जवाब के लिए कृपया सेटिंग्स में अपनी Gemini या OpenAI API कुंजी जोड़ें।"
                LanguageOption.HINGLISH -> "Main abhi offline mode mein hoon$nameTag, isliye pichle topic par deep AI follow-up ke liye Settings mein API key add karni hogi. Tab tak aap phone commands ya web search pooch sakte hain!"
                else -> "I'd love to dive deeper into that$nameTag, though I need an API key in Settings to generate full cloud AI follow-ups. In the meantime, I can still run device controls, timers, or web searches."
            }
        }

        val variant = (prompt.length + recentHistory.size) % 2
        return when (effectiveLang) {
            LanguageOption.HINDI -> if (variant == 0) {
                "मैं $assistantName हूँ${if (nameTag.isNotEmpty()) ", $userAddress" else ""}। अभी लाइव AI जवाब के लिए API कुंजी सेट नहीं है, लेकिन आप मुझसे ऐप्स खोलने, ब्राइटनेस या वॉल्यूम बदलने, अलार्म लगाने और वेब सर्च करने के लिए कह सकते हैं।"
            } else {
                "बेशक${if (nameTag.isNotEmpty()) ", $userAddress" else ""}—मैं $assistantName आपके डिवाइस के सभी कामों के लिए तैयार हूँ। गहरी बातचीत के लिए बस सेटिंग्स में API कुंजी जोड़ दें।"
            }
            LanguageOption.HINGLISH -> if (variant == 0) {
                "Hey$nameTag, main $assistantName hoon! Abhi cloud AI ke liye API key set nahi hai, par aap Settings mein key add karke mujhse bilkul natural baat kar sakte hain. Abhi ke liye aap apps, settings, alarms ya battery control karwa sakte hain."
            } else {
                "Bilkul$nameTag, main $assistantName yahin hoon! Cloud AI chat ke liye Settings mein API key lagayein, aur tab tak apps open karne ya search karne ke liye boliye."
            }
            else -> if (variant == 0) {
                "Hey$nameTag, I'm $assistantName! Cloud conversation needs an API key in Settings, but I'm right here for opening apps, searching inside apps, setting alarms, or adjusting your device settings."
            } else {
                "I hear you$nameTag! Add an API key in Settings whenever you're ready for full AI chat—meanwhile, I can launch apps, adjust settings, or search the web right away."
            }
        }
    }

    /**
     * Strips markdown symbols, asterisks, bullet points, headers, and excessive whitespace
     * to ensure the assistant's response is natural, clean, and voice-ready for TTS.
     */
    fun cleanVoiceText(raw: String): String {
        val (_, tagStripped) = EmotionDetector.extractAndStripEmotionTag(raw)
        return tagStripped
            .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            .replace(Regex("""\*([^*]+)\*"""), "$1")
            .replace(Regex("""__([^_]+)__"""), "$1")
            .replace(Regex("""_([^_]+)_"""), "$1")
            .replace(Regex("""#{1,6}\s*"""), "")
            .replace(Regex("""```[\s\S]*?```"""), "")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
            .replace(Regex("""^\s*[-*•]\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""\s*[-—–]{2,}\s*"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

