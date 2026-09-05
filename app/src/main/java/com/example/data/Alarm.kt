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
    val snoozeCount: Int = 0,
    val lastTriggeredTime: Long = 0L
) {
    fun getWeeklyDaysSet(): Set<Int> {
        if (weeklyDays.isEmpty()) return emptySet()
        return weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }
}
