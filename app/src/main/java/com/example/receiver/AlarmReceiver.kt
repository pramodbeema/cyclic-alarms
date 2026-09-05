package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm received: ID = ${intent.getIntExtra("ALARM_ID", -1)}")

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = "START_ALARM"
            putExtra("ALARM_ID", intent.getIntExtra("ALARM_ID", -1))
            putExtra("ALARM_LABEL", intent.getStringExtra("ALARM_LABEL") ?: "Alarm")
            putExtra("ALARM_HOUR", intent.getIntExtra("ALARM_HOUR", 0))
            putExtra("ALARM_MINUTE", intent.getIntExtra("ALARM_MINUTE", 0))
            putExtra("ALARM_SOUND", intent.getStringExtra("ALARM_SOUND") ?: "Zen Bowl")
            putExtra("ALARM_CUSTOM_URI", intent.getStringExtra("ALARM_CUSTOM_URI") ?: "")
            putExtra("ALARM_VIBRATE", intent.getBooleanExtra("ALARM_VIBRATE", true))
            putExtra("ALARM_VOLUME", intent.getFloatExtra("ALARM_VOLUME", 0.8f))
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start AlarmService", e)
        }
    }
}
