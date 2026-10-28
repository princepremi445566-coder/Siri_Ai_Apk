package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VoiceState
import com.example.ui.theme.SiriCyan
import com.example.ui.theme.SiriDarkBackground
import com.example.ui.theme.SiriDarkSurface
import com.example.ui.theme.SiriDarkSurfaceVariant
import com.example.ui.theme.SiriMagenta
import com.example.ui.theme.SiriNeonBlue
import com.example.ui.theme.SiriTextPrimary
import com.example.ui.theme.SiriTextSecondary
import com.example.ui.theme.SiriViolet
import com.example.viewmodel.SiriViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionScreen(
    viewModel: SiriViewModel,
    modifier: Modifier = Modifier
) {
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val lastSpeech by viewModel.lastAssistantSpeech.collectAsState()

    var customPrompt by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setSelectedImage(uri)
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SiriDarkBackground,
                        Color(0xFF0F1522),
                        SiriDarkSurface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                        .background(SiriViolet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = SiriViolet,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Camera Vision & Screen AI",
                        color = SiriTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Visual intelligence powered by Gemini Multimodal",
                        color = SiriTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Image Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedImageUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, SiriViolet.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected Visual",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { viewModel.setSelectedImage(null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .testTag("clear_selected_image_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Image ready for Siri AI analysis",
                            color = SiriCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF141C2A))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("select_photo_area"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = SiriCyan,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Select Photo or Screenshot to Analyze",
                                    color = SiriTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Tap to choose from Gallery or Camera",
                                    color = SiriTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D283A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("choose_photo_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Photo", color = SiriTextPrimary, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { viewModel.deviceController.openCameraApp() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D283A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("launch_camera_button")
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = SiriMagenta, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Camera", color = SiriTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Preset Vision Queries
            Text(
                text = "Preset Vision Queries",
                color = SiriTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "Explain what is in this image",
                    "Extract and read visible text",
                    "Summarize this document",
                    "Identify objects and landmarks",
                    "Explain screen buttons and layout"
                )

                for (preset in presets) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SiriDarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D)),
                        modifier = Modifier
                            .clickable {
                                customPrompt = preset
                                viewModel.handleUserUtterance(preset)
                            }
                            .testTag("vision_preset_${preset.take(15).replace(" ", "_").lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = SiriViolet,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = preset, color = SiriTextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Custom Visual Question Input
            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                placeholder = { Text("Ask a question about this image or screen...", color = SiriTextSecondary, fontSize = 14.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vision_custom_prompt_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SiriViolet,
                    unfocusedBorderColor = Color(0xFF28364D),
                    focusedContainerColor = SiriDarkSurfaceVariant,
                    unfocusedContainerColor = SiriDarkSurfaceVariant,
                    focusedTextColor = SiriTextPrimary,
                    unfocusedTextColor = SiriTextPrimary
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (customPrompt.isNotBlank()) {
                                val p = customPrompt.trim()
                                customPrompt = ""
                                viewModel.handleUserUtterance(p)
                            }
                        },
                        modifier = Modifier.testTag("send_vision_prompt_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SiriViolet)
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // AI Vision Response Card
            if (voiceState == VoiceState.THINKING) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = SiriViolet,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Siri Vision AI is analyzing image pixels and text...",
                            color = SiriTextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (lastSpeech.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SiriViolet.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Siri Vision Analysis",
                                color = SiriViolet,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { viewModel.handleUserUtterance(lastSpeech) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Speak", color = SiriCyan, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = lastSpeech,
                            color = SiriTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}
