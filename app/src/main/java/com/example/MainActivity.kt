package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.example.service.RingingState
import com.example.ui.AlarmDashboard
import com.example.ui.AlarmViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle tab navigation from notification tap (cold start)
        intent?.getIntExtra("NAVIGATE_TO_TAB", -1)?.takeIf { it >= 0 }?.let {
            viewModel.requestTab(it)
        }

        // ── Request all special permissions on first launch ──
        requestRequiredPermissions()

        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val lightWhiteness by viewModel.lightWhiteness.collectAsState()
            val activeAlarm by RingingState.activeAlarm.collectAsState()

            // ── Lock Screen Security & Return behavior ──
            // Dynamic window flags: showWhenLocked is ONLY enabled when an alarm is actively ringing.
            // When dismissed/snoozed (or when user locks screen), we clear flags and call moveTaskToBack(true)
            // so the app NEVER leaks dashboard access while the device is locked!
            LaunchedEffect(activeAlarm) {
                if (activeAlarm != null) {
                    applyAlarmWindowFlags(true)
                } else {
                    applyAlarmWindowFlags(false)
                    moveTaskToBack(true)
                }
            }

            MyApplicationTheme(themeMode = themeMode, lightWhiteness = lightWhiteness) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmDashboard(viewModel = viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Security safeguard: If device is locked and alarm is NOT ringing, clear showWhenLocked
        if (RingingState.activeAlarm.value == null) {
            applyAlarmWindowFlags(false)
        }
    }

    /** Apply or clear window flags needed to show the ringing UI over the lock screen. */
    private fun applyAlarmWindowFlags(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enable)
            setTurnScreenOn(enable)
        } else {
            @Suppress("DEPRECATION")
            if (enable) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun requestRequiredPermissions() {
        // 1. SYSTEM_ALERT_WINDOW — "Display over other apps" — needed for lock screen overlay
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        // 2. USE_FULL_SCREEN_INTENT — Android 14+ (API 34) requires explicit grant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        // 3. SCHEDULE_EXACT_ALARM — Android 12+ (API 31-32) needs user approval
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle tab navigation from notification tap (app already running)
        intent.getIntExtra("NAVIGATE_TO_TAB", -1).takeIf { it >= 0 }?.let {
            viewModel.requestTab(it)
        }
        if (RingingState.activeAlarm.value != null) {
            applyAlarmWindowFlags(true)
        }
    }
}
