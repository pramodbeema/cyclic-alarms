package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ConflictResult(
    val conflictingAlarm: Alarm,
    val timeFormatted: String,
    val dayDescription: String
)

object AlarmConflictDetector {

    /**
     * Finds if any existing alarm shares the same trigger time (hour:minute) on any overlapping day.
     * Excludes [excludeAlarmId] so an alarm being edited does not conflict with itself.
     */
    fun findConflict(
        target: Alarm,
        existingAlarms: List<Alarm>,
        excludeAlarmId: Int? = null,
        timeFormat: String = "12H"
    ): ConflictResult? {
        val candidates = existingAlarms.filter { other ->
            other.id != excludeAlarmId &&
            other.hour == target.hour &&
            other.minute == target.minute
        }
        if (candidates.isEmpty()) return null

        // Check for schedule overlaps across the next 365 days
        val testCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, target.hour)
            set(Calendar.MINUTE, target.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dfTime = SimpleDateFormat(if (timeFormat == "24H") "HH:mm" else "hh:mm a", Locale.getDefault())
        val timeStr = dfTime.format(testCal.time)

        // Iterate through upcoming days to find the first collision
        for (dayOffset in 0..365) {
            val checkCal = Calendar.getInstance().apply {
                timeInMillis = testCal.timeInMillis + (dayOffset * 86_400_000L)
            }

            if (isAlarmActiveOnCalendarDay(target, checkCal)) {
                for (other in candidates) {
                    if (isAlarmActiveOnCalendarDay(other, checkCal)) {
                        val dayDesc = describeConflictDay(target, other, checkCal)
                        return ConflictResult(
                            conflictingAlarm = other,
                            timeFormatted = timeStr,
                            dayDescription = dayDesc
                        )
                    }
                }
            }
        }

        return null
    }

    /**
     * Determines whether an alarm is scheduled to trigger on a given calendar day.
     */
    fun isAlarmActiveOnCalendarDay(alarm: Alarm, cal: Calendar): Boolean {
        return when (alarm.alarmType) {
            "DAILY" -> {
                if (alarm.cyclicIntervalDays <= 1) true
                else {
                    val startCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }
                    startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
                    val targetCal = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
                    targetCal.set(Calendar.HOUR_OF_DAY, 0); targetCal.set(Calendar.MINUTE, 0); targetCal.set(Calendar.SECOND, 0); targetCal.set(Calendar.MILLISECOND, 0)
                    val diffDays = (targetCal.timeInMillis - startCal.timeInMillis) / 86_400_000L
                    diffDays >= 0 && (diffDays % alarm.cyclicIntervalDays == 0L)
                }
            }
            "CYCLIC" -> {
                val startCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }
                startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
                val targetCal = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
                targetCal.set(Calendar.HOUR_OF_DAY, 0); targetCal.set(Calendar.MINUTE, 0); targetCal.set(Calendar.SECOND, 0); targetCal.set(Calendar.MILLISECOND, 0)
                val diffDays = (targetCal.timeInMillis - startCal.timeInMillis) / 86_400_000L
                diffDays >= 0 && (diffDays % alarm.cyclicIntervalDays == 0L)
            }
            "WEEKLY" -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val appDow = if (dow == Calendar.SUNDAY) 7 else dow - 1
                alarm.getWeeklyDaysSet().contains(appDow)
            }
            "ONCE" -> {
                val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm)
                if (nextTrigger <= 0L) false
                else {
                    val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
                    triggerCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    triggerCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
                }
            }
            "MONTHLY" -> {
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                when (alarm.recurrenceSubtype) {
                    "SELECTED_DATES" -> alarm.getMonthlyDatesSet().contains(dayOfMonth)
                    "DAY_OF_WEEK" -> {
                        val ord = ((dayOfMonth - 1) / 7) + 1
                        val dow = cal.get(Calendar.DAY_OF_WEEK)
                        (alarm.monthlyDayOrdinal == 0 || alarm.monthlyDayOrdinal == ord) &&
                        (alarm.monthlyDayOfWeek == 0 || alarm.monthlyDayOfWeek == dow)
                    }
                    else -> {
                        val baseCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }
                        val targetDate = alarm.monthlyDates.toIntOrNull() ?: baseCal.get(Calendar.DAY_OF_MONTH)
                        dayOfMonth == targetDate
                    }
                }
            }
            "YEARLY" -> {
                val month = cal.get(Calendar.MONTH) + 1
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                when (alarm.recurrenceSubtype) {
                    "SELECTED_MONTHS" -> {
                        val baseCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }
                        val targetDate = alarm.monthlyDates.toIntOrNull() ?: baseCal.get(Calendar.DAY_OF_MONTH)
                        alarm.getYearlyMonthsSet().contains(month) && dayOfMonth == targetDate
                    }
                    "DAY_OF_WEEK" -> {
                        val baseCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }
                        val ord = ((dayOfMonth - 1) / 7) + 1
                        val dow = cal.get(Calendar.DAY_OF_WEEK)
                        month == (baseCal.get(Calendar.MONTH) + 1) &&
                        (alarm.monthlyDayOrdinal == 0 || alarm.monthlyDayOrdinal == ord) &&
                        (alarm.monthlyDayOfWeek == 0 || alarm.monthlyDayOfWeek == dow)
                    }
                    else -> {
                        val baseCal = Calendar.getInstance().apply { timeInMillis = alarm.cyclicStartDate }
                        month == (baseCal.get(Calendar.MONTH) + 1) && dayOfMonth == baseCal.get(Calendar.DAY_OF_MONTH)
                    }
                }
            }
            else -> false
        }
    }

    private fun describeConflictDay(a1: Alarm, a2: Alarm, cal: Calendar): String {
        // If both are weekly on the same day: return weekday name e.g. "Monday"
        val dfDay = SimpleDateFormat("EEEE", Locale.getDefault())
        val dfDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        if (a1.alarmType == "WEEKLY" && a2.alarmType == "WEEKLY") {
            return dfDay.format(cal.time)
        }
        if (a1.alarmType == "DAILY" && a2.alarmType == "DAILY" && a1.cyclicIntervalDays <= 1 && a2.cyclicIntervalDays <= 1) {
            return "Everyday"
        }
        return dfDate.format(cal.time)
    }
}
