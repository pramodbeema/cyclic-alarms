package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SynthPlayer
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AlarmRepository
    private val prefs = application.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)

    val alarms: StateFlow<List<Alarm>>
    val logs: StateFlow<List<AlarmLog>>

    // Theme mode: "Dark", "Light", or "System"
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "Dark") ?: "Dark")
    val themeMode = _themeMode.asStateFlow()

    // Keep for legacy compatibility — always false now
    val isDynamicColor = MutableStateFlow(false).asStateFlow()

    private val _snoozeMinutes = MutableStateFlow(prefs.getInt("snooze_minutes", 5))
    val snoozeMinutes = _snoozeMinutes.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AlarmRepository(database.alarmDao(), application)
        
        alarms = repository.alarms.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        logs = repository.logs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setSnoozeMinutes(minutes: Int) {
        _snoozeMinutes.value = minutes
        prefs.edit().putInt("snooze_minutes", minutes).apply()
    }

    fun addAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.insertAlarm(alarm)
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.updateAlarm(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = !alarm.isEnabled)
            repository.updateAlarm(updated)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun previewSound(presetName: String, volume: Float) {
        SynthPlayer.playPreset(getApplication(), presetName, loop = false, volume = volume)
    }

    fun stopPreview() {
        SynthPlayer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        SynthPlayer.stop()
    }
}
