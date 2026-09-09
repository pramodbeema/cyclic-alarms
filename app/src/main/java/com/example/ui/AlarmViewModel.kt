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

    // Theme mode: "Pure Dark", "Dark", "Light", or "System"
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "Dark") ?: "Dark")
    val themeMode = _themeMode.asStateFlow()

    // Time format: "12H" or "24H"
    private val _timeFormat = MutableStateFlow(prefs.getString("time_format", "12H") ?: "12H")
    val timeFormat = _timeFormat.asStateFlow()

    // First launch theme setup status
    private val _isThemeSetupDone = MutableStateFlow(prefs.getBoolean("theme_setup_done", false))
    val isThemeSetupDone = _isThemeSetupDone.asStateFlow()

    // Keep for legacy compatibility — always false now
    val isDynamicColor = MutableStateFlow(false).asStateFlow()

    private val _snoozeMinutes = MutableStateFlow(prefs.getInt("snooze_minutes", 5))
    val snoozeMinutes = _snoozeMinutes.asStateFlow()

    // Light mode whiteness (0.0 = default tinted bg, 1.0 = pure white)
    private val _lightWhiteness = MutableStateFlow(
        prefs.getFloat("light_whiteness", 0f)
    )
    val lightWhiteness = _lightWhiteness.asStateFlow()

    // Last custom track URI — persisted so new alarms inherit it
    private val _lastCustomTrackUri = MutableStateFlow(
        prefs.getString("last_custom_track_uri", "") ?: ""
    )
    val lastCustomTrackUri = _lastCustomTrackUri.asStateFlow()

    // Silent mode info banner — shown once, dismissed permanently
    private val _silentBannerDismissed = MutableStateFlow(
        prefs.getBoolean("silent_banner_dismissed", false)
    )
    val silentBannerDismissed = _silentBannerDismissed.asStateFlow()

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

    fun completeThemeSetup(mode: String) {
        setThemeMode(mode)
        _isThemeSetupDone.value = true
        prefs.edit().putBoolean("theme_setup_done", true).apply()
    }

    fun setTimeFormat(format: String) {
        _timeFormat.value = format
        prefs.edit().putString("time_format", format).apply()
    }

    fun setSnoozeMinutes(minutes: Int) {
        _snoozeMinutes.value = minutes
        prefs.edit().putInt("snooze_minutes", minutes).apply()
    }

    fun setLightWhiteness(value: Float) {
        _lightWhiteness.value = value
        prefs.edit().putFloat("light_whiteness", value).apply()
    }

    fun setLastCustomTrackUri(uri: String) {
        _lastCustomTrackUri.value = uri
        prefs.edit().putString("last_custom_track_uri", uri).apply()
    }

    fun dismissSilentBanner() {
        _silentBannerDismissed.value = true
        prefs.edit().putBoolean("silent_banner_dismissed", true).apply()
    }

    // Tab navigation request — set by MainActivity when notification tap carries NAVIGATE_TO_TAB extra
    private val _requestedTab = MutableStateFlow<Int?>(null)
    val requestedTab = _requestedTab.asStateFlow()

    fun consumeRequestedTab(): Int? {
        val tab = _requestedTab.value
        _requestedTab.value = null
        return tab
    }

    fun requestTab(index: Int) {
        _requestedTab.value = index
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
