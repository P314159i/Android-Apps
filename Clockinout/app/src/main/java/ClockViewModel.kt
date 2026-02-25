package com.example.clocktracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class ClockViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = DataStore(application)

    // UI state
    private val _isClockedIn = MutableStateFlow(false)
    val isClockedIn: StateFlow<Boolean> = _isClockedIn

    private val _isOnBreak = MutableStateFlow(false)
    val isOnBreak: StateFlow<Boolean> = _isOnBreak

    // Elapsed seconds shown on the timer (for the current session only)
    private val _currentSessionSeconds = MutableStateFlow(0L)
    val currentSessionSeconds: StateFlow<Long> = _currentSessionSeconds

    // Weekly totals: day name -> total seconds
    private val _weeklyTotals = MutableStateFlow<Map<String, Long>>(emptyMap())
    val weeklyTotals: StateFlow<Map<String, Long>> = _weeklyTotals

    // Internal tracking
    private var clockInEpochMillis: Long = 0L
    private var accumulatedBeforeBreak: Long = 0L // seconds already banked before a break
    private var breakStartEpochMillis: Long = 0L

    private var timerRunning = false

    init {
        restoreState()
        loadWeeklyTotals()
    }

    // ---- Restore after app kill ----

    private fun restoreState() {
        // First check if the week rolled over
        dataStore.loadWeeklyData() // triggers reset if needed

        val wasClockedIn = dataStore.isClockedIn()
        val wasOnBreak = dataStore.isOnBreak()

        if (wasClockedIn) {
            clockInEpochMillis = dataStore.getClockInEpoch()
            accumulatedBeforeBreak = dataStore.getAccumulatedBeforeBreak()
            breakStartEpochMillis = dataStore.getBreakStartEpoch()

            // Check if the session started on a different day (past midnight)
            val sessionDate = java.time.Instant.ofEpochMilli(clockInEpochMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now()

            if (sessionDate != today) {
                // The session spans midnight — auto clock-out for the old day
                val endOfSessionDay = sessionDate.plusDays(1)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val secondsTillMidnight = if (wasOnBreak) {
                    accumulatedBeforeBreak
                } else {
                    accumulatedBeforeBreak + (endOfSessionDay - clockInEpochMillis) / 1000
                }
                // Add to the old day
                val weeklyData = dataStore.loadWeeklyData()
                val dayKey = sessionDate.dayOfWeek.name
                weeklyData.dailySeconds[dayKey] =
                    (weeklyData.dailySeconds[dayKey] ?: 0L) + secondsTillMidnight
                dataStore.saveWeeklyData(weeklyData)
                dataStore.clearSessionState()
                _isClockedIn.value = false
                _isOnBreak.value = false
                loadWeeklyTotals()
                return
            }

            _isClockedIn.value = true
            _isOnBreak.value = wasOnBreak

            if (!wasOnBreak) {
                startTimerTick()
            } else {
                // Show frozen time
                _currentSessionSeconds.value = accumulatedBeforeBreak
            }
        }
    }

    // ---- Clock In / Out ----

    fun toggleClock() {
        if (_isClockedIn.value) {
            clockOut()
        } else {
            clockIn()
        }
    }

    private fun clockIn() {
        clockInEpochMillis = System.currentTimeMillis()
        accumulatedBeforeBreak = 0L
        _isClockedIn.value = true
        _isOnBreak.value = false
        _currentSessionSeconds.value = 0L
        persistSession()
        startTimerTick()
    }

    private fun clockOut() {
        timerRunning = false
        val sessionSeconds = calculateCurrentSessionSeconds()
        _isClockedIn.value = false
        _isOnBreak.value = false
        _currentSessionSeconds.value = 0L

        // Add session seconds to today's total
        val today = LocalDate.now().dayOfWeek.name
        val weeklyData = dataStore.loadWeeklyData()
        weeklyData.dailySeconds[today] =
            (weeklyData.dailySeconds[today] ?: 0L) + sessionSeconds
        dataStore.saveWeeklyData(weeklyData)
        dataStore.clearSessionState()
        loadWeeklyTotals()
    }

    // ---- Break ----

    fun toggleBreak() {
        if (!_isClockedIn.value) return

        if (_isOnBreak.value) {
            resumeFromBreak()
        } else {
            startBreak()
        }
    }

    private fun startBreak() {
        timerRunning = false
        // Bank the seconds accumulated so far
        accumulatedBeforeBreak = calculateCurrentSessionSeconds()
        breakStartEpochMillis = System.currentTimeMillis()
        _isOnBreak.value = true
        _currentSessionSeconds.value = accumulatedBeforeBreak
        persistSession()
    }

    private fun resumeFromBreak() {
        // Reset clockIn to now; keep accumulated
        clockInEpochMillis = System.currentTimeMillis()
        breakStartEpochMillis = 0L
        _isOnBreak.value = false
        persistSession()
        startTimerTick()
    }

    // ---- Timer tick ----

    private fun startTimerTick() {
        if (timerRunning) return
        timerRunning = true
        viewModelScope.launch {
            while (timerRunning && _isClockedIn.value && !_isOnBreak.value) {
                _currentSessionSeconds.value = calculateCurrentSessionSeconds()
                delay(1000L)
            }
        }
    }

    private fun calculateCurrentSessionSeconds(): Long {
        if (!_isClockedIn.value) return 0L
        return if (_isOnBreak.value) {
            accumulatedBeforeBreak
        } else {
            val now = System.currentTimeMillis()
            accumulatedBeforeBreak + (now - clockInEpochMillis) / 1000
        }
    }

    // ---- Manual week reset and manual day set ----

    fun resetWeekManually() {
        dataStore.resetWeeklyDataToZero()
        dataStore.clearSessionState()
        _isClockedIn.value = false
        _isOnBreak.value = false
        _currentSessionSeconds.value = 0L
        loadWeeklyTotals()
    }

    fun setManualTime(day: DayOfWeek, hours: Int, minutes: Int) {
        val seconds = (hours * 3600 + minutes * 60).toLong()
        val weeklyData = dataStore.loadWeeklyData()
        val key = day.name   // "MONDAY", etc.
        weeklyData.dailySeconds[key] = seconds
        dataStore.saveWeeklyData(weeklyData)
        loadWeeklyTotals()
    }

    // ---- Persistence helpers ----

    private fun persistSession() {
        dataStore.saveSessionState(
            isClockedIn = _isClockedIn.value,
            isOnBreak = _isOnBreak.value,
            clockInEpochMillis = clockInEpochMillis,
            accumulatedBeforeBreak = accumulatedBeforeBreak,
            breakStartEpochMillis = breakStartEpochMillis
        )
    }

    private fun loadWeeklyTotals() {
        val data = dataStore.loadWeeklyData()
        _weeklyTotals.value = data.dailySeconds.toMap()
    }

    // ---- Formatting helpers (called from UI) ----

    companion object {
        fun formatSeconds(totalSeconds: Long): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return String.format("%02d:%02d:%02d", h, m, s)
        }

        fun formatHoursMinutes(totalSeconds: Long): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            return "${h}h ${m}m"
        }

        val DAY_ORDER = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )
    }
}
