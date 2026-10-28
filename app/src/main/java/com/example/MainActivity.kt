package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.ProductivityScreen
import com.example.ui.screens.VisionScreen
import com.example.ui.screens.VoiceAssistantScreen
import com.example.ui.theme.SiriAITheme
import com.example.ui.theme.SiriCyan
import com.example.ui.theme.SiriDarkBackground
import com.example.ui.theme.SiriDarkSurface
import com.example.ui.theme.SiriDarkSurfaceVariant
import com.example.ui.theme.SiriTextPrimary
import com.example.ui.theme.SiriTextSecondary
import com.example.ui.theme.SiriViolet
import com.example.viewmodel.SiriViewModel

enum class SiriTab(val title: String, val icon: ImageVector, val tag: String) {
    ASSISTANT("Siri", Icons.Default.Mic, "tab_assistant"),
    VISION("Vision", Icons.Default.CameraAlt, "tab_vision"),
    PRODUCTIVITY("Planner", Icons.Default.CheckCircleOutline, "tab_productivity"),
    AI_STUDIO("Brain", Icons.Default.AutoAwesome, "tab_ai_studio"),
    PRIVACY("Privacy", Icons.Default.Security, "tab_privacy")
}

class MainActivity : ComponentActivity() {

    private val viewModel: SiriViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SiriAITheme {
                MainContent(viewModel = viewModel)
            }
        }

        checkIntentForBackgroundListen(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentForBackgroundListen(intent)
    }

    private fun checkIntentForBackgroundListen(intent: android.content.Intent?) {
        if (intent != null) {
            val shouldListen = intent.getBooleanExtra(com.example.service.SiriVoiceService.EXTRA_TRIGGER_LISTEN, false) ||
                intent.action == com.example.service.SiriVoiceService.ACTION_TRIGGER_LISTEN
            if (shouldListen) {
                viewModel.startListening()
            }
        }
    }
}

@Composable
fun MainContent(viewModel: SiriViewModel) {
    var selectedTab by remember { mutableStateOf(SiriTab.ASSISTANT) }

    // Request necessary runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled gracefully
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SiriDarkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = SiriDarkSurface,
                contentColor = SiriTextPrimary,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("siri_bottom_nav_bar")
            ) {
                for (tab in SiriTab.values()) {
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) SiriCyan else SiriTextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                color = if (isSelected) SiriCyan else SiriTextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SiriCyan,
                            unselectedIconColor = SiriTextSecondary,
                            selectedTextColor = SiriCyan,
                            unselectedTextColor = SiriTextSecondary,
                            indicatorColor = SiriDarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                SiriTab.ASSISTANT -> VoiceAssistantScreen(viewModel = viewModel)
                SiriTab.VISION -> VisionScreen(viewModel = viewModel)
                SiriTab.PRODUCTIVITY -> ProductivityScreen(viewModel = viewModel)
                SiriTab.AI_STUDIO -> AiStudioScreen(viewModel = viewModel)
                SiriTab.PRIVACY -> PrivacyScreen(viewModel = viewModel)
            }
        }
    }
}
