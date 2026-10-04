package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.service.RingingState
import com.example.ui.AlarmDashboard
import com.example.ui.AlarmViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle tab navigation from notification tap (cold start)
        intent?.getIntExtra("NAVIGATE_TO_TAB", -1)?.takeIf { it >= 0 }?.let {
            viewModel.requestTab(it)
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val lightWhiteness by viewModel.lightWhiteness.collectAsState()
            val activeAlarm by RingingState.activeAlarm.collectAsState()
            val timers by com.example.service.TimerStopwatchState.timers.collectAsState()
            val hasFinishedTimer = timers.any { it.isFinished }
            val isAlertActive = activeAlarm != null || hasFinishedTimer

            // ── Lock Screen Security & Return behavior ──
            // Dynamic window flags: showWhenLocked is ONLY enabled when an alarm or finished timer is actively alerting.
            // When dismissed/snoozed/cleared (or when user locks screen), we clear flags and call moveTaskToBack(true)
            // so the app NEVER leaks dashboard access while the device is locked!
            // Track whether an alarm/timer alert was actively ringing during this session.
            // On cold launch, isAlertActive is false; we MUST NOT call moveTaskToBack(true) on launch!
            var wasRinging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            LaunchedEffect(isAlertActive) {
                if (isAlertActive) {
                    wasRinging = true
                    applyAlarmWindowFlags(true)
                } else {
                    applyAlarmWindowFlags(false)
                    if (wasRinging) {
                        wasRinging = false
                        moveTaskToBack(true)
                    }
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
        // Security safeguard: If device is locked and neither alarm nor timer is ringing, clear showWhenLocked
        val hasFinishedTimer = com.example.service.TimerStopwatchState.timers.value.any { it.isFinished }
        if (RingingState.activeAlarm.value == null && !hasFinishedTimer) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle tab navigation from notification tap (app already running)
        intent.getIntExtra("NAVIGATE_TO_TAB", -1).takeIf { it >= 0 }?.let {
            viewModel.requestTab(it)
        }
        val hasFinishedTimer = com.example.service.TimerStopwatchState.timers.value.any { it.isFinished }
        if (RingingState.activeAlarm.value != null || hasFinishedTimer) {
            applyAlarmWindowFlags(true)
        }
    }
}
