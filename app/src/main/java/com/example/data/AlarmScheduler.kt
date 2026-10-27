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
            return when (alarm.alarmType) {
                "ONCE" -> calculateOneOffNextTrigger(alarm, now)
                "DAILY" -> calculateDailyNextTrigger(alarm, now)
                "CYCLIC" -> calculateCyclicNextTrigger(alarm, now)
                "WEEKLY" -> calculateWeeklyNextTrigger(alarm, now)
                "MONTHLY" -> calculateMonthlyNextTrigger(alarm, now)
                "YEARLY" -> calculateYearlyNextTrigger(alarm, now)
                else -> calculateWeeklyNextTrigger(alarm, now)
            }
        }

        private fun calculateOneOffNextTrigger(alarm: Alarm, now: Long): Long {
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
        }

        private fun calculateDailyNextTrigger(alarm: Alarm, now: Long): Long {
            if (alarm.cyclicIntervalDays > 1) {
                return calculateCyclicNextTrigger(alarm, now)
            }
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
        }

        private fun calculateCyclicNextTrigger(alarm: Alarm, now: Long): Long {
            val interval = if (alarm.cyclicIntervalDays <= 0) 1 else alarm.cyclicIntervalDays
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
            val intervalMs = interval * 24L * 60 * 60 * 1000
            val numIntervals = (diffMs / intervalMs)
            startCal.add(Calendar.DAY_OF_YEAR, (numIntervals * interval).toInt())
            
            while (startCal.timeInMillis <= now) {
                startCal.add(Calendar.DAY_OF_YEAR, interval)
            }
            return startCal.timeInMillis
        }

        private fun calculateWeeklyNextTrigger(alarm: Alarm, now: Long): Long {
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
                val calActiveDays = daysSet.map { mapToCalendarDay(it) }.toSet()
                val tempCal = Calendar.getInstance()
                
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

        private fun calculateMonthlyNextTrigger(alarm: Alarm, now: Long): Long {
            val baseCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }

            return when (alarm.recurrenceSubtype) {
                "DAY_OF_WEEK" -> {
                    // e.g. 3rd Tuesday of every month
                    val ordinal = if (alarm.monthlyDayOrdinal != 0) alarm.monthlyDayOrdinal
                    else ((baseCal.get(Calendar.DAY_OF_MONTH) - 1) / 7 + 1)
                    val dayOfWeek = if (alarm.monthlyDayOfWeek != 0) alarm.monthlyDayOfWeek
                    else baseCal.get(Calendar.DAY_OF_WEEK)

                    for (m in 0..120) {
                        val refCal = Calendar.getInstance().apply {
                            timeInMillis = now
                            add(Calendar.MONTH, m)
                        }
                        val targetMonth = refCal.get(Calendar.MONTH)
                        val targetYear = refCal.get(Calendar.YEAR)

                        val testCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, targetYear)
                            set(Calendar.MONTH, targetMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, alarm.hour)
                            set(Calendar.MINUTE, alarm.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val firstDow = testCal.get(Calendar.DAY_OF_WEEK)
                        val daysUntilFirst = (dayOfWeek - firstDow + 7) % 7
                        testCal.add(Calendar.DAY_OF_MONTH, daysUntilFirst)
                        if (ordinal > 1) {
                            testCal.add(Calendar.DAY_OF_MONTH, (ordinal - 1) * 7)
                        }
                        if (testCal.get(Calendar.MONTH) == targetMonth && testCal.timeInMillis > now) {
                            return testCal.timeInMillis
                        }
                    }
                    now + 24 * 60 * 60 * 1000L
                }
                "SELECTED_DATES" -> {
                    // e.g. 5th, 20th, 25th of every month
                    val dates = alarm.getMonthlyDatesSet().sorted()
                    val effectiveDates = if (dates.isEmpty()) {
                        listOf(baseCal.get(Calendar.DAY_OF_MONTH))
                    } else dates

                    for (m in 0..120) {
                        val refCal = Calendar.getInstance().apply {
                            timeInMillis = now
                            add(Calendar.MONTH, m)
                        }
                        val targetMonth = refCal.get(Calendar.MONTH)
                        val targetYear = refCal.get(Calendar.YEAR)
                        val maxDay = refCal.getActualMaximum(Calendar.DAY_OF_MONTH)

                        var earliestInMonth = Long.MAX_VALUE
                        for (d in effectiveDates) {
                            if (d <= maxDay) {
                                val testCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, targetYear)
                                    set(Calendar.MONTH, targetMonth)
                                    set(Calendar.DAY_OF_MONTH, d)
                                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                                    set(Calendar.MINUTE, alarm.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                if (testCal.timeInMillis > now && testCal.timeInMillis < earliestInMonth) {
                                    earliestInMonth = testCal.timeInMillis
                                }
                            }
                        }
                        if (earliestInMonth != Long.MAX_VALUE) {
                            return earliestInMonth
                        }
                    }
                    now + 24 * 60 * 60 * 1000L
                }
                else -> {
                    // "DATE" - e.g. on the 20th of every month
                    val targetDay = alarm.monthlyDates.split(",").firstOrNull()?.trim()?.toIntOrNull()
                        ?: baseCal.get(Calendar.DAY_OF_MONTH)

                    for (m in 0..120) {
                        val testCal = Calendar.getInstance().apply {
                            timeInMillis = now
                            add(Calendar.MONTH, m)
                            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                            set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDay))
                            set(Calendar.HOUR_OF_DAY, alarm.hour)
                            set(Calendar.MINUTE, alarm.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (testCal.timeInMillis > now) {
                            return testCal.timeInMillis
                        }
                    }
                    now + 24 * 60 * 60 * 1000L
                }
            }
        }

        private fun calculateYearlyNextTrigger(alarm: Alarm, now: Long): Long {
            val baseCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }

            return when (alarm.recurrenceSubtype) {
                "DAY_OF_WEEK" -> {
                    // e.g. 3rd Tuesday of October
                    val targetMonth = baseCal.get(Calendar.MONTH)
                    val ordinal = if (alarm.monthlyDayOrdinal != 0) alarm.monthlyDayOrdinal
                    else ((baseCal.get(Calendar.DAY_OF_MONTH) - 1) / 7 + 1)
                    val dayOfWeek = if (alarm.monthlyDayOfWeek != 0) alarm.monthlyDayOfWeek
                    else baseCal.get(Calendar.DAY_OF_WEEK)

                    for (y in 0..10) {
                        val refCal = Calendar.getInstance().apply {
                            timeInMillis = now
                            add(Calendar.YEAR, y)
                        }
                        val targetYear = refCal.get(Calendar.YEAR)
                        val testCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, targetYear)
                            set(Calendar.MONTH, targetMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, alarm.hour)
                            set(Calendar.MINUTE, alarm.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val firstDow = testCal.get(Calendar.DAY_OF_WEEK)
                        val daysUntilFirst = (dayOfWeek - firstDow + 7) % 7
                        testCal.add(Calendar.DAY_OF_MONTH, daysUntilFirst)
                        if (ordinal > 1) {
                            testCal.add(Calendar.DAY_OF_MONTH, (ordinal - 1) * 7)
                        }
                        if (testCal.get(Calendar.MONTH) == targetMonth && testCal.timeInMillis > now) {
                            return testCal.timeInMillis
                        }
                    }
                    now + 24 * 60 * 60 * 1000L
                }
                "SELECTED_MONTHS" -> {
                    // e.g. 20th of selected months
                    val targetDay = alarm.monthlyDates.split(",").firstOrNull()?.trim()?.toIntOrNull()
                        ?: baseCal.get(Calendar.DAY_OF_MONTH)
                    val months = alarm.getYearlyMonthsSet().sorted()
                    val targetMonths = if (months.isEmpty()) listOf(baseCal.get(Calendar.MONTH) + 1) else months

                    for (y in 0..10) {
                        val refCal = Calendar.getInstance().apply {
                            timeInMillis = now
                            add(Calendar.YEAR, y)
                        }
                        val targetYear = refCal.get(Calendar.YEAR)

                        var earliestInYear = Long.MAX_VALUE
                        for (m in targetMonths) {
                            val mZero = m - 1
                            val testCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, targetYear)
                                set(Calendar.MONTH, mZero)
                                val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                                set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDay))
                                set(Calendar.HOUR_OF_DAY, alarm.hour)
                                set(Calendar.MINUTE, alarm.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            if (testCal.timeInMillis > now && testCal.timeInMillis < earliestInYear) {
                                earliestInYear = testCal.timeInMillis
                            }
                        }
                        if (earliestInYear != Long.MAX_VALUE) {
                            return earliestInYear
                        }
                    }
                    now + 24 * 60 * 60 * 1000L
                }
                else -> {
                    // "DATE" - e.g. 20th October
                    val targetMonth = baseCal.get(Calendar.MONTH)
                    val targetDay = alarm.monthlyDates.split(",").firstOrNull()?.trim()?.toIntOrNull()
                        ?: baseCal.get(Calendar.DAY_OF_MONTH)

                    for (y in 0..10) {
                        val refCal = Calendar.getInstance().apply {
                            timeInMillis = now
                            add(Calendar.YEAR, y)
                        }
                        val targetYear = refCal.get(Calendar.YEAR)
                        val testCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, targetYear)
                            set(Calendar.MONTH, targetMonth)
                            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                            set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDay))
                            set(Calendar.HOUR_OF_DAY, alarm.hour)
                            set(Calendar.MINUTE, alarm.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (testCal.timeInMillis > now) {
                            return testCal.timeInMillis
                        }
                    }
                    now + 24 * 60 * 60 * 1000L
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
