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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutomationRoutine
import com.example.model.AutomationRule
import com.example.model.FlashcardItem
import com.example.model.NoteItem
import com.example.model.SmartDevice
import com.example.model.TaskItem
import com.example.model.VoiceShortcut
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

enum class ProductivityTab(val label: String) {
    TASKS("Tasks"),
    NOTES("Notes"),
    STUDY("Flashcards"),
    ROUTINES("Routines"),
    SMART_HOME("Home")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductivityScreen(
    viewModel: SiriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tasks by viewModel.repository.tasks.collectAsState()
    val notes by viewModel.repository.notes.collectAsState()
    val flashcards by viewModel.repository.flashcards.collectAsState()
    val routines by viewModel.repository.routines.collectAsState()
    val shortcuts by viewModel.repository.shortcuts.collectAsState()
    val automationRules by viewModel.repository.automationRules.collectAsState()
    val smartDevices by viewModel.repository.smartDevices.collectAsState()

    val focusRemaining by viewModel.focusTimerRemaining.collectAsState()
    val isFocusRunning by viewModel.isFocusTimerRunning.collectAsState()
    val sleepRemaining by viewModel.sleepTimerRemaining.collectAsState()

    var selectedSubTab by remember { mutableStateOf(ProductivityTab.TASKS) }

    // Task Priority Filter
    var taskPriorityFilter by remember { mutableStateOf("All") }

    // Shortcuts & Rules Dialogs
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var newShortcutTrigger by remember { mutableStateOf("") }
    var newShortcutCommand by remember { mutableStateOf("") }
    var newShortcutReply by remember { mutableStateOf("") }

    // Notes state
    var noteSearchQuery by remember { mutableStateOf("") }
    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteContent by remember { mutableStateOf("") }
    var newNoteCategory by remember { mutableStateOf("Study") }
    var showAddNoteDialog by remember { mutableStateOf(false) }

    // Flashcards state
    var newCardQuestion by remember { mutableStateOf("") }
    var newCardAnswer by remember { mutableStateOf("") }
    var newCardTopic by remember { mutableStateOf("General") }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var revealedCardId by remember { mutableStateOf<String?>(null) }

    // Tasks state
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskPriority by remember { mutableStateOf("Normal") }

    val scrollState = rememberScrollState()

    val minutes = focusRemaining / 60
    val seconds = focusRemaining % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    // Conflict detection: find task times with multiple items
    val timeConflicts = tasks
        .filter { !it.isCompleted && it.timeLabel.isNotBlank() && it.timeLabel != "Today" }
        .groupBy { it.timeLabel }
        .filter { it.value.size > 1 }

    val filteredTasks = tasks.filter {
        taskPriorityFilter == "All" || it.priority.equals(taskPriorityFilter, ignoreCase = true)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SiriDarkBackground,
                        Color(0xFF0C1322),
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
            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SiriGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = SiriGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Productivity & Learning",
                        color = SiriTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Focus timers, notes, flashcards, automations & home",
                        color = SiriTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-Navigation Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (tab in ProductivityTab.values()) {
                    val isSelected = selectedSubTab == tab
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) SiriCyan.copy(alpha = 0.25f) else SiriDarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SiriCyan else Color(0xFF28364D)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedSubTab = tab }
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) SiriCyan else SiriTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedSubTab) {
                ProductivityTab.TASKS -> {
                    // Focus Study Timer Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SiriCyan.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = SiriCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pomodoro Focus Session", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Surface(
                                    color = if (isFocusRunning) SiriCyan.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (isFocusRunning) "Active Session" else "Paused",
                                        color = if (isFocusRunning) SiriCyan else SiriTextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = formattedTime,
                                color = SiriTextPrimary,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Focus Duration Presets
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                for (duration in listOf(15, 25, 45, 60)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF192436),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D)),
                                        modifier = Modifier.clickable {
                                            viewModel.resetFocusTimer(duration)
                                        }
                                    ) {
                                        Text("${duration}m", color = SiriTextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.toggleFocusTimer() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFocusRunning) SiriMagenta else SiriCyan
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("toggle_focus_timer_button")
                                ) {
                                    Icon(
                                        imageVector = if (isFocusRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isFocusRunning) "Pause" else "Start Focus", color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.resetFocusTimer(25) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D283A)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("reset_focus_timer_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = SiriTextSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reset", color = SiriTextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Media Sleep Timer Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = SiriViolet, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Media Sleep Timer", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                if (sleepRemaining > 0) {
                                    Text("${sleepRemaining / 60}m ${sleepRemaining % 60}s left", color = SiriViolet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val timerOptions = listOf(15, 30, 45, 60)
                                for (t in timerOptions) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF162235),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3E58)),
                                        modifier = Modifier.weight(1f).clickable {
                                            viewModel.startSleepTimer(t)
                                            Toast.makeText(context, "Sleep timer set for $t minutes", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("${t}m", color = SiriTextPrimary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                                if (sleepRemaining > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF381A22),
                                        modifier = Modifier.clickable { viewModel.cancelSleepTimer() }
                                    ) {
                                        Text("Off", color = Color(0xFFFF8A80), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Schedule Conflict Warning
                    if (timeConflicts.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF382315)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Schedule Conflict Detected", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = "Multiple tasks scheduled at: ${timeConflicts.keys.joinToString(", ")}",
                                        color = Color(0xFFFFE082),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Daily Planner & Tasks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tasks & Prioritization",
                            color = SiriTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (p in listOf("All", "High", "Normal")) {
                                val isPSelected = taskPriorityFilter == p
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isPSelected) SiriCyan.copy(alpha = 0.2f) else Color(0xFF162030),
                                    modifier = Modifier.clickable { taskPriorityFilter = p }
                                ) {
                                    Text(p, color = if (isPSelected) SiriCyan else SiriTextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Add Task Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            placeholder = { Text("Add task (e.g. “Prepare slides”)", color = SiriTextSecondary, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f).testTag("new_task_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SiriCyan,
                                unfocusedBorderColor = Color(0xFF28364D),
                                focusedContainerColor = SiriDarkSurfaceVariant,
                                unfocusedContainerColor = SiriDarkSurfaceVariant,
                                focusedTextColor = SiriTextPrimary,
                                unfocusedTextColor = SiriTextPrimary
                            )
                        )

                        Button(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    viewModel.repository.addTask(newTaskTitle.trim(), "Today", "General", newTaskPriority)
                                    newTaskTitle = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SiriCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("add_task_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (task in filteredTasks) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.isCompleted,
                                        onCheckedChange = { viewModel.repository.toggleTask(task.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = SiriGreen,
                                            uncheckedColor = SiriTextSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            color = if (task.isCompleted) SiriTextSecondary else SiriTextPrimary,
                                            fontSize = 14.sp,
                                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${task.timeLabel} • ${task.category}",
                                                color = SiriTextSecondary,
                                                fontSize = 11.sp
                                            )
                                            if (task.priority == "High") {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFFF5252).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("HIGH", color = Color(0xFFFF5252), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.repository.removeTask(task.id) }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                ProductivityTab.NOTES -> {
                    // Notes Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice & Written Notes", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val exported = viewModel.repository.exportNotes()
                                    viewModel.deviceController.shareContent(exported, "Export Notes")
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export Notes", tint = SiriCyan, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { showAddNoteDialog = !showAddNoteDialog },
                                colors = ButtonDefaults.buttonColors(containerColor = SiriCyan),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Note", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Notes Field
                    OutlinedTextField(
                        value = noteSearchQuery,
                        onValueChange = { noteSearchQuery = it },
                        placeholder = { Text("Search notes...", color = SiriTextSecondary, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SiriTextSecondary) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SiriDarkSurfaceVariant,
                            unfocusedContainerColor = SiriDarkSurfaceVariant,
                            focusedTextColor = SiriTextPrimary,
                            unfocusedTextColor = SiriTextPrimary
                        )
                    )

                    // Add Note Dialog/Form
                    if (showAddNoteDialog) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141F30)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SiriCyan.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("New Note", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newNoteTitle,
                                    onValueChange = { newNoteTitle = it },
                                    placeholder = { Text("Title", color = SiriTextSecondary, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newNoteContent,
                                    onValueChange = { newNoteContent = it },
                                    placeholder = { Text("Note content...", color = SiriTextSecondary, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (newNoteTitle.isNotBlank() && newNoteContent.isNotBlank()) {
                                                viewModel.repository.addNote(newNoteTitle.trim(), newNoteContent.trim(), newNoteCategory)
                                                newNoteTitle = ""
                                                newNoteContent = ""
                                                showAddNoteDialog = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SiriCyan)
                                    ) {
                                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { showAddNoteDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF243042))
                                    ) {
                                        Text("Cancel", color = SiriTextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredNotes = notes.filter {
                        noteSearchQuery.isBlank() || it.title.contains(noteSearchQuery, ignoreCase = true) || it.content.contains(noteSearchQuery, ignoreCase = true)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (note in filteredNotes) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28364D))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = SiriCyan.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(note.category, color = SiriCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.repository.deleteNote(note.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(note.title, color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(note.content, color = SiriTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }

                ProductivityTab.STUDY -> {
                    // Flashcards Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Recall Flashcards", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Button(
                            onClick = { showAddCardDialog = !showAddCardDialog },
                            colors = ButtonDefaults.buttonColors(containerColor = SiriViolet),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Card", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (showAddCardDialog) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E172E)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SiriViolet.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("New Flashcard", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newCardQuestion,
                                    onValueChange = { newCardQuestion = it },
                                    placeholder = { Text("Question or Prompt", color = SiriTextSecondary, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newCardAnswer,
                                    onValueChange = { newCardAnswer = it },
                                    placeholder = { Text("Answer", color = SiriTextSecondary, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (newCardQuestion.isNotBlank() && newCardAnswer.isNotBlank()) {
                                                viewModel.repository.addFlashcard(newCardQuestion.trim(), newCardAnswer.trim(), newCardTopic)
                                                newCardQuestion = ""
                                                newCardAnswer = ""
                                                showAddCardDialog = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SiriViolet)
                                    ) {
                                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { showAddCardDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282038))
                                    ) {
                                        Text("Cancel", color = SiriTextPrimary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (card in flashcards) {
                            val isRevealed = revealedCardId == card.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        revealedCardId = if (isRevealed) null else card.id
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isRevealed) SiriViolet else Color(0xFF28364D)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = SiriViolet.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(card.topic, color = SiriViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.repository.deleteFlashcard(card.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Q: ${card.question}",
                                        color = SiriTextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (isRevealed) {
                                        Surface(
                                            color = Color(0xFF13221C),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "A: ${card.answer}",
                                                color = SiriGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    } else {
                                        Text("Tap to reveal answer", color = SiriTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                ProductivityTab.ROUTINES -> {
                    // Predefined & Custom Modes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modes & Automations",
                            color = SiriTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddShortcutDialog = !showAddShortcutDialog },
                            colors = ButtonDefaults.buttonColors(containerColor = SiriCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Shortcut", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    if (showAddShortcutDialog) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF152233)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SiriCyan.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("New Voice Shortcut", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newShortcutTrigger,
                                    onValueChange = { newShortcutTrigger = it },
                                    placeholder = { Text("Trigger (e.g. “leaving home”)", color = SiriTextSecondary, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newShortcutCommand,
                                    onValueChange = { newShortcutCommand = it },
                                    placeholder = { Text("Action (e.g. “turn off flashlight”)", color = SiriTextSecondary, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (newShortcutTrigger.isNotBlank() && newShortcutCommand.isNotBlank()) {
                                                viewModel.repository.addVoiceShortcut(newShortcutTrigger, newShortcutCommand)
                                                newShortcutTrigger = ""
                                                newShortcutCommand = ""
                                                showAddShortcutDialog = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SiriCyan)
                                    ) {
                                        Text("Save Shortcut", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { showAddShortcutDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF243042))
                                    ) {
                                        Text("Cancel", color = SiriTextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    // Voice Shortcuts List
                    if (shortcuts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Custom Voice Shortcuts", color = SiriCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        for (sc in shortcuts) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SiriDarkSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("“${sc.triggerPhrase}”", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Runs: ${sc.commandText}", color = SiriTextSecondary, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { viewModel.repository.deleteVoiceShortcut(sc.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Predefined Workflows", color = SiriTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (routine in routines) {
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(routine.name, color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Say: “Hey Siri, ${routine.triggerPhrase}”",
                                                color = SiriViolet,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = { viewModel.handleUserUtterance(routine.triggerPhrase) },
                                                colors = ButtonDefaults.buttonColors(containerColor = SiriViolet.copy(alpha = 0.2f)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.testTag("run_routine_${routine.id}")
                                            ) {
                                                Text("Run", color = SiriViolet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Switch(
                                                checked = routine.isEnabled,
                                                onCheckedChange = { viewModel.repository.toggleRoutine(routine.id) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = SiriCyan,
                                                    checkedTrackColor = SiriCyan.copy(alpha = 0.3f)
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        for ((index, step) in routine.steps.withIndex()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF25344C)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("${index + 1}", color = SiriTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(step.description, color = SiriTextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ProductivityTab.SMART_HOME -> {
                    // Smart Home Section
                    Text("Smart Home Controls", color = SiriTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (dev in smartDevices.take(2)) {
                            SmartDeviceCard(
                                device = dev,
                                modifier = Modifier.weight(1f),
                                onToggle = { viewModel.repository.toggleSmartDevice(dev.id) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (dev in smartDevices.drop(2).take(2)) {
                            SmartDeviceCard(
                                device = dev,
                                modifier = Modifier.weight(1f),
                                onToggle = { viewModel.repository.toggleSmartDevice(dev.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartDeviceCard(
    device: SmartDevice,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isOn) Color(0xFF16253B) else SiriDarkSurfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (device.isOn) SiriCyan.copy(alpha = 0.5f) else Color(0xFF28364D)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (device.isOn) SiriCyan.copy(alpha = 0.2f) else Color(0xFF1E2838)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.type == "LIGHT") Icons.Default.Lightbulb else Icons.Default.Power,
                        contentDescription = null,
                        tint = if (device.isOn) SiriCyan else SiriTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (device.isOn) SiriGreen else Color(0xFF64748B))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(device.name, color = SiriTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(device.stateValue, color = if (device.isOn) SiriCyan else SiriTextSecondary, fontSize = 11.sp)
        }
    }
}
