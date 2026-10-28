package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.model.VoiceGender
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanguageOption
import com.example.model.VoicePersonality
import com.example.service.SiriVoiceService
import com.example.ui.theme.SiriCyan
import com.example.ui.theme.SiriDarkBackground
import com.example.ui.theme.SiriDarkSurface
import com.example.ui.theme.SiriDarkSurfaceVariant
import com.example.ui.theme.SiriGreen
import com.example.ui.theme.SiriMagenta
import com.example.ui.theme.SiriNeonBlue
import com.example.ui.theme.SiriTextPrimary
import com.example.ui.theme.SiriTextSecondary
import com.example.ui.theme.SiriViolet
import com.example.viewmodel.SiriViewModel

@Composable
fun AiStudioScreen(
    viewModel: SiriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val personality by viewModel.repository.voicePersonality.collectAsState()
    val language by viewModel.repository.selectedLanguage.collectAsState()
    val aiConfig by viewModel.repository.aiConfig.collectAsState()
    val userPrefs by viewModel.repository.userPreferences.collectAsState()
    val continuousListening by viewModel.repository.continuousListening.collectAsState()
    val wakeWordEnabled by viewModel.repository.wakeWordEnabled.collectAsState()
    val speedMultiplier by viewModel.repository.customSpeedMultiplier.collectAsState()
    val pitchMultiplier by viewModel.repository.customPitchMultiplier.collectAsState()

    val isTestingApi by viewModel.isTestingApi.collectAsState()
    val apiTestResult by viewModel.apiConnectionTestResult.collectAsState()

    val voiceGender by viewModel.repository.voiceGender.collectAsState()

    var customApiKeyInput by remember(aiConfig.customApiKey) { mutableStateOf(aiConfig.customApiKey) }
    var customEndpointInput by remember(aiConfig.customEndpoint) { mutableStateOf(aiConfig.customEndpoint) }
    var selectedProvider by remember(aiConfig.provider) { mutableStateOf(aiConfig.provider) }
    var selectedModel by remember(aiConfig.model) { mutableStateOf(aiConfig.model) }
    var selectedTemperature by remember(aiConfig.temperature) { mutableStateOf(aiConfig.temperature) }
    var showApiKeyPassword by remember { mutableStateOf(false) }

    var isForegroundServiceActive by remember { mutableStateOf(SiriVoiceService.isServiceRunning) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SiriDarkBackground,
                        Color(0xFF0F121B),
                        SiriDarkSurface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SiriMagenta.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = SiriMagenta,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI Brain & Voice Studio",
                        color = SiriTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Voice personalities, speed, language, & Gemini API engine",
                        color = SiriTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Voice Gender Selection (Female Siri Voice)
            Text(
                text = "Voice Gender (आवाज़ चयन)",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (gender in VoiceGender.values()) {
                    val isSelected = gender == voiceGender
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setVoiceGender(gender) }
                            .testTag("voice_gender_${gender.name.lowercase()}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF241433) else SiriDarkSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SiriMagenta else Color(0xFF28364D)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = when (gender) {
                                    VoiceGender.FEMALE -> "👩 Female (Siri)"
                                    VoiceGender.MALE -> "👨 Male"
                                    VoiceGender.SYSTEM_DEFAULT -> "⚙️ System"
                                },
                                color = if (isSelected) SiriMagenta else SiriTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (gender == VoiceGender.FEMALE) "Recommended" else if (gender == VoiceGender.MALE) "Deep Pitch" else "Default Engine",
                                color = SiriTextSecondary,
                                fontSize = 10.sp
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = SiriMagenta.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Active", color = SiriMagenta, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Voice Personality Studio
            Text(
                text = "Voice Personality",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in VoicePersonality.values()) {
                    val isSelected = p == personality
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setPersonality(p) }
                            .testTag("personality_${p.name.lowercase()}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF1B192C) else SiriDarkSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SiriMagenta else Color(0xFF28364D)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.displayName, color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = SiriMagenta.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Active", color = SiriMagenta, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(p.toneDesc, color = SiriTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Speech Rate and Pitch Multipliers Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Speech Rate & Pitch Controls", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speech Speed: ${String.format("%.2f", speedMultiplier)}x", color = SiriTextSecondary, fontSize = 12.sp)
                        Text(if (speedMultiplier < 1.0f) "Slower" else if (speedMultiplier > 1.0f) "Faster" else "Normal", color = SiriCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speedMultiplier,
                        onValueChange = { viewModel.setSpeedMultiplier(it) },
                        valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = SiriCyan, activeTrackColor = SiriCyan, inactiveTrackColor = Color(0xFF27384F))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Pitch: ${String.format("%.2f", pitchMultiplier)}x", color = SiriTextSecondary, fontSize = 12.sp)
                        Text(if (pitchMultiplier < 1.0f) "Deeper" else if (pitchMultiplier > 1.0f) "Higher" else "Standard", color = SiriViolet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = pitchMultiplier,
                        onValueChange = { viewModel.setPitchMultiplier(it) },
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(thumbColor = SiriViolet, activeTrackColor = SiriViolet, inactiveTrackColor = Color(0xFF27384F))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val testPhrase = when (language) {
                        LanguageOption.HINDI -> "नमस्ते! मैं सिरी हूँ, आपकी व्यक्तिगत एआई वॉइस असिस्टेंट।"
                        LanguageOption.HINGLISH -> "Hello! Main Siri hoon, aapka personal AI voice assistant."
                        LanguageOption.ENGLISH -> "Hello! I am Siri, your personal voice assistant on Android."
                        LanguageOption.AUTO -> "Hello! I am Siri, ready to assist in English, Hindi, and Hinglish."
                    }
                    viewModel.stopAllSpeech()
                    viewModel.handleUserUtterance(testPhrase)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("preview_voice_sample_button")
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Preview Voice Sample", color = SiriTextPrimary, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Language Selection
            Text(
                text = "Language & Accent Support",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (lang in LanguageOption.values()) {
                    val isSelected = lang == language
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.setLanguage(lang) }
                            .testTag("lang_${lang.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF0F2633) else SiriDarkSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SiriCyan else Color(0xFF28364D)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = lang.displayName,
                                color = if (isSelected) SiriCyan else SiriTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. AI Brain Configuration & Custom API Settings
            Text(
                text = "AI Brain & API Configuration (एपीआई सेटिंग्स)",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Provider Selector
                    Text("AI Provider (प्रदाता चुनें)", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val providers = listOf("Google Gemini", "Custom / OpenAI API")
                        for (p in providers) {
                            val selected = p == selectedProvider
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) SiriViolet.copy(alpha = 0.3f) else Color(0xFF141D2C),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) SiriViolet else Color(0xFF2A3A52)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedProvider = p
                                        val defaultModel = if (p == "Google Gemini") "gemini-2.5-flash" else "gpt-4o-mini"
                                        selectedModel = defaultModel
                                        viewModel.repository.updateAiConfig(
                                            aiConfig.copy(provider = p, model = defaultModel)
                                        )
                                    }
                            ) {
                                Text(
                                    text = p,
                                    color = if (selected) Color.White else SiriTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Model Selection
                    Text("Model Name", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (selectedProvider == "Google Gemini") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            val models = listOf("gemini-2.5-flash", "gemini-2.5-pro")
                            for (m in models) {
                                val selected = m == selectedModel
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) SiriViolet.copy(alpha = 0.3f) else Color(0xFF141D2C),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) SiriViolet else Color(0xFF2A3A52)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedModel = m
                                            viewModel.repository.updateAiConfig(aiConfig.copy(model = m))
                                        }
                                ) {
                                    Text(
                                        text = m,
                                        color = if (selected) Color.White else SiriTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(10.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {
                                selectedModel = it
                                viewModel.repository.updateAiConfig(aiConfig.copy(model = it))
                            },
                            placeholder = { Text("e.g. gpt-4o-mini, deepseek-chat", color = SiriTextSecondary, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SiriCyan,
                                unfocusedBorderColor = Color(0xFF2E3E58),
                                focusedContainerColor = Color(0xFF121A28),
                                unfocusedContainerColor = Color(0xFF121A28),
                                focusedTextColor = SiriTextPrimary,
                                unfocusedTextColor = SiriTextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    if (selectedProvider == "Custom / OpenAI API") {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Custom Base URL / Endpoint (वैकल्पिक एंडपॉइंट)", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Default: https://api.openai.com/v1. Or enter your customer proxy/server URL:", color = SiriTextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customEndpointInput,
                            onValueChange = {
                                customEndpointInput = it
                                viewModel.repository.updateAiConfig(aiConfig.copy(customEndpoint = it))
                            },
                            placeholder = { Text("https://api.openai.com/v1", color = SiriTextSecondary, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(18.dp))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SiriCyan,
                                unfocusedBorderColor = Color(0xFF2E3E58),
                                focusedContainerColor = Color(0xFF121A28),
                                unfocusedContainerColor = Color(0xFF121A28),
                                focusedTextColor = SiriTextPrimary,
                                unfocusedTextColor = SiriTextPrimary
                            ),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("User / Customer API Key (एपीआई कुंजी)", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Surface(
                            color = if (customApiKeyInput.isNotBlank()) Color(0xFF00E676).copy(alpha = 0.2f) else SiriCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (customApiKeyInput.isNotBlank()) "🟢 Custom Key Active" else "🔵 Default Key Active",
                                color = if (customApiKeyInput.isNotBlank()) Color(0xFF00E676) else SiriCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "User ya customer apni personal API key yahan paste kar sakte hain:",
                        color = SiriTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customApiKeyInput,
                        onValueChange = {
                            customApiKeyInput = it
                            viewModel.repository.updateAiConfig(aiConfig.copy(customApiKey = it))
                        },
                        placeholder = { Text("AI Studio default key active (or paste your key)", color = SiriTextSecondary, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("custom_api_key_input"),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showApiKeyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiriCyan,
                            unfocusedBorderColor = Color(0xFF2E3E58),
                            focusedContainerColor = Color(0xFF121A28),
                            unfocusedContainerColor = Color(0xFF121A28),
                            focusedTextColor = SiriTextPrimary,
                            unfocusedTextColor = SiriTextPrimary
                        ),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showApiKeyPassword = !showApiKeyPassword }) {
                                    Icon(
                                        imageVector = if (showApiKeyPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = SiriTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (customApiKeyInput.isNotEmpty()) {
                                    IconButton(onClick = {
                                        customApiKeyInput = ""
                                        viewModel.repository.updateAiConfig(aiConfig.copy(customApiKey = ""))
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SiriTextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.testApiConnection() },
                        colors = ButtonDefaults.buttonColors(containerColor = SiriViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("test_api_connection_button"),
                        enabled = !isTestingApi
                    ) {
                        if (isTestingApi) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Connection...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test API Connection & Latency", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (apiTestResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val (success, message) = apiTestResult!!
                        Surface(
                            color = if (success) SiriGreen.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (success) SiriGreen else Color(0xFFFF5252)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (success) SiriGreen else Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    color = if (success) SiriGreen else Color(0xFFFF5252),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Response Creativity: ${String.format("%.1f", selectedTemperature)}", color = SiriTextPrimary, fontSize = 13.sp)
                    Slider(
                        value = selectedTemperature,
                        onValueChange = {
                            selectedTemperature = it
                            viewModel.repository.updateAiConfig(aiConfig.copy(temperature = it))
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = SiriCyan,
                            activeTrackColor = SiriCyan,
                            inactiveTrackColor = Color(0xFF27384F)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Background Operation & Services
            Text(
                text = "Background Voice Operation (क्या यह बैकग्राउंड में काम करेगा?)",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Informative Architecture Card explaining background working
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131929)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2D48))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("हाँ, यह बैकग्राउंड में काम करता है!", color = SiriCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Foreground Service & Notification: Android 12/13/14/15 privacy rules ke mutabiq background me mic access ke liye ongoing service zaroori hoti hai jo bina battery drain ke chalti hai.\n" +
                               "• 1-Tap '🎙️ Ask Siri' Quick Access: Notification panel me 'Ask Siri' button rehta hai. Aap kisi bhi doosre app me ya lock screen par ho, ek tap me bol kar command de sakte hain.\n" +
                               "• Automatic Background Execution: Focus timer, alarms, reminders, scheduled routines, aur notification announcements background me bina app open kiye automatically chalte hain.",
                        color = SiriTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Foreground Service toggle with live status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Background Voice Service", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (isForegroundServiceActive) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isForegroundServiceActive) "🟢 Active" else "⚪ Inactive",
                                        color = if (isForegroundServiceActive) Color(0xFF00E676) else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("Ongoing notification with '🎙️ Ask Siri' button stays active", color = SiriTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isForegroundServiceActive,
                            onCheckedChange = { enable ->
                                isForegroundServiceActive = enable
                                if (enable) {
                                    SiriVoiceService.start(context)
                                } else {
                                    SiriVoiceService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SiriCyan, checkedTrackColor = SiriCyan.copy(alpha = 0.3f))
                        )
                    }

                    // Voice Session Timeout Selector
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Voice Session Timeout", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(if (userPrefs.voiceSessionTimeoutSeconds == 0) "Never" else "${userPrefs.voiceSessionTimeoutSeconds}s", color = SiriCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Stops continuous listening after silence to save battery", color = SiriTextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            for (t in listOf(15, 30, 60, 0)) {
                                val isSelected = userPrefs.voiceSessionTimeoutSeconds == t
                                val label = if (t == 0) "Never" else "${t}s"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) SiriCyan.copy(alpha = 0.25f) else Color(0xFF141E2C),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SiriCyan else Color(0xFF28364D)),
                                    modifier = Modifier.weight(1f).clickable {
                                        viewModel.repository.updateUserPreferences(userPrefs.copy(voiceSessionTimeoutSeconds = t))
                                    }
                                ) {
                                    Text(label, color = if (isSelected) SiriCyan else SiriTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }

                    // Continuous conversation toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Conversation", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Re-arms microphone automatically after Siri speaks", color = SiriTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = continuousListening,
                            onCheckedChange = { viewModel.repository.setContinuousListening(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SiriCyan, checkedTrackColor = SiriCyan.copy(alpha = 0.3f))
                        )
                    }

                    // Wake phrase toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wake Word Activation", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Recognize 'Hey Siri' / 'Siri' / 'Suno Siri' prefix", color = SiriTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { viewModel.repository.setWakeWordEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SiriCyan, checkedTrackColor = SiriCyan.copy(alpha = 0.3f))
                        )
                    }

                    // Reduced Motion Accessibility toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reduced Motion Mode", color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Minimizes particle effects and heavy visual animations", color = SiriTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = userPrefs.reducedMotion,
                            onCheckedChange = { viewModel.repository.updateUserPreferences(userPrefs.copy(reducedMotion = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SiriViolet, checkedTrackColor = SiriViolet.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. In-App Update Verification & System Info
            val updateInfo by viewModel.repository.updateInfo.collectAsState()
            val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()

            Text(
                text = "In-App Updates & System Integrity",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Siri AI Assistant", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Installed Version: v${updateInfo.latestVersionName} (Build ${updateInfo.latestVersionCode})", color = SiriTextSecondary, fontSize = 12.sp)
                        }
                        Surface(
                            color = SiriGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Up to Date", color = SiriGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.checkForUpdates() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2738)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("check_updates_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify Latest Release & Manifest", color = SiriTextPrimary, fontSize = 13.sp)
                    }
                }
            }

            if (showUpdateDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { viewModel.dismissUpdateDialog() },
                    title = { Text("Siri AI Version ${updateInfo.latestVersionName}", color = SiriTextPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Version Code: ${updateInfo.latestVersionCode}", color = SiriCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(updateInfo.releaseNotes, color = SiriTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    },
                    containerColor = SiriDarkSurfaceVariant,
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.deviceController.openApp(updateInfo.downloadUrl)
                                viewModel.dismissUpdateDialog()
                            }
                        ) {
                            Text("Release Page", color = SiriCyan, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                            Text("Done", color = SiriTextSecondary)
                        }
                    }
                )
            }
        }
    }
}
