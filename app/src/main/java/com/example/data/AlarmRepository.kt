package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val alarmDao: AlarmDao,
    context: Context
) {
    private val scheduler = AlarmScheduler(context)

    val alarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()
    val logs: Flow<List<AlarmLog>> = alarmDao.getAllLogs()

    suspend fun getAlarmById(id: Int): Alarm? {
        return alarmDao.getAlarmById(id)
    }

    suspend fun insertAlarm(alarm: Alarm) {
        val insertedId = alarmDao.insertAlarm(alarm)
        // Retrieve alarm with proper ID to schedule
        val savedAlarm = alarmDao.getAlarmById(insertedId.toInt())
        if (savedAlarm != null) {
            scheduler.schedule(savedAlarm)
        }
    }

    suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
        if (alarm.isEnabled) {
            scheduler.schedule(alarm)
        } else {
            scheduler.cancel(alarm)
        }
    }

    suspend fun deleteAlarm(alarm: Alarm) {
        scheduler.cancel(alarm)
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun insertLog(log: AlarmLog) {
        alarmDao.insertLog(log)
    }

    suspend fun clearLogs() {
        alarmDao.clearLogs()
    }
}
