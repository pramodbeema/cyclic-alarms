package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_logs")
data class AlarmLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alarmId: Int,
    val label: String,
    val hour: Int,
    val minute: Int,
    val triggerTime: Long,
    val action: String // "DISMISSED", "SNOOZED", "MISSED"
)
