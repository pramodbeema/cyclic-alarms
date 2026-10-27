package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.audio.SynthPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Data model for a single timer instance
// ─────────────────────────────────────────────────────────────────────────────
data class TimerInstance(
    val id: Int,
    val label: String = "Timer ${id + 1}",
    val totalMs: Long = 0L,
    val remainingMs: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Singleton state — shared between Service and UI composables
// ─────────────────────────────────────────────────────────────────────────────
object TimerStopwatchState {
    // Multiple timers (max 5)
    private val _timers = MutableStateFlow<List<TimerInstance>>(emptyList())
    val timers = _timers.asStateFlow()

    // Stopwatch
    private val _swElapsedMs = MutableStateFlow(0L)
    val swElapsedMs = _swElapsedMs.asStateFlow()

    private val _swRunning = MutableStateFlow(false)
    val swRunning = _swRunning.asStateFlow()

    private val _swLaps = MutableStateFlow<List<Long>>(emptyList())
    val swLaps = _swLaps.asStateFlow()

    private val _swLastLapMs = MutableStateFlow(0L)
    val swLastLapMs = _swLastLapMs.asStateFlow()

    // ── Timer operations ──
    fun addTimer(): Int {
        val newId = (_timers.value.maxOfOrNull { it.id } ?: -1) + 1
        val newIndex = _timers.value.size + 1
        _timers.value = _timers.value + TimerInstance(id = newId, label = "Timer $newIndex")
        return newId
    }

    fun removeTimer(id: Int) {
        val remaining = _timers.value.filter { it.id != id }
        _timers.value = remaining.mapIndexed { index, timer ->
            if (timer.label.startsWith("Timer")) {
                timer.copy(label = "Timer ${index + 1}")
            } else timer
        }
    }

    fun startTimer(id: Int) {
        _timers.value = _timers.value.map { t ->
            if (t.id == id && !t.isFinished && t.remainingMs > 0) t.copy(isRunning = true) else t
        }
    }

    fun pauseTimer(id: Int) {
        _timers.value = _timers.value.map { t ->
            if (t.id == id) t.copy(isRunning = false) else t
        }
    }

    fun setTimerDuration(id: Int, totalMs: Long) {
        _timers.value = _timers.value.map { t ->
            if (t.id == id) t.copy(totalMs = totalMs, remainingMs = totalMs, isRunning = false, isFinished = false) else t
        }
    }

    fun resetTimer(id: Int) {
        _timers.value = _timers.value.map { t ->
            if (t.id == id) t.copy(remainingMs = t.totalMs, isRunning = false, isFinished = false) else t
        }
    }

    /** Resets ALL finished timers back to their original duration without touching running ones.
     *  Called by the service AFTER releasing the alert wake lock, so the lock screen is re-engaged
     *  before [isAlertActive] transitions to false in the UI. */
    fun resetFinishedTimers() {
        _timers.value = _timers.value.map { t ->
            if (t.isFinished) t.copy(remainingMs = t.totalMs, isRunning = false, isFinished = false) else t
        }
    }

    fun clearTimer(id: Int) {
        _timers.value = _timers.value.map { t ->
            if (t.id == id) t.copy(totalMs = 0L, remainingMs = 0L, isRunning = false, isFinished = false) else t
        }
    }

    fun updateLabel(id: Int, label: String) {
        _timers.value = _timers.value.map { t -> if (t.id == id) t.copy(label = label) else t }
    }

    // Internal tick — called by service
    internal fun tickTimers(tickMs: Long): List<Int> {
        val finished = mutableListOf<Int>()
        _timers.value = _timers.value.map { t ->
            if (t.isRunning && t.remainingMs > 0L) {
                val newRemaining = (t.remainingMs - tickMs).coerceAtLeast(0L)
                if (newRemaining == 0L) {
                    finished.add(t.id)
                    t.copy(remainingMs = 0L, isRunning = false, isFinished = true)
                } else {
                    t.copy(remainingMs = newRemaining)
                }
            } else t
        }
        return finished
    }

    // ── Stopwatch operations ──
    fun startStopwatch() { _swRunning.value = true }
    fun pauseStopwatch() { _swRunning.value = false }
    fun resetStopwatch() {
        _swRunning.value = false
        _swElapsedMs.value = 0L
        _swLaps.value = emptyList()
        _swLastLapMs.value = 0L
    }
    fun lapStopwatch() {
        val lapTime = _swElapsedMs.value - _swLastLapMs.value
        _swLaps.value = _swLaps.value + lapTime
        _swLastLapMs.value = _swElapsedMs.value
    }

    // Internal tick — called by service
    internal fun tickStopwatch(tickMs: Long) {
        if (_swRunning.value) {
            _swElapsedMs.value += tickMs
        }
    }

    fun isAnythingRunning(): Boolean {
        return _timers.value.any { it.isRunning } || _swRunning.value
    }

    fun hasActiveOrPausedSession(): Boolean {
        val hasTimers = _timers.value.any { it.isRunning || (it.remainingMs < it.totalMs && !it.isFinished) }
        val hasStopwatch = _swRunning.value || _swElapsedMs.value > 0L
        return hasTimers || hasStopwatch
    }

    /** Explicitly stop and reset all running or finished timers and the stopwatch. */
    fun stopAll() {
        _timers.value = _timers.value.map { t ->
            t.copy(remainingMs = t.totalMs, isRunning = false, isFinished = false)
        }
        resetStopwatch()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Foreground Service
// ─────────────────────────────────────────────────────────────────────────────
class TimerStopwatchService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var tickJob: Job? = null
    private val TICK_MS = 20L
    private val NOTIF_ID = 2001
    private val CHANNEL_ID = "timer_stopwatch_channel"

    companion object {
        const val ACTION_START = "TS_START"
        const val ACTION_STOP_SELF = "TS_STOP_SELF"
        const val ACTION_STOP_ALERT = "TS_STOP_ALERT"

        fun start(context: Context) {
            val intent = Intent(context, TimerStopwatchService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerStopwatchService::class.java).apply {
                action = ACTION_STOP_SELF
            }
            context.startService(intent)
        }

        fun stopAlert(context: Context) {
            val intent = Intent(context, TimerStopwatchService::class.java).apply {
                action = ACTION_STOP_ALERT
            }
            context.startService(intent)
        }

        fun stopIfIdle(context: Context) {
            if (!TimerStopwatchState.hasActiveOrPausedSession()) {
                stop(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SELF -> {
                stopAlertSound()
                TimerStopwatchState.stopAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_ALERT -> {
                // 1. Release wake lock FIRST — so the screen is no longer force-kept on.
                stopAlertSound()
                // 2. Reset finished timers AFTER the wake lock is released — this causes
                //    isAlertActive to become false in MainActivity, which then calls
                //    moveTaskToBack(true) AFTER the screen can dim/keyguard can re-engage.
                TimerStopwatchState.resetFinishedTimers()
                if (!TimerStopwatchState.hasActiveOrPausedSession()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                } else if (!TimerStopwatchState.isAnythingRunning()) {
                    val notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notifMgr.notify(NOTIF_ID, buildNotification())
                }
                return START_STICKY
            }
        }

        startForeground(NOTIF_ID, buildNotification())
        startTicking()
        return START_STICKY
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            var notifTickCounter = 0
            while (true) {
                delay(TICK_MS)
                val finishedTimers = TimerStopwatchState.tickTimers(TICK_MS)
                TimerStopwatchState.tickStopwatch(TICK_MS)

                // Play alert sound for each finished timer
                finishedTimers.forEach { _ ->
                    playTimerFinishSound()
                }

                // If nothing is running or paused, and no finished timer alert is ringing, shut down service cleanly
                if (!TimerStopwatchState.hasActiveOrPausedSession() && !isPlayingAlert) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                // Update notification every ~500ms (every 25 ticks at 20ms each)
                notifTickCounter++
                if (notifTickCounter >= 25 || finishedTimers.isNotEmpty()) {
                    notifTickCounter = 0
                    val notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notifMgr.notify(NOTIF_ID, buildNotification())
                }
            }
        }
    }

    private var alertWakeLock: PowerManager.WakeLock? = null
    private var isPlayingAlert = false
    // True once the full-screen timer-finish activity has been successfully launched.
    // While true the foreground notification is rebuilt without a fullScreenIntent so
    // the system does not show a redundant heads-up pop-up on top of the full-screen UI.
    private var isFullScreenActivityActive = false

    private fun stopAlertSound() {
        try {
            SynthPlayer.stop()
            isPlayingAlert = false
            isFullScreenActivityActive = false
            alertWakeLock?.let {
                if (it.isHeld) it.release()
            }
            alertWakeLock = null
        } catch (_: Exception) {}
    }

    private fun playTimerFinishSound() {
        try {
            stopAlertSound()

            // Acquire FULL WakeLock to turn on the screen when timer goes off on locked device.
            // ON_AFTER_RELEASE is intentionally omitted: releasing this lock must NOT extend
            // the screen-on period, so the keyguard re-engages promptly once the alert is stopped.
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            alertWakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "com.example:TimerFinishWakeLock"
            ).also { it.acquire(5 * 60 * 1000L /* 5 min max */) }

            isPlayingAlert = true
            // Play "High Pitch" synth preset in a loop
            SynthPlayer.playPreset(this, "High Pitch", loop = true, volume = 1.0f)

            // Vibrate
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 300, 500), -1)
            }

            // Launch MainActivity full-screen to show timer-finish UI on the lock screen.
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = "SHOW_TIMER_FINISH"
                putExtra("NAVIGATE_TO_TAB", 1)
            }
            startActivity(launchIntent)
            // Mark the full-screen activity as active so buildNotification() skips the
            // fullScreenIntent, preventing a redundant heads-up notification from appearing
            // on top of the already-visible full-screen ringing UI.
            isFullScreenActivityActive = true
            // Switch to the silent foreground notification so no banner appears alongside
            // the full-screen ringing UI — that would be a duplicate alert.
            val notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            startForeground(SILENT_NOTIF_ID, buildSilentNotification())
            notifMgr.cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }

    private fun buildNotification(): android.app.Notification {
        val state = TimerStopwatchState
        val runningTimers = state.timers.value.filter { it.isRunning }
        val finishedTimers = state.timers.value.filter { it.isFinished }
        val swRunning = state.swRunning.value
        val hasFinished = finishedTimers.isNotEmpty() || isPlayingAlert

        // Target tab: 1 (Timer) if any timer is running or finished, else 2 (Stopwatch) if swRunning, else 1
        val targetTab = if (swRunning && runningTimers.isEmpty() && !hasFinished) 2 else 1

        val notifTitle = when {
            hasFinished -> "Timer Finished!"
            runningTimers.isNotEmpty() && swRunning -> "Timer & Stopwatch"
            runningTimers.isNotEmpty() -> "Timer"
            swRunning -> "Stopwatch"
            state.swElapsedMs.value > 0L -> "Stopwatch (Paused)"
            else -> "Timer (Paused)"
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = if (hasFinished) "SHOW_TIMER_FINISH" else "NAVIGATE_TAB_$targetTab"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO_TAB", targetTab)
        }

        val contentIntent = PendingIntent.getActivity(
            this, targetTab + 8000,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Only set fullScreenIntent when the full-screen activity has NOT yet been launched.
        // Once the activity is showing, omit it so the system does not show a redundant
        // heads-up / peek notification on top of the full-screen timer-finish UI.
        val fullScreenIntent = if (hasFinished && !isFullScreenActivityActive) {
            PendingIntent.getActivity(
                this, 8888,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val parts = mutableListOf<String>()
        if (hasFinished) {
            val names = finishedTimers.map { it.label }.ifEmpty { listOf("Timer") }
            parts.add("Time's up for ${names.joinToString(", ")}")
        }
        runningTimers.forEach { t ->
            val h = t.remainingMs / 3_600_000L
            val m = (t.remainingMs / 60_000L) % 60
            val s = (t.remainingMs / 1_000L) % 60
            parts.add("${t.label}: ${if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)}")
        }
        if (swRunning || (!hasFinished && runningTimers.isEmpty() && state.swElapsedMs.value > 0L)) {
            val e = state.swElapsedMs.value
            val m = (e / 60_000L) % 60
            val s = (e / 1_000L) % 60
            val cs = (e % 1_000L) / 10
            parts.add("Stopwatch: %02d:%02d.%02d".format(m, s, cs))
        }

        val text = if (parts.isNotEmpty()) parts.joinToString("  •  ") else "Paused"

        val channelToUse = if (hasFinished) ALERT_CHANNEL_ID else CHANNEL_ID

        val builder = NotificationCompat.Builder(this, channelToUse)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notifTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(!hasFinished)
            .setShowWhen(false)
            .setContentIntent(contentIntent)

        if (hasFinished) {
            // Use PRIORITY_MAX only when we still need the system to pop up the fullScreenIntent
            // (i.e. before the activity is launched). Once the full-screen UI is already showing,
            // drop to DEFAULT priority to avoid a redundant heads-up notification.
            val finishedPriority = if (isFullScreenActivityActive)
                NotificationCompat.PRIORITY_DEFAULT
            else
                NotificationCompat.PRIORITY_MAX
            builder.setPriority(finishedPriority)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            if (fullScreenIntent != null) {
                builder.setFullScreenIntent(fullScreenIntent, true)
            }
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val isAnythingRunning = TimerStopwatchState.isAnythingRunning() || hasFinished
        if (isAnythingRunning) {
            // Non-dismissible while actively running or finished — add Stop All action
            val stopIntent = PendingIntent.getService(
                this, 9999,
                Intent(this, TimerStopwatchService::class.java).apply { action = ACTION_STOP_SELF },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop All", stopIntent)
        } else {
            builder.setOngoing(false)
        }

        val notif = builder.build()
        if (isAnythingRunning) {
            notif.flags = notif.flags or
                android.app.Notification.FLAG_NO_CLEAR or
                android.app.Notification.FLAG_ONGOING_EVENT
        }
        return notif
    }

    private val ALERT_CHANNEL_ID = "timer_alert_channel"
    // Minimum-importance channel used while the full-screen timer-finish UI is visible.
    // IMPORTANCE_MIN means the notification satisfies the foreground-service requirement
    // but is NOT shown in the status bar or shade — eliminating the duplicate alert.
    private val SILENT_CHANNEL_ID = "timer_silent_bg_channel"
    private val SILENT_NOTIF_ID = 2002

    private fun buildSilentNotification(): android.app.Notification {
        return androidx.core.app.NotificationCompat.Builder(this, SILENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timer")
            .setContentText("Timer alert active")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val standardChannel = NotificationChannel(
                CHANNEL_ID,
                "Timer & Stopwatch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows running timers and stopwatch in background"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(standardChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Timer Finish Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts and shows lock screen popup when timer finishes"
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)

            // Silent background channel: satisfies foreground-service requirement while the
            // full-screen ringing UI is visible. IMPORTANCE_MIN = never shown in status bar.
            val silentChannel = NotificationChannel(
                SILENT_CHANNEL_ID,
                "Timer Background (Silent)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent foreground-service placeholder used when full-screen timer alert is active"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(silentChannel)
        }
    }

    override fun onDestroy() {
        stopAlertSound()
        tickJob?.cancel()
        serviceJob.cancel()
        // Clean up the silent foreground notification if it was used.
        val notifMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifMgr.cancel(SILENT_NOTIF_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
