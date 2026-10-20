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
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.util.LocaleManager
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
    val c = LocalAppColors.current
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val logs   by viewModel.logs.collectAsStateWithLifecycle()
    val activeAlarm by com.example.service.RingingState.activeAlarm.collectAsState()

    var showAddEdit   by remember { mutableStateOf(false) }
    var editTarget    by remember { mutableStateOf<Alarm?>(null) }
    var currentTab    by remember { mutableStateOf(0) }

    // Handle navigation requests from notification taps
    val requestedTab by viewModel.requestedTab.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            currentTab = it
            viewModel.consumeRequestedTab()
        }
    }
    var selectedIds   by remember { mutableStateOf(setOf<Int>()) }
    var showHistory   by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val themeMode        by viewModel.themeMode.collectAsStateWithLifecycle()
    val timeFormat       by viewModel.timeFormat.collectAsStateWithLifecycle()
    val isThemeSetupDone by viewModel.isThemeSetupDone.collectAsStateWithLifecycle()
    val lightWhiteness   by viewModel.lightWhiteness.collectAsStateWithLifecycle()
    val lastCustomTrackUri by viewModel.lastCustomTrackUri.collectAsStateWithLifecycle()
    val silentBannerDismissed by viewModel.silentBannerDismissed.collectAsStateWithLifecycle()
    val appLanguage      by viewModel.appLanguage.collectAsStateWithLifecycle()
    var selectedThemeForSetup by remember { mutableStateOf(themeMode) }
    var selectedLanguageForSetup by remember { mutableStateOf(appLanguage) }

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

    if (activeAlarm != null) { RingingScreen(activeAlarm = activeAlarm!!); return }

    // ── FIRST LAUNCH SETUP DIALOG (Language & Theme Selection) ──
    // Displayed immediately on fresh install before any permission blocks
    if (!isThemeSetupDone) {
        Dialog(
            onDismissRequest = { /* Force explicit user choice */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = c.surfaceColor,
                border = BorderStroke(1.dp, c.outlineColor),
                modifier = Modifier.fillMaxWidth(0.95f).padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(BrandBlue.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.welcome_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.first_run_subtitle),
                        fontSize = 12.sp,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    // Language Selection Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.select_language),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceVariant)
                            .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(10.dp))
                            .padding(4.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(LocaleManager.SUPPORTED_LANGUAGES) { lang ->
                                val isLangSel = selectedLanguageForSetup == lang.code
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isLangSel) BrandBlue.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            selectedLanguageForSetup = lang.code
                                            LocaleManager.setLanguage(context, lang.code, restartActivity = false)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(lang.flagEmoji, fontSize = 16.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                lang.nativeName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isLangSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isLangSel) BrandBlue else c.textPrimary
                                            )
                                            if (lang.code.isNotEmpty()) {
                                                Text(
                                                    lang.englishName,
                                                    fontSize = 10.sp,
                                                    color = c.textSecondary
                                                )
                                            }
                                        }
                                    }
                                    if (isLangSel) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Theme Selection Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.select_theme),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // Dark options row
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceVariant).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            ("Pure Dark" to "🌑") to stringResource(R.string.theme_pure_dark),
                            ("Dark" to "🌙") to stringResource(R.string.theme_dark)
                        ).forEach { (pair, label) ->
                            val (mode, emoji) = pair
                            val isSel = selectedThemeForSetup == mode
                            Box(
                                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF1A1A2E) else Color.Transparent)
                                    .border(BorderStroke(if (isSel) 1.5.dp else 0.dp, if (isSel) BrandBlue else Color.Transparent), RoundedCornerShape(8.dp))
                                    .clickable { selectedThemeForSetup = mode; viewModel.setThemeMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(emoji, fontSize = 12.sp)
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (isSel) BrandBlue else c.textSecondary)
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
                        listOf(
                            ("System" to "⚙️") to stringResource(R.string.theme_system),
                            ("Light" to "☀️") to stringResource(R.string.theme_light)
                        ).forEach { (pair, label) ->
                            val (mode, emoji) = pair
                            val isSel = selectedThemeForSetup == mode
                            Box(
                                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) BrandBlue else Color.Transparent)
                                    .clickable { selectedThemeForSetup = mode; viewModel.setThemeMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(emoji, fontSize = 12.sp)
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (isSel) Color(0xFF003166) else c.textSecondary)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            viewModel.completeFirstRunSetup(selectedThemeForSetup, selectedLanguageForSetup)
                            LocaleManager.setLanguage(context, selectedLanguageForSetup, restartActivity = true)
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color(0xFF003166)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.save_and_continue), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

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
                stringResource(R.string.perm_gate_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.perm_gate_desc),
                fontSize = 14.sp,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            if (!hasExactAlarm) {
                PermissionGateItem(
                    icon = Icons.Default.Alarm,
                    title = stringResource(R.string.perm_exact_alarm),
                    description = stringResource(R.string.perm_exact_alarm_sub),
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
                    title = stringResource(R.string.perm_fullscreen),
                    description = stringResource(R.string.perm_fullscreen_sub),
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
                    title = stringResource(R.string.perm_overlay),
                    description = stringResource(R.string.perm_overlay_sub),
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
                    BottomTab(stringResource(R.string.nav_alarms),    Icons.Default.Notifications,  currentTab == 0) { currentTab = 0 }
                    BottomTab(stringResource(R.string.nav_timer),     Icons.Default.Timer,          currentTab == 1) { currentTab = 1 }
                    BottomTab(stringResource(R.string.nav_stopwatch), Icons.Default.AccessTime,     currentTab == 2) { currentTab = 2 }
                    BottomTab(stringResource(R.string.nav_settings),  Icons.Default.Settings,       currentTab == 3) { currentTab = 3 }
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
                val nextPrefix = stringResource(R.string.next_alarm_prefix)
                val noActiveStr = stringResource(R.string.no_active_alarms)
                val calcStr = stringResource(R.string.calculating)
                val nextAlarmDisplay = remember(alarms, nextPrefix, noActiveStr, calcStr) {
                    val enabled = alarms.filter { it.isEnabled }
                    if (enabled.isEmpty()) return@remember noActiveStr
                    val next = enabled.map { it to AlarmScheduler.calculateNextTriggerTime(it) }.minByOrNull { it.second }
                    next?.let {
                        val diff = it.second - System.currentTimeMillis()
                        if (diff > 0) {
                            val h = diff / 3_600_000; val m = (diff / 60_000) % 60
                            "$nextPrefix ${SimpleDateFormat("E hh:mm a", Locale.getDefault()).format(Date(it.second))}  (${h}h ${m}m)"
                        } else calcStr
                    } ?: noActiveStr
                }
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(
                    if (c.isDark) Brush.horizontalGradient(listOf(Color(0xFF1C2D4A), Color(0xFF1E1A2E)))
                    else Brush.horizontalGradient(listOf(Color(0xFFD6E4FF), Color(0xFFEBDEFF)))
                ).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(nextAlarmDisplay, fontSize = 12.sp, color = if (c.isDark) BrandCyan else BrandBlue, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Notification banner
            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2D1810)).border(BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.notif_off_warning), color = WarningAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.btn_fix), color = WarningAmber, fontWeight = FontWeight.ExtraBold) }
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
                            text = stringResource(R.string.silent_banner_text),
                            color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp
                        )
                    }
                    IconButton(onClick = { viewModel.dismissSilentBanner() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = SuccessGreen, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Selection bar
            AnimatedVisibility(visible = selectionMode && currentTab == 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedIds = emptySet() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = c.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.selected_count, selectedIds.size), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    val allSel = alarms.isNotEmpty() && selectedIds.containsAll(alarms.map { it.id })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedIds = if (allSel) emptySet() else alarms.map { it.id }.toSet() }) {
                            Text(if (allSel) stringResource(R.string.select_none) else stringResource(R.string.select_all), color = BrandBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { selectedIds.forEach { id -> alarms.find { it.id == id }?.let { viewModel.deleteAlarm(it) } }; selectedIds = emptySet() },
                            colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.delete), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            EmptyStateView(Icons.Default.Notifications, stringResource(R.string.no_alarms_yet), stringResource(R.string.no_alarms_hint))
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
                            onLightWhitenessChange = { viewModel.setLightWhiteness(it) },
                            appLanguage = appLanguage,
                            onLanguageChange = { code ->
                                viewModel.setAppLanguage(code)
                                LocaleManager.setLanguage(context, code, restartActivity = true)
                            }
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
                        Text(stringResource(R.string.alarm_history_title), color = c2.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (logs.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearLogs() }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = DeleteRed, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.clear), color = DeleteRed, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onClick = { showHistory = false }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = c2.textSecondary) }
                        }
                    }
                    HorizontalDivider(color = c2.outlineColor, modifier = Modifier.padding(vertical = 8.dp))
                    if (logs.isEmpty()) {
                        EmptyStateView(Icons.AutoMirrored.Filled.List, stringResource(R.string.no_history_title), stringResource(R.string.no_history_desc))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                            items(logs, key = { it.id }) { LogItem(it) }
                        }
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
    val timeCol    = if (isCyclic) c.cyclicTimeColor else c.weeklyTimeColor
    val alpha      = if (alarm.isEnabled) 1f else 0.4f

    val displayHour = when { alarm.hour == 0 -> 12; alarm.hour > 12 -> alarm.hour - 12; else -> alarm.hour }
    val ampm    = if (alarm.hour >= 12) "PM" else "AM"
    val timeStr = String.format("%02d:%02d", displayHour, alarm.minute)

    val headerText = if (isCyclic) stringResource(R.string.sched_cyclic_prefix, alarm.cyclicIntervalDays)
    else {
        val d = alarm.getWeeklyDaysSet()
        val mo = stringResource(R.string.day_mo)
        val tu = stringResource(R.string.day_tu)
        val we = stringResource(R.string.day_we)
        val th = stringResource(R.string.day_th)
        val fr = stringResource(R.string.day_fr)
        val sa = stringResource(R.string.day_sa)
        val su = stringResource(R.string.day_su)
        when {
            d.isEmpty() -> stringResource(R.string.sched_one_off)
            d.size == 7 -> stringResource(R.string.sched_daily)
            d.size == 5 && !d.contains(6) && !d.contains(7) -> stringResource(R.string.sched_mon_fri)
            d.size == 2 && d.contains(6) && d.contains(7)   -> stringResource(R.string.sched_weekends)
            else -> listOf(mo, tu, we, th, fr, sa, su).let { n -> d.sorted().joinToString(" · ") { n[it - 1] } }
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
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.edit), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(onClick = onDelete, modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeleteRed.copy(alpha = 0.18f), contentColor = DeleteRed),
                        shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
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
                    Text(if (alarm == null) stringResource(R.string.dialog_new_alarm) else stringResource(R.string.dialog_edit_alarm), color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = c.textSecondary) }
                }
                HorizontalDivider(color = c.outlineColor, modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // TIME PICKER
                    item {
                        SectionCard(stringResource(R.string.alarm_time_section)) {
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
                            label = { Text(stringResource(R.string.alarm_label_optional), color = c.textSecondary) },
                            placeholder = { Text(stringResource(R.string.alarm_label_placeholder), color = c.textDisabled) },
                            modifier = Modifier.fillMaxWidth().testTag("alarm_label_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = c.outlineColor, focusedLabelColor = BrandBlue, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = BrandBlue),
                            shape = RoundedCornerShape(12.dp), singleLine = true)
                    }

                    // TYPE TABS
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.appBackground).padding(4.dp)) {
                            listOf("WEEKLY" to stringResource(R.string.tab_weekly_days), "CYCLIC" to stringResource(R.string.tab_cyclic_days)).forEach { (type, lbl) ->
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
                                SectionCard(stringResource(R.string.repeat_on_days_section)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        val dn = listOf(
                                            stringResource(R.string.day_mo),
                                            stringResource(R.string.day_tu),
                                            stringResource(R.string.day_we),
                                            stringResource(R.string.day_th),
                                            stringResource(R.string.day_fr),
                                            stringResource(R.string.day_sa),
                                            stringResource(R.string.day_su)
                                        )
                                        for (i in 1..7) {
                                            val a = weeklyDays[i] ?: false
                                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (a) BrandPurple else c.surfaceVariant).border(BorderStroke(1.dp, if (a) BrandPurple else c.outlineColor), CircleShape).clickable { weeklyDays[i] = !a }.testTag("day_button_$i"), contentAlignment = Alignment.Center) {
                                                Text(dn[i - 1], fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (a) Color.White else c.textSecondary)
                                            }
                                        }
                                    }
                                }
                            } else {
                                SectionCard(stringResource(R.string.cyclic_config_section)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.every_word), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(Modifier.width(10.dp))
                                        OutlinedTextField(value = cyclicInterval, onValueChange = { if (it.all(Char::isDigit)) cyclicInterval = it },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(72.dp).testTag("cyclic_interval_input"), singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = c.outlineColor, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = BrandBlue),
                                            shape = RoundedCornerShape(8.dp),
                                            textStyle = TextStyle(textAlign = TextAlign.Center, color = c.textPrimary, fontSize = 14.sp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.days_word), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    val sdf = remember { SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()) }
                                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(10.dp)).clickable { showDatePicker = true }.padding(12.dp).testTag("cyclic_start_date_selector_card"),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(stringResource(R.string.start_date), fontSize = 11.sp, color = c.textSecondary, fontWeight = FontWeight.Bold)
                                                Text(sdf.format(Date(cyclicStart)), fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    if (showDatePicker) {
                                        DatePickerDialog(onDismissRequest = { showDatePicker = false },
                                            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { cyclicStart = it }; showDatePicker = false }) { Text("OK", color = BrandBlue, fontWeight = FontWeight.Bold) } },
                                            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel), color = c.textSecondary) } }
                                        ) { DatePicker(state = datePickerState) }
                                    }
                                }
                            }
                        }
                    }

                    // SNOOZE DURATION (Per Alarm)
                    item {
                        SectionCard(stringResource(R.string.snooze_duration_section)) {
                            Text(stringResource(R.string.snooze_duration_hint), fontSize = 11.sp, color = c.textSecondary)
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
                                Text(stringResource(R.string.custom_colon), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                Text(stringResource(R.string.minutes_word), color = c.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }

                    // SOUND
                    item {
                        SectionCard(stringResource(R.string.alarm_sound_section)) {
                            // ── Custom track row (always visible) ──
                            if (customTrackUri.isNotEmpty()) {
                                val trackName = run {
                                    val raw = customTrackUri.substringAfterLast("/")
                                    val decoded = try {
                                        android.net.Uri.decode(raw)
                                    } catch (_: Exception) { raw }
                                    decoded.substringAfterLast("%2F").substringAfterLast("/")
                                        .removeSuffix(".mp3").removeSuffix(".m4a")
                                        .removeSuffix(".wav").removeSuffix(".ogg")
                                        .take(36).ifBlank { "Custom Track" }
                                }
                                val selCustom = soundPreset == "Custom Track"
                                val playCustom = isPreviewing == "Custom Track"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selCustom) BrandBlue.copy(alpha = 0.12f) else c.surfaceVariant)
                                        .border(BorderStroke(1.dp, if (selCustom) BrandBlue else c.outlineColor), RoundedCornerShape(10.dp))
                                        .clickable { soundPreset = "Custom Track" }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        RadioButton(
                                            selected = selCustom,
                                            onClick = { soundPreset = "Custom Track" },
                                            colors = RadioButtonDefaults.colors(selectedColor = BrandBlue, unselectedColor = c.textDisabled)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Column {
                                            Text(
                                                if (trackName == "Custom Track") stringResource(R.string.custom_track) else trackName,
                                                color = if (selCustom) c.textPrimary else c.textSecondary,
                                                fontWeight = if (selCustom) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(stringResource(R.string.custom_track_sub), color = c.textDisabled, fontSize = 10.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (playCustom) { onStopPreview(); isPreviewing = null }
                                            else { onPlayPreview(customTrackUri, volume); isPreviewing = "Custom Track" }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (playCustom) Icons.Default.Close else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (playCustom) DeleteRed else BrandBlue
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }

                            // ── Pick / Change track button — always visible ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surfaceVariant)
                                    .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(10.dp))
                                    .clickable { trackPicker.launch(arrayOf("audio/*")) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (customTrackUri.isNotEmpty()) stringResource(R.string.change_track) else stringResource(R.string.pick_from_device),
                                    color = c.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // ── Built-in preset rows ──
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
                        SectionCard(stringResource(R.string.volume_vibration_section)) {
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
                                    Text(stringResource(R.string.vibrate), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
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
                        Text(stringResource(R.string.save_alarm), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
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
    onLightWhitenessChange: (Float) -> Unit = {},
    appLanguage: String = "",
    onLanguageChange: (String) -> Unit = {}
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_settings), tint = BrandBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.back_to_settings), color = BrandBlue, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
            Text(stringResource(R.string.settings_title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.2.sp)

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
                        Text(stringResource(R.string.about_cyclic_alarms), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = c.textPrimary)
                        Text(stringResource(R.string.about_cyclic_alarms_subtitle), fontSize = 12.sp, color = c.textSecondary)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
            }

            // ── APP LANGUAGE CARD (OFFLINE) ──
            var showLanguagePicker by remember { mutableStateOf(false) }
            val currentLanguageObj = LocaleManager.SUPPORTED_LANGUAGES.find { it.code == appLanguage }
                ?: LocaleManager.SUPPORTED_LANGUAGES.first()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surfaceColor)
                    .border(BorderStroke(1.dp, c.outlineColor), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BrandBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.setting_language_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.textPrimary
                            )
                            Text(
                                stringResource(R.string.setting_language_subtitle),
                                fontSize = 11.sp,
                                color = c.textSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Currently selected language chip / expander
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceVariant)
                        .border(BorderStroke(1.dp, if (showLanguagePicker) BrandBlue else c.outlineColor), RoundedCornerShape(10.dp))
                        .clickable { showLanguagePicker = !showLanguagePicker }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(currentLanguageObj.flagEmoji, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                currentLanguageObj.nativeName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandBlue
                            )
                            if (currentLanguageObj.code.isNotEmpty()) {
                                Text(
                                    currentLanguageObj.englishName,
                                    fontSize = 11.sp,
                                    color = c.textSecondary
                                )
                            }
                        }
                    }
                    Icon(
                        if (showLanguagePicker) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showLanguagePicker,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        LocaleManager.SUPPORTED_LANGUAGES.forEach { lang ->
                            val isSel = lang.code == appLanguage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) BrandBlue.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        showLanguagePicker = false
                                        onLanguageChange(lang.code)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(lang.flagEmoji, fontSize = 16.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            lang.nativeName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (isSel) BrandBlue else c.textPrimary
                                        )
                                        if (lang.code.isNotEmpty()) {
                                            Text(
                                                lang.englishName,
                                                fontSize = 10.sp,
                                                color = c.textSecondary
                                            )
                                        }
                                    }
                                }
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
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
                Text(stringResource(R.string.setting_theme_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            // Dark row: Pure Dark + Dark
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceVariant).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("Pure Dark", "🌑", stringResource(R.string.theme_pure_dark)),
                    Triple("Dark", "🌙", stringResource(R.string.theme_dark))
                ).forEach { (mode, emoji, label) ->
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
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) BrandBlue else c.textSecondary)
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
                listOf(
                    Triple("System", "⚙️", stringResource(R.string.theme_system)),
                    Triple("Light", "☀️", stringResource(R.string.theme_light))
                ).forEach { (mode, emoji, label) ->
                    val selected = themeMode == mode
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) BrandBlue else Color.Transparent)
                            .clickable { onThemeModeChange(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(emoji, fontSize = 13.sp)
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color(0xFF003166) else c.textSecondary)
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
                        Text(stringResource(R.string.background_brightness), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text("${(lightWhiteness * 100).toInt()}%", fontSize = 12.sp, color = WarningAmber, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tinted), fontSize = 10.sp, color = c.textDisabled)
                        Slider(
                            value = lightWhiteness,
                            onValueChange = { onLightWhitenessChange(it) },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = WarningAmber, activeTrackColor = WarningAmber, inactiveTrackColor = c.outlineColor)
                        )
                        Text(stringResource(R.string.pure_white), fontSize = 10.sp, color = c.textDisabled)
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
                Text(stringResource(R.string.setting_clock_format_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.setting_clock_format_subtitle), fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("12H" to stringResource(R.string.time_format_12h), "24H" to stringResource(R.string.time_format_24h)).forEach { (fmt, lbl) ->
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
                Text(stringResource(R.string.setting_permissions_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.textPrimary)
            }
            Text(stringResource(R.string.setting_permissions_subtitle), fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(4.dp))

            PermissionRowItem(
                title = stringResource(R.string.perm_notifications),
                subtitle = stringResource(R.string.perm_notifications_sub),
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
                title = stringResource(R.string.perm_exact_alarm),
                subtitle = stringResource(R.string.perm_exact_alarm_sub),
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
                title = stringResource(R.string.perm_fullscreen),
                subtitle = stringResource(R.string.perm_fullscreen_sub),
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
                title = stringResource(R.string.perm_overlay),
                subtitle = stringResource(R.string.perm_overlay_sub),
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
    val statusText = if (isGranted) stringResource(R.string.badge_granted) else stringResource(R.string.badge_disabled)

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
                    text = if (isGranted) stringResource(R.string.btn_manage) else stringResource(R.string.btn_fix_now),
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
            Text(stringResource(R.string.btn_grant), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
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
                stringResource(R.string.timers_header, timers.size),
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
                    Text(stringResource(R.string.add_timer), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    com.example.service.TimerStopwatchService.stopAlert(context)
                    com.example.service.TimerStopwatchState.resetTimer(timer.id)
                    com.example.service.TimerStopwatchService.stopIfIdle(context)
                },
                onClear = {
                    com.example.service.TimerStopwatchService.stopAlert(context)
                    com.example.service.TimerStopwatchState.resetTimer(timer.id)
                    com.example.service.TimerStopwatchService.stopIfIdle(context)
                },
                onRemove = {
                    com.example.service.TimerStopwatchService.stopAlert(context)
                    com.example.service.TimerStopwatchState.removeTimer(timer.id)
                    com.example.service.TimerStopwatchService.stopIfIdle(context)
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
    onClear: () -> Unit,
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
            val timerLabel = if (timer.label.startsWith("Timer")) {
                val num = timer.label.removePrefix("Timer").trim()
                stringResource(R.string.timer_default_name) + (if (num.isNotEmpty()) " $num" else "")
            } else timer.label
            Text(
                text = timerLabel,
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
                    value = inputHours, label = stringResource(R.string.unit_hh), tag = "h_${timer.id}",
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
                    value = inputMinutes, label = stringResource(R.string.unit_mm), tag = "m_${timer.id}",
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
                    value = inputSeconds, label = stringResource(R.string.unit_ss), tag = "s_${timer.id}",
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
                    stringResource(R.string.times_up),
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
                Text(stringResource(R.string.reset), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (timer.isFinished) onClear()
                    else if (timer.isRunning) onPause()
                    else onStart()
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
                    text = if (timer.isFinished) stringResource(R.string.clear) else if (timer.isRunning) stringResource(R.string.pause) else stringResource(R.string.start),
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
        Text(stringResource(R.string.stopwatch_title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.2.sp)
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
                        text = "${stringResource(R.string.lap_prefix, laps.size + 1)} ${formatElapsed(lapMs)}",
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
                        com.example.service.TimerStopwatchService.stopIfIdle(context)
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
                Text(if (isRunning) stringResource(R.string.lap) else stringResource(R.string.reset), fontWeight = FontWeight.ExtraBold)
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
                Text(if (isRunning) stringResource(R.string.stop) else stringResource(R.string.start), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
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
                Text(stringResource(R.string.table_lap), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 0.8.sp)
                Text(stringResource(R.string.table_lap_time), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 0.8.sp)
                Text(stringResource(R.string.table_overall), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 0.8.sp)
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
        Text(stringResource(R.string.about_release_notes_header), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.2.sp)

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
                Text("Version 1.7", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SuccessGreen.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.hero_tagline), fontSize = 12.sp, color = c.textSecondary, textAlign = TextAlign.Center)
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
            Text(stringResource(R.string.support_community_header), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
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
                        Text(stringResource(R.string.support_email_title), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = c.textPrimary)
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
                Text(stringResource(R.string.release_history_title), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = c.textPrimary)
            }
            Text(stringResource(R.string.release_history_subtitle), fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(4.dp))

            // v1.7 — Expanded by default (current release)
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_7_title),
                badgeText = stringResource(R.string.badge_latest),
                isInitiallyExpanded = true,
                items = listOf(
                    "🔔" to stringResource(R.string.rel_v1_7_item1),
                    "⏰" to stringResource(R.string.rel_v1_7_item2)
                )
            )

            // v1.6.5 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_6_5_title),
                badgeText = "v1.6.5",
                isInitiallyExpanded = false,
                items = listOf(
                    "⏰" to stringResource(R.string.rel_v1_6_5_item1)
                )
            )

            // v1.6.4 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_6_4_title),
                badgeText = "v1.6.4",
                isInitiallyExpanded = false,
                items = listOf(
                    "🌐" to stringResource(R.string.rel_v1_6_4_item1),
                    "🚀" to stringResource(R.string.rel_v1_6_4_item2),
                    "🔢" to stringResource(R.string.rel_v1_6_4_item3)
                )
            )

            // v1.6.3 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_6_3_title),
                badgeText = "v1.6.3",
                isInitiallyExpanded = false,
                items = listOf(
                    "🌐" to stringResource(R.string.rel_v1_6_3_item1),
                    "🚀" to stringResource(R.string.rel_v1_6_3_item2),
                    "⚙️" to stringResource(R.string.rel_v1_6_3_item3)
                )
            )

            // v1.6.2 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_6_2_title),
                badgeText = "v1.6.2",
                isInitiallyExpanded = false,
                items = listOf(
                    "🛡" to stringResource(R.string.rel_v1_6_2_item1),
                    "⏱" to stringResource(R.string.rel_v1_6_2_item2),
                    "🔇" to stringResource(R.string.rel_v1_6_2_item3),
                    "🧭" to stringResource(R.string.rel_v1_6_2_item4),
                    "🔒" to stringResource(R.string.rel_v1_6_2_item5)
                )
            )

            // v1.6.1 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_6_1_title),
                badgeText = "v1.6.1",
                isInitiallyExpanded = false,
                items = listOf(
                    "📦" to stringResource(R.string.rel_v1_6_1_item1),
                    "🔑" to stringResource(R.string.rel_v1_6_1_item2)
                )
            )

            // v1.6 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_6_title),
                badgeText = "v1.6",
                isInitiallyExpanded = false,
                items = listOf(
                    "🔔" to stringResource(R.string.rel_v1_6_item1),
                    "🎨" to stringResource(R.string.rel_v1_6_item2),
                    "🌤" to stringResource(R.string.rel_v1_6_item3)
                )
            )

            // v1.5 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_5_title),
                badgeText = "v1.5",
                isInitiallyExpanded = false,
                items = listOf(
                    "🎵" to stringResource(R.string.rel_v1_5_item1),
                    "⏱" to stringResource(R.string.rel_v1_5_item2),
                    "🌑" to stringResource(R.string.rel_v1_5_item3),
                    "☀️" to stringResource(R.string.rel_v1_5_item4),
                    "🔄" to stringResource(R.string.rel_v1_5_item5),
                    "🔔" to stringResource(R.string.rel_v1_5_item6)
                )
            )

            // v1.4 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_4_title),
                badgeText = "v1.4",
                isInitiallyExpanded = false,
                items = listOf(
                    "📦" to stringResource(R.string.rel_v1_4_item1),
                    "🎯" to stringResource(R.string.rel_v1_4_item2)
                )
            )

            // v1.3 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_3_title),
                badgeText = "v1.3",
                isInitiallyExpanded = false,
                items = listOf(
                    "⏱" to stringResource(R.string.rel_v1_3_item1),
                    "⏱" to stringResource(R.string.rel_v1_3_item2),
                    "🔒" to stringResource(R.string.rel_v1_3_item3),
                    "📱" to stringResource(R.string.rel_v1_3_item4),
                    "🌀" to stringResource(R.string.rel_v1_3_item5),
                    "🖼" to stringResource(R.string.rel_v1_3_item6)
                )
            )

            // v1.2 — Collapsed
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_2_title),
                badgeText = "v1.2",
                isInitiallyExpanded = false,
                items = listOf(
                    "🌀" to stringResource(R.string.rel_v1_2_item1),
                    "⏱" to stringResource(R.string.rel_v1_2_item2),
                    "🔒" to stringResource(R.string.rel_v1_2_item3),
                    "📱" to stringResource(R.string.rel_v1_2_item4),
                    "🕒" to stringResource(R.string.rel_v1_2_item5),
                    "📅" to stringResource(R.string.rel_v1_2_item6),
                    "🎨" to stringResource(R.string.rel_v1_2_item7),
                    "🔑" to stringResource(R.string.rel_v1_2_item8),
                    "✉️" to stringResource(R.string.rel_v1_2_item9)
                )
            )

            // v1.1 — Collapsed by default
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_1_title),
                badgeText = "v1.1",
                isInitiallyExpanded = false,
                items = listOf(
                    "🌙" to stringResource(R.string.rel_v1_1_item1),
                    "🕐" to stringResource(R.string.rel_v1_1_item2),
                    "👁"  to stringResource(R.string.rel_v1_1_item3),
                    "🔔" to stringResource(R.string.rel_v1_1_item4),
                    "🔊" to stringResource(R.string.rel_v1_1_item5),
                    "🏷"  to stringResource(R.string.rel_v1_1_item6)
                )
            )

            // v1.0 — Collapsed by default
            ExpandableReleaseNoteCard(
                version = stringResource(R.string.rel_v1_0_title),
                badgeText = "v1.0",
                isInitiallyExpanded = false,
                items = listOf(
                    "⏰" to stringResource(R.string.rel_v1_0_item1),
                    "📅" to stringResource(R.string.rel_v1_0_item2),
                    "🎵" to stringResource(R.string.rel_v1_0_item3),
                    "😴" to stringResource(R.string.rel_v1_0_item4),
                    "📋" to stringResource(R.string.rel_v1_0_item5)
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
            Text(stringResource(R.string.creator_ack_header), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.textSecondary, letterSpacing = 1.sp)
            Text(stringResource(R.string.creator_ack_body1), fontSize = 12.sp, color = c.textSecondary, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.creator_ack_body2), fontSize = 11.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
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

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val currentTimeStr = remember(currentTimeMillis) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(currentTimeMillis))
    }
    val currentDateStr = remember(currentTimeMillis) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(currentTimeMillis))
    }
    val originalTimeStr = remember(activeAlarm.hour, activeAlarm.minute) {
        val dh = if (activeAlarm.hour == 0) 12 else if (activeAlarm.hour > 12) activeAlarm.hour - 12 else activeAlarm.hour
        String.format("%02d:%02d %s", dh, activeAlarm.minute, if (activeAlarm.hour >= 12) "PM" else "AM")
    }
    val snoozedAtStr = remember(activeAlarm.snoozedAt) {
        if (activeAlarm.snoozedAt > 0) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(activeAlarm.snoozedAt))
        } else ""
    }

    val accentColor = if (activeAlarm.isSnoozed) BrandPurple else BrandBlue

    val pulse by animateFloatAsState(
        targetValue = if (ticks % 2 == 0) 1.1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackground, Color(0xFF0D1B3E), Color(0xFF160D2E))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight().padding(vertical = 36.dp)
        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (activeAlarm.isSnoozed) {
                        stringResource(R.string.snoozed_alarm_ringing_title)
                    } else {
                        stringResource(R.string.alarm_ringing_title)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer(scaleX = pulse, scaleY = pulse)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape)
                        .border(BorderStroke(2.dp, accentColor.copy(alpha = 0.35f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (activeAlarm.isSnoozed) Icons.Default.Refresh else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(70.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.current_time_label).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    currentTimeStr,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    currentDateStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    activeAlarm.label.ifEmpty { stringResource(R.string.ringing_default_label) },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                if (activeAlarm.isSnoozed && snoozedAtStr.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BrandPurple.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, BrandPurple.copy(alpha = 0.45f)),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = BrandPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.ringing_snoozed_info, snoozedAtStr),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (activeAlarm.snoozedDurationMinutes > 0) {
                                        stringResource(R.string.ringing_snoozed_subinfo, activeAlarm.snoozedDurationMinutes, originalTimeStr)
                                    } else {
                                        stringResource(R.string.ringing_original_time, originalTimeStr)
                                    },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.ringing_original_time, originalTimeStr),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                var selectedSnoozeMins by remember(activeAlarm.snoozeMinutes) {
                    mutableIntStateOf(activeAlarm.snoozeMinutes.coerceIn(1, 180))
                }

                // Stepper Row for Snooze Duration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.ringing_snooze_duration),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (selectedSnoozeMins > 1) {
                                    val step = if (selectedSnoozeMins <= 5) 1 else 5
                                    selectedSnoozeMins = (selectedSnoozeMins - step).coerceAtLeast(1)
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(accentColor.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease Snooze", tint = accentColor, modifier = Modifier.size(20.dp))
                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            "${selectedSnoozeMins}m",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(Modifier.width(12.dp))

                        IconButton(
                            onClick = {
                                val step = if (selectedSnoozeMins < 5) 1 else 5
                                selectedSnoozeMins = (selectedSnoozeMins + step).coerceAtMost(180)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(accentColor.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase Snooze", tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Button(
                    onClick = {
                        com.example.service.AlarmService.snoozeAlarm(
                            context = context,
                            alarmId = activeAlarm.alarmId,
                            label = activeAlarm.label,
                            hour = activeAlarm.hour,
                            minute = activeAlarm.minute,
                            sound = activeAlarm.soundPreset,
                            vibrate = activeAlarm.vibrate,
                            volume = activeAlarm.volume,
                            snoozeMinutes = selectedSnoozeMins,
                            customTrackUri = activeAlarm.customTrackUri
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp).testTag("snooze_active_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color(0xFF003166)),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ringing_btn_snooze) + " (${selectedSnoozeMins}m)", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = {
                        com.example.service.AlarmService.dismissAlarm(
                            context,
                            activeAlarm.alarmId,
                            activeAlarm.label,
                            activeAlarm.hour,
                            activeAlarm.minute
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp).testTag("dismiss_active_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(2.dp, OutlineColor),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ringing_btn_dismiss), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}


