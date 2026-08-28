package com.routine.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RoutineReminderPolicyTest {
    @Test fun `quiet hours span overnight`() {
        assertTrue(RoutineReminderPolicy.isQuietHour(22))
        assertTrue(RoutineReminderPolicy.isQuietHour(7))
        assertFalse(RoutineReminderPolicy.isQuietHour(8))
        assertFalse(RoutineReminderPolicy.isQuietHour(15))
    }

    @Test fun `skip today resumes next morning`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.AUGUST, 28, 14, 45)
        }.timeInMillis

        val result = Calendar.getInstance(utc).apply {
            timeInMillis = RoutineReminderPolicy.nextMorning(now, utc)
        }

        assertEquals(29, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
    }

    @Test fun `cooldown backs off overdue reminders`() {
        assertEquals(24L, RoutineReminderPolicy.cooldownHours(48, 0))
        assertEquals(48L, RoutineReminderPolicy.cooldownHours(48, 2))
    }
}
