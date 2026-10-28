package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.model.InformationFreshness
import com.example.model.VoiceGender
import com.example.model.VoiceState
import com.example.ui.components.SiriOrb
import com.example.ui.theme.SiriCyan
import com.example.ui.theme.SiriDarkBackground
import com.example.ui.theme.SiriDarkSurface
import com.example.ui.theme.SiriDarkSurfaceVariant
import com.example.ui.theme.SiriGold
import com.example.ui.theme.SiriLightGold
import com.example.ui.theme.SiriMagenta
import com.example.ui.theme.SiriNeonBlue
import com.example.ui.theme.SiriTextPrimary
import com.example.ui.theme.SiriTextSecondary
import com.example.ui.theme.SiriViolet
import com.example.ui.theme.SiriWarmGold
import com.example.viewmodel.SiriViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAssistantScreen(
    viewModel: SiriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val transcription by viewModel.transcription.collectAsState()
    val lastSpeech by viewModel.lastAssistantSpeech.collectAsState()
    val isTorchOn by viewModel.isTorchActive.collectAsState()
    val personality by viewModel.repository.voicePersonality.collectAsState()
    val voiceGender by viewModel.repository.voiceGender.collectAsState()
    val language by viewModel.repository.selectedLanguage.collectAsState()
    val messages by viewModel.repository.messages.collectAsState()
    val sessions by viewModel.repository.sessions.collectAsState()
    val currentSessionId by viewModel.repository.currentSessionId.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }
    var historySearchQuery by remember { mutableStateOf("") }
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val currentSessionMessages = messages.filter {
        it.sessionId == currentSessionId &&
        (historySearchQuery.isBlank() || it.text.contains(historySearchQuery, ignoreCase = true))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SiriDarkBackground,
                        Color(0xFF0D121D),
                        SiriDarkSurface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language & Mic Status Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = SiriDarkSurfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SiriCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (voiceState == VoiceState.LISTENING) Color(0xFF00E676) else SiriCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (voiceState == VoiceState.LISTENING) "Mic Live" else language.displayName,
                                color = if (voiceState == VoiceState.LISTENING) Color(0xFF00E676) else SiriTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        color = SiriDarkSurfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable {
                                val nextGender = when (voiceGender) {
                                    VoiceGender.FEMALE -> VoiceGender.MALE
                                    VoiceGender.MALE -> VoiceGender.SYSTEM_DEFAULT
                                    VoiceGender.SYSTEM_DEFAULT -> VoiceGender.FEMALE
                                }
                                viewModel.setVoiceGender(nextGender)
                            }
                            .testTag("voice_gender_quick_toggle"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SiriMagenta.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = when (voiceGender) {
                                VoiceGender.FEMALE -> "👩 Female Siri"
                                VoiceGender.MALE -> "👨 Male Voice"
                                VoiceGender.SYSTEM_DEFAULT -> "⚙️ System Voice"
                            },
                            color = SiriMagenta,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Daily Briefing, Flashlight, New Chat, and History Quick Toggles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Daily Briefing shortcut
                    IconButton(
                        onClick = { viewModel.triggerDailyBriefing() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SiriDarkSurfaceVariant)
                            .testTag("daily_briefing_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Daily Briefing",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // New Session button
                    IconButton(
                        onClick = {
                            viewModel.repository.createNewSession()
                            Toast.makeText(context, "Started new conversation", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SiriDarkSurfaceVariant)
                            .testTag("new_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session",
                            tint = SiriCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Flashlight toggle
                    IconButton(
                        onClick = { viewModel.toggleFlashlight() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isTorchOn) SiriCyan.copy(alpha = 0.25f) else SiriDarkSurfaceVariant)
                            .testTag("flashlight_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) SiriCyan else SiriTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // History view
                    IconButton(
                        onClick = { showHistoryDialog = !showHistoryDialog },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (showHistoryDialog) SiriViolet.copy(alpha = 0.25f) else SiriDarkSurfaceVariant)
                            .testTag("history_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Conversation History",
                            tint = if (showHistoryDialog) SiriViolet else SiriTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Siri Orb Experience
            if (!showHistoryDialog) {
                Spacer(modifier = Modifier.weight(0.35f))

                // Interactive 3D Siri Orb
                SiriOrb(
                    voiceState = voiceState,
                    audioLevel = audioLevel,
                    onClick = {
                        if (voiceState == VoiceState.LISTENING) {
                            viewModel.stopListening()
                        } else if (voiceState == VoiceState.SPEAKING) {
                            // Instant voice interruption and transition to listening for follow-up
                            viewModel.stopAllSpeech()
                            viewModel.startListening()
                        } else {
                            viewModel.startListening()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Voice State Indicator Text
                val stateText = when (voiceState) {
                    VoiceState.LISTENING -> "Listening..."
                    VoiceState.THINKING -> "Thinking..."
                    VoiceState.SPEAKING -> "Speaking..."
                    VoiceState.ERROR -> "Something went wrong"
                    VoiceState.IDLE -> "Tap orb or mic to speak"
                }

                Text(
                    text = stateText,
                    color = when (voiceState) {
                        VoiceState.LISTENING -> SiriLightGold
                        VoiceState.THINKING -> SiriWarmGold
                        VoiceState.SPEAKING -> SiriGold
                        VoiceState.ERROR -> Color(0xFFFF5252)
                        VoiceState.IDLE -> SiriTextSecondary
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Live Transcript / Response Preview Card
                val lastMsg = currentSessionMessages.lastOrNull()
                val displayText = when {
                    voiceState == VoiceState.LISTENING -> transcription.ifBlank { "Listening..." }
                    voiceState == VoiceState.THINKING -> "Thinking..."
                    lastSpeech.isNotBlank() -> lastSpeech
                    lastMsg != null -> lastMsg.text
                    else -> "How can I help you?"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant.copy(alpha = 0.85f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                             verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (voiceState == VoiceState.LISTENING) "You:" else "Siri:",
                                    color = if (voiceState == VoiceState.LISTENING) SiriLightGold else SiriGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Speak / Read Aloud button
                                if (displayText.isNotBlank() && voiceState == VoiceState.IDLE) {
                                    IconButton(
                                        onClick = { viewModel.handleUserUtterance("repeat that") },
                                        modifier = Modifier.size(28.dp).testTag("read_aloud_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Read Aloud",
                                            tint = SiriCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Copy response icon
                                if (displayText.isNotBlank()) {
                                    IconButton(
                                        onClick = { viewModel.copyResponseToClipboard(displayText) },
                                        modifier = Modifier.size(28.dp).testTag("copy_response_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Response",
                                            tint = SiriTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (voiceState == VoiceState.SPEAKING) {
                                    IconButton(
                                        onClick = { viewModel.stopAllSpeech() },
                                        modifier = Modifier.size(28.dp).testTag("stop_speech_small_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop",
                                            tint = SiriMagenta,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayText,
                            color = SiriTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        // Grounded Source Links
                        if (lastMsg != null && lastMsg.sources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (src in lastMsg.sources) {
                                    Surface(
                                        color = Color(0xFF1B283E),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SiriCyan.copy(alpha = 0.3f)),
                                        modifier = Modifier.clickable {
                                            viewModel.deviceController.openApp(src.url)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Link, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(src.title, color = SiriCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.35f))

                // Quick Command Action Chips
                Text(
                    text = "Quick Actions",
                    color = SiriTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 3
                ) {
                    val quickCommands = listOf(
                        "☀️ Daily Briefing",
                        "Tell me a joke",
                        "What is 0 / 0?",
                        "Who are you?",
                        "Turn on Flashlight",
                        "Battery Status",
                        "Koi chutkula sunao",
                        "What can you do?",
                        "↩️ Undo Action"
                    )

                    for (cmd in quickCommands) {
                        val cleanCmd = cmd.replace("☀️ ", "").replace("↩️ ", "")
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SiriDarkSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3E58)),
                            modifier = Modifier
                                .clickable {
                                    if (cmd.contains("Daily Briefing")) {
                                        viewModel.triggerDailyBriefing()
                                    } else if (cmd.contains("Undo Action")) {
                                        viewModel.handleUserUtterance("undo")
                                    } else {
                                        viewModel.handleUserUtterance(cleanCmd)
                                    }
                                }
                                .testTag("quick_action_${cleanCmd.replace(" ", "_").lowercase()}")
                        ) {
                            Text(
                                text = cmd,
                                color = SiriTextPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            } else {
                // Conversation History & Session Management View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Conversations & Sessions",
                            color = SiriTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val exported = viewModel.repository.exportCurrentSession()
                                    viewModel.deviceController.shareContent(exported, "Export Conversation")
                                },
                                modifier = Modifier.size(32.dp).testTag("export_chat_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export Session", tint = SiriCyan, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { showHistoryDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = SiriTextSecondary)
                            }
                        }
                    }

                    // Search input in conversation history
                    OutlinedTextField(
                        value = historySearchQuery,
                        onValueChange = { historySearchQuery = it },
                        placeholder = { Text("Search messages...", color = SiriTextSecondary, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = SiriTextSecondary, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (historySearchQuery.isNotBlank()) {
                                IconButton(onClick = { historySearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = SiriTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiriCyan,
                            unfocusedBorderColor = Color(0xFF28364D),
                            focusedContainerColor = SiriDarkSurfaceVariant,
                            unfocusedContainerColor = SiriDarkSurfaceVariant,
                            focusedTextColor = SiriTextPrimary,
                            unfocusedTextColor = SiriTextPrimary
                        ),
                        singleLine = true
                    )

                    // Session Selector Tabs
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (sess in sessions) {
                            val isSelected = sess.id == currentSessionId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) SiriViolet.copy(alpha = 0.35f) else Color(0xFF162030),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) SiriViolet else Color(0xFF28364D)
                                ),
                                modifier = Modifier.clickable { viewModel.repository.switchSession(sess.id) }
                            ) {
                                Text(
                                    text = sess.title,
                                    color = if (isSelected) Color.White else SiriTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(currentSessionMessages) { msg ->
                            val isUser = msg.isFromUser
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUser) SiriCyan.copy(alpha = 0.20f) else SiriDarkSurfaceVariant
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isUser) SiriCyan.copy(alpha = 0.5f) else Color(0xFF2E3E58)
                                    ),
                                    modifier = Modifier.fillMaxWidth(0.88f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (isUser) "You" else "Siri",
                                                    color = if (isUser) SiriCyan else SiriMagenta,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                                if (!isUser) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = Color(msg.freshness.badgeColorHex).copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = msg.freshness.label,
                                                            color = Color(msg.freshness.badgeColorHex),
                                                            fontSize = 8.sp,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.copyResponseToClipboard(msg.text) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = SiriTextSecondary, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = msg.text,
                                            color = SiriTextPrimary,
                                            fontSize = 13.sp
                                        )
                                        if (msg.actionSummary != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = Color(0xFF00E676).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "✓ ${msg.actionSummary}",
                                                    color = Color(0xFF00E676),
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Input & Microphone Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Text input field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask Siri anything...", color = SiriTextSecondary, fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("siri_text_input"),
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SiriCyan,
                        unfocusedBorderColor = Color(0xFF28364D),
                        focusedContainerColor = SiriDarkSurfaceVariant,
                        unfocusedContainerColor = SiriDarkSurfaceVariant,
                        focusedTextColor = SiriTextPrimary,
                        unfocusedTextColor = SiriTextPrimary
                    ),
                    maxLines = 2,
                    trailingIcon = {
                        if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val prompt = textInput.trim()
                                    textInput = ""
                                    viewModel.handleUserUtterance(prompt)
                                },
                                modifier = Modifier.testTag("send_text_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SiriCyan)
                            }
                        }
                    }
                )

                // Interrupt / Stop button
                if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.LISTENING) {
                    IconButton(
                        onClick = { viewModel.stopAllSpeech() },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2A85))
                            .testTag("siri_stop_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop speaking",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    // Primary Voice Mic button
                    val isListening = voiceState == VoiceState.LISTENING
                    IconButton(
                        onClick = {
                            if (isListening) viewModel.stopListening() else viewModel.startListening()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(SiriCyan, SiriNeonBlue)
                                )
                            )
                            .testTag("siri_primary_mic_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Activate Microphone",
                            tint = Color(0xFF00363D),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
