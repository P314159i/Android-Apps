package com.example.clocktracker

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Holds accumulated seconds for each day of the week (Mon-Sun).
 * Also stores the Monday date that defines "this week".
 */
data class WeeklyData(
    val weekStartMonday: String, // ISO date string of the Monday
    val dailySeconds: MutableMap<String, Long> = mutableMapOf(
        "MONDAY" to 0L,
        "TUESDAY" to 0L,
        "WEDNESDAY" to 0L,
        "THURSDAY" to 0L,
        "FRIDAY" to 0L,
        "SATURDAY" to 0L,
        "SUNDAY" to 0L
    )
)

/**
 * Persists weekly data, current session state, and accumulated
 * seconds for today using SharedPreferences + Gson.
 */
class DataStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clock_tracker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ---- Week detection ----

    /**
     * Returns the Monday of the current week.
     * Week runs Monday 00:00 to Sunday 23:59.
     */
    fun getCurrentWeekMonday(): LocalDate {
        val today = LocalDate.now()
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    // ---- Weekly data ----

    fun loadWeeklyData(): WeeklyData {
        val json = prefs.getString("weekly_data", null)
        val currentMonday = getCurrentWeekMonday().toString()

        if (json != null) {
            val data = gson.fromJson(json, WeeklyData::class.java)
            // If the stored week is old, reset for the new week
            if (data.weekStartMonday != currentMonday) {
                val fresh = WeeklyData(weekStartMonday = currentMonday)
                saveWeeklyData(fresh)
                // Also clear any lingering session data from previous week
                clearSessionState()
                return fresh
            }
            return data
        }

        val fresh = WeeklyData(weekStartMonday = currentMonday)
        saveWeeklyData(fresh)
        return fresh
    }

    fun saveWeeklyData(data: WeeklyData) {
        prefs.edit().putString("weekly_data", gson.toJson(data)).apply()
    }

    // ---- Session state (survives app kill mid-shift) ----

    fun saveSessionState(
        isClockedIn: Boolean,
        isOnBreak: Boolean,
        clockInEpochMillis: Long,
        accumulatedBeforeBreak: Long, // seconds accumulated before current running segment
        breakStartEpochMillis: Long
    ) {
        prefs.edit()
            .putBoolean("is_clocked_in", isClockedIn)
            .putBoolean("is_on_break", isOnBreak)
            .putLong("clock_in_epoch", clockInEpochMillis)
            .putLong("accumulated_before_break", accumulatedBeforeBreak)
            .putLong("break_start_epoch", breakStartEpochMillis)
            .apply()
    }

    fun isClockedIn(): Boolean = prefs.getBoolean("is_clocked_in", false)
    fun isOnBreak(): Boolean = prefs.getBoolean("is_on_break", false)
    fun getClockInEpoch(): Long = prefs.getLong("clock_in_epoch", 0L)
    fun getAccumulatedBeforeBreak(): Long = prefs.getLong("accumulated_before_break", 0L)
    fun getBreakStartEpoch(): Long = prefs.getLong("break_start_epoch", 0L)

    fun clearSessionState() {
        prefs.edit()
            .putBoolean("is_clocked_in", false)
            .putBoolean("is_on_break", false)
            .putLong("clock_in_epoch", 0L)
            .putLong("accumulated_before_break", 0L)
            .putLong("break_start_epoch", 0L)
            .apply()
    }

    // ---- Manual reset of the whole week ----

    fun resetWeeklyDataToZero() {
        val currentMonday = getCurrentWeekMonday().toString()
        val fresh = WeeklyData(weekStartMonday = currentMonday)
        saveWeeklyData(fresh)
    }
}
