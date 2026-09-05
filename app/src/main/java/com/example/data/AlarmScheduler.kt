package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.receiver.AlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }

        val triggerTime = calculateNextTriggerTime(alarm)
        if (triggerTime <= 0L) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_LABEL", alarm.label)
            putExtra("ALARM_HOUR", alarm.hour)
            putExtra("ALARM_MINUTE", alarm.minute)
            putExtra("ALARM_SOUND", alarm.soundPreset)
            putExtra("ALARM_CUSTOM_URI", alarm.customTrackUri)
            putExtra("ALARM_VIBRATE", alarm.vibrate)
            putExtra("ALARM_VOLUME", alarm.volume)
            putExtra("ALARM_SNOOZE_MINUTES", alarm.snoozeMinutes)
        }

        // Use a unique requestCode for each alarm
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Alarm scheduled for ${alarm.label} (ID: ${alarm.id}) at $triggerTime")
        } catch (e: SecurityException) {
            // Fallback to inexact alarm if exact permission is missing on Android 14+
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.w("AlarmScheduler", "Fallback to non-exact alarm due to security exception", e)
        }
    }

    fun cancel(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Alarm canceled for ${alarm.label} (ID: ${alarm.id})")
        }
    }

    companion object {
        fun calculateNextTriggerTime(alarm: Alarm): Long {
            val now = System.currentTimeMillis()
            
            if (alarm.alarmType == "CYCLIC") {
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = alarm.cyclicStartDate
                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                    set(Calendar.MINUTE, alarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                
                if (startCal.timeInMillis > now) {
                    return startCal.timeInMillis
                }
                
                val diffMs = now - startCal.timeInMillis
                val intervalMs = alarm.cyclicIntervalDays * 24L * 60 * 60 * 1000
                val numIntervals = (diffMs / intervalMs) + 1
                startCal.add(Calendar.DAY_OF_YEAR, (numIntervals * alarm.cyclicIntervalDays).toInt())
                
                while (startCal.timeInMillis <= now) {
                    startCal.add(Calendar.DAY_OF_YEAR, alarm.cyclicIntervalDays)
                }
                return startCal.timeInMillis
            } else {
                // WEEKLY RECURRING OR ONE-OFF
                val daysSet = alarm.getWeeklyDaysSet()
                
                if (daysSet.isEmpty()) {
                    // One-off alarm
                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, alarm.hour)
                        set(Calendar.MINUTE, alarm.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (targetCal.timeInMillis <= now) {
                        targetCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return targetCal.timeInMillis
                } else {
                    // Find the next active day of the week
                    // Map daysSet from ISO (1=Mon..7=Sun) to Calendar days
                    val calActiveDays = daysSet.map { mapToCalendarDay(it) }.toSet()
                    
                    val tempCal = Calendar.getInstance()
                    
                    // We check up to 7 days into the future
                    for (i in 0..7) {
                        tempCal.timeInMillis = now + i * 24L * 60 * 60 * 1000
                        tempCal.set(Calendar.HOUR_OF_DAY, alarm.hour)
                        tempCal.set(Calendar.MINUTE, alarm.minute)
                        tempCal.set(Calendar.SECOND, 0)
                        tempCal.set(Calendar.MILLISECOND, 0)
                        
                        val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
                        if (calActiveDays.contains(dayOfWeek)) {
                            if (tempCal.timeInMillis > now) {
                                return tempCal.timeInMillis
                            }
                        }
                    }
                    
                    // Fallback
                    val fallbackCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, alarm.hour)
                        set(Calendar.MINUTE, alarm.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (fallbackCal.timeInMillis <= now) {
                        fallbackCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return fallbackCal.timeInMillis
                }
            }
        }

        private fun mapToCalendarDay(dayOfWeek: Int): Int {
            return when (dayOfWeek) {
                1 -> Calendar.MONDAY
                2 -> Calendar.TUESDAY
                3 -> Calendar.WEDNESDAY
                4 -> Calendar.THURSDAY
                5 -> Calendar.FRIDAY
                6 -> Calendar.SATURDAY
                7 -> Calendar.SUNDAY
                else -> Calendar.MONDAY
            }
        }
    }
}
