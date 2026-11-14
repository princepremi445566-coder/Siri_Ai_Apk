package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.AiConfig
import com.example.model.LanguageOption
import com.example.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class AiGenerationResult(
    val text: String,
    val latencyMs: Long = 0L,
    val isSuccess: Boolean = true
)

class AiBrainRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
            return@withContext AiGenerationResult(
                text = offlineMsg,
                latencyMs = 5L,
                isSuccess = false
            )
        }

        val langInstruction = when (language) {
            LanguageOption.HINDI -> "STRICT LANGUAGE REQUIREMENT: You MUST reply entirely in natural, warm, conversational Hindi using Devanagari script (हिन्दी). Sound friendly and human—avoid stiff textbook Hindi. Keep it crisp in 1-3 spoken sentences with zero markdown or bullet points."
            LanguageOption.HINGLISH -> "STRICT LANGUAGE REQUIREMENT: You MUST reply in natural, friendly everyday Hinglish written entirely in the Latin/Roman alphabet (for example: 'Haan bilkul! Chaliye isse aasan shabdon mein samajhte hain.'). Sound warm and human in 1-3 spoken sentences with zero markdown."
            LanguageOption.ENGLISH -> "STRICT LANGUAGE REQUIREMENT: You MUST reply in warm, fluent, conversational English using natural contractions and human cadence. Keep it concise (1-3 sentences) and voice-ready without markdown or robotic filler."
            LanguageOption.AUTO -> "Detect whether the user is speaking English, Hindi (Devanagari), or Hinglish (Latin alphabet), and reply naturally in that exact matching style. Keep it warm, human, and voice-ready in 1-3 sentences without markdown."
        }

        val currentTimeDate = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val lengthInstruction = config.responseLength.instruction
        val addressRule = if (userAddress.equals("Friend", ignoreCase = true)) {
            "User addressing: Address the user warmly and naturally (you can occasionally say 'friend' or speak directly without forcing a title)."
        } else {
            "User addressing: The user asked to be addressed as '$userAddress'. Address them naturally as '$userAddress' where warm and fitting (do not force it into every single sentence)."
        }
        val fullSystemPrompt = "${config.systemPrompt}\n" +
            "Assistant identity: Your name is $assistantName. Always refer to yourself as $assistantName if asked who you are.\n" +
            "$addressRule\n" +
            "User context: Home='${userPrefs.homeLocation}', Work='${userPrefs.workLocation}', TempUnit='${userPrefs.preferredTempUnit}'.\n" +
            "Current date and time: $currentTimeDate\n" +
            "Length instruction: $lengthInstruction\n" +
            "Language instruction: $langInstruction\n" +
            "Conversational continuity: Maintain deep context across turns. Understand short follow-ups ('why?', 'tell me more', 'aur batao', 'short mein batao', 'in Hindi', 'what about tomorrow?', 'that'/'it'/'iska') cleanly from the recent dialogue."

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
                        isSuccess = false
                    )
                }

                val respJson = JSONObject(respString)
                val choices = respJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val msgObj = firstChoice.optJSONObject("message")
                    val content = msgObj?.optString("content", "") ?: ""
                    if (content.isNotBlank()) {
                        return@withContext AiGenerationResult(
                            text = cleanVoiceText(content),
                            latencyMs = latency,
                            isSuccess = true
                        )
                    }
                }
                return@withContext AiGenerationResult("Empty response received from Custom API.", latency, false)
            } else {
                // Google Gemini API
                val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=$apiKey"
                val root = JSONObject()

                // System instruction
                val sysInstructionObj = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", fullSystemPrompt) })
                    })
                }
                root.put("systemInstruction", sysInstructionObj)

                // Contents array with history
                val contentsArray = JSONArray()

                // Deep multi-turn context (last 12 messages)
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

                // Current user turn with optional image
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

                userTurnParts.put(JSONObject().apply {
                    put("text", prompt)
                })

                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", userTurnParts)
                })

                root.put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("temperature", config.temperature.toDouble())
                    put("maxOutputTokens", config.maxOutputTokens)
                }
                root.put("generationConfig", genConfig)

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
                        isSuccess = false
                    )
                }

                val respJson = JSONObject(respString)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val combinedText = buildString {
                            for (i in 0 until parts.length()) {
                                val pObj = parts.optJSONObject(i) ?: continue
                                if (!pObj.optBoolean("thought", false)) {
                                    val t = pObj.optString("text", "")
                                    if (t.isNotBlank()) {
                                        if (isNotEmpty()) append(' ')
                                        append(t)
                                    }
                                }
                            }
                        }
                        if (combinedText.isNotBlank()) {
                            return@withContext AiGenerationResult(
                                text = cleanVoiceText(combinedText),
                                latencyMs = latency,
                                isSuccess = true
                            )
                        }
                    }
                }
                AiGenerationResult("I heard you, but could not generate a response. Please try again.", latency, false)
            }
        } catch (e: IllegalArgumentException) {
            val latency = System.currentTimeMillis() - startTime
            Log.e("SiriAI", "Invalid URL", e)
            AiGenerationResult("Invalid API endpoint URL. Please check Settings.", latency, false)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e("SiriAI", "Generation exception", e)
            AiGenerationResult("I'm having trouble connecting to the network right now. Device controls and local features are still available.", latency, false)
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

        return when (effectiveLang) {
            LanguageOption.HINDI -> "मैं $assistantName हूँ${if (nameTag.isNotEmpty()) ", $userAddress" else ""}। अभी लाइव AI जवाब के लिए API कुंजी सेट नहीं है, लेकिन आप मुझसे ऐप्स खोलने, ब्राइटनेस या वॉल्यूम बदलने, अलार्म लगाने और वेब सर्च करने के लिए कह सकते हैं।"
            LanguageOption.HINGLISH -> "Hey$nameTag, main $assistantName hoon! Abhi cloud AI ke liye API key set nahi hai, par aap Settings mein key add karke mujhse bilkul natural baat kar sakte hain. Abhi ke liye aap apps, settings, alarms ya battery control karwa sakte hain."
            else -> "Hey$nameTag, I'm $assistantName! Cloud conversation needs an API key in Settings, but I'm right here for opening apps, searching inside apps, setting alarms, or adjusting your device settings."
        }
    }

    /**
     * Strips markdown symbols, asterisks, bullet points, headers, and excessive whitespace
     * to ensure the assistant's response is natural, clean, and voice-ready for TTS.
     */
    fun cleanVoiceText(raw: String): String {
        return raw
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
