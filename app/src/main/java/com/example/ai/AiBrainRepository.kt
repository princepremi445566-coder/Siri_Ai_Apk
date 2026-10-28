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

                val response = client.newCall(reqBuilder.build()).execute()
                val latency = System.currentTimeMillis() - startTime
                val respString = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    Pair(true, "Connected to Custom API ($modelName) in ${latency}ms!")
                } else {
                    val errorMsg = try {
                        val json = JSONObject(respString)
                        json.optJSONObject("error")?.optString("message", "")
                            ?: "HTTP ${response.code}: $respString"
                    } catch (e: Exception) {
                        "HTTP ${response.code}"
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
                val response = client.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime
                val respString = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    Pair(true, "Connected to Gemini (${config.model}) in ${latency}ms!")
                } else {
                    val errorMsg = try {
                        JSONObject(respString).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $respString"
                    }
                    Pair(false, "Gemini Error ($latency ms): $errorMsg")
                }
            }
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
        imageUri: Uri? = null
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(config)
        val startTime = System.currentTimeMillis()
        val isCustomApi = config.provider == "Custom / OpenAI API" || config.customEndpoint.isNotBlank()

        if (apiKey.isBlank() && !isCustomApi) {
            val offlineMsg = when (language) {
                LanguageOption.HINDI -> "मैं अभी ऑफलाइन मोड में चल रही हूँ क्योंकि कोई API key सेट नहीं है। आप अलार्म, ऐप्स, फ्लैशलाइट, समय और बैटरी जैसी सुविधाएं इस्तेमाल कर सकते हैं, या सेटिंग्स में अपनी API key दर्ज कर सकते हैं।"
                LanguageOption.HINGLISH -> "Main abhi offline mode mein hoon kyunki API key configured nahi hai. Aap alarms, apps, flashlight, battery aur local tasks use kar sakte hain, ya Settings mein API key add kar sakte hain."
                LanguageOption.ENGLISH, LanguageOption.AUTO -> "I am running in offline mode because no API key is configured. You can control flashlight, apps, alarms, battery and device tasks, or configure your API key in Settings."
            }
            return@withContext AiGenerationResult(
                text = offlineMsg,
                latencyMs = 5L,
                isSuccess = false
            )
        }

        val langInstruction = when (language) {
            LanguageOption.HINDI -> "STRICT LANGUAGE REQUIREMENT: You MUST reply entirely in natural, polished Hindi using the Devanagari script (हिन्दी). Do NOT use English or Latin letters. Keep the response conversational, warm, and concise in 1-2 spoken sentences (maximum 3). Never use markdown, asterisks, bullet points, or robotic filler."
            LanguageOption.HINGLISH -> "STRICT LANGUAGE REQUIREMENT: You MUST reply in friendly, conversational everyday Hinglish (natural Hindi-English mix written entirely in the Latin/Roman alphabet, e.g. 'Haan bilkul, maine yeh kar diya hai! Aap aur kuch poochna chahte hain?'). Keep it warm, natural, and concise in 1-2 spoken sentences (maximum 3). Never use markdown, asterisks, or bullet points."
            LanguageOption.ENGLISH -> "STRICT LANGUAGE REQUIREMENT: You MUST reply in fluent, natural English. Keep it conversational, calm, and concise in 1-2 spoken sentences suitable for voice speech. Avoid robotic chatbot clichés and never use markdown, asterisks, or bullet points."
            LanguageOption.AUTO -> "Detect whether the user is speaking English, Hindi (Devanagari), or Hinglish (Latin alphabet), and reply fluently in that exact matching language style. Keep it conversational and voice-ready in 1-2 sentences without any markdown."
        }

        val currentTimeDate = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val lengthInstruction = config.responseLength.instruction
        val fullSystemPrompt = "${config.systemPrompt}\n" +
            "Current date and time: $currentTimeDate\n" +
            "Length instruction: $lengthInstruction\n" +
            "Language instruction: $langInstruction\n" +
            "Conversational continuity: Maintain context across turns. If the user refers to previous items ('it', 'that', 'why?'), resolve them from recent dialogue.\n" +
            "Assistant name: Siri."

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
                    val historySlice = recentHistory.takeLast(10)
                    for ((text, isUser) in historySlice) {
                        put(JSONObject().apply {
                            put("role", if (isUser) "user" else "assistant")
                            put("content", text)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
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

                val response = client.newCall(reqBuilder.build()).execute()
                val latency = System.currentTimeMillis() - startTime
                val respString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(respString).optJSONObject("error")?.optString("message", "") ?: "HTTP ${response.code}"
                    } catch (e: Exception) {
                        "HTTP ${response.code}"
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

                // Deep multi-turn context (last 10 messages)
                val historySlice = recentHistory.takeLast(10)
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

                val response = client.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime
                val respString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    Log.e("SiriAI", "API Error ${response.code}: $respString")
                    val errMsg = try {
                        JSONObject(respString).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code}"
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
                        val text = parts.getJSONObject(0).optString("text", "")
                        return@withContext AiGenerationResult(
                            text = cleanVoiceText(text),
                            latencyMs = latency,
                            isSuccess = true
                        )
                    }
                }
                AiGenerationResult("I heard you, but could not generate a response. Please try again.", latency, false)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e("SiriAI", "Generation exception", e)
            AiGenerationResult("I'm having trouble connecting to the network right now. Device controls and local features are still available.", latency, false)
        }
    }

    private fun readImageAsBase64(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxDimension = 1024
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val targetW: Int
                val targetH: Int
                if (width > height) {
                    targetW = maxDimension
                    targetH = (maxDimension / ratio).toInt()
                } else {
                    targetH = maxDimension
                    targetW = (maxDimension * ratio).toInt()
                }
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("SiriAI", "Failed to encode image to base64", e)
            null
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
