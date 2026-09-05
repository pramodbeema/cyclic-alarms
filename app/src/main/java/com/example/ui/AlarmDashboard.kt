package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Alarm
import com.example.data.AlarmLog
import com.example.data.AlarmScheduler
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════
//  MAIN DASHBOARD
// ════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmDashboard(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val logs   by viewModel.logs.collectAsStateWithLifecycle()
    val activeAlarm by com.example.service.RingingState.activeAlarm.collectAsState()

    var showSettings  by remember { mutableStateOf(false) }
    var showAddEdit   by remember { mutableStateOf(false) }
    var editTarget    by remember { mutableStateOf<Alarm?>(null) }
    var currentTab    by remember { mutableStateOf(0) }
    var selectedIds   by remember { mutableStateOf(setOf<Int>()) }
    val selectionMode = selectedIds.isNotEmpty()

    val themeMode  by viewModel.themeMode.collectAsState()
    val snoozeMins by viewModel.snoozeMinutes.collectAsState()

    var clock by remember { mutableStateOf("--:--:--") }
    var date  by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val tf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val df = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        while (true) {
            val now = Calendar.getInstance().time
            clock = tf.format(now); date = df.format(now)
            delay(1000)
        }
    }

    var hasNotifPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasNotifPerm = it }

    val nextAlarmText = remember(alarms) {
        val enabled = alarms.filter { it.isEnabled }
        if (enabled.isEmpty()) return@remember "No active alarms"
        val next = enabled.map { it to AlarmScheduler.calculateNextTriggerTime(it) }.minByOrNull { it.second }
        next?.let {
            val diff = it.second - System.currentTimeMillis()
            if (diff > 0) {
                val h = diff / 3_600_000; val m = (diff / 60_000) % 60
                "Next: ${SimpleDateFormat("E hh:mm a", Locale.getDefault()).format(Date(it.second))}  (${h}h ${m}m)"
            } else "Calculating…"
        } ?: "No active alarms"
    }

    if (activeAlarm != null) { RingingScreen(activeAlarm = activeAlarm!!); return }

    val c = LocalAppColors.current

    Scaffold(
        containerColor = c.appBackground,
        floatingActionButton = {
            if (currentTab == 0) {
                if (selectionMode) {
                    FloatingActionButton(
                        onClick = { selectedIds.forEach { id -> alarms.find { it.id == id }?.let { viewModel.deleteAlarm(it) } }; selectedIds = emptySet() },
                        containerColor = DeleteRed, contentColor = Color.White, shape = RoundedCornerShape(16.dp)
                    ) { Icon(Icons.Default.Delete, contentDescription = "Delete selected", modifier = Modifier.size(26.dp)) }
                } else {
                    FloatingActionButton(
                        onClick = { editTarget = null; showAddEdit = true },
                        containerColor = BrandBlue, contentColor = Color(0xFF003166), shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("add_alarm_fab")
                    ) { Icon(Icons.Default.Add, contentDescription = "Add alarm", modifier = Modifier.size(28.dp)) }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(c.surfaceColor)) {
                HorizontalDivider(color = c.outlineColor)
                Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    BottomTab("Alarms",  Icons.Default.Notifications,    currentTab == 0) { currentTab = 0 }
                    BottomTab("History", Icons.AutoMirrored.Filled.List, currentTab == 1) { currentTab = 1 }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(c.appBackground).padding(padding).padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    // "Beema's FINCON" — tap to send feedback email
                    Text(
                        text = "Beema's FINCON",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandBlue,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:bp.beema@outlook.com")
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Cyclic Alarms Feedback")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Send Feedback"))
                        }
                    )
                    Text(clock, fontSize = 28.sp, fontWeight = FontWeight.Black, color = c.textPrimary, fontFamily = FontFamily.Monospace)
                    Text(date,  fontSize = 13.sp, color = c.textSecondary)
                }
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(c.surfaceVariant).border(BorderStroke(1.dp, c.outlineColor), CircleShape).clickable { showSettings = true }.testTag("settings_gear_button"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Next alarm pill
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(
                if (c.isDark) Brush.horizontalGradient(listOf(Color(0xFF1C2D4A), Color(0xFF1E1A2E)))
                else Brush.horizontalGradient(listOf(Color(0xFFD6E4FF), Color(0xFFEBDEFF)))
            ).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(nextAlarmText, fontSize = 12.sp, color = if (c.isDark) BrandCyan else BrandBlue, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Notification banner
            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2D1810)).border(BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠  Notifications off — alarms won't ring", color = WarningAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Fix", color = WarningAmber, fontWeight = FontWeight.ExtraBold) }
                }
            }

            // Selection bar
            AnimatedVisibility(visible = selectionMode, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedIds = emptySet() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${selectedIds.size} selected", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    val allSel = alarms.isNotEmpty() && selectedIds.containsAll(alarms.map { it.id })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedIds = if (allSel) emptySet() else alarms.map { it.id }.toSet() }) {
                            Text(if (allSel) "None" else "All", color = BrandBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { selectedIds.forEach { id -> alarms.find { it.id == id }?.let { viewModel.deleteAlarm(it) } }; selectedIds = emptySet() },
                            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab content
            AnimatedContent(targetState = currentTab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { tab ->
                if (tab == 0) {
                    if (alarms.isEmpty()) {
                        EmptyStateView(Icons.Default.Notifications, "No Alarms Yet", "Tap + to create your first cyclic or weekly alarm.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                            items(alarms, key = { it.id }) { alarm ->
                                val isSel = selectedIds.contains(alarm.id)
                                AlarmCard(
                                    alarm = alarm, isSelected = isSel, isSelectionMode = selectionMode,
                                    onToggle = { if (!selectionMode) viewModel.toggleAlarm(alarm) },
                                    onEdit = { editTarget = alarm; showAddEdit = true },
                                    onDelete = { viewModel.deleteAlarm(alarm) },
                                    onPlayPreview = { viewModel.previewSound(alarm.soundPreset, alarm.volume) },
                                    onLongPress = { selectedIds = selectedIds + alarm.id },
                                    onSelectToggle = { selectedIds = if (isSel) selectedIds - alarm.id else selectedIds + alarm.id }
                                )
                            }
                        }
                    }
                } else {
                    if (logs.isEmpty()) {
                        EmptyStateView(Icons.AutoMirrored.Filled.List, "No History", "Dismissed and snoozed alarms will appear here.")
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.clearLogs() }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = DeleteRed, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear", color = DeleteRed, fontWeight = FontWeight.Bold)
                                }
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(bottom = 80.dp)) {
                                items(logs, key = { it.id }) { LogItem(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            themeMode = themeMode,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            snoozeMinutes = snoozeMins,
            onSnoozeMinutesChange = { viewModel.setSnoozeMinutes(it) }
        )
    }
    if (showAddEdit) {
        AlarmAddEditDialog(
            alarm = editTarget,
            onDismiss = { viewModel.stopPreview(); showAddEdit = false },
            onSave = { a -> viewModel.stopPreview(); if (editTarget == null) viewModel.addAlarm(a) else viewModel.updateAlarm(a); showAddEdit = false },
            onPlayPreview = { s, v -> viewModel.previewSound(s, v) },
            onStopPreview = { viewModel.stopPreview() }
        )
    }
}

// ════════════════════════════════════════════════════════
//  BOTTOM TAB
// ════════════════════════════════════════════════════════
@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp)) {
        Box(modifier = Modifier.width(56.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) BrandBlue.copy(alpha = 0.2f) else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (selected) BrandBlue else c.textDisabled, modifier = Modifier.size(18.dp))
        }
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal, color = if (selected) BrandBlue else c.textDisabled)
    }
}

// ════════════════════════════════════════════════════════
//  ALARM CARD
// ════════════════════════════════════════════════════════
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlarmCard(
    alarm: Alarm, isSelected: Boolean, isSelectionMode: Boolean,
    onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onPlayPreview: () -> Unit, onLongPress: () -> Unit, onSelectToggle: () -> Unit
) {
    val isCyclic   = alarm.alarmType != "WEEKLY"
    val c          = LocalAppColors.current
    val cardBg     = if (isCyclic) c.cyclicCardBg else c.weeklyCardBg
    val cardBorder = if (isCyclic) c.cyclicCardBorder else c.weeklyCardBorder
    val accent     = if (isCyclic) CyclicAccent else WeeklyAccent
    val timeCol    = if (isCyclic) CyclicTime else WeeklyTime
    val alpha      = if (alarm.isEnabled) 1f else 0.4f

    val displayHour = when { alarm.hour == 0 -> 12; alarm.hour > 12 -> alarm.hour - 12; else -> alarm.hour }
    val ampm    = if (alarm.hour >= 12) "PM" else "AM"
    val timeStr = String.format("%02d:%02d", displayHour, alarm.minute)

    val headerText = if (isCyclic) "CYCLIC · EVERY ${alarm.cyclicIntervalDays}D"
    else {
        val d = alarm.getWeeklyDaysSet()
        when {
            d.isEmpty() -> "ONE-OFF"
            d.size == 7 -> "DAILY"
            d.size == 5 && !d.contains(6) && !d.contains(7) -> "MON – FRI"
            d.size == 2 && d.contains(6) && d.contains(7)   -> "WEEKENDS"
            else -> listOf("Mo","Tu","We","Th","Fr","Sa","Su").let { n -> d.sorted().joinToString(" · ") { n[it - 1] } }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .shadow(if (alarm.isEnabled) 4.dp else 0.dp, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { if (isSelectionMode) onSelectToggle() }, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) accent.copy(alpha = 0.18f) else cardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isSelected) accent else cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {

            // Badge + switch row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(headerText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accent.copy(alpha = alpha), letterSpacing = 0.8.sp)
                }
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onSelectToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = c.textDisabled))
                } else {
                    Switch(checked = alarm.isEnabled, onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent, uncheckedThumbColor = c.textDisabled, uncheckedTrackColor = c.surfaceVariant),
                        modifier = Modifier.testTag("alarm_switch_${alarm.id}"))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Time + sound pill row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(timeStr, fontSize = 48.sp, fontWeight = FontWeight.Black, color = timeCol.copy(alpha = alpha), fontFamily = FontFamily.Monospace, lineHeight = 48.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(ampm, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = accent.copy(alpha = alpha), modifier = Modifier.padding(bottom = 8.dp))
                Spacer(modifier = Modifier.weight(1f))
                if (!isSelectionMode) {
                    Box(modifier = Modifier.align(Alignment.Bottom).clip(RoundedCornerShape(8.dp)).background(c.surfaceVariant).clickable { onPlayPreview() }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = accent.copy(alpha = alpha), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(alarm.soundPreset.take(14), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.textSecondary.copy(alpha = alpha))
                        }
                    }
                }
            }

            // Label
            if (alarm.label.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(alarm.label, fontSize = 13.sp, color = c.textSecondary.copy(alpha = alpha), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = cardBorder)
                Spacer(modifier = Modifier.height(10.dp))

                // Edit + Delete buttons — full width, equal size
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.2f), contentColor = accent),
                        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(onClick = onDelete, modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeleteRed.copy(alpha = 0.18f), contentColor = DeleteRed),
                        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  LOG ITEM
// ════════════════════════════════════════════════════════
@Composable
fun LogItem(log: AlarmLog) {
    val c = LocalAppColors.current
    val dateStr = remember(log.triggerTime) { SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault()).format(Date(log.triggerTime)) }
    val color = when (log.action) { "DISMISSED" -> BrandBlue; "SNOOZED" -> BrandPurple; else -> c.textSecondary }
    val icon  = when (log.action) { "DISMISSED" -> Icons.Default.Close; "SNOOZED" -> Icons.Default.Refresh; else -> Icons.Default.Notifications }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = c.surfaceColor), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, c.outlineColor)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(log.label.ifEmpty { "Alarm" }, color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(dateStr, color = c.textSecondary, fontSize = 11.sp)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(log.action, color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  EMPTY STATE
// ════════════════════════════════════════════════════════
@Composable
fun EmptyStateView(icon: ImageVector, title: String, description: String) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(72.dp).background(c.surfaceVariant, CircleShape).border(BorderStroke(1.dp, c.outlineColor), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.textDisabled, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, color = c.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(description, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

// ════════════════════════════════════════════════════════
//  SECTION CARD HELPER
// ════════════════════════════════════════════════════════
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.appBackground).border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 0.8.sp)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

// ════════════════════════════════════════════════════════
//  TIME SPINNER HELPER
// ════════════════════════════════════════════════════════
@Composable
private fun TimeSpinner(
    value: String, onUp: () -> Unit, onDown: () -> Unit,
    onValueChange: (String) -> Unit, onDone: (() -> Unit)? = null, tag: String
) {
    val c = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onUp, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = BrandBlue)
        }
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, color = c.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.width(68.dp).testTag(tag),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = c.outlineColor,
                focusedContainerColor = c.surfaceVariant,
                unfocusedContainerColor = c.surfaceVariant,
                cursorColor = BrandBlue,
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                disabledTextColor = c.textPrimary,
                disabledContainerColor = c.surfaceVariant,
            ),
            singleLine = true, shape = RoundedCornerShape(10.dp)
        )
        IconButton(onClick = onDown, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = BrandBlue)
        }
    }
}

// ════════════════════════════════════════════════════════
//  ADD / EDIT DIALOG
// ════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmAddEditDialog(
    alarm: Alarm?, onDismiss: () -> Unit, onSave: (Alarm) -> Unit,
    onPlayPreview: (String, Float) -> Unit, onStopPreview: () -> Unit
) {
    // Default to current time for new alarms
    val nowCal  = remember { java.util.Calendar.getInstance() }
    val nowHour = remember { nowCal.get(java.util.Calendar.HOUR_OF_DAY) }
    val nowMin  = remember { nowCal.get(java.util.Calendar.MINUTE) }

    var label     by remember { mutableStateOf(alarm?.label ?: "") }
    var hour      by remember { mutableIntStateOf(alarm?.hour ?: nowHour) }
    var minute    by remember { mutableIntStateOf(alarm?.minute ?: nowMin) }
    var alarmType by remember { mutableStateOf(alarm?.alarmType ?: "WEEKLY") }
    var isPm      by remember { mutableStateOf((alarm?.hour ?: nowHour) >= 12) }
    var hour12Str by remember { mutableStateOf(run { val h = alarm?.hour ?: nowHour; (if (h == 0) 12 else if (h > 12) h - 12 else h).toString() }) }
    var minuteStr by remember { mutableStateOf(String.format("%02d", alarm?.minute ?: nowMin)) }

    val weeklyDays = remember { mutableStateMapOf<Int, Boolean>() }
    val origDays   = alarm?.getWeeklyDaysSet() ?: emptySet()
    LaunchedEffect(Unit) { for (i in 1..7) weeklyDays[i] = origDays.contains(i) }

    var cyclicInterval  by remember { mutableStateOf((alarm?.cyclicIntervalDays ?: 3).toString()) }
    var cyclicStart     by remember { mutableLongStateOf(alarm?.cyclicStartDate ?: System.currentTimeMillis()) }
    var showDatePicker  by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = cyclicStart)

    var soundPreset    by remember { mutableStateOf(alarm?.soundPreset ?: "High Pitch") }
    var customTrackUri by remember { mutableStateOf(alarm?.customTrackUri ?: "") }
    var vibrate        by remember { mutableStateOf(alarm?.vibrate ?: true) }
    var volume         by remember { mutableFloatStateOf(alarm?.volume ?: 1.0f) }
    var isPreviewing   by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val presets = listOf("High Pitch","Zen Bowl","Sunrise Chime","Digital Beeps","Morning Forest","Synth Wave Beat","Cyber Alert","Lofi Chord")

    val trackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            customTrackUri = uri.toString(); soundPreset = "Custom Track"
        }
    }

    fun syncTime() {
        val h12 = (hour12Str.toIntOrNull() ?: 12).coerceIn(1, 12)
        hour   = if (h12 == 12) (if (isPm) 12 else 0) else (if (isPm) h12 + 12 else h12)
        minute = (minuteStr.toIntOrNull() ?: 0).coerceIn(0, 59)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val c = LocalAppColors.current
        Surface(modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f), shape = RoundedCornerShape(20.dp), color = c.surfaceColor, border = BorderStroke(1.dp, c.outlineColor)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (alarm == null) "New Alarm" else "Edit Alarm", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = c.textSecondary) }
                }
                HorizontalDivider(color = c.outlineColor, modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // TIME PICKER
                    item {
                        SectionCard("Alarm Time") {
                            val focusMgr = LocalFocusManager.current
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                TimeSpinner(value = hour12Str, tag = "hour_input",
                                    onUp   = { val v = hour12Str.toIntOrNull() ?: 12; hour12Str = (if (v == 12) 1 else v + 1).toString(); syncTime() },
                                    onDown = { val v = hour12Str.toIntOrNull() ?: 12; hour12Str = (if (v == 1) 12 else v - 1).toString(); syncTime() },
                                    onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { hour12Str = it; syncTime() } })
                                Text(":", fontSize = 36.sp, fontWeight = FontWeight.Black, color = BrandBlue, modifier = Modifier.padding(horizontal = 8.dp))
                                TimeSpinner(value = minuteStr, tag = "minute_input",
                                    onUp   = { minuteStr = String.format("%02d", ((minuteStr.toIntOrNull() ?: 0) + 1) % 60); syncTime() },
                                    onDown = { minuteStr = String.format("%02d", ((minuteStr.toIntOrNull() ?: 0) - 1 + 60) % 60); syncTime() },
                                    onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { minuteStr = it; syncTime() } },
                                    onDone = { focusMgr.clearFocus(); minuteStr = String.format("%02d", minuteStr.toIntOrNull() ?: 0); syncTime() })
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("AM" to false, "PM" to true).forEach { (lbl, pm) ->
                                        Box(modifier = Modifier.width(52.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(if (isPm == pm) BrandBlue else c.surfaceVariant).clickable { isPm = pm; syncTime() }.testTag("${lbl.lowercase()}_button"), contentAlignment = Alignment.Center) {
                                            Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (isPm == pm) Color.White else c.textSecondary)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val rh = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                            Text("→  ${String.format("%d:%02d %s", rh, minute, if (hour >= 12) "PM" else "AM")}", color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }

                    // LABEL
                    item {
                        OutlinedTextField(value = label, onValueChange = { label = it },
                            label = { Text("Label (optional)", color = c.textSecondary) },
                            placeholder = { Text("e.g. Work Shift", color = c.textDisabled) },
                            modifier = Modifier.fillMaxWidth().testTag("alarm_label_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = c.outlineColor, focusedLabelColor = BrandBlue, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = BrandBlue),
                            shape = RoundedCornerShape(12.dp), singleLine = true)
                    }

                    // TYPE TABS
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.appBackground).padding(4.dp)) {
                            listOf("WEEKLY" to "Weekly Days", "CYCLIC" to "Cyclic Days").forEach { (type, lbl) ->
                                val active = alarmType == type
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (active) (if (type == "WEEKLY") BrandPurple else BrandBlue) else Color.Transparent).clickable { alarmType = type }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(lbl, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (active) Color.White else c.textSecondary)
                                }
                            }
                        }
                    }

                    // SCHEDULE DETAILS
                    item {
                        AnimatedContent(targetState = alarmType, label = "sched") { type ->
                            if (type == "WEEKLY") {
                                SectionCard("Repeat on Days") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        val dn = listOf("Mo","Tu","We","Th","Fr","Sa","Su")
                                        for (i in 1..7) {
                                            val a = weeklyDays[i] ?: false
                                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (a) BrandPurple else c.surfaceVariant).border(BorderStroke(1.dp, if (a) BrandPurple else c.outlineColor), CircleShape).clickable { weeklyDays[i] = !a }.testTag("day_button_$i"), contentAlignment = Alignment.Center) {
                                                Text(dn[i - 1], fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (a) Color.White else c.textSecondary)
                                            }
                                        }
                                    }
                                }
                            } else {
                                SectionCard("Cyclic Configuration") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Every", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(Modifier.width(10.dp))
                                        OutlinedTextField(value = cyclicInterval, onValueChange = { if (it.all(Char::isDigit)) cyclicInterval = it },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(72.dp).testTag("cyclic_interval_input"), singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = c.outlineColor, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = BrandBlue),
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = TextStyle(textAlign = TextAlign.Center, color = c.textPrimary, fontSize = 14.sp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("days", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    val sdf = remember { SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()) }
                                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(10.dp)).clickable { showDatePicker = true }.padding(12.dp).testTag("cyclic_start_date_selector_card"),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text("Start Date", fontSize = 11.sp, color = c.textSecondary, fontWeight = FontWeight.Bold)
                                                Text(sdf.format(Date(cyclicStart)), fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    if (showDatePicker) {
                                        DatePickerDialog(onDismissRequest = { showDatePicker = false },
                                            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { cyclicStart = it }; showDatePicker = false }) { Text("OK", color = BrandBlue, fontWeight = FontWeight.Bold) } },
                                            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = c.textSecondary) } }
                                        ) { DatePicker(state = datePickerState) }
                                    }
                                }
                            }
                        }
                    }

                    // SOUND
                    item {
                        SectionCard("Alarm Sound") {
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (soundPreset == "Custom Track") BrandBlue.copy(alpha = 0.12f) else c.surfaceVariant).border(BorderStroke(1.dp, if (soundPreset == "Custom Track") BrandBlue else c.outlineColor), RoundedCornerShape(10.dp)).clickable { trackPicker.launch(arrayOf("audio/*")) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = if (soundPreset == "Custom Track") BrandBlue else c.textSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Pick from device", color = if (soundPreset == "Custom Track") BrandBlue else c.textSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (customTrackUri.isNotEmpty()) Text(customTrackUri.substringAfterLast("/").take(28), color = c.textDisabled, fontSize = 10.sp)
                                    }
                                }
                                if (soundPreset == "Custom Track" && customTrackUri.isNotEmpty()) {
                                    IconButton(onClick = { if (isPreviewing == "Custom Track") { onStopPreview(); isPreviewing = null } else { onPlayPreview(customTrackUri, volume); isPreviewing = "Custom Track" } }, modifier = Modifier.size(32.dp)) {
                                        Icon(if (isPreviewing == "Custom Track") Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null, tint = BrandBlue)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                presets.forEach { preset ->
                                    val sel  = preset == soundPreset
                                    val play = isPreviewing == preset
                                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) BrandPurple.copy(alpha = 0.12f) else c.surfaceVariant).border(BorderStroke(1.dp, if (sel) BrandPurple else c.outlineColor), RoundedCornerShape(10.dp)).clickable { soundPreset = preset }.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = sel, onClick = { soundPreset = preset }, colors = RadioButtonDefaults.colors(selectedColor = BrandPurple, unselectedColor = c.textDisabled))
                                            Spacer(Modifier.width(4.dp))
                                            Text(preset, color = if (sel) c.textPrimary else c.textSecondary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                        }
                                        IconButton(onClick = { if (play) { onStopPreview(); isPreviewing = null } else { onPlayPreview(preset, volume); isPreviewing = preset } }, modifier = Modifier.size(32.dp)) {
                                            Icon(if (play) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null, tint = if (play) DeleteRed else BrandPurple, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // VOLUME + VIBRATE
                    item {
                        SectionCard("Volume & Vibration") {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                Slider(value = volume, onValueChange = { volume = it }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue, inactiveTrackColor = c.surfaceVariant))
                                Icon(Icons.Default.Build, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = if (vibrate) BrandPurple else c.textDisabled, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Vibrate", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Switch(checked = vibrate, onCheckedChange = { vibrate = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandPurple, uncheckedThumbColor = c.textDisabled, uncheckedTrackColor = c.surfaceVariant))
                            }
                        }
                    }
                }

                HorizontalDivider(color = c.outlineColor, modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                        border = BorderStroke(1.dp, c.outlineColor), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = {
                        val daysStr  = weeklyDays.filterValues { it }.keys.sorted().joinToString(",")
                        val interval = cyclicInterval.toIntOrNull() ?: 3
                        onSave(Alarm(id = alarm?.id ?: 0, label = label, hour = hour, minute = minute, isEnabled = true,
                            alarmType = alarmType, weeklyDays = daysStr, cyclicIntervalDays = interval,
                            cyclicStartDate = cyclicStart, soundPreset = soundPreset, customTrackUri = customTrackUri,
                            vibrate = vibrate, volume = volume, lastTriggeredTime = alarm?.lastTriggeredTime ?: 0L))
                    }, modifier = Modifier.weight(1.6f).height(48.dp).testTag("save_alarm_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color(0xFF003166)),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("Save Alarm", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  RINGING SCREEN
// ════════════════════════════════════════════════════════
@Composable
fun RingingScreen(activeAlarm: com.example.service.RingingState.ActiveAlarm) {
    val context = LocalContext.current
    var ticks by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(800); ticks++ } }

    val pulse by animateFloatAsState(targetValue = if (ticks % 2 == 0) 1.1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "pulse")

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppBackground, Color(0xFF0D1B3E), Color(0xFF160D2E)))).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight().padding(vertical = 40.dp)) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ALARM RINGING", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue, letterSpacing = 2.sp)
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.size(160.dp).graphicsLayer(scaleX = pulse, scaleY = pulse).background(BrandBlue.copy(alpha = 0.15f), CircleShape).border(BorderStroke(2.dp, BrandBlue.copy(alpha = 0.3f)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(80.dp))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val timeStr = remember(activeAlarm.hour, activeAlarm.minute) {
                    val dh = if (activeAlarm.hour == 0) 12 else if (activeAlarm.hour > 12) activeAlarm.hour - 12 else activeAlarm.hour
                    String.format("%02d:%02d %s", dh, activeAlarm.minute, if (activeAlarm.hour >= 12) "PM" else "AM")
                }
                Text(timeStr, fontSize = 60.sp, fontWeight = FontWeight.Black, color = TextPrimary, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                Text(activeAlarm.label.ifEmpty { "Cyclic Alarm" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextSecondary, textAlign = TextAlign.Center)
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { com.example.service.AlarmService.snoozeAlarm(context, activeAlarm.alarmId, activeAlarm.label, activeAlarm.hour, activeAlarm.minute, activeAlarm.soundPreset, activeAlarm.vibrate, activeAlarm.volume) },
                    modifier = Modifier.fillMaxWidth().height(60.dp).testTag("snooze_active_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color(0xFF003166)),
                    shape = RoundedCornerShape(30.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Snooze", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(onClick = { com.example.service.AlarmService.dismissAlarm(context, activeAlarm.alarmId, activeAlarm.label, activeAlarm.hour, activeAlarm.minute) },
                    modifier = Modifier.fillMaxWidth().height(60.dp).testTag("dismiss_active_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(2.dp, OutlineColor), shape = RoundedCornerShape(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dismiss", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  SETTINGS DIALOG
// ════════════════════════════════════════════════════════
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutesChange: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val c = LocalAppColors.current
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(),
            shape = RoundedCornerShape(20.dp), color = c.surfaceColor,
            border = BorderStroke(1.dp, c.outlineColor)
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)) {

                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = c.textSecondary)
                    }
                }
                HorizontalDivider(color = c.outlineColor, modifier = Modifier.padding(vertical = 8.dp))

                // ── APPEARANCE — Theme Mode ──
                Text("APPEARANCE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.appBackground)
                        .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text("Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceVariant)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf("Dark", "System", "Light").forEach { mode ->
                            val selected = themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) BrandBlue else Color.Transparent)
                                    .clickable { onThemeModeChange(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) Color(0xFF003166) else c.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── DEFAULT SNOOZE ──
                Text("DEFAULT SNOOZE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.appBackground)
                        .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text("$snoozeMinutes minutes", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20).forEach { opt ->
                            val sel = opt == snoozeMinutes
                            Box(
                                modifier = Modifier
                                    .weight(1f).height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) BrandBlue else c.surfaceVariant)
                                    .border(BorderStroke(1.dp, if (sel) BrandBlue else c.outlineColor), RoundedCornerShape(8.dp))
                                    .clickable { onSnoozeMinutesChange(opt) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${opt}m", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                    color = if (sel) Color(0xFF003166) else c.textSecondary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── WHAT'S NEW ──
                Text("WHAT'S NEW", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.appBackground)
                        .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("v1.1", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue,
                            modifier = Modifier.width(32.dp))
                        Text("Latest", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SuccessGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    val newItems = listOf(
                        "🌙" to "Dark / Light / System theme selector in Settings",
                        "🕐" to "Alarm editor now defaults to current time",
                        "👁"  to "Minute spinner always visible — no more invisible digits",
                        "🔔" to "New High Pitch alarm sound — loud dual-tone alert",
                        "🔊" to "Volume defaults to maximum for new alarms",
                        "🏷"  to "Beema's FINCON branding on home screen",
                        "✉️" to "Tap header to send feedback to bp.beema@outlook.com",
                    )
                    newItems.forEach { (emoji, desc) ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            Text(emoji, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(desc, fontSize = 12.sp, color = c.textSecondary, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = c.outlineColor)
                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Text("v1.0", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textDisabled,
                            modifier = Modifier.width(32.dp))
                    }
                    val v1Items = listOf(
                        "⏰" to "Cyclic alarms — repeat every N days from a start date",
                        "📅" to "Weekly alarms — pick any combination of weekdays",
                        "🎵" to "7 built-in synth sounds (Zen Bowl, Sunrise Chime, Digital Beeps…)",
                        "📂" to "Pick any audio file from your device as alarm sound",
                        "😴" to "Configurable snooze (5 / 10 / 15 / 20 minutes)",
                        "📳" to "Vibration toggle per alarm",
                        "🔔" to "Rings on lock screen — even when phone is sleeping",
                        "📋" to "Alarm history log with clear option",
                    )
                    v1Items.forEach { (emoji, desc) ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            Text(emoji, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(desc, fontSize = 12.sp, color = c.textDisabled, fontWeight = FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── ABOUT ──
                Text("ABOUT", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.appBackground)
                        .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Cyclic Alarms", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = c.textPrimary)
                    Text("Version 1.1", fontSize = 12.sp, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text("Smart shift & roster alarm manager", fontSize = 11.sp, color = c.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

