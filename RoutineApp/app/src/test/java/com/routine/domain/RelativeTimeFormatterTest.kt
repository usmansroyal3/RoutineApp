package com.routine.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeFormatterTest {
    private val now = 10 * DAY

    @Test fun `formats missing completion`() =
        assertEquals("Never", RelativeTimeFormatter.format(null, now))

    @Test fun `formats recent completion`() =
        assertEquals("Just now", RelativeTimeFormatter.format(now - 15 * MINUTE, now))

    @Test fun `formats hours`() =
        assertEquals("2 hours ago", RelativeTimeFormatter.format(now - 2 * HOUR, now))

    @Test fun `formats days`() =
        assertEquals("2 days ago", RelativeTimeFormatter.format(now - 2 * DAY, now))

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

