package com.example.ui

import android.Manifest
import android.app.AlarmManager
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.service.TimerStopwatchState
import com.example.service.TimerStopwatchService
import com.example.service.TimerInstance
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    var showAddEdit   by remember { mutableStateOf(false) }
    var editTarget    by remember { mutableStateOf<Alarm?>(null) }
    var currentTab    by remember { mutableStateOf(0) }
    var selectedIds   by remember { mutableStateOf(setOf<Int>()) }
    var showHistory   by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val themeMode        by viewModel.themeMode.collectAsStateWithLifecycle()
    val timeFormat       by viewModel.timeFormat.collectAsStateWithLifecycle()
    val isThemeSetupDone by viewModel.isThemeSetupDone.collectAsStateWithLifecycle()
    val lightWhiteness   by viewModel.lightWhiteness.collectAsStateWithLifecycle()
    val lastCustomTrackUri by viewModel.lastCustomTrackUri.collectAsStateWithLifecycle()
    val silentBannerDismissed by viewModel.silentBannerDismissed.collectAsStateWithLifecycle()
    var selectedThemeForSetup by remember { mutableStateOf(themeMode) }

    var clock by remember { mutableStateOf("--:--:--") }
    var date  by remember { mutableStateOf("") }
    LaunchedEffect(timeFormat) {
        val tfPattern = if (timeFormat == "24H") "HH:mm:ss" else "hh:mm:ss a"
        val tf = SimpleDateFormat(tfPattern, Locale.US)
        val df = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
        while (true) {
            val now = Calendar.getInstance().time
            clock = tf.format(now).uppercase(Locale.US)
            date  = df.format(now)
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

    // Critical permission state — re-checked on every resume so UI reflects immediately after settings change
    var hasExactAlarm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() ?: true
            else true
        )
    }
    var hasFullScreen by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)?.canUseFullScreenIntent() ?: true
            else true
        )
    }
    var hasOverlay by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    // Re-read all permissions on every Activity resume (no force-quit needed)
    val dashLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(dashLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                else true
                hasExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() ?: true
                else true
                hasFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    (context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)?.canUseFullScreenIntent() ?: true
                else true
                hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
            }
        }
        dashLifecycle.addObserver(observer)
        onDispose { dashLifecycle.removeObserver(observer) }
    }

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

    var availableUpdateVersion by remember { mutableStateOf<String?>(null) }
    var availableUpdateUrl     by remember { mutableStateOf<String?>(null) }
    var availableUpdateNotes   by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog       by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Fetch GitHub Update Check
            try {
                val url = java.net.URL("https://api.github.com/repos/pramodbeema/cyclicalarms/releases/latest")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "CyclicAlarms-App")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val stream = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(stream)
                    val tagName = json.optString("tag_name", "").replace("v", "").trim()
                    val body = json.optString("body", "New version available!")
                    val htmlUrl = json.optString("html_url", "https://github.com/pramodbeema/cyclicalarms/releases/latest")

                    var downloadUrl = htmlUrl
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk") && !name.contains("unsigned")) {
                                downloadUrl = asset.optString("browser_download_url", htmlUrl)
                                break
                            }
                        }
                    }

                    val currentVersion = "1.5"
                    if (tagName.isNotEmpty() && tagName != currentVersion) {
                        withContext(Dispatchers.Main) {
                            availableUpdateVersion = tagName
                            availableUpdateUrl = downloadUrl
                            availableUpdateNotes = body
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    if (activeAlarm != null) { RingingScreen(activeAlarm = activeAlarm!!); return }

    val c = LocalAppColors.current

    // ── PERMISSION GATE — block app until critical permissions are granted ──
    val criticalMissing = !hasExactAlarm || !hasFullScreen || !hasOverlay
    if (criticalMissing) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.appBackground)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Permissions Required",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cyclic Alarms needs these system permissions to ring alarms reliably. Please grant them all to continue.",
                fontSize = 14.sp,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            if (!hasExactAlarm) {
                PermissionGateItem(
                    icon = Icons.Default.Alarm,
                    title = "Exact Alarm Scheduling",
                    description = "Allows alarms to fire at the exact time you set",
                    onOpenSettings = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
            if (!hasFullScreen) {
                PermissionGateItem(
                    icon = Icons.Default.Fullscreen,
                    title = "Full-Screen Alarm Alerts",
                    description = "Shows the ringing screen over your lock screen",
                    onOpenSettings = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
            if (!hasOverlay) {
                PermissionGateItem(
                    icon = Icons.Default.Layers,
                    title = "Display Over Other Apps",
                    description = "Needed for the lock screen alarm popup",
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
            }
        }
        return
    }

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
                Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    BottomTab("Alarms",    Icons.Default.Notifications,  currentTab == 0) { currentTab = 0 }
                    BottomTab("Timer",     Icons.Default.Timer,          currentTab == 1) { currentTab = 1 }
                    BottomTab("Stopwatch", Icons.Default.AccessTime,     currentTab == 2) { currentTab = 2 }
                    BottomTab("Settings",  Icons.Default.Settings,       currentTab == 3) { currentTab = 3 }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(c.appBackground).padding(padding).padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = "Beema's FINCON",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandBlue
                    )
                    Text(clock, fontSize = 24.sp, fontWeight = FontWeight.Black, color = c.textPrimary, fontFamily = FontFamily.Monospace)
                    Text(date,  fontSize = 13.sp, color = c.textSecondary, fontWeight = FontWeight.Medium)
                }
                // History icon button (top-right, only visible on Alarms tab)
                if (currentTab == 0) {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Alarm History",
                            tint = c.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Next alarm pill
            if (currentTab == 0) {
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
            }

            // Update Banner
            if (availableUpdateVersion != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandBlue.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, BrandBlue), RoundedCornerShape(10.dp))
                        .clickable { showUpdateDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Update Available: v${availableUpdateVersion}!",
                            color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Text("Update", color = BrandBlue, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
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

            // Silent mode info banner (one-time, only on Alarms tab)
            if (!silentBannerDismissed && currentTab == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SuccessGreen.copy(alpha = 0.1f))
                        .border(BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "⚡ Alarms ring even in Silent & DND mode. Note: if phone is switched off, alarms cannot ring.",
                            color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp
                        )
                    }
                    IconButton(onClick = { viewModel.dismissSilentBanner() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SuccessGreen, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Selection bar
            AnimatedVisibility(visible = selectionMode && currentTab == 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
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
                when (tab) {
                    0 -> {
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
                    }
                    1 -> { TimerScreen() }
                    2 -> { StopwatchScreen() }
                    3 -> {
                        SettingsPageView(
                            themeMode = themeMode,
                            onThemeModeChange = { viewModel.setThemeMode(it) },
                            timeFormat = timeFormat,
                            onTimeFormatChange = { viewModel.setTimeFormat(it) },
                            lightWhiteness = lightWhiteness,
                            onLightWhitenessChange = { viewModel.setLightWhiteness(it) }
                        )
                    }
                }
            }
        }
    }

    if (showAddEdit) {
        AlarmAddEditDialog(
            alarm = editTarget,
            lastCustomTrackUri = if (editTarget == null) lastCustomTrackUri else "",
            onDismiss = { viewModel.stopPreview(); showAddEdit = false },
            onSave = { a -> viewModel.stopPreview(); if (editTarget == null) viewModel.addAlarm(a) else viewModel.updateAlarm(a); showAddEdit = false },
            onPlayPreview = { s, v -> viewModel.previewSound(s, v) },
            onStopPreview = { viewModel.stopPreview() },
            onTrackPicked = { uri -> viewModel.setLastCustomTrackUri(uri) }
        )
    }

    if (showUpdateDialog && availableUpdateVersion != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = BrandBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Update Available: v${availableUpdateVersion}", fontWeight = FontWeight.Bold, color = c.textPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A new release of Cyclic Alarms is available on GitHub!", fontSize = 13.sp, color = c.textPrimary)
                    if (!availableUpdateNotes.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Text(availableUpdateNotes!!, fontSize = 11.sp, color = c.textSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(availableUpdateUrl ?: "https://github.com/pramodbeema/cyclicalarms/releases/latest"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color(0xFF003166))
                ) { Text("Download Latest APK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Later", color = c.textSecondary) }
            },
            containerColor = c.surfaceColor
        )
    }

    // History bottom sheet
    if (showHistory) {
        val c2 = LocalAppColors.current
        Dialog(onDismissRequest = { showHistory = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.82f),
                shape = RoundedCornerShape(20.dp), color = c2.surfaceColor, border = BorderStroke(1.dp, c2.outlineColor)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Alarm History", color = c2.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (logs.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearLogs() }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = DeleteRed, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear", color = DeleteRed, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onClick = { showHistory = false }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = c2.textSecondary) }
                        }
                    }
                    HorizontalDivider(color = c2.outlineColor, modifier = Modifier.padding(vertical = 8.dp))
                    if (logs.isEmpty()) {
                        EmptyStateView(Icons.AutoMirrored.Filled.List, "No History", "Dismissed and snoozed alarms will appear here.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                            items(logs, key = { it.id }) { LogItem(it) }
                        }
                    }
                }
            }
        }
    }

    // First Launch Theme Selection Dialog
    if (!isThemeSetupDone) {
        Dialog(
            onDismissRequest = { /* Force explicit user choice */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = c.surfaceColor,
                border = BorderStroke(1.dp, c.outlineColor),
                modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(BrandBlue.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Welcome to Cyclic Alarms", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Select your preferred theme. You can change this anytime in Settings.", fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp)
                    Spacer(Modifier.height(20.dp))

                    // Dark options row
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceVariant).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Pure Dark" to "🌑", "Dark" to "🌙").forEach { (mode, emoji) ->
                            val isSel = selectedThemeForSetup == mode
                            Box(
                                modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF1A1A2E) else Color.Transparent)
                                    .border(BorderStroke(if (isSel) 1.5.dp else 0.dp, if (isSel) BrandBlue else Color.Transparent), RoundedCornerShape(8.dp))
                                    .clickable { selectedThemeForSetup = mode; viewModel.setThemeMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(emoji, fontSize = 13.sp)
                                    Text(mode, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (isSel) BrandBlue else c.textSecondary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Light options row
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceVariant).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("System" to "⚙️", "Light" to "☀️").forEach { (mode, emoji) ->
                            val isSel = selectedThemeForSetup == mode
                            Box(
                                modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) BrandBlue else Color.Transparent)
                                    .clickable { selectedThemeForSetup = mode; viewModel.setThemeMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(emoji, fontSize = 13.sp)
                                    Text(mode, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (isSel) Color(0xFF003166) else c.textSecondary)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.completeThemeSetup(selectedThemeForSetup) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color(0xFF003166)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save & Continue", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  BOTTOM TAB
// ════════════════════════════════════════════════════════
@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Box(modifier = Modifier.width(48.dp).height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (selected) BrandBlue.copy(alpha = 0.2f) else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (selected) BrandBlue else c.textDisabled, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(2.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(headerText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accent.copy(alpha = alpha), letterSpacing = 0.8.sp)
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.surfaceVariant).padding(horizontal = 6.dp, vertical = 3.dp)) {
                        Text("💤 ${alarm.snoozeMinutes}m", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.textSecondary.copy(alpha = alpha))
                    }
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
    onPlayPreview: (String, Float) -> Unit, onStopPreview: () -> Unit,
    lastCustomTrackUri: String = "",
    onTrackPicked: (String) -> Unit = {}
) {
    // Default to current time for new alarms
    val nowCal  = remember { java.util.Calendar.getInstance() }
    val nowHour = remember { nowCal.get(java.util.Calendar.HOUR_OF_DAY) }
    val nowMin  = remember { nowCal.get(java.util.Calendar.MINUTE) }

    var label     by remember { mutableStateOf(alarm?.label ?: "") }
    var hour      by remember { mutableIntStateOf(alarm?.hour ?: nowHour) }
    var minute    by remember { mutableIntStateOf(alarm?.minute ?: nowMin) }
    var alarmType by remember { mutableStateOf(alarm?.alarmType ?: "CYCLIC") }
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
    // For new alarms, pre-fill from lastCustomTrackUri (persistent custom track feature)
    var customTrackUri by remember {
        mutableStateOf(
            alarm?.customTrackUri
                ?: if (lastCustomTrackUri.isNotEmpty()) lastCustomTrackUri else ""
        )
    }
    // Auto-select Custom Track preset if pre-filled
    LaunchedEffect(Unit) {
        if (alarm == null && customTrackUri.isNotEmpty()) soundPreset = "Custom Track"
    }
    var vibrate        by remember { mutableStateOf(alarm?.vibrate ?: true) }
    var volume         by remember { mutableFloatStateOf(alarm?.volume ?: 1.0f) }
    var isPreviewing   by remember { mutableStateOf<String?>(null) }

    // Per-Alarm Snooze Duration
    var snoozeMinutes  by remember { mutableIntStateOf(alarm?.snoozeMinutes ?: 5) }
    var customSnooze   by remember { mutableStateOf((alarm?.snoozeMinutes ?: 5).toString()) }

    val context = LocalContext.current
    val presets = listOf("High Pitch","Zen Bowl","Sunrise Chime","Digital Beeps","Morning Forest","Synth Wave Beat","Cyber Alert","Lofi Chord")

    val trackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val uriStr = uri.toString()
            customTrackUri = uriStr
            soundPreset = "Custom Track"
            onTrackPicked(uriStr)  // persist for future alarms
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

                    // SNOOZE DURATION (Per Alarm)
                    item {
                        SectionCard("Snooze Duration") {
                            Text("Select or enter snooze duration in minutes", fontSize = 11.sp, color = c.textSecondary)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5, 10, 15, 20, 30).forEach { opt ->
                                    val sel = opt == snoozeMinutes
                                    Box(
                                        modifier = Modifier
                                            .weight(1f).height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) BrandBlue else c.surfaceVariant)
                                            .border(BorderStroke(1.dp, if (sel) BrandBlue else c.outlineColor), RoundedCornerShape(8.dp))
                                            .clickable { snoozeMinutes = opt; customSnooze = opt.toString() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${opt}m", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                            color = if (sel) Color(0xFF003166) else c.textSecondary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Custom:", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = customSnooze,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } && input.length <= 3) {
                                            customSnooze = input
                                            input.toIntOrNull()?.let { snoozeMinutes = it.coerceIn(1, 180) }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(78.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = c.outlineColor, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = BrandBlue),
                                    textStyle = TextStyle(textAlign = TextAlign.Center, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("minutes", color = c.textSecondary, fontSize = 13.sp)
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
                        val validSnooze = snoozeMinutes.coerceIn(1, 180)
                        onSave(Alarm(id = alarm?.id ?: 0, label = label, hour = hour, minute = minute, isEnabled = true,
                            alarmType = alarmType, weeklyDays = daysStr, cyclicIntervalDays = interval,
                            cyclicStartDate = cyclicStart, soundPreset = soundPreset, customTrackUri = customTrackUri,
                            vibrate = vibrate, volume = volume, snoozeMinutes = validSnooze, lastTriggeredTime = alarm?.lastTriggeredTime ?: 0L))
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
//  SETTINGS PAGE VIEW
// ════════════════════════════════════════════════════════
@Composable
fun SettingsPageView(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    timeFormat: String,
    onTimeFormatChange: (String) -> Unit,
    lightWhiteness: Float = 0f,
    onLightWhitenessChange: (Float) -> Unit = {}
) {
    val c = LocalAppColors.current
    val context = LocalContext.current

    // Helper functions to check permissions live
    fun checkNotif(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun checkExactAlarm(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: true
        } else true
    }

    fun checkFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() ?: true
        } else true
    }

    fun checkOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    var notifGranted      by remember { mutableStateOf(checkNotif()) }
    var exactAlarmGranted by remember { mutableStateOf(checkExactAlarm()) }
    var fullScreenGranted by remember { mutableStateOf(checkFullScreenIntent()) }
    var overlayGranted    by remember { mutableStateOf(checkOverlay()) }
    var currentSubpage    by remember { mutableStateOf<String?>(null) }

    // Re-check all permissions every time the user returns from system settings (ON_RESUME)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifGranted      = checkNotif()
                exactAlarmGranted = checkExactAlarm()
                fullScreenGranted = checkFullScreenIntent()
                overlayGranted    = checkOverlay()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (currentSubpage == "About") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { currentSubpage = null }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BrandBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back to Settings", color = BrandBlue, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            AboutPageView()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("SETTINGS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.2.sp)

            // ── ABOUT SUBPAGE NAVIGATION CARD ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surfaceColor)
                    .border(BorderStroke(1.dp, BrandBlue.copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
                    .clickable { currentSubpage = "About" }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(40.dp).background(BrandBlue.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("About Cyclic Alarms", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = c.textPrimary)
                        Text("Version 1.5 • Release notes, support & creator info", fontSize = 12.sp, color = c.textSecondary)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
            }

            // ── APPEARANCE — Theme Mode ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceColor)
                .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Theme", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            // Dark row: Pure Dark + Dark
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Pure Dark" to "🌑", "Dark" to "🌙").forEach { (mode, emoji) ->
                    val selected = themeMode == mode
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF1A1A2E) else Color.Transparent)
                            .border(BorderStroke(if (selected) 1.5.dp else 0.dp, if (selected) BrandBlue else Color.Transparent), RoundedCornerShape(8.dp))
                            .clickable { onThemeModeChange(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(emoji, fontSize = 13.sp)
                            Text(mode, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) BrandBlue else c.textSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // Light row: System + Light
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("System" to "⚙️", "Light" to "☀️").forEach { (mode, emoji) ->
                    val selected = themeMode == mode
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) BrandBlue else Color.Transparent)
                            .clickable { onThemeModeChange(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(emoji, fontSize = 13.sp)
                            Text(mode, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color(0xFF003166) else c.textSecondary)
                        }
                    }
                }
            }
            // Whiteness slider — only shown when Light mode is active
            AnimatedVisibility(visible = themeMode == "Light") {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Background Brightness", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text("${(lightWhiteness * 100).toInt()}%", fontSize = 12.sp, color = WarningAmber, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tinted", fontSize = 10.sp, color = c.textDisabled)
                        Slider(
                            value = lightWhiteness,
                            onValueChange = { onLightWhitenessChange(it) },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = WarningAmber, activeTrackColor = WarningAmber, inactiveTrackColor = c.outlineColor)
                        )
                        Text("Pure White", fontSize = 10.sp, color = c.textDisabled)
                    }
                }
            }
        }


        // ── CLOCK DISPLAY FORMAT (12H / 24H) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceColor)
                .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clock Display Format", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Text("Choose how time is displayed on the home screen clock", fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("12H" to "12-Hour (AM/PM)", "24H" to "24-Hour").forEach { (fmt, lbl) ->
                    val selected = timeFormat == fmt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) BrandBlue else Color.Transparent)
                            .clickable { onTimeFormatChange(fmt) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lbl,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) Color(0xFF003166) else c.textSecondary
                        )
                    }
                }
            }
        }

        // ── PERMISSIONS & SYSTEM ACCESS ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceColor)
                .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Permissions & App Access", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.textPrimary)
            }
            Text("Manage system access required for reliable alarm ringing and lock-screen popups", fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(4.dp))

            PermissionRowItem(
                title = "Notifications",
                subtitle = "Required to trigger loud alarm sounds and banners",
                isGranted = notifGranted,
                onClickManage = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    }
                    context.startActivity(intent)
                }
            )

            PermissionRowItem(
                title = "Exact Alarm Scheduling",
                subtitle = "Allows alarm to fire at the exact second requested",
                isGranted = exactAlarmGranted,
                onClickManage = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    }
                    context.startActivity(intent)
                }
            )

            PermissionRowItem(
                title = "Full-Screen Alarm Alerts",
                subtitle = "Displays full-screen ringing UI over keyguard when device is locked",
                isGranted = fullScreenGranted,
                onClickManage = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    }
                    context.startActivity(intent)
                }
            )

            PermissionRowItem(
                title = "Display Over Other Apps",
                subtitle = "Allows popup window display on top of other running applications",
                isGranted = overlayGranted,
                onClickManage = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            )
        }
    }
} // end else block
} // end SettingsPageView

@Composable
private fun PermissionRowItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClickManage: () -> Unit
) {
    val c = LocalAppColors.current
    val bg = if (isGranted) SuccessGreen.copy(alpha = 0.1f) else DeleteRed.copy(alpha = 0.12f)
    val borderCol = if (isGranted) SuccessGreen.copy(alpha = 0.35f) else DeleteRed.copy(alpha = 0.4f)
    val statusCol = if (isGranted) SuccessGreen else DeleteRed
    val statusText = if (isGranted) "✓ Granted" else "⚠ Disabled"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusCol.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusCol
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = c.textSecondary,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onClickManage,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) BrandBlue else DeleteRed,
                    contentColor = if (isGranted) Color(0xFF003166) else Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    text = if (isGranted) "Manage" else "Fix Now",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  PERMISSION GATE ITEM  (used in blocking screen)
// ════════════════════════════════════════════════════════
@Composable
private fun PermissionGateItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onOpenSettings: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeleteRed.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, DeleteRed.copy(alpha = 0.45f)), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(DeleteRed.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DeleteRed, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = c.textPrimary)
            Text(description, fontSize = 11.sp, color = c.textSecondary, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White),
            modifier = Modifier.height(38.dp)
        ) {
            Text("Grant", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
}

// ════════════════════════════════════════════════════════
//  ABOUT PAGE VIEW
// ════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════
//  MULTI-TIMER SCREEN (Supports up to 5 concurrent timers)
// ════════════════════════════════════════════════════════
@Composable
fun TimerScreen() {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val timers by com.example.service.TimerStopwatchState.timers.collectAsStateWithLifecycle()

    // Ensure at least one timer exists initially
    LaunchedEffect(Unit) {
        if (timers.isEmpty()) {
            val id = com.example.service.TimerStopwatchState.addTimer()
            com.example.service.TimerStopwatchState.setTimerDuration(id, 5 * 60 * 1000L)
        }
    }

    // Auto-start foreground service whenever any timer or stopwatch is running
    val anyRunning = timers.any { it.isRunning }
    LaunchedEffect(anyRunning) {
        if (anyRunning) {
            com.example.service.TimerStopwatchService.start(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TIMERS (${timers.size}/5)",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.textSecondary,
                letterSpacing = 1.2.sp
            )
            if (timers.size < 5) {
                Button(
                    onClick = {
                        val newId = com.example.service.TimerStopwatchState.addTimer()
                        com.example.service.TimerStopwatchState.setTimerDuration(newId, 5 * 60 * 1000L)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Timer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        timers.forEach { timer ->
            MultiTimerCard(
                timer = timer,
                canRemove = timers.size > 1,
                onStart = {
                    com.example.service.TimerStopwatchState.startTimer(timer.id)
                    com.example.service.TimerStopwatchService.start(context)
                },
                onPause = {
                    com.example.service.TimerStopwatchState.pauseTimer(timer.id)
                },
                onReset = {
                    com.example.service.TimerStopwatchState.resetTimer(timer.id)
                },
                onRemove = {
                    com.example.service.TimerStopwatchState.removeTimer(timer.id)
                },
                onSetDuration = { ms ->
                    com.example.service.TimerStopwatchState.setTimerDuration(timer.id, ms)
                }
            )
        }
    }
}

@Composable
private fun MultiTimerCard(
    timer: com.example.service.TimerInstance,
    canRemove: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
    onSetDuration: (Long) -> Unit
) {
    val c = LocalAppColors.current
    var inputHours by remember(timer.id) { mutableStateOf("%02d".format(timer.totalMs / 3_600_000L)) }
    var inputMinutes by remember(timer.id) { mutableStateOf("%02d".format((timer.totalMs / 60_000L) % 60)) }
    var inputSeconds by remember(timer.id) { mutableStateOf("%02d".format((timer.totalMs / 1_000L) % 60)) }

    val progress = if (timer.totalMs == 0L) 0f else (timer.remainingMs.toFloat() / timer.totalMs.toFloat())
    val remH = timer.remainingMs / 3_600_000L
    val remM = (timer.remainingMs / 60_000L) % 60
    val remS = (timer.remainingMs / 1_000L) % 60

    val infiniteTransition = rememberInfiniteTransition(label = "flash_${timer.id}")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing)), label = "flash"
    )
    val displayAlpha = if (timer.isFinished) flashAlpha else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceColor)
            .border(
                BorderStroke(
                    1.dp,
                    if (timer.isFinished) DeleteRed else if (timer.isRunning) BrandBlue else c.outlineColor
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timer.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (timer.isRunning) CyclicAccent else c.textPrimary
            )
            if (canRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Timer", tint = DeleteRed, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (!timer.isRunning && timer.remainingMs == timer.totalMs && !timer.isFinished) {
            // Edit duration mode
            val focusMgr = LocalFocusManager.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TimerInputField(
                    value = inputHours, label = "HH", tag = "h_${timer.id}",
                    onValueChange = {
                        if (it.length <= 2 && it.all(Char::isDigit)) {
                            inputHours = it
                            val ms = ((inputHours.toLongOrNull() ?: 0L) * 3600 + (inputMinutes.toLongOrNull() ?: 0L) * 60 + (inputSeconds.toLongOrNull() ?: 0L)) * 1000L
                            onSetDuration(ms)
                        }
                    }
                )
                Text(":", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandBlue, modifier = Modifier.padding(horizontal = 2.dp))
                TimerInputField(
                    value = inputMinutes, label = "MM", tag = "m_${timer.id}",
                    onValueChange = {
                        if (it.length <= 2 && it.all(Char::isDigit)) {
                            inputMinutes = it
                            val ms = ((inputHours.toLongOrNull() ?: 0L) * 3600 + (inputMinutes.toLongOrNull() ?: 0L) * 60 + (inputSeconds.toLongOrNull() ?: 0L)) * 1000L
                            onSetDuration(ms)
                        }
                    }
                )
                Text(":", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandBlue, modifier = Modifier.padding(horizontal = 2.dp))
                TimerInputField(
                    value = inputSeconds, label = "SS", tag = "s_${timer.id}",
                    onValueChange = {
                        if (it.length <= 2 && it.all(Char::isDigit)) {
                            inputSeconds = it
                            val ms = ((inputHours.toLongOrNull() ?: 0L) * 3600 + (inputMinutes.toLongOrNull() ?: 0L) * 60 + (inputSeconds.toLongOrNull() ?: 0L)) * 1000L
                            onSetDuration(ms)
                        }
                    },
                    onDone = { focusMgr.clearFocus() }
                )
            }

            // Quick presets
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1m" to 60_000L, "5m" to 300_000L, "10m" to 600_000L, "15m" to 900_000L, "30m" to 1_800_000L).forEach { (lbl, ms) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandBlue.copy(alpha = 0.12f))
                            .border(BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                            .clickable {
                                inputHours = "%02d".format(ms / 3_600_000L)
                                inputMinutes = "%02d".format((ms / 60_000L) % 60)
                                inputSeconds = "00"
                                onSetDuration(ms)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                    }
                }
            }
        } else {
            // Running or Paused / Finished
            Text(
                text = String.format("%02d:%02d:%02d", remH, remM, remS),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = (if (timer.isFinished) DeleteRed else c.textPrimary).copy(alpha = displayAlpha)
            )
            if (timer.isFinished) {
                Text(
                    "Time's Up!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DeleteRed.copy(alpha = displayAlpha)
                )
            }
        }

        // Progress line
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (timer.isFinished) DeleteRed else BrandBlue,
            trackColor = c.surfaceVariant,
        )

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, c.outlineColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary)
            ) {
                Text("Reset", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (timer.isRunning) onPause() else onStart()
                },
                modifier = Modifier.weight(1.5f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (timer.isFinished) DeleteRed else if (timer.isRunning) WarningAmber else BrandBlue,
                    contentColor = if (timer.isFinished) Color.White else Color(0xFF003166)
                )
            ) {
                Icon(
                    imageVector = if (timer.isFinished) Icons.Default.Close else if (timer.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (timer.isFinished) "Clear" else if (timer.isRunning) "Pause" else "Start",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun TimerInputField(
    value: String, label: String, tag: String,
    onValueChange: (String) -> Unit, onDone: (() -> Unit)? = null
) {
    val c = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center, color = c.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.width(62.dp).testTag(tag),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandBlue, unfocusedBorderColor = c.outlineColor,
                focusedContainerColor = c.surfaceVariant, unfocusedContainerColor = c.surfaceVariant,
                cursorColor = BrandBlue, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary
            ),
            singleLine = true, shape = RoundedCornerShape(10.dp)
        )
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.textSecondary)
    }
}

// ════════════════════════════════════════════════════════
//  STOPWATCH SCREEN (Foreground Service Synced)
// ════════════════════════════════════════════════════════
@Composable
fun StopwatchScreen() {
    val c = LocalAppColors.current
    val context = LocalContext.current

    val elapsedMs by com.example.service.TimerStopwatchState.swElapsedMs.collectAsStateWithLifecycle()
    val isRunning by com.example.service.TimerStopwatchState.swRunning.collectAsStateWithLifecycle()
    val laps by com.example.service.TimerStopwatchState.swLaps.collectAsStateWithLifecycle()
    val lastLapMs by com.example.service.TimerStopwatchState.swLastLapMs.collectAsStateWithLifecycle()

    fun playClickBeep() {
        try {
            val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60)
            toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 80)
        } catch (_: Exception) {}
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            com.example.service.TimerStopwatchService.start(context)
        }
    }

    fun formatElapsed(ms: Long): String {
        val h  = ms / 3_600_000L
        val m  = (ms / 60_000L) % 60
        val s  = (ms / 1_000L) % 60
        val cs = (ms % 1_000L) / 10
        return if (h > 0) String.format("%02d:%02d:%02d.%02d", h, m, s, cs)
        else String.format("%02d:%02d.%02d", m, s, cs)
    }

    val lapMs = elapsedMs - lastLapMs

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text("STOPWATCH", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(16.dp))

        // Main display
        Box(
            modifier = Modifier.size(200.dp)
                .background(BrandBlue.copy(alpha = 0.07f), CircleShape)
                .border(BorderStroke(2.dp, BrandBlue.copy(alpha = 0.18f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatElapsed(elapsedMs),
                    fontSize = if (elapsedMs >= 3_600_000L) 30.sp else 36.sp,
                    fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                    color = if (isRunning) CyclicAccent else c.textPrimary
                )
                if (laps.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Lap ${laps.size + 1}: ${formatElapsed(lapMs)}",
                        fontSize = 12.sp, color = c.textSecondary, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Controls
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Lap / Reset button
            OutlinedButton(
                onClick = {
                    playClickBeep()
                    if (isRunning) {
                        com.example.service.TimerStopwatchState.lapStopwatch()
                    } else {
                        com.example.service.TimerStopwatchState.resetStopwatch()
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, c.outlineColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Flag else Icons.Default.Refresh,
                    contentDescription = null, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isRunning) "Lap" else "Reset", fontWeight = FontWeight.ExtraBold)
            }

            // Start / Stop button
            Button(
                onClick = {
                    playClickBeep()
                    if (isRunning) {
                        com.example.service.TimerStopwatchState.pauseStopwatch()
                    } else {
                        com.example.service.TimerStopwatchState.startStopwatch()
                        com.example.service.TimerStopwatchService.start(context)
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) DeleteRed else CyclicAccent,
                    contentColor = if (isRunning) Color.White else Color(0xFF003166)
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Stop" else "Start", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        // Lap list
        if (laps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = c.outlineColor, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LAP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 0.8.sp)
                Text("LAP TIME", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 0.8.sp)
                Text("OVERALL", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val overallMs = laps.runningFold(0L) { acc, v -> acc + v }.drop(1)
                val fastestLap = laps.minOrNull() ?: 0L
                val slowestLap = laps.maxOrNull() ?: 0L
                laps.indices.toList().reversed().forEach { i ->
                    val curLapTime = laps[i]
                    val overall = overallMs[i]
                    val isF = laps.size > 1 && curLapTime == fastestLap
                    val isS = laps.size > 1 && curLapTime == slowestLap
                    val accent = when { isF -> CyclicAccent; isS -> DeleteRed; else -> c.textSecondary }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.07f))
                            .border(BorderStroke(1.dp, accent.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = accent, modifier = Modifier.width(32.dp))
                        Text(formatElapsed(curLapTime), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text(formatElapsed(overall), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutPageView() {
    val c = LocalAppColors.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ABOUT & RELEASE NOTES", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.2.sp)

        // Hero Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceColor)
                .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BrandBlue.copy(alpha = 0.18f))
                    .border(BorderStroke(1.dp, BrandBlue), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Cyclic Alarms", fontWeight = FontWeight.Black, fontSize = 20.sp, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Beema's FINCON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                Spacer(Modifier.width(8.dp))
                Text("Version 1.5", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SuccessGreen.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Smart shift, roster & repeating cycle alarm app", fontSize = 12.sp, color = c.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var updateDialogInfo by remember { mutableStateOf<com.example.util.AppUpdateManager.UpdateInfo?>(null) }
            var updateError by remember { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()

            Button(
                onClick = {
                    if (!isCheckingUpdate) {
                        isCheckingUpdate = true
                        updateError = null
                        coroutineScope.launch {
                            val result = com.example.util.AppUpdateManager.checkForUpdates()
                            isCheckingUpdate = false
                            result.onSuccess { info ->
                                if (info.isUpdateAvailable) {
                                    updateDialogInfo = info
                                } else {
                                    android.widget.Toast.makeText(context, "You are on the latest version (v1.5)!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }.onFailure { err ->
                                updateError = err.message
                                android.widget.Toast.makeText(context, "Check failed: ${err.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(color = Color(0xFF003166), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Checking...", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF003166))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF003166))
                    Spacer(Modifier.width(6.dp))
                    Text("Check for Updates", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF003166))
                }
            }

            if (updateDialogInfo != null) {
                val info = updateDialogInfo!!
                AlertDialog(
                    onDismissRequest = { updateDialogInfo = null },
                    containerColor = c.surfaceColor,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowCircleDown, contentDescription = null, tint = BrandBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("Update Available (v${info.latestVersion})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.textPrimary)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("A new version of Cyclic Alarms is available to download and install.", fontSize = 13.sp, color = c.textSecondary)
                            if (info.releaseNotes.isNotBlank()) {
                                Text("Release notes:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.textPrimary)
                                Text(info.releaseNotes.take(300), fontSize = 11.sp, color = c.textSecondary)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val url = info.downloadUrl
                                val ver = info.latestVersion
                                updateDialogInfo = null
                                com.example.util.AppUpdateManager.downloadAndInstall(context, url, ver)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Text("Download & Install", fontWeight = FontWeight.Bold, color = Color(0xFF003166))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateDialogInfo = null }) {
                            Text("Later", color = c.textSecondary)
                        }
                    }
                )
            }
        }

        // ── SUPPORT & COMMUNITY CARD ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceColor)
                .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Text("SUPPORT & COMMUNITY", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandBlue.copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:bp.beema@outlook.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Cyclic Alarms - Feedbacks, Suggestions or Bugs")
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(40.dp).background(BrandBlue.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Feedbacks, Suggestions or Bugs Reporting", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = c.textPrimary)
                        Text("bp.beema@outlook.com", fontSize = 11.sp, color = BrandBlue, fontWeight = FontWeight.Medium)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
            }
        }

        // ── EXPANDABLE RELEASE NOTES ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceColor)
                .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = BrandPurple, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Release History & Notes", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = c.textPrimary)
            }
            Text("Tap on any release version to expand or collapse notes", fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(4.dp))

            // v1.5 — Expanded by default (current release)
            ExpandableReleaseNoteCard(
                version = "v1.5 (Current Release)",
                badgeText = "Latest",
                isInitiallyExpanded = true,
                items = listOf(
                    "🎵" to "Persistent Custom Track — last-used music file auto-applied to every new alarm",
                    "⏱" to "Multiple Timers — run up to 5 independent countdown timers simultaneously",
                    "🌑" to "Pure Dark Mode — true AMOLED black theme for OLED screen battery savings",
                    "☀️" to "Light Mode Brightness Slider — adjust background brightness from tinted to pure white",
                    "🔄" to "Background Timer & Stopwatch — continue running when app is backgrounded via Foreground Service",
                    "🔔" to "Rings in Silent & DND Mode — alarms bypass ringer volume, no more missed alarms",
                    "⬆️" to "In-App Auto-Update — tap 'Check for Updates' to download & install new releases directly"
                )
            )

            // v1.4 — Collapsed
            ExpandableReleaseNoteCard(
                version = "v1.4 Release Notes",
                badgeText = "v1.4",
                isInitiallyExpanded = false,
                items = listOf(
                    "🔥" to "Firebase Cloud Messaging (FCM) — instant broadcast push notifications and update announcements direct to all users",
                    "📦" to "Official Package Rebrand — package updated to com.beemasfincon.cyclicalarms",
                    "⚡" to "Direct Broadcast Topics — automatic subscription to 'announcements' & 'all' topics for zero-token mass messaging",
                    "🚀" to "GitHub Auto-Update Checker — fast releases checker and 1-tap download prompt"
                )
            )

            // v1.3 — Collapsed
            ExpandableReleaseNoteCard(
                version = "v1.3 Release Notes",
                badgeText = "v1.3",
                isInitiallyExpanded = false,
                items = listOf(
                    "🚀" to "GitHub Auto-Update Checker — automatic update notification & 1-tap download prompt for new signed releases",
                    "⏱" to "Timer — built-in countdown timer with progress ring, quick presets (1m–30m), sound & vibration alert",
                    "⏱" to "Stopwatch — elapsed time tracker with lap recording, fastest/slowest lap highlights, touch audio tones",
                    "🔒" to "Strict Lock Screen Privacy — app only shows over keyguard while actively ringing; dismiss/snooze immediately locks & returns to prior screen without exposing dashboard",
                    "📱" to "4-Tab Navigation — Alarms, Timer, Stopwatch, Settings (with embedded About sub-page)",
                    "🌀" to "Default Cyclic — new alarms open on Cyclic Days tab by default",
                    "🖼" to "Icon Fit Fix — app icon no longer appears cropped on launcher"
                )
            )

            // v1.2 — Collapsed
            ExpandableReleaseNoteCard(
                version = "v1.2 Release Notes",
                badgeText = "v1.2",
                isInitiallyExpanded = false,
                items = listOf(
                    "🌀" to "Pencil-Sketched Spiral Alarm Icon — clean, symmetrical alarm clock launcher icon",
                    "⏱" to "Per-Alarm Custom Snooze — set desired snooze duration (5, 10, 15, 20m or custom minutes)",
                    "🔒" to "Native Lock Screen Ringing — rings smoothly over keyguard without PIN unlock prompt",
                    "📱" to "4-Tab Navigation — clean layout for Alarms, History, Settings & About",
                    "🕒" to "12H / 24H Clock Toggle — select preferred format with uppercase 'PM'/'AM'",
                    "📅" to "Year Display in Date — home clock displays full year (e.g. Sat, Sep 5, 2026)",
                    "🎨" to "First-Launch Theme Prompt & Status Bar Fix — seamless light/dark theme icon visibility",
                    "🔑" to "In-App Permissions Manager — view and manage notification, exact alarm & overlay access",
                    "✉️" to "Support & Community Card — moved to About page for easy feedback & bug reporting"
                )
            )

            // v1.1 — Collapsed by default
            ExpandableReleaseNoteCard(
                version = "v1.1 Release Notes",
                badgeText = "v1.1",
                isInitiallyExpanded = false,
                items = listOf(
                    "🌙" to "Dark / Light / System theme selector in Settings",
                    "🕐" to "Alarm editor now defaults to current time",
                    "👁"  to "Minute spinner always visible — no more invisible digits",
                    "🔔" to "New High Pitch alarm sound — loud dual-tone alert",
                    "🔊" to "Volume defaults to maximum for new alarms",
                    "🏷"  to "Beema's FINCON branding on home screen"
                )
            )

            // v1.0 — Collapsed by default
            ExpandableReleaseNoteCard(
                version = "v1.0 Initial Release",
                badgeText = "v1.0",
                isInitiallyExpanded = false,
                items = listOf(
                    "⏰" to "Cyclic alarms — repeat every N days from a start date",
                    "📅" to "Weekly alarms — pick any combination of weekdays",
                    "🎵" to "7 built-in synth sounds + custom audio file support",
                    "😴" to "Configurable snooze and vibration toggle",
                    "📋" to "Alarm history log with clear option"
                )
            )
        }

        // Creator Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceColor)
                .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("CREATOR & ACKNOWLEDGMENTS", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
            Text("Built for shift workers, roster personnel, and anyone needing recurring interval alarms beyond standard weekly schedules.", fontSize = 12.sp, color = c.textSecondary, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text("100% AI-assisted development. Zero tracking. Zero ads. 100% free.", fontSize = 11.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExpandableReleaseNoteCard(
    version: String,
    badgeText: String,
    isInitiallyExpanded: Boolean,
    items: List<Pair<String, String>>
) {
    val c = LocalAppColors.current
    var expanded by remember { mutableStateOf(isInitiallyExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceVariant)
            .border(BorderStroke(1.dp, if (isInitiallyExpanded) BrandBlue.copy(alpha = 0.4f) else c.outlineColor), RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(version, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = c.textPrimary)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isInitiallyExpanded) BrandBlue.copy(alpha = 0.18f) else c.outlineColor.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isInitiallyExpanded) BrandBlue else c.textSecondary)
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = c.outlineColor)
                Spacer(Modifier.height(2.dp))
                items.forEach { (emoji, desc) ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Text(emoji, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(desc, fontSize = 12.sp, color = c.textSecondary, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
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
                Button(onClick = { com.example.service.AlarmService.snoozeAlarm(context, activeAlarm.alarmId, activeAlarm.label, activeAlarm.hour, activeAlarm.minute, activeAlarm.soundPreset, activeAlarm.vibrate, activeAlarm.volume, activeAlarm.snoozeMinutes) },
                    modifier = Modifier.fillMaxWidth().height(60.dp).testTag("snooze_active_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color(0xFF003166)),
                    shape = RoundedCornerShape(30.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Snooze (${activeAlarm.snoozeMinutes}m)", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
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


