package com.routine.domain

import java.util.Calendar
import java.util.TimeZone

object RoutineReminderPolicy {
    const val QUIET_HOUR_START = 22
    const val QUIET_HOUR_END = 8

    fun isQuietHour(hourOfDay: Int): Boolean =
        hourOfDay >= QUIET_HOUR_START || hourOfDay < QUIET_HOUR_END

    fun cooldownHours(intervalHours: Long, escalationLevel: Int): Long =
        if (escalationLevel >= 2) 48L else (intervalHours / 2L).coerceIn(8L, 24L)

    fun nextMorning(
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, QUIET_HOUR_END)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

