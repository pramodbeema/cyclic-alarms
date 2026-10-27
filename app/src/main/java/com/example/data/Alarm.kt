package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val hour: Int, // 0 - 23
    val minute: Int, // 0 - 59
    val isEnabled: Boolean = true,
    
    // Type of recurrence: "WEEKLY" or "CYCLIC"
    val alarmType: String = "WEEKLY", // "WEEKLY" or "CYCLIC"
    
    // WEEKLY recurrences: comma-separated days of week, e.g., "1,2,3,4,5" (1 = Monday, 7 = Sunday)
    val weeklyDays: String = "", 
    
    // CYCLIC recurrences: interval in days, e.g., 3 (repeats every 3 days)
    val cyclicIntervalDays: Int = 3,
    
    // Start date for CYCLIC recurrences (Epoch millisecond)
    val cyclicStartDate: Long = System.currentTimeMillis(),
    
    // Customization
    val soundPreset: String = "High Pitch",
    val customTrackUri: String = "",   // URI string if user picked a file from device, else ""
    val vibrate: Boolean = true,
    val volume: Float = 1.0f,
    val snoozeMinutes: Int = 5,
    val snoozeCount: Int = 0,
    val lastTriggeredTime: Long = 0L,

    // Flexible recurrence fields (v1.7.1)
    // For MONTHLY: "DATE", "DAY_OF_WEEK", "SELECTED_DATES"
    // For YEARLY: "DATE", "DAY_OF_WEEK", "SELECTED_MONTHS"
    val recurrenceSubtype: String = "",
    val monthlyDates: String = "",      // Comma-separated dates, e.g. "5,20,25"
    val monthlyDayOrdinal: Int = 0,     // 1=1st, 2=2nd, 3=3rd, 4=4th, -1=last
    val monthlyDayOfWeek: Int = 0,      // Calendar.SUNDAY(1)..Calendar.SATURDAY(7)
    val yearlyMonths: String = ""       // Comma-separated months, e.g. "1,4,7,10" (1=Jan..12=Dec)
) {
    fun getWeeklyDaysSet(): Set<Int> {
        if (weeklyDays.isEmpty()) return emptySet()
        return weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun getMonthlyDatesSet(): Set<Int> {
        if (monthlyDates.isEmpty()) return emptySet()
        return monthlyDates.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun getYearlyMonthsSet(): Set<Int> {
        if (yearlyMonths.isEmpty()) return emptySet()
        return yearlyMonths.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    val isOneOff: Boolean
        get() = when (alarmType) {
            "ONCE" -> true
            "WEEKLY" -> getWeeklyDaysSet().isEmpty()
            "DAILY", "CYCLIC", "MONTHLY", "YEARLY" -> false
            else -> false
        }
}
