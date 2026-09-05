package com.example.service

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.audio.SynthPlayer
import com.example.data.AlarmLog
import com.example.data.AppDatabase
import com.example.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object RingingState {
    data class ActiveAlarm(
        val alarmId: Int,
        val label: String,
        val hour: Int,
        val minute: Int,
        val soundPreset: String,
        val customTrackUri: String = "",
        val vibrate: Boolean,
        val volume: Float
    )
    
    private val _activeAlarm = MutableStateFlow<ActiveAlarm?>(null)
    val activeAlarm = _activeAlarm.asStateFlow()
    
    fun startRinging(alarm: ActiveAlarm) {
        _activeAlarm.value = alarm
    }
    
    fun stopRinging() {
        _activeAlarm.value = null
    }
}

class AlarmService : Service() {
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: AlarmRepository

    companion object {
        fun dismissAlarm(context: Context, alarmId: Int, label: String, hour: Int, minute: Int) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = "DISMISS_ALARM"
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", label)
                putExtra("ALARM_HOUR", hour)
                putExtra("ALARM_MINUTE", minute)
            }
            context.startService(intent)
        }

        fun snoozeAlarm(
            context: Context,
            alarmId: Int,
            label: String,
            hour: Int,
            minute: Int,
            sound: String,
            vibrate: Boolean,
            volume: Float
        ) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = "SNOOZE_ALARM"
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", label)
                putExtra("ALARM_HOUR", hour)
                putExtra("ALARM_MINUTE", minute)
                putExtra("ALARM_SOUND", sound)
                putExtra("ALARM_VIBRATE", vibrate)
                putExtra("ALARM_VOLUME", volume)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = AlarmRepository(database.alarmDao(), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        val label = intent?.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val hour = intent?.getIntExtra("ALARM_HOUR", 0) ?: 0
        val minute = intent?.getIntExtra("ALARM_MINUTE", 0) ?: 0
        val soundPreset = intent?.getStringExtra("ALARM_SOUND") ?: "Zen Bowl"
        val customTrackUri = intent?.getStringExtra("ALARM_CUSTOM_URI") ?: ""
        val vibrate = intent?.getBooleanExtra("ALARM_VIBRATE", true) ?: true
        val volume = intent?.getFloatExtra("ALARM_VOLUME", 0.8f) ?: 0.8f

        when (action) {
            "START_ALARM" -> {
                startAlarmMode(alarmId, label, hour, minute, soundPreset, customTrackUri, vibrate, volume)
            }
            "DISMISS_ALARM" -> {
                dismissAlarmMode(alarmId, label, hour, minute)
            }
            "SNOOZE_ALARM" -> {
                snoozeAlarmMode(alarmId, label, hour, minute, soundPreset, vibrate, volume)
            }
        }

        return START_NOT_STICKY
    }

    private fun startAlarmMode(
        alarmId: Int,
        label: String,
        hour: Int,
        minute: Int,
        soundPreset: String,
        customTrackUri: String,
        vibrate: Boolean,
        volume: Float
    ) {
        // Acquire a FULL WakeLock to turn on the screen when the alarm fires on a locked device.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock?.release()
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "com.example:AlarmServiceWakeLock"
        ).also { it.acquire(10 * 60 * 1000L /* 10 minutes max */) }

        // Play custom track URI if set, otherwise fall back to synth preset
        val soundToPlay = if (customTrackUri.isNotEmpty()) customTrackUri else soundPreset
        SynthPlayer.playPreset(this, soundToPlay, loop = true, volume = volume)

        // Set the active ringing alarm state
        RingingState.startRinging(
            RingingState.ActiveAlarm(
                alarmId = alarmId,
                label = label,
                hour = hour,
                minute = minute,
                soundPreset = soundPreset,
                customTrackUri = customTrackUri,
                vibrate = vibrate,
                volume = volume
            )
        )

        if (vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            val pattern = longArrayOf(0, 800, 800, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }

        createNotificationChannel()

        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            action = "DISMISS_ALARM"
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_HOUR", hour)
            putExtra("ALARM_MINUTE", minute)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            alarmId + 1000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
            action = "SNOOZE_ALARM"
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_HOUR", hour)
            putExtra("ALARM_MINUTE", minute)
            putExtra("ALARM_SOUND", soundPreset)
            putExtra("ALARM_CUSTOM_URI", customTrackUri)
            putExtra("ALARM_VIBRATE", vibrate)
            putExtra("ALARM_VOLUME", volume)
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            alarmId + 2000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // FLAG_ACTIVITY_SINGLE_TOP + FLAG_ACTIVITY_CLEAR_TOP ensures the existing MainActivity
        // instance is reused (onNewIntent is called) rather than recreated, which would trigger
        // onDestroy and kill the audio.
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "SHOW_RINGING_SCREEN"
            putExtra("ALARM_ID", alarmId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            alarmId + 3000,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeStr = String.format("%02d:%02d", hour, minute)
        val prefs = getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
        val snoozeMins = prefs.getInt("snooze_minutes", 5)

        val notification = NotificationCompat.Builder(this, "alarm_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm: $label")
            .setContentText("Ringing since $timeStr")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Snooze (${snoozeMins}m)", snoozePendingIntent)
            .build()

        startForeground(1001, notification)

        // Directly launch MainActivity over the lock screen so the ringing UI shows immediately
        // without the user having to tap the notification.
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "SHOW_RINGING_SCREEN"
            putExtra("ALARM_ID", alarmId)
        }
        startActivity(launchIntent)
    }

    private fun dismissAlarmMode(alarmId: Int, label: String, hour: Int, minute: Int) {
        stopAlarmResources()

        serviceScope.launch {
            repository.insertLog(
                AlarmLog(
                    alarmId = alarmId,
                    label = label,
                    hour = hour,
                    minute = minute,
                    triggerTime = System.currentTimeMillis(),
                    action = "DISMISSED"
                )
            )

            if (alarmId != -1) {
                val alarm = repository.getAlarmById(alarmId)
                if (alarm != null) {
                    val updated = alarm.copy(
                        lastTriggeredTime = System.currentTimeMillis(),
                        snoozeCount = 0
                    )
                    repository.updateAlarm(updated)
                }
            }
        }

        stopSelf()
    }

    private fun snoozeAlarmMode(
        alarmId: Int,
        label: String,
        hour: Int,
        minute: Int,
        soundPreset: String,
        vibrate: Boolean,
        volume: Float
    ) {
        stopAlarmResources()

        serviceScope.launch {
            repository.insertLog(
                AlarmLog(
                    alarmId = alarmId,
                    label = label,
                    hour = hour,
                    minute = minute,
                    triggerTime = System.currentTimeMillis(),
                    action = "SNOOZED"
                )
            )

            val prefs = getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
            val snoozeMins = prefs.getInt("snooze_minutes", 5)
            val snoozeTimeMs = System.currentTimeMillis() + snoozeMins * 60 * 1000

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(applicationContext, com.example.receiver.AlarmReceiver::class.java).apply {
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", "$label (Snoozed)")
                putExtra("ALARM_HOUR", hour)
                putExtra("ALARM_MINUTE", minute)
                putExtra("ALARM_SOUND", soundPreset)
                putExtra("ALARM_VIBRATE", vibrate)
                putExtra("ALARM_VOLUME", volume)
            }

            val snoozePendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                alarmId + 5000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            snoozeTimeMs,
                            snoozePendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            snoozeTimeMs,
                            snoozePendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        snoozeTimeMs,
                        snoozePendingIntent
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTimeMs,
                    snoozePendingIntent
                )
            }

            if (alarmId != -1) {
                val alarm = repository.getAlarmById(alarmId)
                if (alarm != null) {
                    repository.updateAlarm(alarm.copy(snoozeCount = alarm.snoozeCount + 1))
                }
            }
        }

        stopSelf()
    }

    private fun stopAlarmResources() {
        SynthPlayer.stop()
        RingingState.stopRinging()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping vibrator", e)
        }
        // Release the WakeLock now that the alarm has been handled.
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Error releasing WakeLock", e)
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_channel",
                "Alarms & Timers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification and sounds for scheduled alarms"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarmResources()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
