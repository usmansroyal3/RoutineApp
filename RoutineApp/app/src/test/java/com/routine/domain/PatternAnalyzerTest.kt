package com.routine.domain

import com.routine.data.model.RoutineType
import com.routine.data.model.TaskLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PatternAnalyzerTest {
    private val analyzer = PatternAnalyzer()
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `requires at least three completion intervals`() {
        assertNull(analyzer.analyze(logsAtHours(0, 48, 96), utc))
    }

    @Test
    fun `detects a consistent two day cadence`() {
        val result = analyzer.analyze(logsAtHours(0, 48, 96, 144, 192), utc)

        assertNotNull(result)
        assertEquals(48, result!!.intervalHours)
        assertEquals(RoutineType.STRICT, result.suggestedType)
        assertTrue(analyzer.shouldPropose(result))
    }

    @Test
    fun `missed completion does not distort median cadence`() {
        val result = analyzer.analyze(logsAtHours(0, 48, 96, 336, 384, 432), utc)

        assertNotNull(result)
        assertEquals(48, result!!.intervalHours)
        assertTrue(result.confidence >= PatternAnalyzer.HIGH_CONFIDENCE)
    }

    @Test
    fun `circular time average handles midnight`() {
        val base = utcMillis(2026, Calendar.JANUARY, 1, 23, 30)
        val timestamps = listOf(0L, 25L, 48L, 73L).mapIndexed { index, hours ->
            TaskLog(id = index.toLong(), taskId = 1, timestamp = base + hours * HOUR)
        }

        val result = analyzer.analyze(timestamps, utc)

        assertNotNull(result)
        assertTrue(result!!.preferredHourOfDay == 0 || result.preferredHourOfDay == 23)
    }

    @Test
    fun `duplicate timestamps are not treated as evidence`() {
        val duplicate = TaskLog(taskId = 1, timestamp = 1_000L)
        val result = analyzer.analyze(listOf(duplicate, duplicate, duplicate, duplicate), utc)

        assertNull(result)
    }

    private fun logsAtHours(vararg hours: Int): List<TaskLog> = hours.mapIndexed { index, hour ->
        TaskLog(id = index.toLong(), taskId = 1, timestamp = BASE + hour * HOUR)
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    private companion object {
        const val HOUR = 3_600_000L
        const val BASE = 1_767_225_600_000L
    }
}

