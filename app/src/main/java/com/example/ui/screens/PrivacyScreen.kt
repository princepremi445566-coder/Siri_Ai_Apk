package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.example.service.SiriNotificationListenerService
import com.example.ui.theme.SiriCyan
import com.example.ui.theme.SiriDarkBackground
import com.example.ui.theme.SiriDarkSurface
import com.example.ui.theme.SiriDarkSurfaceVariant
import com.example.ui.theme.SiriGreen
import com.example.ui.theme.SiriTextPrimary
import com.example.ui.theme.SiriTextSecondary
import com.example.ui.theme.SiriViolet
import com.example.viewmodel.SiriViewModel

@Composable
fun PrivacyScreen(
    viewModel: SiriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userPrefs by viewModel.repository.userPreferences.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showWipeAllConfirmDialog by remember { mutableStateOf(false) }

    // User preference memory form state
    var editName by remember(userPrefs.userName) { mutableStateOf(userPrefs.userName) }
    var editHome by remember(userPrefs.homeLocation) { mutableStateOf(userPrefs.homeLocation) }
    var editWork by remember(userPrefs.workLocation) { mutableStateOf(userPrefs.workLocation) }

    val hasMicPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    val isNotifListenerActive = SiriNotificationListenerService.isConnected

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
                        .background(SiriCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = SiriCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Privacy & Security Dashboard",
                        color = SiriTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zero unauthorized recording & local intent processing",
                        color = SiriTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Cloud Disclosure & Local Architecture Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = SiriGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cloud & On-Device Processing Disclosure", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Local Device Actions: Flashlight, alarms, timers, volume, battery status, opening apps, and offline math execute 100% on-device.\n" +
                                "• AI General Intelligence: When asking open-ended questions or analyzing photos, text/images are transmitted securely via encrypted HTTPS to Google Gemini. No voice audio is stored or sold.\n" +
                                "• Microphone Status: Active only when the pulsing orb or mic icon is live. Voice activity detection releases audio hardware immediately when silent.",
                        color = SiriTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // User-Approved Preference Memory
            Text(
                text = "Assistant Memory & Personalization",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Preferences Siri references for daily briefings and navigation without external tracking.",
                color = SiriTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Personal Profile", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Your Preferred Name / Nickname", color = SiriTextSecondary, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiriCyan,
                            unfocusedBorderColor = Color(0xFF28364D),
                            focusedContainerColor = Color(0xFF152030),
                            unfocusedContainerColor = Color(0xFF152030),
                            focusedTextColor = SiriTextPrimary,
                            unfocusedTextColor = SiriTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editHome,
                            onValueChange = { editHome = it },
                            label = { Text("Home Area", color = SiriTextSecondary, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SiriCyan,
                                unfocusedBorderColor = Color(0xFF28364D),
                                focusedContainerColor = Color(0xFF152030),
                                unfocusedContainerColor = Color(0xFF152030),
                                focusedTextColor = SiriTextPrimary,
                                unfocusedTextColor = SiriTextPrimary
                            )
                        )
                        OutlinedTextField(
                            value = editWork,
                            onValueChange = { editWork = it },
                            label = { Text("Work Area", color = SiriTextSecondary, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SiriCyan,
                                unfocusedBorderColor = Color(0xFF28364D),
                                focusedContainerColor = Color(0xFF152030),
                                unfocusedContainerColor = Color(0xFF152030),
                                focusedTextColor = SiriTextPrimary,
                                unfocusedTextColor = SiriTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.repository.updateUserPreferences(
                                userPrefs.copy(
                                    userName = editName.trim(),
                                    homeLocation = editHome.trim(),
                                    workLocation = editWork.trim()
                                )
                            )
                            Toast.makeText(context, "Saved assistant preferences", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SiriCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Profile", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Notification Privacy & Quiet Hours
            Text(
                text = "Notification Privacy & Quiet Hours",
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
                    // Filter Sensitive Notifications
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Filter Sensitive OTPs & Bank Codes", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Excludes one-time verification codes and bank alerts from voice readouts", color = SiriTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = userPrefs.filterSensitiveNotifications,
                            onCheckedChange = {
                                viewModel.repository.updateUserPreferences(userPrefs.copy(filterSensitiveNotifications = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SiriCyan, checkedTrackColor = SiriCyan.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quiet Hours Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quiet Hours (10:00 PM - 07:00 AM)", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Silences spoken notifications and proactive voice announcements during nighttime", color = SiriTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = userPrefs.quietHoursEnabled,
                            onCheckedChange = {
                                viewModel.repository.updateUserPreferences(userPrefs.copy(quietHoursEnabled = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SiriViolet, checkedTrackColor = SiriViolet.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Permissions Status Audit
            Text(
                text = "Android System Permissions Audit",
                color = SiriTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Microphone
                PermissionAuditCard(
                    title = "Microphone Permission",
                    purpose = "Used exclusively for voice recognition when you activate Siri.",
                    isGranted = hasMicPermission,
                    icon = Icons.Default.Mic,
                    onOpenSettings = { openAppSettings(context) }
                )

                // Camera & Flashlight
                PermissionAuditCard(
                    title = "Camera & Flashlight",
                    purpose = "Used to control the torch and capture photos for Vision AI analysis.",
                    isGranted = hasCameraPermission,
                    icon = Icons.Default.CameraAlt,
                    onOpenSettings = { openAppSettings(context) }
                )

                // Notifications Listener
                PermissionAuditCard(
                    title = "Notification Listener",
                    purpose = "Optional: enables Siri to read and summarize permitted notifications when asked.",
                    isGranted = isNotifListenerActive,
                    icon = Icons.Default.Notifications,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Data Storage & Erasure Controls
            Text(
                text = "Local Storage & Data Deletion Controls",
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
                    Text(
                        text = "Wipe Stored Conversation History",
                        color = SiriTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Permanently removes all cached chat messages and local queries from this device.",
                        color = SiriTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF421D24)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Chat History", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Complete Local Reset (All Notes, Tasks & History)",
                        color = SiriTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Resets all personal data, tasks, notes, routines, and preferences to clean factory default state.",
                        color = SiriTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showWipeAllConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E1B24)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("wipe_all_data_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wipe All Local Data & Reset", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Clear Chat History?", color = SiriTextPrimary, fontWeight = FontWeight.Bold) },
                    text = { Text("This will permanently delete all conversation messages from local storage.", color = SiriTextSecondary) },
                    containerColor = SiriDarkSurfaceVariant,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.repository.clearHistory()
                                showDeleteConfirmDialog = false
                                Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel", color = SiriTextSecondary)
                        }
                    }
                )
            }

            if (showWipeAllConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showWipeAllConfirmDialog = false },
                    title = { Text("Wipe All Local Data?", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure? This will delete all tasks, notes, flashcards, conversation history, and preferences on this device.", color = SiriTextSecondary) },
                    containerColor = SiriDarkSurfaceVariant,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.repository.clearAllPersonalData()
                                showWipeAllConfirmDialog = false
                                Toast.makeText(context, "All local data reset to default", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Wipe Everything", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWipeAllConfirmDialog = false }) {
                            Text("Cancel", color = SiriTextSecondary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionAuditCard(
    title: String,
    purpose: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) SiriGreen.copy(alpha = 0.15f) else Color(0xFF2E384D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = if (isGranted) SiriGreen else SiriTextSecondary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isGranted) SiriGreen else Color(0xFFFFB74D),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(purpose, color = SiriTextSecondary, fontSize = 11.sp)
                }
            }

            if (!isGranted) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2A3C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enable", color = SiriCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
